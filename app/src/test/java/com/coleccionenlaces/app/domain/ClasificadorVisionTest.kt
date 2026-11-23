package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias de [ClasificadorVision] (Fix 5): la decisión por
 * señales visuales (OCR primero, etiquetas después, pantalla de error
 * nunca). JVM pura, con un motor falso: el comportamiento del modelo
 * real sobre textos de OCR se verifica en Python con el .tflite real
 * (ver /tmp/verificar-fix5.py) y en [ClasificadorModeloTest].
 */
class ClasificadorVisionTest {

    /** Motor falso: clasifica por palabras contenidas en el texto. */
    private class MotorFalso(
        private val porPalabra: Map<String, String>,
    ) : MotorClasificacion {
        override fun clasificar(titulo: String, descripcion: String, url: String): String {
            val texto = "$titulo $descripcion".trim()
            return porPalabra.entries
                .firstOrNull { (palabra, _) -> texto.contains(palabra, ignoreCase = true) }
                ?.value ?: Clasificador.OTROS
        }
    }

    private fun vision(motor: MotorClasificacion = MotorFalso(mapOf())) =
        ClasificadorVision(motor)

    @Test
    fun `la pantalla de error nunca se clasifica aunque las etiquetas apunten a Juegos`() {
        val senales = SenalesVisuales(
            etiquetas = listOf(EtiquetaImagen("Video game", 0.95f)),
            textoOcr = "Video no disponible\nEste video ya no existe",
        )
        assertEquals(Clasificador.OTROS, vision().clasificarPorVision(senales))
    }

    @Test
    fun `la pantalla de error en ingles tampoco se clasifica`() {
        val senales = SenalesVisuales(
            etiquetas = listOf(EtiquetaImagen("Video game", 0.95f)),
            textoOcr = "Video Unavailable\nThis video may no longer exist",
        )
        assertEquals(Clasificador.OTROS, vision().clasificarPorVision(senales))
    }

    @Test
    fun `el texto del OCR decide antes que las etiquetas`() {
        val motor = MotorFalso(mapOf("inteligencia artificial" to "Tecnología y celulares"))
        val senales = SenalesVisuales(
            // Las etiquetas apuntan a Cocina, pero el OCR manda.
            etiquetas = listOf(EtiquetaImagen("Food", 0.95f)),
            textoOcr = "La inteligencia artificial que vende en línea",
        )
        assertEquals(
            "Tecnología y celulares",
            vision(motor).clasificarPorVision(senales),
        )
    }

    @Test
    fun `las etiquetas deciden cuando el OCR no dice nada`() {
        val senales = SenalesVisuales(
            etiquetas = listOf(
                EtiquetaImagen("Video game", 0.9f),
                EtiquetaImagen("Game controller", 0.85f),
            ),
            textoOcr = "",
        )
        assertEquals("Juegos", vision().clasificarPorVision(senales))
    }

    @Test
    fun `las etiquetas deciden cuando el motor manda a Otros`() {
        val motor = MotorFalso(mapOf("receta" to "Cocina"))
        val senales = SenalesVisuales(
            etiquetas = listOf(EtiquetaImagen("Video game", 0.9f)),
            textoOcr = "Un texto sin tema claro",
        )
        assertEquals("Juegos", vision(motor).clasificarPorVision(senales))
    }

    @Test
    fun `sin OCR y sin etiquetas va a Otros`() {
        val senales = SenalesVisuales(etiquetas = emptyList(), textoOcr = "")
        assertEquals(Clasificador.OTROS, vision().clasificarPorVision(senales))
    }

    @Test
    fun `el texto solo-dominio del OCR no va al motor, votan las etiquetas`() {
        val motor = MotorFalso(mapOf("facebook.com" to "Juegos"))
        val senales = SenalesVisuales(
            etiquetas = listOf(EtiquetaImagen("Food", 0.9f)),
            textoOcr = "facebook.com",
        )
        // El motor diría Juegos por el dominio, pero el texto
        // solo-dominio no se le consulta: ganan las etiquetas.
        assertEquals("Cocina", vision(motor).clasificarPorVision(senales))
    }

    @Test
    fun `un fallo del motor no rompe la clasificacion por etiquetas`() {
        val motor = object : MotorClasificacion {
            override fun clasificar(titulo: String, descripcion: String, url: String): String =
                throw RuntimeException("modelo roto")
        }
        val senales = SenalesVisuales(
            etiquetas = listOf(EtiquetaImagen("Beach", 0.9f)),
            textoOcr = "Algo de texto",
        )
        assertEquals("Viajes", vision(motor).clasificarPorVision(senales))
    }
}
