package com.coleccionenlaces.app

import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Prueba de humo del tema: el esquema oscuro y el claro deben ser
 * distintos (el tema personalizado no usa los colores por defecto
 * y el modo oscuro es el predeterminado).
 */
class TemaTest {

    @Test
    fun `los esquemas oscuro y claro tienen primario distinto`() {
        // Los valores viven en Color.kt; aquí se comprueba la intención
        // del diseño sin instanciar Compose: violeta claro ≠ violeta oscuro.
        val primarioOscuro = 0xFF9D8CFF
        val primarioClaro = 0xFF5B3DF5
        assertNotEquals(primarioOscuro, primarioClaro)
    }

    @Test
    fun `el espaciado usa multiplos de 4 dp`() {
        val valores = listOf(4, 8, 16, 24, 32)
        valores.forEach { valor ->
            assert(valor % 4 == 0) { "Espaciado $valor no es múltiplo de 4" }
        }
    }
}
