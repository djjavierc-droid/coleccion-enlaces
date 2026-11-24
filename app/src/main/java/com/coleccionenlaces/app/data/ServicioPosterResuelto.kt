package com.coleccionenlaces.app.data

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.coleccionenlaces.app.domain.PosterResuelto
import com.coleccionenlaces.app.domain.extraerOgImage
import com.coleccionenlaces.app.domain.extraerUrlsVideoMp4
import com.coleccionenlaces.app.domain.parsearHtml
import com.coleccionenlaces.app.domain.resolverUrlCanonicaFacebook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Póster resuelto de un vídeo de Facebook (Fix 5, fuente de cuadros
 * resuelta).
 *
 * Ruta medida en `miniatura-real-curl.md` §8: pedir la página
 * canónica del vídeo (`/reel/<id>/` o `/watch/?v=<id>`) con
 * User-Agent de rastreador entrega `og:title`, `og:image` (el póster
 * real, 157.257 bytes, JPEG 1312×1576, descargable sin sesión con el
 * UA de la app) y, con Googlebot, hasta 3 URLs `.mp4` directas
 * (descargables sin sesión: HEAD 200, rango → 206, MP4 válido).
 *
 * La clase y sus funciones son `open` solo para que las pruebas del
 * repositorio puedan sustituir la extracción del cuadro (que usa
 * `MediaMetadataRetriever`, solo disponible en el teléfono) por un
 * falso que escriba una imagen válida; la resolución por red se
 * prueba con un OkHttpClient con interceptor, como el resto de
 * servicios.
 */
open class ServicioPosterResuelto(
    private val cliente: OkHttpClient = crearCliente(),
) {
    /**
     * Resuelve el póster de [urlEnlace], o null si no se puede.
     *
     * Solo se intenta cuando la URL ya es canónica (ver
     * [resolverUrlCanonicaFacebook]): un `/share/r/` devuelve null
     * sin tocar la red, hasta que la investigación de una hora
     * resuelva ese hueco. La página se pide primero con
     * `facebookexternalhit` y, si no responde, con `Googlebot`.
     * Solo hay póster si la página trae `og:image` (meta, no el
     * respaldo `<img>` del muro de login) o URLs `.mp4` directas.
     * Nunca lanza: cualquier fallo de red o de parseo es null.
     */
    open suspend fun resolver(urlEnlace: String): PosterResuelto? =
        withContext(Dispatchers.IO) {
            val urlCanonica = resolverUrlCanonicaFacebook(urlEnlace)
                ?: return@withContext null
            val html = descargarPaginaRastreador(urlCanonica)
                ?: return@withContext null
            val vistaPrevia = parsearHtml(html, urlCanonica)
            val urlImagenReal = extraerOgImage(html, urlCanonica)
            val urlsVideo = extraerUrlsVideoMp4(html)
            if (urlImagenReal == null && urlsVideo.isEmpty()) {
                return@withContext null
            }
            PosterResuelto(
                vistaPrevia = vistaPrevia.copy(urlImagen = urlImagenReal),
                urlsVideo = urlsVideo,
            )
        }

    /**
     * Elige la rendition de VÍDEO de [urlsVideo] por bitrate
     * (investigación de 1 hora, hilo B, §1): la primera `.mp4` de la
     * lista puede ser solo audio (60.394 bps, 110.435 B, stream AAC
     * único). Hace un HEAD por URL y elige la de mayor Content-Length
     * con Content-Type `video/mp4` (el audio es ~20 veces más pequeño
     * que el vídeo: 110 KB frente a 2,2 MB en Yofer). Si ningún HEAD
     * responde, devuelve la primera URL (la descarga decidirá).
     * Nunca lanza: lista vacía → null.
     */
    open suspend fun elegirVideoPorBitrate(urlsVideo: List<String>): String? =
        withContext(Dispatchers.IO) {
            if (urlsVideo.isEmpty()) return@withContext null
            var mejor: String? = null
            var mejorTamano = -1L
            for (url in urlsVideo) {
                val tamano = try {
                    val peticion = Request.Builder()
                        .url(url)
                        .head()
                        .header("User-Agent", ServicioVistaPrevia.USER_AGENT)
                        .build()
                    cliente.newCall(peticion).execute().use { respuesta ->
                        val esVideo = respuesta.header("Content-Type")
                            ?.startsWith("video/", ignoreCase = true) == true
                        if (!respuesta.isSuccessful || !esVideo) return@use -1L
                        respuesta.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                } catch (_: Exception) {
                    -1L
                }
                if (tamano > mejorTamano) {
                    mejorTamano = tamano
                    mejor = url
                }
            }
            mejor ?: urlsVideo.firstOrNull()
        }

    /**
     * Saca un cuadro del vídeo directo [urlVideo] y lo guarda como
     * JPEG en [destino]. Descarga solo los primeros
     * [BYTES_VIDEO_PARCIAL] del archivo (el `moov` y los primeros
     * cuadros clave suelen ir al principio en los `.mp4` de
     * Facebook, preparados para streaming) y extrae el cuadro del
     * segundo 1 con `MediaMetadataRetriever`. Devuelve true si el
     * cuadro quedó guardado. Nunca lanza: si el archivo parcial no
     * trae el `moov`, si no hay cuadro clave o si falla la red, es
     * false y el flujo sigue con el WebView.
     *
     * Solo corre de verdad en el teléfono: `MediaMetadataRetriever`
     * no existe en la JVM de CI.
     */
    open suspend fun extraerCuadroDeVideo(urlVideo: String, destino: File): Boolean =
        withContext(Dispatchers.IO) {
            val temporal = try {
                File.createTempFile("cuadro-video-", ".mp4", destino.parentFile)
            } catch (_: Exception) {
                return@withContext false
            }
            try {
                val peticion = Request.Builder()
                    .url(urlVideo)
                    .header("Range", "bytes=0-${BYTES_VIDEO_PARCIAL - 1}")
                    .header("User-Agent", ServicioVistaPrevia.USER_AGENT)
                    .build()
                cliente.newCall(peticion).execute().use { respuesta ->
                    if (!respuesta.isSuccessful && respuesta.code != 206) {
                        return@withContext false
                    }
                    val cuerpo = respuesta.body ?: return@withContext false
                    FileOutputStream(temporal).use { salida ->
                        cuerpo.byteStream().copyTo(salida)
                    }
                }
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(temporal.absolutePath)
                    val cuadro = retriever.getFrameAtTime(
                        1_000_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    ) ?: return@withContext false
                    FileOutputStream(destino).use { salida ->
                        cuadro.compress(Bitmap.CompressFormat.JPEG, 90, salida)
                    }
                    cuadro.recycle()
                    true
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                false
            } finally {
                temporal.delete()
            }
        }

    /**
     * Descarga la página canónica con User-Agent de rastreador,
     * probando primero `facebookexternalhit` y luego `Googlebot`.
     * Null si ninguna responde 200 con cuerpo.
     */
    private fun descargarPaginaRastreador(urlCanonica: String): String? {
        for (ua in UAS_RASTREADOR) {
            try {
                val peticion = Request.Builder()
                    .url(urlCanonica)
                    .header("User-Agent", ua)
                    .build()
                cliente.newCall(peticion).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) return@use
                    val cuerpo = respuesta.body?.string().orEmpty()
                    if (cuerpo.isNotBlank()) return cuerpo
                }
            } catch (_: Exception) {
                // Siguiente User-Agent.
            }
        }
        return null
    }

    companion object {
        /**
         * User-Agents de rastreador (medido en
         * `miniatura-real-curl.md` §8): con el UA de la app la
         * página canónica responde 400 (muro de login); con estos
         * dos responde 200 con `og:title`, `og:image` y `.mp4`.
         *
         * NOTA: estos UA presentan la petición como el rastreador de
         * Meta / de Google. Es lo que la medición exige para que la
         * ruta funcione; si el responsable del proyecto decide que
         * no se use, basta cambiar esta lista (el resto del flujo
         * sigue igual y el póster simplemente no se resuelve).
         */
        val UAS_RASTREADOR = listOf(
            "facebookexternalhit/1.1",
            "Googlebot/2.1",
        )

        /** Primeros bytes del `.mp4` que se descargan para el cuadro. */
        const val BYTES_VIDEO_PARCIAL = 4 * 1024 * 1024L

        fun crearCliente(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .build()

        private const val TIMEOUT_SEGUNDOS = 10L
    }
}
