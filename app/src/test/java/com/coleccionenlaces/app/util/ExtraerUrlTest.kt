package com.coleccionenlaces.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de [extraerUrl] y [esUrlValida] (Fase 7).
 *
 * Funciones puras sin Android ni red: JUnit simple, sin Robolectric.
 */
class ExtraerUrlTest {

    @Test
    fun `URL sola se extrae tal cual`() {
        assertEquals(
            "https://ejemplo.com/articulo",
            extraerUrl("https://ejemplo.com/articulo"),
        )
    }

    @Test
    fun `URL dentro de un texto como el que comparte YouTube`() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            extraerUrl("Mira este vídeo\nhttps://youtu.be/dQw4w9WgXcQ\n¡Está increíble!"),
        )
    }

    @Test
    fun `URL dentro de un texto con titulo delante y comentario detras`() {
        assertEquals(
            "https://ejemplo.com/receta-de-paella",
            extraerUrl(
                "Receta de paella valenciana https://ejemplo.com/receta-de-paella " +
                    "La receta tradicional paso a paso.",
            ),
        )
    }

    @Test
    fun `sin URL devuelve null`() {
        assertNull(extraerUrl("Mira este vídeo, está increíble, sin enlace."))
    }

    @Test
    fun `varias URLs toma la primera`() {
        assertEquals(
            "https://primero.com/a",
            extraerUrl("Mira https://primero.com/a y también https://segundo.com/b"),
        )
    }

    @Test
    fun `texto vacio devuelve null`() {
        assertNull(extraerUrl(""))
        assertNull(extraerUrl("   "))
    }

    @Test
    fun `texto null devuelve null`() {
        assertNull(extraerUrl(null))
    }

    @Test
    fun `solo el esquema sin host no es URL`() {
        assertNull(extraerUrl("mira https:// y nada más"))
    }

    @Test
    fun `URL http tambien se extrae`() {
        assertEquals(
            "http://ejemplo.com/pagina",
            extraerUrl("Te paso http://ejemplo.com/pagina por si acaso"),
        )
    }

    @Test
    fun `la consulta de la URL se conserva`() {
        assertEquals(
            "https://ejemplo.com/buscar?q=paella&pagina=2",
            extraerUrl("https://ejemplo.com/buscar?q=paella&pagina=2"),
        )
    }

    @Test
    fun `la puntuacion final del texto no forma parte de la URL`() {
        assertEquals(
            "https://ejemplo.com/articulo",
            extraerUrl("Mira https://ejemplo.com/articulo, te va a gustar."),
        )
        assertEquals(
            "https://ejemplo.com/articulo",
            extraerUrl("Mira https://ejemplo.com/articulo."),
        )
    }

    @Test
    fun `la URL entre parentesis pierde el cierre sin abrir`() {
        assertEquals(
            "https://ejemplo.com/pagina",
            extraerUrl("(mira https://ejemplo.com/pagina)"),
        )
    }

    @Test
    fun `esUrlValida acepta http y https con host`() {
        assertTrue(esUrlValida("https://ejemplo.com/articulo"))
        assertTrue(esUrlValida("http://ejemplo.com"))
        assertTrue(esUrlValida("  https://ejemplo.com  "))
        assertTrue(esUrlValida("HTTPS://EJEMPLO.COM"))
    }

    @Test
    fun `esUrlValida rechaza lo que no es http o https con host`() {
        assertFalse(esUrlValida(""))
        assertFalse(esUrlValida("   "))
        assertFalse(esUrlValida("no-es-una-url"))
        assertFalse(esUrlValida("ftp://ejemplo.com/archivo"))
        assertFalse(esUrlValida("ejemplo.com/pagina"))
        assertFalse(esUrlValida("https://"))
        assertFalse(esUrlValida("https:///sin-host"))
    }
}
