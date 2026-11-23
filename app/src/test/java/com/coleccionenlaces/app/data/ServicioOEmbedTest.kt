package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.ResultadoOEmbed
import com.coleccionenlaces.app.domain.TipoErrorOEmbed
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
import java.io.IOException

/**
 * Pruebas del servicio oEmbed (corrección 1.0.1, punto 3).
 *
 * Sin red: el cliente OkHttp lleva interceptores que devuelven las
 * respuestas (la forma real verificada con curl el 2026-11-23).
 */
class ServicioOEmbedTest {

    private val jsonYouTube = """
        {"type":"video","version":"1.0","title":"Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)","author_name":"Rick Astley","provider_name":"YouTube","thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg","html":"<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\"></iframe>","width":480,"height":270}
    """.trimIndent()

    private val jsonFacebook = """
        {"html":"<div class=\"fb-video\" data-href=\"https://www.facebook.com/share/r/1JqVPz7de\"></div><script src=\"https://connect.facebook.net/es_LA/sdk.js#xfbml=1&version=v27.0\"></script>","provider_name":"Facebook","type":"video","version":"1.0","width":500}
    """.trimIndent()

    private val jsonTikTok = """
        {"version":"1.0","type":"video","provider_name":"TikTok","html":"<iframe src=\"https://www.tiktok.com/embed/v2/1?autoplay=1&muted=1\"></iframe>","width":"325","height":"578"}
    """.trimIndent()

    private val jsonError100 = """
        {"error":{"message":"(#100) Invalid parameter","type":"OAuthException","code":100}}
    """.trimIndent()

    /**
     * Cliente que responde según el prefijo de la URL pedida
     * (código + cuerpo) y anota cada URL pedida en [pedidas].
     * Sin prefijo que coincida: 404 con `{}`.
     */
    private fun clienteFalso(
        respuestas: Map<String, Pair<Int, String>>,
        pedidas: MutableList<String> = mutableListOf(),
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { cadena ->
            val url = cadena.request().url.toString()
            pedidas.add(url)
            val (codigo, cuerpo) = respuestas.entries
                .firstOrNull { (prefijo, _) -> url.startsWith(prefijo) }?.value
                ?: (404 to "{}")
            Response.Builder()
                .request(cadena.request())
                .protocol(Protocol.HTTP_1_1)
                .code(codigo)
                .message(if (codigo == 200) "OK" else "Error")
                .body(cuerpo.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `obtenerVideo de YouTube devuelve titulo miniatura y html`() = runBlocking {
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf("https://www.youtube.com/oembed" to (200 to jsonYouTube)),
            ),
        )

        val resultado = servicio.obtenerVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertEquals(
            "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
            exito.titulo,
        )
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", exito.urlMiniatura)
        assertTrue(exito.html.contains("youtube.com/embed"))
    }

    @Test
    fun `obtenerVideo de Facebook con video correcto no llama a oembed_post`() = runBlocking {
        val pedidas = mutableListOf<String>()
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf("https://graph.facebook.com/v27.0/oembed_video" to (200 to jsonFacebook)),
                pedidas,
            ),
        )

        val resultado = servicio.obtenerVideo("https://www.facebook.com/share/r/1JqVPz7de")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        assertTrue((resultado as ResultadoOEmbed.Exito).html.contains("fb-video"))
        assertEquals(1, pedidas.size)
        assertTrue(pedidas[0].contains("oembed_video"))
    }

    @Test
    fun `obtenerVideo de Facebook con error 100 en video cae a oembed_post`() = runBlocking {
        val pedidas = mutableListOf<String>()
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf(
                    "https://graph.facebook.com/v27.0/oembed_video" to (400 to jsonError100),
                    "https://graph.facebook.com/v27.0/oembed_post" to (200 to jsonFacebook),
                ),
                pedidas,
            ),
        )

        val resultado = servicio.obtenerVideo("https://www.facebook.com/share/r/1JqVPz7de")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        assertEquals(2, pedidas.size)
        assertTrue(pedidas[0].contains("oembed_video"))
        assertTrue(pedidas[1].contains("oembed_post"))
    }

    @Test
    fun `obtenerVideo de Facebook con error 100 en video y en post devuelve PARAMETRO_INVALIDO`() =
        runBlocking {
            val servicio = ServicioOEmbed(
                clienteFalso(
                    mapOf(
                        "https://graph.facebook.com/v27.0/oembed_video" to (400 to jsonError100),
                        "https://graph.facebook.com/v27.0/oembed_post" to (400 to jsonError100),
                    ),
                ),
            )

            val resultado = servicio.obtenerVideo("https://www.facebook.com/share/r/1JqVPz7de")

            assertTrue(resultado is ResultadoOEmbed.Error)
            assertEquals(
                TipoErrorOEmbed.PARAMETRO_INVALIDO,
                (resultado as ResultadoOEmbed.Error).tipo,
            )
        }

    @Test
    fun `obtenerVideo de TikTok quita el autoplay del html`() = runBlocking {
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf("https://www.tiktok.com/oembed" to (200 to jsonTikTok)),
            ),
        )

        val resultado = servicio.obtenerVideo("https://www.tiktok.com/@tiktok/video/1")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val html = (resultado as ResultadoOEmbed.Exito).html
        assertTrue(html.contains("autoplay=0"))
        assertFalse(html.contains("autoplay=1"))
    }

    @Test
    fun `obtenerVideo sin red devuelve RED`() = runBlocking {
        val servicio = ServicioOEmbed(
            OkHttpClient.Builder()
                .addInterceptor { throw IOException("sin red") }
                .build(),
        )

        val resultado = servicio.obtenerVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertTrue(resultado is ResultadoOEmbed.Error)
        assertEquals(TipoErrorOEmbed.RED, (resultado as ResultadoOEmbed.Error).tipo)
    }

    @Test
    fun `obtenerVideo sin proveedor devuelve NO_DISPONIBLE sin tocar la red`() = runBlocking {
        val servicio = ServicioOEmbed(
            OkHttpClient.Builder()
                .addInterceptor { throw IOException("no se debe llamar a la red") }
                .build(),
        )

        val resultado = servicio.obtenerVideo("https://ejemplo.com/articulo")

        assertTrue(resultado is ResultadoOEmbed.Error)
        assertEquals(
            TipoErrorOEmbed.NO_DISPONIBLE,
            (resultado as ResultadoOEmbed.Error).tipo,
        )
    }
}
