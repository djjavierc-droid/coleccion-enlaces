package com.coleccionenlaces.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.coleccionenlaces.app.data.EstadoVideo
import com.coleccionenlaces.app.domain.detectarVideoNoDisponible
import org.json.JSONTokener

/**
 * Reproductor oEmbed en un WebView, para capturar su cuadro como
 * miniatura (corrección 1.0.1, punto 3).
 *
 * Carga el HTML oficial que devuelve oEmbed (Facebook, TikTok,
 * Instagram, X, YouTube, Vimeo o Dailymotion) SIN autoplay:
 * `mediaPlaybackRequiresUserGesture = true` y, en TikTok, el
 * `autoplay=1` ya viene cambiado a `autoplay=0` en el HTML
 * ([com.coleccionenlaces.app.domain.quitarAutoplayTikTok]). El vídeo
 * NO se reproduce solo: solo se pinta su primer cuadro.
 * [baseUrl] es la URL del propio enlace: los recursos relativos del
 * HTML resuelven contra su origen.
 *
 * ## Tres capas contra la página de error (Fix 5, ver
 * fix-5-diagnostico.md)
 *
 * El mecanismo de Fix 4 está MUERTO y no se usa: leía
 * `document.body.innerText` del documento principal, que es siempre
 * `""` (longitud 0) porque el reproductor vive dentro de un iframe, y
 * `innerText` nunca incluye contenido de iframes. La detección nunca
 * se disparaba y la captura procedía como antes.
 *
 * - **M5 (primario):** antes de montar el WebView, [comprobarVideoDisponible]
 *   pre-comprueba la página del plugin de Facebook con OkHttp
 *   (`plugins/video.php?href=...`): si la página no trae ninguna
 *   señal positiva de cuadro real (ni `<video>` ni `.mp4`), el
 *   WebView NO se monta y se avisa con [onVideoNoDisponible]. La
 *   decisión es SOLO por señal positiva, nunca por el texto de error
 *   (investigación de 1 hora, hilo C): la página buena contiene
 *   "Video unavailable" como respaldo y el error de copyright no lo
 *   contiene. Para enlaces que no son de Facebook (o si la
 *   comprobación no se pudo hacer) el WebView se monta como antes.
 * - **M2 (texto):** tras cargar la página, el texto se lee dentro
 *   del iframe (`iframe.contentDocument.body.innerText`, mismo
 *   origen) con sondeo cada segundo hasta ~8 s. El texto SOLO
 *   enriquece el título/descripción con [onTextoDetectado] (en Fix 4
 *   nunca se disparaba para Facebook): el texto de error NUNCA se
 *   entrega (el clasificador lo tomaría por Tecnología 0,737 —
 *   peligro medido en la investigación de 1 hora, hilo D) y el
 *   texto NUNCA decide si hay error (eso lo hacen M5 y M3).
 * - **M3 (red de seguridad):** antes de entregar el cuadro, se mide
 *   su riqueza ([esBitmapSinCuadroReal]): un bitmap con muy pocos
 *   colores distintos y oscuro es la caja de error, no un cuadro
 *   real, y no se entrega.
 *
 * Regla honesta: nunca se guarda una miniatura sin una señal positiva
 * de cuadro real. Sin cuadro, la tarjeta muestra su marcador propio.
 *
 * La lectura del texto y la captura del cuadro no se pueden probar en
 * pruebas unitarias: solo verificable en dispositivo. Lo que SÍ se
 * prueba en la JVM es el filtro del texto de error
 * ([detectarVideoNoDisponible]) y la riqueza del bitmap
 * ([esBitmapSinCuadroReal]).
 */
@Composable
fun MiniaturaWebView(
    html: String,
    baseUrl: String,
    onMiniaturaCapturada: (Bitmap) -> Unit,
    onTextoDetectado: (String) -> Unit,
    onVideoNoDisponible: () -> Unit = {},
    comprobarVideoDisponible: (suspend () -> EstadoVideo)? = null,
    modifier: Modifier = Modifier,
) {
    var capturaEntregada by remember { mutableStateOf(false) }
    var textoEntregado by remember { mutableStateOf(false) }
    var errorAvisado by remember { mutableStateOf(false) }
    // M5: null = comprobando; el WebView solo se monta si el vídeo
    // está disponible o no se pudo comprobar.
    var estadoComprobacion by remember(html, baseUrl) {
        mutableStateOf<EstadoVideo?>(null)
    }

    LaunchedEffect(html, baseUrl) {
        estadoComprobacion =
            comprobarVideoDisponible?.invoke() ?: EstadoVideo.DISPONIBLE
        if (estadoComprobacion == EstadoVideo.NO_DISPONIBLE && !errorAvisado) {
            errorAvisado = true
            onVideoNoDisponible()
        }
    }

    if (estadoComprobacion == EstadoVideo.DISPONIBLE ||
        estadoComprobacion == EstadoVideo.DESCONOCIDO
    ) {
        AndroidView(
            factory = { contexto ->
                WebView(contexto).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Sin autoplay: el vídeo no se reproduce solo.
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            programarCaptura(
                                view = view,
                                entregada = { capturaEntregada },
                                marcarEntregada = { capturaEntregada = true },
                                textoEntregado = { textoEntregado },
                                marcarTextoEntregado = { textoEntregado = true },
                                errorAvisado = { errorAvisado },
                                marcarErrorAvisado = { errorAvisado = true },
                                intentosRestantes = SONDEOS_MAXIMOS,
                                esperaMs = ESPERA_PINTADO_MS,
                                onMiniaturaCapturada = onMiniaturaCapturada,
                                onTextoDetectado = onTextoDetectado,
                                onVideoNoDisponible = onVideoNoDisponible,
                            )
                        }
                    }
                    // El HTML del oEmbed usa URLs absolutas (SDK del
                    // proveedor); la base es la URL del propio enlace
                    // para que lo relativo resuelva contra su origen.
                    loadDataWithBaseURL(
                        baseUrl,
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            },
            update = { /* El HTML no cambia: no se recarga al recomponer. */ },
            onRelease = { webView -> webView.destroy() },
            modifier = modifier,
        )
    }
}

/**
 * Espera a que el reproductor se pinte, lee su texto visible (M2) y,
 * solo si el cuadro es un cuadro real (M3), captura el cuadro.
 *
 * Sondeo (M2): la primera lectura es a los 2 s (lo que tarda el
 * reproductor en pintarse) y luego cada segundo hasta ~8 s. El texto
 * del error tarda en aparecer dentro del iframe (en la reproducción
 * del diagnóstico estaba vacío a los 2 s y legible a los 10 s), así
 * que una sola lectura a los 2 s no basta. El cuadro se captura en
 * el primer intento en que ya está pintado y no es la caja de error;
 * si a los 8 s sigue en blanco, se rinde sin capturar nada.
 *
 * El texto NUNCA decide si hay error (investigación de 1 hora, hilo
 * C: el texto falla en ambos sentidos): solo M5 (señal positiva
 * `<video>`/`.mp4` antes de montar) y M3 (riqueza del bitmap)
 * deciden. El texto solo enriquece, y el texto de error nunca se
 * entrega: el clasificador lo tomaría por Tecnología 0,737 (hilo D).
 */
private fun programarCaptura(
    view: WebView,
    entregada: () -> Boolean,
    marcarEntregada: () -> Unit,
    textoEntregado: () -> Boolean,
    marcarTextoEntregado: () -> Unit,
    errorAvisado: () -> Boolean,
    marcarErrorAvisado: () -> Unit,
    intentosRestantes: Int,
    esperaMs: Long,
    onMiniaturaCapturada: (Bitmap) -> Unit,
    onTextoDetectado: (String) -> Unit,
    onVideoNoDisponible: () -> Unit,
) {
    Handler(Looper.getMainLooper()).postDelayed({
        if (view.width <= 0 || view.height <= 0) return@postDelayed
        view.evaluateJavascript(JS_TEXTO_REPRODUCTOR) { resultado ->
            val texto = decodificarCadenaJs(resultado)?.trim().orEmpty()
            // M2: el texto solo enriquece. El texto de error nunca se
            // entrega (el clasificador lo tomaría por Tecnología
            // 0,737) y nunca decide si hay error: eso lo hacen M5 y M3.
            if (!textoEntregado() && texto.isNotBlank() &&
                !detectarVideoNoDisponible(texto)
            ) {
                marcarTextoEntregado()
                onTextoDetectado(texto)
            }
            if (!entregada()) {
                val bitmap = capturarCuadro(view)
                if (bitmap != null && !esBitmapSinCuadroReal(bitmap)) {
                    marcarEntregada()
                    onMiniaturaCapturada(bitmap)
                } else if (bitmap != null) {
                    // M3: el cuadro parece la caja de error (pocos
                    // colores y oscuro): no es un cuadro real.
                    if (!errorAvisado()) {
                        marcarErrorAvisado()
                        onVideoNoDisponible()
                    }
                } else if (intentosRestantes > 0) {
                    // En blanco: se sigue sondeando hasta ~8 s.
                    programarCaptura(
                        view = view,
                        entregada = entregada,
                        marcarEntregada = marcarEntregada,
                        textoEntregado = textoEntregado,
                        marcarTextoEntregado = marcarTextoEntregado,
                        errorAvisado = errorAvisado,
                        marcarErrorAvisado = marcarErrorAvisado,
                        intentosRestantes = intentosRestantes - 1,
                        esperaMs = SONDEO_MS,
                        onMiniaturaCapturada = onMiniaturaCapturada,
                        onTextoDetectado = onTextoDetectado,
                        onVideoNoDisponible = onVideoNoDisponible,
                    )
                }
            }
        }
    }, esperaMs)
}

/**
 * Texto visible del reproductor: el del documento principal MÁS el
 * de cada iframe del mismo origen (M2). `innerText` nunca incluye
 * iframes, y el reproductor de Facebook vive entero en uno: leer
 * solo el documento principal es siempre "" (causa raíz de Fix 5).
 * El acceso a un iframe de otro origen lanza SecurityError y se
 * ignora (ese iframe no se puede leer: lo cubren M5 y M3).
 */
internal const val JS_TEXTO_REPRODUCTOR =
    "(function(){var texto='';" +
        "if(document.body&&document.body.innerText){texto=document.body.innerText;}" +
        "var marcos=document.getElementsByTagName('iframe');" +
        "for(var i=0;i<marcos.length;i++){" +
        "try{var doc=marcos[i].contentDocument;" +
        "if(doc&&doc.body&&doc.body.innerText){texto+='\\n'+doc.body.innerText;}" +
        "}catch(e){}}" +
        "return texto;})();"

/**
 * El cuadro del WebView como bitmap, o null si sale en blanco (todos
 * los píxeles muestreados iguales: página aún sin pintar).
 */
private fun capturarCuadro(view: WebView): Bitmap? {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    if (esBitmapEnBlanco(bitmap)) return null
    return bitmap
}

/** ¿Son todos los píxeles muestreados del mismo color? */
private fun esBitmapEnBlanco(bitmap: Bitmap): Boolean {
    val ancho = bitmap.width
    val alto = bitmap.height
    if (ancho <= 0 || alto <= 0) return true
    val primero = bitmap.getPixel(0, 0)
    val pasoX = maxOf(1, ancho / 10)
    val pasoY = maxOf(1, alto / 10)
    var y = 0
    while (y < alto) {
        var x = 0
        while (x < ancho) {
            if (bitmap.getPixel(x, y) != primero) return false
            x += pasoX
        }
        y += pasoY
    }
    return true
}

/** Riqueza de un bitmap: colores distintos y luminancia media. */
internal data class RiquezaBitmap(
    val coloresDistintos: Int,
    val luminanciaMedia: Double,
)

/** Mide la riqueza de [bitmap] con un muestreo en rejilla 100x100 (hasta 10.000 píxeles). */
internal fun medirRiquezaBitmap(bitmap: Bitmap): RiquezaBitmap {
    val ancho = bitmap.width
    val alto = bitmap.height
    if (ancho <= 0 || alto <= 0) return RiquezaBitmap(0, 0.0)
    val colores = HashSet<Int>()
    var sumaLuminancia = 0L
    var n = 0
    // Rejilla 100x100: con 10x10 solo habría 100 muestras y el
    // conteo de colores distintos nunca podría pasar de 100 (el
    // umbral de 500 no tendría sentido). Con 10.000 muestras, un
    // cuadro real tiene miles de colores y la caja de error, pocos.
    val pasoX = maxOf(1, ancho / 100)
    val pasoY = maxOf(1, alto / 100)
    var y = 0
    while (y < alto) {
        var x = 0
        while (x < ancho) {
            val pixel = bitmap.getPixel(x, y)
            colores.add(pixel)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            sumaLuminancia += (299 * r + 587 * g + 114 * b) / 1000
            n++
            x += pasoX
        }
        y += pasoY
    }
    return RiquezaBitmap(colores.size, if (n == 0) 0.0 else sumaLuminancia.toDouble() / n)
}

/**
 * M3: ¿parece la pantalla de error de Facebook y no un cuadro real?
 *
 * La caja de error es gris muy oscuro (RGB ~24) con texto blanco:
 * pocos colores distintos y luminancia baja. Un cuadro real de vídeo,
 * hasta en su mitad oscura, tiene miles de colores. Medido sobre
 * recortes JPEG de la captura de Javier (diagnóstico §5.3, indicativo,
 * no calibrado): caja de error 810–1.030 colores, luminancia 42,7–48,2;
 * cuadro real 4.088–62.542 colores. El umbral de 500 es conservador a
 * propósito (el JPEG infla el conteo del error; el PNG real que
 * analiza la app tendrá menos colores que el recorte JPEG): puede
 * dejar pasar alguna caja de error, pero no rechaza cuadros reales.
 * Debe recalibrarse con los bitmaps PNG reales que captura la app.
 */
internal fun esBitmapSinCuadroReal(bitmap: Bitmap): Boolean {
    val riqueza = medirRiquezaBitmap(bitmap)
    return riqueza.coloresDistintos < UMBRAL_COLORES_SIN_CUADRO &&
        riqueza.luminanciaMedia < UMBRAL_LUMINANCIA_SIN_CUADRO
}

/** Colores distintos por debajo de los cuales un bitmap no es un cuadro real (M3). */
internal const val UMBRAL_COLORES_SIN_CUADRO = 500

/** Luminancia media por debajo de la cual un bitmap no es un cuadro real (M3). */
internal const val UMBRAL_LUMINANCIA_SIN_CUADRO = 60.0

/**
 * Una cadena JavaScript devuelta por `evaluateJavascript` viene
 * codificada como JSON (`"texto"` con escapes): se decodifica o
 * null si no es una cadena.
 */
private fun decodificarCadenaJs(resultado: String?): String? {
    if (resultado.isNullOrBlank() || resultado == "null") return null
    return try {
        JSONTokener(resultado).nextValue() as? String
    } catch (_: Exception) {
        null
    }
}

/** Espera al primer intento de captura (lo que tarda el reproductor en pintarse). */
private const val ESPERA_PINTADO_MS = 2000L

/** Pausa entre sondeos del iframe (M2). */
private const val SONDEO_MS = 1000L

/** Sondeos tras el primer intento: 2 s + 6 × 1 s = ~8 s en total. */
private const val SONDEOS_MAXIMOS = 6
