package com.coleccionenlaces.app.data

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Estado de un vídeo de Facebook según la pre-comprobación (M5).
 *
 * - [DISPONIBLE]: la página del plugin trae reproductor (al menos un
 *   `<video>` o una referencia `.mp4`).
 * - [NO_DISPONIBLE]: la página del plugin no trae ninguna señal
 *   positiva de cuadro real (ni `<video>` ni `.mp4`).
 * - [DESCONOCIDO]: no se pudo comprobar (sin red, timeout, error
 *   HTTP): no se bloquea el WebView; deciden M2 y M3.
 *
 * La decisión es SOLO por señal positiva (investigación de 1 hora,
 * hilo C, §5): NUNCA por el texto de error. La página BUENA de
 * Yofer contiene el texto "Video unavailable" como respaldo (falso
 * positivo inverso si se detecta por texto), y el error nuevo de
 * copyright ("Facebook Unavailable — This video can't be embedded
 * because it may contain content owned by someone else") NO contiene
 * "Video Unavailable" (falso negativo). Detectar por texto de error
 * falla en ambos sentidos.
 */
enum class EstadoVideo {
    DISPONIBLE,
    NO_DISPONIBLE,
    DESCONOCIDO,
}

/**
 * Pre-comprobación del vídeo con OkHttp ANTES de montar el WebView
 * (M5, Fix 5 — el detector primario del diagnóstico
 * fix-5-diagnostico.md).
 *
 * El mecanismo de Fix 4 no puede funcionar nunca: `innerText` del
 * documento principal es siempre "" porque el reproductor vive en un
 * iframe. El error, además, lo sirve Facebook del lado del servidor
 * para peticiones anónimas: la página del plugin ya trae "Video
 * Unavailable" y cero `<video>`, cero `.mp4`, cero póster — para esos
 * enlaces no existe ningún cuadro real que capturar (el vídeo se ve
 * en la app de Facebook porque ahí Javier tiene sesión).
 *
 * La URL del plugin es determinista (la misma forma que construye el
 * SDK): `plugins/video.php?href=<URL>&show_text=false&width=500`.
 * Medido (diagnóstico §5 + verificación propia 2026-11-23 con el
 * enlace de reproducción 1JqVPz7de + investigación de 1 hora, hilo C):
 * - error "Video Unavailable": `<video>` ×0, `.mp4` ×0;
 * - error nuevo de copyright: tampoco trae "Video Unavailable",
 *   `<video>` ×0, `.mp4` ×0;
 * - caso bueno (reel de Yofer): 1 `<video>`, 14 `.mp4` (Y contiene
 *   el texto "Video unavailable" como respaldo: por eso la decisión
 *   es solo por señal positiva, nunca por texto).
 *
 * Límite honesto: M5 se apoya en pocas muestras contrastadas (2
 * errores + 1 bueno + 1 débil); conviene ampliarlas por tipo de
 * vídeo. Por eso no es el único detector: M2 (sondeo del iframe)
 * confirma y M3 (riqueza del bitmap) es la red de seguridad. La
 * regla que une a los tres: nunca guardar una miniatura sin una
 * señal positiva de cuadro real.
 *
 * Solo comprueba enlaces de Facebook: para el resto de plataformas
 * devuelve [EstadoVideo.DISPONIBLE] sin red.
 */
class ServicioComprobacionVideo(
    private val cliente: OkHttpClient = crearCliente(),
) {

    /**
     * ¿El vídeo de [urlEnlace] se puede incrustar? Nunca lanza: sin
     * red o con error HTTP devuelve [EstadoVideo.DESCONOCIDO].
     *
     * Decisión SOLO por señal positiva (investigación de 1 hora,
     * hilo C): DISPONIBLE si la página del plugin trae al menos un
     * `<video>` o una referencia `.mp4`; NO_DISPONIBLE si no trae
     * ninguna. El texto de la página NO se consulta: la página
     * buena contiene "Video unavailable" como respaldo y el error
     * de copyright no lo contiene, así que el texto falla en ambos
     * sentidos.
     */
    suspend fun comprobar(urlEnlace: String): EstadoVideo {
        if (!esFacebook(urlEnlace)) return EstadoVideo.DISPONIBLE
        return try {
            withContext(Dispatchers.IO) {
                val urlPlugin =
                    "https://www.facebook.com/plugins/video.php" +
                        "?href=${URLEncoder.encode(urlEnlace.trim(), "UTF-8")}" +
                        "&show_text=false&width=500"
                val peticion = Request.Builder()
                    .url(urlPlugin)
                    .header("User-Agent", ServicioVistaPrevia.USER_AGENT)
                    .build()
                cliente.newCall(peticion).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) return@withContext EstadoVideo.DESCONOCIDO
                    val cuerpo = respuesta.body?.string().orEmpty()
                    val videos = REGEX_VIDEO.findAll(cuerpo).count()
                    val mp4 = REGEX_MP4.findAll(cuerpo).count()
                    if (videos > 0 || mp4 > 0) {
                        EstadoVideo.DISPONIBLE
                    } else {
                        // Sin señal positiva de cuadro real: no se
                        // monta el WebView (regla honesta de Fix 5).
                        EstadoVideo.NO_DISPONIBLE
                    }
                }
            }
        } catch (_: Exception) {
            EstadoVideo.DESCONOCIDO
        }
    }

    private fun esFacebook(url: String): Boolean {
        val limpio = url.trim().lowercase()
        return limpio.contains("facebook.com") || limpio.contains("fb.watch")
    }

    companion object {
        private val REGEX_VIDEO = Regex("<video", RegexOption.IGNORE_CASE)
        private val REGEX_MP4 = Regex("\\.mp4", RegexOption.IGNORE_CASE)

        /** Espera máxima de la pre-comprobación (una petición pequeña). */
        const val TIMEOUT_SEGUNDOS = 10L

        fun crearCliente(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .build()
    }
}
