package com.coleccionenlaces.app.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas de la marca de la pista del menú lateral (corrección
 * 1.0.1, punto 2): la pista se muestra una sola vez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferenciasMenuTest {

    private lateinit var contexto: Context

    @Before
    fun crear() {
        contexto = ApplicationProvider.getApplicationContext()
        contexto
            .getSharedPreferences(PreferenciasMenu.NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `por defecto la pista no se ha mostrado`() {
        assertFalse(PreferenciasMenu.leerPistaMenuVista(contexto))
    }

    @Test
    fun `guardar la pista la marca como vista`() {
        PreferenciasMenu.guardarPistaMenuVista(contexto)

        assertTrue(PreferenciasMenu.leerPistaMenuVista(contexto))
    }
}
