package com.coleccionenlaces.app.ui.theme

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
 * Pruebas de la preferencia del tema claro/oscuro (Fase 9).
 *
 * SharedPreferences del framework, sin librería nueva. Se limpia la
 * preferencia antes de cada prueba para no depender del orden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferenciasTemaTest {

    private lateinit var contexto: Context

    @Before
    fun limpiar() {
        contexto = ApplicationProvider.getApplicationContext()
        contexto
            .getSharedPreferences(PreferenciasTema.NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `sin nada guardado el tema es oscuro por defecto`() {
        assertTrue(PreferenciasTema.leerTemaOscuro(contexto))
    }

    @Test
    fun `guardar el modo claro persiste`() {
        PreferenciasTema.guardarTemaOscuro(contexto, false)

        assertFalse(PreferenciasTema.leerTemaOscuro(contexto))
    }

    @Test
    fun `guardar el modo oscuro persiste`() {
        PreferenciasTema.guardarTemaOscuro(contexto, false)
        PreferenciasTema.guardarTemaOscuro(contexto, true)

        assertTrue(PreferenciasTema.leerTemaOscuro(contexto))
    }
}
