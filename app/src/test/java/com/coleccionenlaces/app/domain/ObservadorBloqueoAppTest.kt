package com.coleccionenlaces.app.domain

import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del bloqueo automático al pasar a segundo plano (Fix 4,
 * requisito 4). El observador es lógica pura sobre
 * [BloqueoCarpetas]: se prueba llamando a [ObservadorBloqueoApp.onStop]
 * directamente, sin dispositivo ni ProcessLifecycleOwner real (eso
 * solo se verifica en el teléfono).
 */
class ObservadorBloqueoAppTest {

    /** Dueño de ciclo de vida mínimo: onStop no lo usa. */
    private val dueno = object : LifecycleOwner {
        override val lifecycle: androidx.lifecycle.Lifecycle
            get() = error("no se usa en esta prueba")
    }

    @Test
    fun `onStop bloquea todas las carpetas desbloqueadas`() {
        val bloqueo = BloqueoCarpetas()
        bloqueo.desbloquear(1)
        bloqueo.desbloquear(2)
        bloqueo.desbloquear(17)
        val observador = ObservadorBloqueoApp(bloqueo)

        observador.onStop(dueno)

        assertFalse(bloqueo.estaDesbloqueada(1))
        assertFalse(bloqueo.estaDesbloqueada(2))
        assertFalse(bloqueo.estaDesbloqueada(17))
        assertTrue(bloqueo.desbloqueadas.value.isEmpty())
    }

    @Test
    fun `onStop con todo bloqueado no hace nada malo`() {
        val bloqueo = BloqueoCarpetas()
        val observador = ObservadorBloqueoApp(bloqueo)

        observador.onStop(dueno)

        assertTrue(bloqueo.desbloqueadas.value.isEmpty())
    }

    @Test
    fun `tras onStop se puede desbloquear de nuevo con huella`() {
        val bloqueo = BloqueoCarpetas()
        val observador = ObservadorBloqueoApp(bloqueo)
        bloqueo.desbloquear(5)
        observador.onStop(dueno)

        bloqueo.desbloquear(5)

        assertTrue(bloqueo.estaDesbloqueada(5))
    }
}
