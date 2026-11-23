package com.coleccionenlaces.app.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de [ServicioComprobacionVideo] (M5, Fix 5): la
 * pre-comprobación de la página del plugin de Facebook con OkHttp.
 * Ninguna prueba toca internet: el cliente usa interceptores que
 * simulan la respuesta del servidor (mismo patrón que
 * `RepositorioEnlacesTest`). Los HTML son representativos de las
 * muestras medidas en fix-5-diagnostico.md §5.5 (error: "Video
 * Unavailable" ×1, `<video>` ×0, `.mp4` ×0; bueno: 0 / 1 / 14).
 */
class ServicioComprobacionVideoTest {

    /** HTML del plugin para el caso de error (medido). */
    private val htmlError = """
        <html><body>
        <div class="_3i0o _50f7">Video Unavailable</div>
        <div class="_3i0p">This video may no longer exist, or you don't have permission to view it.</div>
        </body></html>
    """.trimIndent()

    /** HTML del plugin en español (el servidor puede devolverlo así). */
    private val htmlErrorEs = """
        <html><body>
        <div class="_3i0o _50f7">Video no disponible</div>
        <div class="_3i0p">Es posible que este video ya no exista o que no tengas permiso para verlo.</div>
        </body></html>
    """.trimIndent()

    /** HTML del plugin para el caso bueno: 1 <video> y 14 .mp4. */
    private val htmlBueno =
        "<html><body><video src=\"https://video.xx.fbcdn.net/v/1.mp4\"></video>" +
            (2..14).joinToString("") { "<a href=\"https://video.xx.fbcdn.net/v/$it.mp4\">x</a>" } +
            "</body></html>"

    /** Cliente que responde siempre con (código, cuerpo). */
    private fun clienteFijo(codigo: Int, cuerpo: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(codigo)
                    .message(if (codigo == 200) "OK" else "Error")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()

    /** Cliente que falla como si no hubiera red, y registra si se llamó. */
    private fun clienteSinRed(llamado: BooleanArray): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                llamado[0] = true
                throw IOException("sin red")
            }
            .build()

    private val urlFacebook = "https://www.facebook.com/share/r/1JqVPz7de"

    @Test
    fun `la pagina de error da NO_DISPONIBLE`() = runBlocking {
        val servicio = ServicioComprobacionVideo(clienteFijo(200, htmlError))
        assertEquals(EstadoVideo.NO_DISPONIBLE, servicio.comprobar(urlFacebook))
    }

    @Test
    fun `la pagina de error en espanol da NO_DISPONIBLE`() = runBlocking {
        val servicio = ServicioComprobacionVideo(clienteFijo(200, htmlErrorEs))
        assertEquals(EstadoVideo.NO_DISPONIBLE, servicio.comprobar(urlFacebook))
    }

    @Test
    fun `el caso bueno con video y mp4 da DISPONIBLE`() = runBlocking {
        val servicio = ServicioComprobacionVideo(clienteFijo(200, htmlBueno))
        assertEquals(EstadoVideo.DISPONIBLE, servicio.comprobar(urlFacebook))
    }

    @Test
    fun `sin error y sin video no hay senal positiva y da NO_DISPONIBLE`() = runBlocking {
        val servicio = ServicioComprobacionVideo(
            clienteFijo(200, "<html><body><p>Una página cualquiera</p></body></html>"),
        )
        assertEquals(EstadoVideo.NO_DISPONIBLE, servicio.comprobar(urlFacebook))
    }

    @Test
    fun `un enlace que no es de Facebook da DISPONIBLE sin pedir nada`() = runBlocking {
        val llamado = booleanArrayOf(false)
        val servicio = ServicioComprobacionVideo(clienteSinRed(llamado))
        assertEquals(
            EstadoVideo.DISPONIBLE,
            servicio.comprobar("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertFalse("no se debe pedir nada para un enlace que no es de Facebook", llamado[0])
    }

    @Test
    fun `sin red da DESCONOCIDO y no bloquea el WebView`() = runBlocking {
        val servicio = ServicioComprobacionVideo(clienteSinRed(booleanArrayOf(false)))
        assertEquals(EstadoVideo.DESCONOCIDO, servicio.comprobar(urlFacebook))
    }

    @Test
    fun `un error HTTP da DESCONOCIDO`() = runBlocking {
        val servicio = ServicioComprobacionVideo(clienteFijo(500, "error del servidor"))
        assertEquals(EstadoVideo.DESCONOCIDO, servicio.comprobar(urlFacebook))
    }

    @Test
    fun `el HTML bueno tiene de verdad 1 video y 14 mp4`() {
        assertEquals(1, Regex("<video", RegexOption.IGNORE_CASE).findAll(htmlBueno).count())
        assertEquals(14, Regex("\\.mp4", RegexOption.IGNORE_CASE).findAll(htmlBueno).count())
        assertEquals(1, Regex("Video Unavailable").findAll(htmlError).count())
        assertEquals(0, Regex("<video").findAll(htmlError).count())
        assertEquals(0, Regex("\\.mp4").findAll(htmlError).count())
        assertTrue(htmlBueno.contains("Video Unavailable").not())
    }
}
