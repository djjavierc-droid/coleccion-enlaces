package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del póster resuelto (Fix 5, fuente de cuadros resuelta):
 * detección de la URL canónica de un vídeo de Facebook, punto de
 * entrada de la resolución `/share/r/` → canónica (hoy null, hueco
 * de la investigación de una hora) y extracción de `og:image` y de
 * las URLs `.mp4` directas de la página del rastreador.
 */
class PosterResueltoTest {

    @Test
    fun `reel es URL canonica de video`() {
        assertTrue(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/reel/1168661422485798/",
            ),
        )
        assertTrue(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/reel/1168661422485798",
            ),
        )
    }

    @Test
    fun `watch con v es URL canonica de video`() {
        assertTrue(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/watch/?v=1168661422485798",
            ),
        )
        assertFalse(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/watch/",
            ),
        )
    }

    @Test
    fun `videos de pagina es URL canonica de video`() {
        assertTrue(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/yofer/videos/1168661422485798/",
            ),
        )
        assertTrue(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/yofer/videos/-50000-visitas/1168661422485798/",
            ),
        )
    }

    @Test
    fun `video php con v es URL canonica de video`() {
        assertTrue(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/video.php?v=1168661422485798",
            ),
        )
    }

    @Test
    fun `share r no es URL canonica`() {
        assertFalse(
            esUrlVideoFacebookCanonica(
                "https://www.facebook.com/share/r/1JqVPz7de",
            ),
        )
        assertFalse(
            esUrlVideoFacebookCanonica(
                "https://m.facebook.com/share/r/1JqVPz7de?wtsid=rdr_0vkCsvCtHfdXkKwn2",
            ),
        )
    }

    @Test
    fun `fb watch y otras URLs no son canonicas`() {
        assertFalse(
            esUrlVideoFacebookCanonica("https://fb.watch/1168661422485798/"),
        )
        assertFalse(
            esUrlVideoFacebookCanonica(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            ),
        )
        assertFalse(
            esUrlVideoFacebookCanonica("https://www.facebook.com/"),
        )
        assertFalse(
            esUrlVideoFacebookCanonica("https://www.facebook.com/yofer"),
        )
    }

    @Test
    fun `resolverUrlCanonicaFacebook devuelve la propia URL si ya es canonica`() {
        val canonica = "https://www.facebook.com/reel/1168661422485798/"
        assertEquals(canonica, resolverUrlCanonicaFacebook(canonica))
        assertEquals(
            "https://www.facebook.com/watch/?v=1168661422485798",
            resolverUrlCanonicaFacebook(
                "https://www.facebook.com/watch/?v=1168661422485798",
            ),
        )
    }

    @Test
    fun `resolverUrlCanonicaFacebook devuelve null para share r, el hueco pendiente`() {
        assertNull(
            resolverUrlCanonicaFacebook(
                "https://www.facebook.com/share/r/1JqVPz7de",
            ),
        )
        assertNull(
            resolverUrlCanonicaFacebook("https://fb.watch/1168661422485798/"),
        )
        assertNull(
            resolverUrlCanonicaFacebook(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            ),
        )
    }

    private val htmlRastreador = """
        <html><head>
        <meta property="og:title" content="🦇🔥 ¡¡¡50.000 VISITAS!!! 🖤">
        <meta property="og:image" content="https://scontent-ord5-1.xx.fbcdn.net/v/t15.5256-10/896273245_abc_n.jpg?oh=123&oe=6B0AA877">
        <meta property="og:url" content="https://www.facebook.com/yofer/videos/-50000-visitas/1168661422485798/">
        </head><body>
        <script>{"video_url":"https:\/\/video-sjc3-1.xx.fbcdn.net\/o1\/v\/t2\/f2\/m412\/abc.mp4?oe=6B0A93AD"}</script>
        <script>var x = "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/def.mp4?oe=6B0A93AD&oh=456";</script>
        <script>var y = "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/abc.mp4?oe=6B0A93AD";</script>
        </body></html>
    """.trimIndent()

    @Test
    fun `extraerOgImage devuelve la og image del meta`() {
        assertEquals(
            "https://scontent-ord5-1.xx.fbcdn.net/v/t15.5256-10/896273245_abc_n.jpg?oh=123&oe=6B0AA877",
            extraerOgImage(htmlRastreador, "https://www.facebook.com/"),
        )
    }

    @Test
    fun `extraerOgImage devuelve null sin meta aunque haya img`() {
        val htmlMuro = """
            <html><head>
            <meta property="og:title" content="Log in or sign up to view">
            </head><body>
            <img src="https://static.xx.fbcdn.net/rsrc.php/v4/yD/r/xxx.png">
            <img src="https://www.facebook.com/tr?id=123&ev=PageView">
            </body></html>
        """.trimIndent()
        assertNull(extraerOgImage(htmlMuro, "https://www.facebook.com/"))
    }

    @Test
    fun `extraerUrlsVideoMp4 devuelve las 3 directas sin duplicados y desescapadas`() {
        assertEquals(
            listOf(
                "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/abc.mp4?oe=6B0A93AD",
                "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/def.mp4?oe=6B0A93AD&oh=456",
            ),
            extraerUrlsVideoMp4(htmlRastreador),
        )
    }

    @Test
    fun `extraerUrlsVideoMp4 devuelve vacio sin mp4`() {
        assertEquals(
            emptyList<String>(),
            extraerUrlsVideoMp4(
                "<html><head><title>Error</title></head><body></body></html>",
            ),
        )
        assertEquals(emptyList<String>(), extraerUrlsVideoMp4(""))
    }

    @Test
    fun `extraerUrlsVideoMp4 desescapa u0026`() {
        val html = """
            <script>var u = "https://video-sjc3-1.xx.fbcdn.net/v.mp4?oe=1\u0026oh=2";</script>
        """.trimIndent()
        assertEquals(
            listOf("https://video-sjc3-1.xx.fbcdn.net/v.mp4?oe=1&oh=2"),
            extraerUrlsVideoMp4(html),
        )
    }
}
