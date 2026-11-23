package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del parseo puro de vista previa (Fase 3).
 *
 * El parseo no toca la red: trabaja sobre los HTML de ejemplo de
 * `src/test/resources`. La URL base usada es https://ejemplo.com
 * (con ruta en los casos de imagen relativa).
 */
class ParseadorVistaPreviaTest {

    private fun leerRecurso(nombre: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(nombre)) {
            "Recurso de prueba no encontrado: $nombre"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `etiquetas OG completas ganan a los respaldos`() {
        val vista = parsearHtml(leerRecurso("og-completo.html"), "https://ejemplo.com/receta")

        assertEquals("Receta de paella valenciana", vista.titulo)
        assertEquals("La receta tradicional paso a paso.", vista.descripcion)
        assertEquals("https://ejemplo.com/img/paella.jpg", vista.urlImagen)
    }

    @Test
    fun `imagen OG relativa se resuelve a absoluta`() {
        val vista = parsearHtml(
            leerRecurso("og-imagen-relativa.html"),
            "https://ejemplo.com/receta/tortilla",
        )

        assertEquals("Tortilla de patatas jugosa", vista.titulo)
        assertEquals("Con cebolla caramelizada y patata confitada.", vista.descripcion)
        assertEquals("https://ejemplo.com/img/portada.jpg", vista.urlImagen)
    }

    @Test
    fun `sin OG usa title, meta description y primera imagen`() {
        val vista = parsearHtml(leerRecurso("sin-og.html"), "https://ejemplo.com/guias/bici")

        assertEquals("Cómo arreglar una bicicleta", vista.titulo)
        assertEquals("Guía para ajustar los frenos y cambiar la cadena.", vista.descripcion)
        // La primera img con src no vacío es la relativa fotos/frenos.jpg.
        assertEquals("https://ejemplo.com/guias/fotos/frenos.jpg", vista.urlImagen)
    }

    @Test
    fun `sin imagen la urlImagen es null y el resto se conserva`() {
        val vista = parsearHtml(leerRecurso("sin-imagen.html"), "https://ejemplo.com/articulo")

        assertEquals("Artículo sin imágenes", vista.titulo)
        assertEquals("Este artículo no tiene ninguna imagen.", vista.descripcion)
        assertNull(vista.urlImagen)
    }

    @Test
    fun `HTML vacio devuelve vista previa vacia`() {
        val vista = parsearHtml(leerRecurso("vacio.html"), "https://ejemplo.com/")

        assertNull(vista.titulo)
        assertNull(vista.descripcion)
        assertNull(vista.urlImagen)
        assertTrue(vista.estaVacia)
    }

    @Test
    fun `HTML malformado recupera lo que puede`() {
        val vista = parsearHtml(leerRecurso("malformado.html"), "https://ejemplo.com/roto")

        assertEquals("Título roto", vista.titulo)
        assertEquals("Descripción rota", vista.descripcion)
        assertEquals("https://ejemplo.com/img/roto.jpg", vista.urlImagen)
    }

    @Test
    fun `og title en blanco usa el title de la pagina`() {
        val html = """
            <html><head>
            <title>Título de respaldo</title>
            <meta property="og:title" content="   ">
            </head><body></body></html>
        """.trimIndent()

        val vista = parsearHtml(html, "https://ejemplo.com/")
        assertEquals("Título de respaldo", vista.titulo)
    }

    @Test
    fun `sin og description usa meta description`() {
        val html = """
            <html><head>
            <title>Un artículo</title>
            <meta property="og:title" content="Un artículo">
            <meta name="description" content="La descripción genérica.">
            </head><body></body></html>
        """.trimIndent()

        val vista = parsearHtml(html, "https://ejemplo.com/")
        assertEquals("La descripción genérica.", vista.descripcion)
    }

    @Test
    fun `imagen OG absoluta se conserva tal cual`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Con imagen absoluta">
            <meta property="og:image" content="https://cdn.otro.com/foto.png">
            </head><body></body></html>
        """.trimIndent()

        val vista = parsearHtml(html, "https://ejemplo.com/")
        assertEquals("https://cdn.otro.com/foto.png", vista.urlImagen)
    }

    @Test
    fun `sin titulo ni descripcion ni imagen todo es null`() {
        val vista = parsearHtml(
            "<html><head></head><body><p>Solo texto, sin metadatos.</p></body></html>",
            "https://ejemplo.com/",
        )

        assertNull(vista.titulo)
        assertNull(vista.descripcion)
        assertNull(vista.urlImagen)
        assertTrue(vista.estaVacia)
    }

    // ---- Punto 3 de la corrección 1.0.1: títulos genéricos ----

    @Test
    fun `titulo Error de pagina de error no se usa como titulo`() {
        val vista = parsearHtml(
            "<html><head><title>Error</title></head><body></body></html>",
            "https://www.facebook.com/share/r/abc",
        )

        assertNull(vista.titulo)
        assertTrue(vista.estaVacia)
    }

    @Test
    fun `titulo Error Facebook de pagina movil no se usa como titulo`() {
        val vista = parsearHtml(
            "<html><head><title>Error Facebook</title></head><body></body></html>",
            "https://m.facebook.com/share/r/abc",
        )

        assertNull(vista.titulo)
        assertTrue(vista.estaVacia)
    }

    @Test
    fun `og title con solo el nombre del sitio no se usa como titulo`() {
        val vista = parsearHtml(
            """
            <html><head>
            <meta property="og:title" content="Facebook">
            <meta property="og:description" content="Explore the things you love">
            </head><body></body></html>
            """.trimIndent(),
            "https://www.facebook.com/",
        )

        assertNull(vista.titulo)
        assertEquals("Explore the things you love", vista.descripcion)
    }

    @Test
    fun `titulo generico no tapa un titulo real de otra pagina`() {
        val vista = parsearHtml(
            "<html><head><title>Error al cargar</title></head><body></body></html>",
            "https://ejemplo.com/",
        )

        assertEquals("Error al cargar", vista.titulo)
    }
}
