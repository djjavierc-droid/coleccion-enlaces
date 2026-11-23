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
 *   `<video>` o una referencia `.mp4`) y no trae el error.
 * - [NO_DISPONIBLE]: el servidor sirve "Video Unavailable" / "Video
 *   no disponible", o no hay ninguna señal positiva de cuadro real.
 * - [DESCONOCIDO]: no se pudo comprobar (sin red, timeout, error
 *   HTTP): no se bloquea el WebView; deciden M2 y M3.
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
 * enlace de reproducción 1JqVPz7de):
 * - error: "Video Unavailable" ×1, `<video>` ×0, `.mp4` ×0;
 * - caso bueno (reel de Yofer): 0 / 1 / 14.
 *
 * Límite honesto: M5 se apoya en pocas muestras contrastadas (1 error
 * + 1 bueno + 1 débil); conviene ampliarlas por tipo de vídeo. Por
 * eso no es el único detector: M2 (sondeo del iframe) confirma y M3
 * (riqueza del bitmap) es la red de seguridad. La regla que une a
 * los tres: nunca guardar una miniatura sin una señal positiva de
 * cuadro real.
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
                    val error = cuerpo.contains("Video Unavailable", ignoreCase = true) ||
                        cuerpo.contains("Video no disponible", ignoreCase = true)
                    val videos = REGEX_VIDEO.findAll(cuerpo).count()
                    val mp4 = REGEX_MP4.findAll(cuerpo).count()
                    when {
                        error -> EstadoVideo.NO_DISPONIBLE
                        videos > 0 || mp4 > 0 -> EstadoVideo.DISPONIBLE
                        // Sin señal positiva de cuadro real: no se
                        // monta el WebView (regla honesta de Fix 5).
                        else -> EstadoVideo.NO_DISPONIBLE
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
