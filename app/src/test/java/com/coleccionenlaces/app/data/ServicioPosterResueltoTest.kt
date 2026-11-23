package com.coleccionenlaces.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del servicio de póster resuelto (Fix 5, fuente de cuadros
 * resuelta): la página canónica pedida con User-Agent de rastreador
 * entrega el póster real y los `.mp4` directos; el muro de login y
 * los enlaces `/share/r/` no resuelven nada. Sin red real: el
 * cliente es un OkHttpClient con interceptor (mismo patrón que
 * `ServicioComprobacionVideoTest`).
 */
class ServicioPosterResueltoTest {

    /** Página canónica realista (forma medida en miniatura-real-curl.md §8). */
    private val htmlRastreador = """
        <html><head>
        <meta property="og:title" content="🦇🔥 ¡¡¡50.000 VISITAS!!! 🖤">
        <meta property="og:image" content="https://scontent-ord5-1.xx.fbcdn.net/v/t15.5256-10/896273245_abc_n.jpg?oh=123&oe=6B0AA877">
        <meta property="og:description" content="El video de Yofer con 50.000 visitas.">
        <meta property="og:url" content="https://www.facebook.com/yofer/videos/-50000-visitas/1168661422485798/">
        </head><body>
        <script>{"video_url":"https:\/\/video-sjc3-1.xx.fbcdn.net\/o1\/v\/t2\/f2\/m412\/abc.mp4?oe=6B0A93AD"}</script>
        <script>var x = "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/def.mp4?oe=6B0A93AD";</script>
        <script>var y = "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/ghi.mp4?oe=6B0A93AD";</script>
        </body></html>
    """.trimIndent()

    /** Muro de login: og:title genérico, sin og:image ni .mp4, solo 2 img. */
    private val htmlMuroLogin = """
        <html><head>
        <meta property="og:title" content="Log in or sign up to view">
        <meta property="og:description" content="See posts, photos and more on Facebook">
        </head><body>
        <img src="https://static.xx.fbcdn.net/rsrc.php/v4/yD/r/xxx.png">
        <img src="https://www.facebook.com/tr?id=123&ev=PageView">
        </body></html>
    """.trimIndent()

    private val urlReel = "https://www.facebook.com/reel/1168661422485798/"
    private val urlImagen =
        "https://scontent-ord5-1.xx.fbcdn.net/v/t15.5256-10/896273245_abc_n.jpg?oh=123&oe=6B0AA877"

    /** Cliente falso que responde según la URL y el User-Agent; registra las peticiones. */
    private class ClienteFalso(
        private val pagina: String,
        private val codigoPrimerUa: Int = 200,
    ) {
        val peticiones = mutableListOf<Pair<String, String>>()

        fun cliente(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val url = cadena.request().url.toString()
                val ua = cadena.request().header("User-Agent") ?: ""
                peticiones.add(url to ua)
                val (codigo, cuerpo) = if (ua.contains("facebookexternalhit")) {
                    codigoPrimerUa to pagina
                } else {
                    200 to pagina
                }
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(codigo)
                    .message(if (codigo == 200) "OK" else "Error")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
    }

    @Test
    fun `reel canonico con pagina de rastreador devuelve poster con titulo imagen y 3 mp4`() =
        runBlocking {
            val falso = ClienteFalso(htmlRastreador)
            val servicio = ServicioPosterResuelto(cliente = falso.cliente())

            val poster = servicio.resolver(urlReel)

            assertTrue(poster != null)
            assertEquals("🦇🔥 ¡¡¡50.000 VISITAS!!! 🖤", poster!!.vistaPrevia.titulo)
            assertEquals("El video de Yofer con 50.000 visitas.", poster.vistaPrevia.descripcion)
            assertEquals(urlImagen, poster.vistaPrevia.urlImagen)
            assertEquals(3, poster.urlsVideo.size)
            assertTrue(poster.urlsVideo.all { it.endsWith(".mp4?oe=6B0A93AD") })
            // Se pidió con el User-Agent de rastreador, no con el de la app.
            assertTrue(falso.peticiones.all { (_, ua) -> ua.contains("facebookexternalhit") })
        }

    @Test
    fun `muro de login no resuelve poster aunque tenga img`() = runBlocking {
        val falso = ClienteFalso(htmlMuroLogin)
        val servicio = ServicioPosterResuelto(cliente = falso.cliente())

        assertNull(servicio.resolver(urlReel))
    }

    @Test
    fun `share r no resuelve y no toca la red`() = runBlocking {
        val falso = ClienteFalso(htmlRastreador)
        val servicio = ServicioPosterResuelto(cliente = falso.cliente())

        assertNull(servicio.resolver("https://www.facebook.com/share/r/1JqVPz7de"))
        assertTrue(falso.peticiones.isEmpty())
    }

    @Test
    fun `URL no de Facebook no resuelve y no toca la red`() = runBlocking {
        val falso = ClienteFalso(htmlRastreador)
        val servicio = ServicioPosterResuelto(cliente = falso.cliente())

        assertNull(servicio.resolver("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(falso.peticiones.isEmpty())
    }

    @Test
    fun `si facebookexternalhit falla se reintenta con Googlebot`() = runBlocking {
        val falso = ClienteFalso(htmlRastreador, codigoPrimerUa = 400)
        val servicio = ServicioPosterResuelto(cliente = falso.cliente())

        val poster = servicio.resolver(urlReel)

        assertTrue(poster != null)
        assertEquals(urlImagen, poster!!.vistaPrevia.urlImagen)
        assertEquals(
            listOf("facebookexternalhit/1.1", "Googlebot/2.1"),
            falso.peticiones.map { (_, ua) -> ua },
        )
    }

    @Test
    fun `sin red devuelve null sin lanzar`() = runBlocking {
        val clienteSinRed = OkHttpClient.Builder()
            .addInterceptor { throw java.net.UnknownHostException("facebook.com") }
            .build()
        val servicio = ServicioPosterResuelto(cliente = clienteSinRed)

        assertNull(servicio.resolver(urlReel))
    }
}
