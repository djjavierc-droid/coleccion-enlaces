package com.coleccionenlaces.app.ui.home

import com.coleccionenlaces.app.data.Categoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del menú lateral (corrección 1.0.1, punto 2).
 *
 * [itemsMenuLateral] es una función pura: lista las categorías con
 * su ícono, su color y su contador, y marca las que siguen
 * bloqueadas. El menú no muestra contenido de carpetas bloqueadas:
 * sus filas solo llevan nombre, ícono, color y contador (un número),
 * nunca títulos, descripciones, imágenes ni URLs de enlaces.
 */
class MenuLateralTest {

    private val cocina = Categoria(
        id = 1, nombre = "Cocina", color = 0xFFFB8C00, icono = "restaurant",
        esPredefinida = true, orden = 0,
    )
    private val otros = Categoria(
        id = 17, nombre = "Otros", color = 0xFF90A4AE, icono = "category",
        esPredefinida = true, orden = 16,
    )
    private val propia = Categoria(
        id = 18, nombre = "Mis cosas", color = 0xFF7E57C2, icono = "star",
        esPredefinida = false, orden = 17,
    )

    private val categorias = listOf(cocina, otros, propia)

    @Test
    fun `el menu lista todas las categorias con su icono su color y su contador`() {
        val items = itemsMenuLateral(
            categorias,
            mapOf(1L to 2, 17L to 1),
            desbloqueadas = setOf(1L, 17L, 18L),
        )

        assertEquals(3, items.size)
        assertEquals("Cocina", items[0].nombre)
        assertEquals("restaurant", items[0].icono)
        assertEquals(0xFFFB8C00, items[0].color)
        assertEquals(2, items[0].cantidad)
        assertEquals("Otros", items[1].nombre)
        assertEquals(1, items[1].cantidad)
        assertEquals("Mis cosas", items[2].nombre)
        assertEquals(0, items[2].cantidad)
    }

    @Test
    fun `las categorias bloqueadas se marcan bloqueadas y no revelan contenido`() {
        val items = itemsMenuLateral(
            categorias,
            mapOf(1L to 2, 17L to 1),
            desbloqueadas = setOf(17L),
        )

        assertTrue(items[0].bloqueada)
        assertFalse(items[1].bloqueada)
        assertTrue(items[2].bloqueada)
        // Las filas no llevan ningún contenido de enlaces: solo
        // nombre, ícono, color, contador y bloqueo.
        for (item in items) {
            val campos = item.javaClass.declaredFields
                .filter { !it.isSynthetic }.map { it.name }.toSet()
            assertEquals(
                setOf(
                    "categoriaId", "nombre", "color", "icono",
                    "cantidad", "bloqueada", "esPredefinida",
                ),
                campos,
            )
        }
    }

    @Test
    fun `las carpetas propias se distinguen de las predefinidas`() {
        val items = itemsMenuLateral(
            categorias,
            emptyMap(),
            desbloqueadas = emptySet(),
        )

        assertTrue(items[0].esPredefinida)
        assertTrue(items[1].esPredefinida)
        assertFalse(items[2].esPredefinida)
    }

    @Test
    fun `una categoria sin contador muestra cero`() {
        val items = itemsMenuLateral(
            listOf(cocina),
            emptyMap(),
            desbloqueadas = setOf(1L),
        )

        assertEquals(1, items.size)
        assertEquals(0, items[0].cantidad)
        assertFalse(items[0].bloqueada)
    }

    @Test
    fun `el orden del menu es el orden de las categorias`() {
        val items = itemsMenuLateral(
            categorias,
            emptyMap(),
            desbloqueadas = emptySet(),
        )

        assertEquals(listOf(1L, 17L, 18L), items.map { it.categoriaId })
    }
}
