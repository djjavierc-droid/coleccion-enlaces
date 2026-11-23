package com.coleccionenlaces.app.util

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas de [crearIntentAbrirEnlace] y [crearIntentCompartirEnlace]
 * (Fase 8).
 *
 * Los intents se construyen pero no se lanzan: la prueba afirma la
 * acción, los datos y los extras que Android usará al lanzarlos.
 * `Intent` es una clase de Android, así que corren con Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentsEnlaceTest {

    @Test
    fun `abrir usa ACTION_VIEW con la URL como datos`() {
        val intent = crearIntentAbrirEnlace("https://ejemplo.com/articulo")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://ejemplo.com/articulo", intent.data.toString())
    }

    @Test
    fun `abrir recorta los espacios alrededor de la URL`() {
        val intent = crearIntentAbrirEnlace("  https://ejemplo.com/articulo  ")
        assertEquals("https://ejemplo.com/articulo", intent.data.toString())
    }

    @Test
    fun `compartir usa ACTION_SEND con texto plano`() {
        val intent = crearIntentCompartirEnlace("https://ejemplo.com/articulo")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
    }

    @Test
    fun `compartir lleva la URL en EXTRA_TEXT`() {
        val intent = crearIntentCompartirEnlace("https://ejemplo.com/articulo")
        assertEquals(
            "https://ejemplo.com/articulo",
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    @Test
    fun `compartir recorta los espacios alrededor de la URL`() {
        val intent = crearIntentCompartirEnlace("  https://ejemplo.com/articulo  ")
        assertEquals(
            "https://ejemplo.com/articulo",
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    @Test
    fun `compartir conserva la consulta de la URL`() {
        val intent = crearIntentCompartirEnlace(
            "https://ejemplo.com/buscar?q=paella&pagina=2",
        )
        assertEquals(
            "https://ejemplo.com/buscar?q=paella&pagina=2",
            intent.getStringExtra(Intent.EXTRA_TEXT),
        )
    }
}
