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
        // nombre, ícono, color, contador y bloqueo. Los campos que
        // empiezan por `$` no se cuentan: los genera el compilador
        // (el de Compose añade `$stable`, público y no sintético,
        // a las clases estables) y no son datos de la fila.
        for (item in items) {
            val campos = item.javaClass.declaredFields
                .filter { !it.isSynthetic && !it.name.startsWith("$") }
                .map { it.name }.toSet()
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

    /**
     * Fix 4, bug 1: las filas del menú incluyen TODAS las categorías
     * (17 predefinidas + propias) y la sección "Acerca de" completa.
     * Es lo que dibuja el LazyColumn: si una fila faltara aquí, el
     * cajón no la mostraría ni desplazando (eso es lo que pasó en la
     * v1.0.2: "Hazlo tú mismo (DIY)" quedaba cortada y "Acerca de"
     * era inalcanzable).
     */
    @Test
    fun `las filas del menu incluyen las 17 categorias las propias y Acerca de`() {
        val diecisiete = (1..17).map { i ->
            Categoria(
                id = i.toLong(), nombre = "Categoría $i",
                color = 0xFF000000, icono = "category",
                esPredefinida = true, orden = i - 1,
            )
        }
        val propias = listOf(
            Categoria(
                id = 18, nombre = "Mis cosas", color = 0xFF7E57C2,
                icono = "star", esPredefinida = false, orden = 17,
            ),
            Categoria(
                id = 19, nombre = "Trabajo", color = 0xFF00897B,
                icono = "work", esPredefinida = false, orden = 18,
            ),
        )
        val filas = filasMenuLateral(
            diecisiete + propias,
            emptyMap(),
            desbloqueadas = emptySet(),
        )

        val filasCategoria = filas.filterIsInstance<FilaMenuLateral.Categoria>()
        assertEquals(19, filasCategoria.size)
        assertEquals(
            (1L..19L).toList(),
            filasCategoria.map { it.item.categoriaId },
        )
        // "Acerca de" al final, con sus tres entradas y en orden.
        val legales = filas.filterIsInstance<FilaMenuLateral.Legal>()
        assertEquals(3, legales.size)
        assertEquals(
            com.coleccionenlaces.app.ui.legal.TipoLegal.entries,
            legales.map { it.tipo },
        )
        assertTrue(filas.contains(FilaMenuLateral.TituloAcercaDe))
        assertTrue(filas.indexOf(FilaMenuLateral.TituloAcercaDe) < filas.indexOf(legales.first()))
        // "Nueva carpeta" está entre las categorías y "Acerca de".
        val indiceNueva = filas.indexOf(FilaMenuLateral.NuevaCarpeta)
        assertTrue(indiceNueva > filas.indexOf(filasCategoria.last()))
        assertTrue(indiceNueva < filas.indexOf(FilaMenuLateral.TituloAcercaDe))
        assertTrue(filas.contains(FilaMenuLateral.Todos))
        assertTrue(filas.contains(FilaMenuLateral.Favoritos))
    }
}
