package com.coleccionenlaces.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la detección de la página de error de vídeo de Facebook
 * y de los enlaces cortos de compartir (Fix 4, bug 3). Funciones
 * puras, sin red ni WebView: lo que el WebView no puede probar en la
 * JVM es solo el pintado; la decisión de no guardar la página de
 * error como miniatura se prueba aquí.
 */
class DeteccionVideoTest {

    @Test
    fun `detecta el texto exacto de la captura de Javier`() {
        assertTrue(
            detectarVideoNoDisponible(
                "Video no disponible — Es posible que este video ya no " +
                    "exista o que no tengas permiso para verlo",
            ),
        )
    }

    @Test
    fun `detecta variantes en ingles y sin acentos`() {
        assertTrue(detectarVideoNoDisponible("Video unavailable"))
        assertTrue(detectarVideoNoDisponible("This video may no longer exist"))
        assertTrue(detectarVideoNoDisponible("This video is unavailable"))
        assertTrue(detectarVideoNoDisponible("Contenido no disponible"))
        assertTrue(
            detectarVideoNoDisponible(
                "VIDEO NO DISPONIBLE\nEs posible que este video ya no exista",
            ),
        )
    }

    /**
     * Error nuevo de copyright (investigación de 1 hora, hilo C, §5):
     * no contiene "Video Unavailable", pero es la misma pantalla de
     * error. El filtro del OCR y del enriquecimiento debe cazarlo
     * igual: si ese texto llegara al clasificador, clasificaría mal.
     */
    @Test
    fun `detecta el error nuevo de copyright sin Video Unavailable`() {
        assertTrue(
            detectarVideoNoDisponible(
                "Facebook Unavailable — This video can't be embedded " +
                    "because it may contain content owned by someone else.",
            ),
        )
        assertTrue(detectarVideoNoDisponible("Facebook Unavailable"))
        assertTrue(detectarVideoNoDisponible("This video can't be embedded"))
    }

    @Test
    fun `no detecta error en textos normales de video`() {
        assertFalse(detectarVideoNoDisponible("Mira este video de Minecraft"))
        assertFalse(
            detectarVideoNoDisponible(
                "Contratamos a una IA... y el primer día ya vendía croquetas en Marte...",
            ),
        )
        assertFalse(detectarVideoNoDisponible("Gameplay de Minecraft construyendo mi base"))
        assertFalse(detectarVideoNoDisponible(""))
        assertFalse(detectarVideoNoDisponible(null))
        // "Video" solo no es error: el título de un video cualquiera
        // no debe bloquear la captura de su cuadro.
        assertFalse(detectarVideoNoDisponible("Video"))
    }

    @Test
    fun `detecta enlaces cortos de compartir de Facebook`() {
        assertTrue(
            esUrlCortaFacebookCompartida("https://www.facebook.com/share/r/1JqVPz7de"),
        )
        assertTrue(
            esUrlCortaFacebookCompartida("https://www.facebook.com/share/r/1DbnJLtb"),
        )
        assertTrue(
            esUrlCortaFacebookCompartida("https://m.facebook.com/share/v/abc123"),
        )
        assertTrue(esUrlCortaFacebookCompartida("https://fb.watch/abc123/"))
    }

    @Test
    fun `no marca como cortos otros enlaces de Facebook ni de fuera`() {
        assertFalse(
            esUrlCortaFacebookCompartida("https://www.facebook.com/EterniCodIA/"),
        )
        assertFalse(
            esUrlCortaFacebookCompartida("https://www.facebook.com/watch?v=123"),
        )
        assertFalse(
            esUrlCortaFacebookCompartida("https://www.youtube.com/watch?v=x"),
        )
        assertFalse(esUrlCortaFacebookCompartida("https://www.ejemplo.com/share/r/1"))
        assertFalse(esUrlCortaFacebookCompartida("no-es-una-url"))
    }
}
