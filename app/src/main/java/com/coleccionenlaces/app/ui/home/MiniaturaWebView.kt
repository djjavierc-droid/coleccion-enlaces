package com.coleccionenlaces.app.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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
 * Al terminar de cargar la página espera unos 2 segundos a que el
 * reproductor se pinte, lee primero su texto visible y SOLO captura
 * el cuadro si ese texto no es la página de error de Facebook (Fix
 * 4, bug 3): si el reproductor muestra "Video no disponible", el
 * cuadro NO se captura ni se guarda, y se avisa con
 * [onVideoNoDisponible] para que la tarjeta muestre su marcador
 * propio en vez de la página de error como imagen. Antes la captura
 * solo rechazaba bitmaps en blanco, y la página de error (que no es
 * en blanco) se guardaba como miniatura local.
 *
 * La lectura del texto y la captura del cuadro no se pueden probar
 * en pruebas unitarias: solo verificable en dispositivo. Lo que SÍ
 * se prueba en la JVM es la decisión ([detectarVideoNoDisponible]).
 *
 * El texto vive dentro de un iframe y el WebView no siempre lo puede
 * leer (otro origen): si sale vacío, no se detecta nada y la captura
 * procede como antes. Es un límite honesto: la detección funciona
 * cuando el texto es legible, que es el caso medido (baseUrl del
 * propio enlace, mismo origen que el iframe del reproductor).
 */
@Composable
fun MiniaturaWebView(
    html: String,
    baseUrl: String,
    onMiniaturaCapturada: (Bitmap) -> Unit,
    onTextoDetectado: (String) -> Unit,
    onVideoNoDisponible: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var capturaEntregada by remember { mutableStateOf(false) }
    var textoEntregado by remember { mutableStateOf(false) }
    var reintentado by remember { mutableStateOf(false) }
    var errorAvisado by remember { mutableStateOf(false) }

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
                            reintentado = { reintentado },
                            marcarReintentado = { reintentado = true },
                            errorAvisado = { errorAvisado },
                            marcarErrorAvisado = { errorAvisado = true },
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

/** Espera a que el reproductor se pinte, lee su texto visible y, solo si no es la página de error, captura el cuadro. */
private fun programarCaptura(
    view: WebView,
    entregada: () -> Boolean,
    marcarEntregada: () -> Unit,
    textoEntregado: () -> Boolean,
    marcarTextoEntregado: () -> Unit,
    reintentado: () -> Boolean,
    marcarReintentado: () -> Unit,
    errorAvisado: () -> Boolean,
    marcarErrorAvisado: () -> Unit,
    onMiniaturaCapturada: (Bitmap) -> Unit,
    onTextoDetectado: (String) -> Unit,
    onVideoNoDisponible: () -> Unit,
) {
    Handler(Looper.getMainLooper()).postDelayed({
        if (view.width <= 0 || view.height <= 0) return@postDelayed
        // Primero el texto: si el reproductor muestra la página de
        // error ("Video no disponible"), NO se captura nada. El
        // texto se lee antes que el cuadro a propósito: capturar
        // primero y preguntar después es como se guardó la página
        // de error como miniatura en la v1.0.2.
        view.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : '';})();",
        ) { resultado ->
            val texto = decodificarCadenaJs(resultado)?.trim().orEmpty()
            if (detectarVideoNoDisponible(texto)) {
                if (!errorAvisado()) {
                    marcarErrorAvisado()
                    onVideoNoDisponible()
                }
                return@evaluateJavascript
            }
            if (!entregada()) {
                val bitmap = capturarCuadro(view)
                if (bitmap != null) {
                    marcarEntregada()
                    onMiniaturaCapturada(bitmap)
                } else if (!reintentado()) {
                    // En blanco: se le da una oportunidad más al
                    // reproductor para pintar, y ahí se rinde.
                    marcarReintentado()
                    programarCaptura(
                        view = view,
                        entregada = entregada,
                        marcarEntregada = marcarEntregada,
                        textoEntregado = textoEntregado,
                        marcarTextoEntregado = marcarTextoEntregado,
                        reintentado = reintentado,
                        marcarReintentado = marcarReintentado,
                        errorAvisado = errorAvisado,
                        marcarErrorAvisado = marcarErrorAvisado,
                        onMiniaturaCapturada = onMiniaturaCapturada,
                        onTextoDetectado = onTextoDetectado,
                        onVideoNoDisponible = onVideoNoDisponible,
                    )
                }
            }
            if (!textoEntregado() && texto.isNotBlank()) {
                marcarTextoEntregado()
                onTextoDetectado(texto)
            }
        }
    }, ESPERA_PINTADO_MS)
}

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

private const val ESPERA_PINTADO_MS = 2000L
