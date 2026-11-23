package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.data.Enlace
import com.coleccionenlaces.app.data.EnlaceConCategoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica de bloqueo por carpeta (Fase 9).
 *
 * Lógica pura en la JVM, sin BiometricPrompt: el prompt real no se
 * puede probar aquí (no hay huella ni credencial en el runner) y
 * solo se ejecuta en un teléfono real.
 */
class BloqueoCarpetasTest {

    private fun item(categoriaId: Long): EnlaceConCategoria =
        EnlaceConCategoria(
            Enlace(
                url = "https://ejemplo.com/$categoriaId",
                titulo = "Enlace $categoriaId",
                categoriaId = categoriaId,
            ),
            Categoria(
                id = categoriaId,
                nombre = "Carpeta $categoriaId",
                color = 0xFF000000,
                icono = "category",
            ),
        )

    @Test
    fun `por defecto todas las carpetas estan bloqueadas`() {
        val bloqueo = BloqueoCarpetas()

        assertFalse(bloqueo.estaDesbloqueada(1))
        assertFalse(bloqueo.estaDesbloqueada(2))
        assertTrue(bloqueo.desbloqueadas.value.isEmpty())
    }

    @Test
    fun `desbloquear abre solo esa carpeta`() {
        val bloqueo = BloqueoCarpetas()

        bloqueo.desbloquear(1)

        assertTrue(bloqueo.estaDesbloqueada(1))
        assertFalse(bloqueo.estaDesbloqueada(2))
        assertEquals(setOf(1L), bloqueo.desbloqueadas.value)
    }

    @Test
    fun `desbloquear dos veces la misma carpeta no la duplica`() {
        val bloqueo = BloqueoCarpetas()

        bloqueo.desbloquear(1)
        bloqueo.desbloquear(1)

        assertEquals(setOf(1L), bloqueo.desbloqueadas.value)
    }

    @Test
    fun `bloquearTodas vuelve a bloquear todo`() {
        val bloqueo = BloqueoCarpetas()
        bloqueo.desbloquear(1)
        bloqueo.desbloquear(2)

        bloqueo.bloquearTodas()

        assertFalse(bloqueo.estaDesbloqueada(1))
        assertFalse(bloqueo.estaDesbloqueada(2))
        assertTrue(bloqueo.desbloqueadas.value.isEmpty())
    }

    @Test
    fun `filtrarEnlacesVisibles sin desbloqueo no deja ver nada`() {
        val enlaces = listOf(item(1), item(2))

        assertTrue(filtrarEnlacesVisibles(enlaces, emptySet()).isEmpty())
    }

    @Test
    fun `filtrarEnlacesVisibles deja ver solo las carpetas desbloqueadas`() {
        val enlaces = listOf(item(1), item(2), item(1))

        val visibles = filtrarEnlacesVisibles(enlaces, setOf(1))

        assertEquals(2, visibles.size)
        assertTrue(visibles.all { it.categoria.id == 1L })
    }

    @Test
    fun `filtrarEnlacesVisibles con todo desbloqueado deja ver todo`() {
        val enlaces = listOf(item(1), item(2))

        assertEquals(2, filtrarEnlacesVisibles(enlaces, setOf(1, 2)).size)
    }
}
