package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de oEmbed sin token (corrección 1.0.1, punto 3).
 *
 * JUnit simple, sin red: los JSON son la FORMA de las respuestas
 * reales verificadas con curl el 2026-11-23 (Facebook, YouTube y
 * TikTok). No son copias byte a byte: lo que se prueba es que el
 * parseo extrae los campos que cada proveedor devuelve de verdad
 * (y que no inventa los que no devuelve).
 */
class OEmbedTest {

    /** Respuesta real de YouTube (Rick Astley, verificada con curl). */
    private val jsonYouTube = """
        {"type":"video","version":"1.0","title":"Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)","author_name":"Rick Astley","author_url":"https://www.youtube.com/@RickAstley","provider_name":"YouTube","provider_url":"https://www.youtube.com/","thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg","thumbnail_width":480,"thumbnail_height":360,"html":"<iframe width=\"480\" height=\"270\" src=\"https://www.youtube.com/embed/dQw4w9WgXcQ?feature=oembed\" frameborder=\"0\" allowfullscreen title=\"Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)\"></iframe>","width":480,"height":270}
    """.trimIndent()

    /** Respuesta real de Facebook para el /share/r/ de EterniCodIA (verificada con curl). */
    private val jsonFacebook = """
        {"html":"<div class=\"fb-video\" data-href=\"https://www.facebook.com/share/r/1JqVPz7de\" data-width=\"500\"><blockquote cite=\"https://www.facebook.com/share/r/1JqVPz7de\" class=\"fb-xfbml-parse-ignore\"></blockquote></div><script async=\"1\" defer=\"1\" crossorigin=\"anonymous\" src=\"https://connect.facebook.net/es_LA/sdk.js#xfbml=1&version=v27.0\"></script>","provider_name":"Facebook","type":"video","version":"1.0","width":500}
    """.trimIndent()

    /** Respuesta real de TikTok (verificada con curl): solo HTML, sin título ni miniatura. */
    private val jsonTikTok = """
        {"version":"1.0","type":"video","provider_name":"TikTok","provider_url":"https://www.tiktok.com","html":"<iframe src=\"https://www.tiktok.com/embed/v2/7106686408261946670?autoplay=1&muted=1\" width=\"325\" height=\"578\"></iframe>","width":"325","height":"578","embed_product_id":"12345"}
    """.trimIndent()

    /** Error 100 de Graph (oembed_post con URL de página, verificado con curl). */
    private val jsonError100 = """
        {"error":{"message":"(#100) Invalid parameter","type":"OAuthException","code":100,"error_subcode":33,"fbtrace_id":"ABC"}}
    """.trimIndent()

    @Test
    fun `parsearOEmbed de YouTube extrae titulo miniatura html y ancho`() {
        val resultado = parsearOEmbed(jsonYouTube)

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertEquals(
            "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
            exito.titulo,
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            exito.urlMiniatura,
        )
        assertTrue(exito.html.contains("youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals(480, exito.ancho)
    }

    @Test
    fun `parsearOEmbed de Facebook extrae el html del reproductor sin titulo ni miniatura`() {
        val resultado = parsearOEmbed(jsonFacebook)

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertTrue(exito.html.contains("fb-video"))
        assertTrue(exito.html.contains("sdk.js"))
        assertTrue(exito.html.contains("https://www.facebook.com/share/r/1JqVPz7de"))
        assertNull(exito.titulo)
        assertNull(exito.urlMiniatura)
        assertEquals(500, exito.ancho)
    }

    @Test
    fun `parsearOEmbed de TikTok extrae solo el html sin titulo ni miniatura`() {
        val resultado = parsearOEmbed(jsonTikTok)

        assertTrue(resultado is ResultadoOEmbed.Exito)
        val exito = resultado as ResultadoOEmbed.Exito
        assertTrue(exito.html.contains("tiktok.com/embed"))
        assertNull(exito.titulo)
        assertNull(exito.urlMiniatura)
        assertEquals(325, exito.ancho)
    }

    @Test
    fun `parsearOEmbed con error 100 devuelve PARAMETRO_INVALIDO`() {
        val resultado = parsearOEmbed(jsonError100)

        assertTrue(resultado is ResultadoOEmbed.Error)
        val error = resultado as ResultadoOEmbed.Error
        assertEquals(TipoErrorOEmbed.PARAMETRO_INVALIDO, error.tipo)
        assertTrue(error.mensaje!!.contains("100"))
    }

    @Test
    fun `parsearOEmbed con error sin codigo devuelve NO_DISPONIBLE`() {
        val resultado = parsearOEmbed(
            """{"error":{"message":"Algo falló","type":"OAuthException"}}""",
        )

        assertTrue(resultado is ResultadoOEmbed.Error)
        assertEquals(
            TipoErrorOEmbed.NO_DISPONIBLE,
            (resultado as ResultadoOEmbed.Error).tipo,
        )
    }

    @Test
    fun `parsearOEmbed sin html devuelve RESPUESTA_INVALIDA`() {
        assertEquals(
            TipoErrorOEmbed.RESPUESTA_INVALIDA,
            (parsearOEmbed("""{"type":"video","version":"1.0"}""") as ResultadoOEmbed.Error).tipo,
        )
        assertEquals(
            TipoErrorOEmbed.RESPUESTA_INVALIDA,
            (parsearOEmbed("") as ResultadoOEmbed.Error).tipo,
        )
        assertEquals(
            TipoErrorOEmbed.RESPUESTA_INVALIDA,
            (parsearOEmbed("no es json") as ResultadoOEmbed.Error).tipo,
        )
    }

    @Test
    fun `parsearOEmbed resuelve escapes y unicode del titulo`() {
        val resultado = parsearOEmbed(
            """{"html":"<iframe></iframe>","title":"Vídeo \"entre comillas\" \u00e9xito","width":100}""",
        )

        assertTrue(resultado is ResultadoOEmbed.Exito)
        assertEquals(
            "Vídeo \"entre comillas\" éxito",
            (resultado as ResultadoOEmbed.Exito).titulo,
        )
    }

    @Test
    fun `proveedorOEmbedDe detecta YouTube TikTok y Facebook por host`() {
        assertEquals(
            ProveedorOEmbed.YOUTUBE,
            proveedorOEmbedDe("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            ProveedorOEmbed.YOUTUBE,
            proveedorOEmbedDe("https://youtu.be/dQw4w9WgXcQ"),
        )
        assertEquals(
            ProveedorOEmbed.YOUTUBE,
            proveedorOEmbedDe("https://www.youtube.com/shorts/dQw4w9WgXcQ"),
        )
        assertEquals(
            ProveedorOEmbed.TIKTOK,
            proveedorOEmbedDe("https://www.tiktok.com/@tiktok/video/7106686408261946670"),
        )
        assertEquals(
            ProveedorOEmbed.FACEBOOK,
            proveedorOEmbedDe("https://www.facebook.com/share/r/1JqVPz7de"),
        )
        assertEquals(
            ProveedorOEmbed.FACEBOOK,
            proveedorOEmbedDe("https://fb.watch/abc123/"),
        )
        assertNull(proveedorOEmbedDe("https://ejemplo.com/articulo"))
        assertNull(proveedorOEmbedDe("no-es-una-url"))
    }

    @Test
    fun `construirUrlOEmbed codifica la URL del enlace en cada endpoint`() {
        val url = "https://www.facebook.com/share/r/1JqVPz7de"
        val fb = construirUrlOEmbed(ProveedorOEmbed.FACEBOOK, url)
        assertTrue(fb.startsWith("https://graph.facebook.com/v27.0/oembed_video?url="))
        assertTrue(fb.contains("facebook.com%2Fshare%2Fr%2F1JqVPz7de"))
        assertFalse(fb.contains("token"))

        val yt = construirUrlOEmbed(
            ProveedorOEmbed.YOUTUBE,
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
        assertTrue(yt.startsWith("https://www.youtube.com/oembed?url="))
        assertTrue(yt.endsWith("&format=json"))

        val tt = construirUrlOEmbed(
            ProveedorOEmbed.TIKTOK,
            "https://www.tiktok.com/@tiktok/video/7106686408261946670",
        )
        assertTrue(tt.startsWith("https://www.tiktok.com/oembed?url="))

        val post = construirUrlOEmbedPostFacebook(url)
        assertTrue(post.startsWith("https://graph.facebook.com/v27.0/oembed_post?url="))
    }

    @Test
    fun `quitarAutoplayTikTok cambia autoplay 1 por 0 y deja el resto`() {
        val html = "<iframe src=\"https://www.tiktok.com/embed/v2/1?autoplay=1&muted=1\"></iframe>"
        val sinAutoplay = quitarAutoplayTikTok(html)

        assertTrue(sinAutoplay.contains("autoplay=0"))
        assertFalse(sinAutoplay.contains("autoplay=1"))
        assertTrue(sinAutoplay.contains("muted=1"))
        assertEquals(
            "<iframe src=\"https://www.facebook.com/x\"></iframe>",
            quitarAutoplayTikTok("<iframe src=\"https://www.facebook.com/x\"></iframe>"),
        )
    }
}
