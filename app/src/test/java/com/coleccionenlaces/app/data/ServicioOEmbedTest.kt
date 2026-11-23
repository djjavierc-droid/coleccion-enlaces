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
import org.junit.Assert.assertNull
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

    private val jsonInstagram = """
        {"version":"1.0","provider_name":"Instagram","provider_url":"https://www.instagram.com/","type":"rich","width":658,"html":"<blockquote class=\"instagram-media\" data-instgrm-permalink=\"https://www.instagram.com/reel/Df2uUvyt_S4/\"></blockquote><script async src=\"//www.instagram.com/embed.js\"></script>"}
    """.trimIndent()

    private val jsonX = """
        {"url":"https://x.com/Interior/status/507185938620219395","author_name":"U.S. Department of the Interior","html":"<blockquote class=\"twitter-tweet\"><p>Happy 50th anniversary to the Wilderness Act!</p></blockquote><script async src=\"https://platform.x.com/widgets.js\" charset=\"utf-8\"></script>","width":550,"height":null,"type":"rich","provider_name":"X","provider_url":"https://x.com","version":"1.0"}
    """.trimIndent()

    private val jsonVimeo = """
        {"type":"video","version":"1.0","provider_name":"Vimeo","title":"The New Vimeo Player (You Know, For Videos)","html":"<iframe src=\"https://player.vimeo.com/video/76979871\"></iframe>","width":480,"height":270,"thumbnail_url":"https://i.vimeocdn.com/video/452001751-d_295x166?region=us"}
    """.trimIndent()

    private val jsonDailymotion = """
        {"type":"video","version":"1.0","provider_name":"Dailymotion","title":"Así se vivió el Home Run Derby X en la CDMX","html":"<iframe src=\"https://www.dailymotion.com/embed/video/x8ek5sr\"></iframe>","width":480,"height":270,"thumbnail_url":"https://s1.dmcdn.net/v/UIaXR1g4gkw6X7Bnf/x720"}
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
    fun `obtenerVideo de Instagram devuelve el html del incrustado sin titulo ni miniatura`() =
        runBlocking {
            val pedidas = mutableListOf<String>()
            val servicio = ServicioOEmbed(
                clienteFalso(
                    mapOf("https://graph.facebook.com/v27.0/instagram_oembed" to (200 to jsonInstagram)),
                    pedidas,
                ),
            )

            val resultado = servicio.obtenerVideo("https://www.instagram.com/reel/Df2uUvyt_S4/")

            assertTrue(resultado is ResultadoOEmbed.Exito)
            val exito = resultado as ResultadoOEmbed.Exito
            assertTrue(exito.html.contains("instagram-media"))
            assertTrue(exito.html.contains("embed.js"))
            assertNull(exito.titulo)
            assertNull(exito.urlMiniatura)
            assertEquals(1, pedidas.size)
            assertFalse(pedidas[0].contains("token"))
        }

    @Test
    fun `obtenerVideo de X devuelve el html del tuit sin titulo ni miniatura`() = runBlocking {
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf("https://publish.twitter.com/oembed" to (200 to jsonX)),
            ),
        )

        val resultado = servicio.obtenerVideo("https://x.com/Interior/status/507185938620219395")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertTrue(exito.html.contains("twitter-tweet"))
        assertTrue(exito.html.contains("platform.x.com/widgets.js"))
        assertNull(exito.titulo)
        assertNull(exito.urlMiniatura)
    }

    @Test
    fun `obtenerVideo de Vimeo devuelve titulo miniatura y html`() = runBlocking {
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf("https://vimeo.com/api/oembed.json" to (200 to jsonVimeo)),
            ),
        )

        val resultado = servicio.obtenerVideo("https://vimeo.com/76979871")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertEquals("The New Vimeo Player (You Know, For Videos)", exito.titulo)
        assertEquals(
            "https://i.vimeocdn.com/video/452001751-d_295x166?region=us",
            exito.urlMiniatura,
        )
        assertTrue(exito.html.contains("player.vimeo.com/video/76979871"))
    }

    @Test
    fun `obtenerVideo de Dailymotion devuelve titulo miniatura y html`() = runBlocking {
        val servicio = ServicioOEmbed(
            clienteFalso(
                mapOf("https://www.dailymotion.com/services/oembed" to (200 to jsonDailymotion)),
            ),
        )

        val resultado = servicio.obtenerVideo("https://www.dailymotion.com/video/x8ek5sr")

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertEquals("Así se vivió el Home Run Derby X en la CDMX", exito.titulo)
        assertEquals(
            "https://s1.dmcdn.net/v/UIaXR1g4gkw6X7Bnf/x720",
            exito.urlMiniatura,
        )
        assertTrue(exito.html.contains("dailymotion.com/embed/video/x8ek5sr"))
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
