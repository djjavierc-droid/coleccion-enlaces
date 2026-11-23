package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas unitarias de [MapeoEtiquetas] (Fix 5): el mapeo a mano de
 * etiquetas de imagen (ML Kit, en inglés) a las 16 categorías, y el
 * voto entre varios cuadros/etiquetas. JVM pura, sin Android.
 */
class MapeoEtiquetasTest {

    @Test
    fun `etiquetas conocidas mapean a su categoria`() {
        assertEquals("Cocina", MapeoEtiquetas.mapa["food"])
        assertEquals("Cocina", MapeoEtiquetas.mapa["cooking"])
        assertEquals("Videos de mecánica", MapeoEtiquetas.mapa["engine"])
        assertEquals("Videos de construcción", MapeoEtiquetas.mapa["construction"])
        assertEquals("Entretenimiento", MapeoEtiquetas.mapa["sports"])
        assertEquals("Entretenimiento", MapeoEtiquetas.mapa["concert"])
        assertEquals("Tecnología y celulares", MapeoEtiquetas.mapa["laptop"])
        assertEquals("Tecnología y celulares", MapeoEtiquetas.mapa["mobile phone"])
        assertEquals("Autos y motos", MapeoEtiquetas.mapa["motorcycle"])
        assertEquals("Salud y ejercicio", MapeoEtiquetas.mapa["yoga"])
        assertEquals("Dinero y finanzas", MapeoEtiquetas.mapa["money"])
        assertEquals("Juegos", MapeoEtiquetas.mapa["video game"])
        assertEquals("Juegos", MapeoEtiquetas.mapa["game controller"])
        assertEquals("Hazlo tú mismo (DIY)", MapeoEtiquetas.mapa["hammer"])
        assertEquals("Jardín y plantas", MapeoEtiquetas.mapa["plant"])
        assertEquals("Pesca y campo", MapeoEtiquetas.mapa["fishing"])
        assertEquals("Moda y ropa", MapeoEtiquetas.mapa["dress"])
        assertEquals("Viajes", MapeoEtiquetas.mapa["beach"])
    }

    @Test
    fun `ni Noticias ni Contenido XXX tienen etiquetas mapeadas`() {
        assertNull(MapeoEtiquetas.mapa.values.find { it == "Noticias" })
        assertNull(MapeoEtiquetas.mapa.values.find { it == "Contenido XXX" })
    }

    @Test
    fun `una persona sola no vota ninguna categoria`() {
        // "Person" sale en vídeos de cualquier tema: no se mapea.
        assertNull(MapeoEtiquetas.mapa["person"])
        assertNull(
            MapeoEtiquetas.votar(
                listOf(EtiquetaImagen("Person", 0.95f)),
            ),
        )
    }

    @Test
    fun `el voto suma confianzas por categoria`() {
        assertEquals(
            "Juegos",
            MapeoEtiquetas.votar(
                listOf(
                    EtiquetaImagen("Video game", 0.9f),
                    EtiquetaImagen("Game controller", 0.85f),
                ),
            ),
        )
    }

    @Test
    fun `una sola etiqueta fuerte gana el voto`() {
        assertEquals(
            "Cocina",
            MapeoEtiquetas.votar(listOf(EtiquetaImagen("Food", 0.9f))),
        )
    }

    @Test
    fun `un total bajo no gana el voto`() {
        assertNull(
            MapeoEtiquetas.votar(listOf(EtiquetaImagen("Food", 0.6f))),
        )
    }

    @Test
    fun `un empate no gana el voto`() {
        assertNull(
            MapeoEtiquetas.votar(
                listOf(
                    EtiquetaImagen("Food", 0.9f),
                    EtiquetaImagen("Car", 0.9f),
                ),
            ),
        )
    }

    @Test
    fun `una ventaja pequena no gana el voto`() {
        assertNull(
            MapeoEtiquetas.votar(
                listOf(
                    EtiquetaImagen("Food", 0.9f),
                    EtiquetaImagen("Car", 0.8f),
                ),
            ),
        )
    }

    @Test
    fun `las etiquetas de confianza baja no votan`() {
        assertNull(
            MapeoEtiquetas.votar(listOf(EtiquetaImagen("Food", 0.4f))),
        )
    }

    @Test
    fun `el mapeo no distingue mayusculas`() {
        assertEquals(
            "Cocina",
            MapeoEtiquetas.votar(listOf(EtiquetaImagen("Food", 0.9f))),
        )
        assertEquals(
            "Cocina",
            MapeoEtiquetas.votar(listOf(EtiquetaImagen("FOOD", 0.9f))),
        )
    }

    @Test
    fun `sin etiquetas no hay voto`() {
        assertNull(MapeoEtiquetas.votar(emptyList()))
    }
}
