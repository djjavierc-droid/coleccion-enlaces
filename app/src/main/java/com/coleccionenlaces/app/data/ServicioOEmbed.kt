package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.ProveedorOEmbed
import com.coleccionenlaces.app.domain.ResultadoOEmbed
import com.coleccionenlaces.app.domain.TipoErrorOEmbed
import com.coleccionenlaces.app.domain.construirUrlOEmbed
import com.coleccionenlaces.app.domain.construirUrlOEmbedPostFacebook
import com.coleccionenlaces.app.domain.parsearOEmbed
import com.coleccionenlaces.app.domain.proveedorOEmbedDe
import com.coleccionenlaces.app.domain.quitarAutoplayTikTok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Servicio oEmbed sin token ni cuenta (corrección 1.0.1, punto 3).
 *
 * Sin servidor, sin cuenta, sin token y sin fingir ser el rastreador
 * de Meta: son simples GET públicos a los endpoints oEmbed de cada
 * proveedor. Solo funciona con contenido PÚBLICO; con contenido no
 * público o inexistente el endpoint da error y el enlace se guarda
 * igual con el título editable (Fix 1).
 *
 * - YouTube: título, miniatura y HTML del reproductor.
 * - TikTok: solo el HTML del reproductor (sin título ni miniatura en
 *   las respuestas probadas); se le quita el `autoplay=1` antes de
 *   cargarlo en el WebView, para que no reproduzca solo.
 * - Facebook: `oembed_video` y, si da error 100 (parámetro
 *   inválido), `oembed_post`. Solo devuelve el HTML del reproductor.
 *
 * Sin reintentos: los errores de oEmbed son deterministas en su
 * mayoría (contenido no público, parámetro inválido); un fallo de
 * red se devuelve como [TipoErrorOEmbed.RED] y el enlace se guarda
 * igual con el título automático (texto compartido o dominio).
 *
 * @param cliente Cliente OkHttp a usar (inyectable para las pruebas,
 *   que lo sustituyen por interceptores sin red).
 */
class ServicioOEmbed(
    private val cliente: OkHttpClient = crearCliente(),
) {

    /**
     * Obtiene el oEmbed del vídeo o publicación de [url].
     * Devuelve [ResultadoOEmbed.Error] con `NO_DISPONIBLE` si la URL
     * no es de YouTube, TikTok ni Facebook (sin tocar la red).
     */
    suspend fun obtenerVideo(url: String): ResultadoOEmbed {
        val proveedor = proveedorOEmbedDe(url.trim())
            ?: return ResultadoOEmbed.Error(TipoErrorOEmbed.NO_DISPONIBLE)
        return when (proveedor) {
            ProveedorOEmbed.YOUTUBE, ProveedorOEmbed.TIKTOK -> {
                val resultado = pedir(construirUrlOEmbed(proveedor, url.trim()))
                if (proveedor == ProveedorOEmbed.TIKTOK &&
                    resultado is ResultadoOEmbed.Exito
                ) {
                    resultado.copy(html = quitarAutoplayTikTok(resultado.html))
                } else {
                    resultado
                }
            }
            ProveedorOEmbed.FACEBOOK -> {
                val video = pedir(construirUrlOEmbed(proveedor, url.trim()))
                if (video is ResultadoOEmbed.Error &&
                    video.tipo == TipoErrorOEmbed.PARAMETRO_INVALIDO
                ) {
                    pedir(construirUrlOEmbedPostFacebook(url.trim()))
                } else {
                    video
                }
            }
        }
    }

    /** Un GET al endpoint oEmbed y el parseo de su respuesta. */
    private suspend fun pedir(endpoint: String): ResultadoOEmbed =
        withContext(Dispatchers.IO) {
            try {
                val peticion = Request.Builder().url(endpoint).build()
                cliente.newCall(peticion).execute().use { respuesta ->
                    val cuerpo = respuesta.body?.string().orEmpty()
                    if (!respuesta.isSuccessful) {
                        // Graph devuelve el error también en JSON
                        // (HTTP 400 con `{"error":{"code":100,...}}`).
                        val interpretado = parsearOEmbed(cuerpo)
                        if (interpretado is ResultadoOEmbed.Error) interpretado
                        else ResultadoOEmbed.Error(TipoErrorOEmbed.RED)
                    } else {
                        parsearOEmbed(cuerpo)
                    }
                }
            } catch (_: IOException) {
                ResultadoOEmbed.Error(TipoErrorOEmbed.RED)
            } catch (_: Exception) {
                ResultadoOEmbed.Error(TipoErrorOEmbed.RESPUESTA_INVALIDA)
            }
        }

    companion object {
        /** Timeout por intento (igual que la vista previa). */
        const val TIMEOUT_SEGUNDOS = 15L

        fun crearCliente(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .build()
    }
}
