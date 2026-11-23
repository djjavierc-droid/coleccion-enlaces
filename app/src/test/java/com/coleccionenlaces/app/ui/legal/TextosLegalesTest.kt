package com.coleccionenlaces.app.ui.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de los textos legales de "Acerca de" (corrección 1.0.1,
 * punto 2). JUnit simple, sin Android: los textos deben ser reales
 * y completos (no marcadores vacíos) y decir lo que Javier pidió:
 * sin cuentas, sin datos personales, sin nube ni anuncios, y todo
 * se borra desinstalando o desde "Borrar todos los datos".
 */
class TextosLegalesTest {

    @Test
    fun `los tres textos legales son largos y no estan vacios`() {
        for (tipo in TipoLegal.entries) {
            val texto = textoLegal(tipo)
            assertTrue(texto.isNotBlank())
            assertTrue(
                "El texto de $tipo es demasiado corto para ser real: ${texto.length} caracteres",
                texto.trim().length > 500,
            )
        }
    }

    @Test
    fun `la privacidad dice sin cuentas sin datos sin anuncios y sin rastreadores`() {
        val texto = textoLegal(TipoLegal.PRIVACIDAD)
        assertTrue(texto.contains("no tiene cuentas"))
        assertTrue(texto.contains("No recoge datos personales"))
        assertTrue(texto.contains("no muestra anuncios"))
        assertTrue(texto.contains("no incluye rastreadores"))
        assertTrue(texto.contains("no hay ningún servidor"))
        assertTrue(texto.contains("en tu teléfono"))
    }

    @Test
    fun `los terminos dicen que los enlaces son del usuario y sin copia en la nube`() {
        val texto = textoLegal(TipoLegal.TERMINOS)
        assertTrue(texto.contains("TUS ENLACES SON TUYOS"))
        assertTrue(texto.contains("SIN COPIA DE SEGURIDAD"))
        assertTrue(texto.contains("no hay ninguna copia en un servidor"))
        assertTrue(texto.contains("se ofrece tal cual"))
        assertTrue(texto.contains("sin cuentas, sin nube y sin anuncios"))
    }

    @Test
    fun `la eliminacion explica borrar todo y desinstalar sin vuelta atras`() {
        val texto = textoLegal(TipoLegal.ELIMINACION)
        assertTrue(texto.contains("Borrar todos los datos"))
        assertTrue(texto.contains("Almacenamiento"))
        assertTrue(texto.contains("desinstalar"))
        assertTrue(texto.contains("no se pueden recuperar"))
        assertTrue(texto.contains("no hay ninguna copia"))
    }

    @Test
    fun `deRuta resuelve las tres rutas y rechaza las demas`() {
        assertEquals(TipoLegal.PRIVACIDAD, TipoLegal.deRuta("privacidad"))
        assertEquals(TipoLegal.TERMINOS, TipoLegal.deRuta("terminos"))
        assertEquals(TipoLegal.ELIMINACION, TipoLegal.deRuta("eliminacion"))
        assertNull(TipoLegal.deRuta("nada"))
        assertNull(TipoLegal.deRuta(""))
        assertNull(TipoLegal.deRuta(null))
    }

    @Test
    fun `el orden del menu es privacidad terminos eliminacion`() {
        assertEquals(
            listOf(TipoLegal.PRIVACIDAD, TipoLegal.TERMINOS, TipoLegal.ELIMINACION),
            TipoLegal.entries.toList(),
        )
    }
}
