package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias de [ClasificadorVision] (Fix 5): la decisión por
 * OCR (respaldo débil, umbral 0,85), pantalla de error nunca.
 * JVM pura, con un motor falso: el comportamiento del modelo real
 * sobre textos de OCR se verifica en Python con el .tflite real
 * (ver /tmp/verificar-fix5.py) y en [ClasificadorModeloTest].
 *
 * Las etiquetas de imagen se QUITARON (ola 2, hilo E: ML Kit 0/3
 * vídeos como clasificador): ya no hay voto de etiquetas; sin OCR
 * con confianza suficiente, "Otros".
 */
class ClasificadorVisionTest {

    /**
     * Motor falso: clasifica por palabras contenidas en el texto, con
     * la confianza indicada (1,0 por defecto: el falso decide con
     * seguridad total, como un motor real muy seguro). La confianza
     * es el parámetro que prueban los tests del umbral OCR.
     */
    private class MotorFalso(
        private val porPalabra: Map<String, String>,
        private val confianza: Float = 1.0f,
    ) : MotorClasificacion {
        override fun clasificar(titulo: String, descripcion: String, url: String): String {
            val texto = "$titulo $descripcion".trim()
            return porPalabra.entries
                .firstOrNull { (palabra, _) -> texto.contains(palabra, ignoreCase = true) }
                ?.value ?: Clasificador.OTROS
        }

        override fun clasificarTextoConConfianza(texto: String): Pair<String, Float>? {
            val categoria = clasificar(texto, "", "")
            return if (categoria != Clasificador.OTROS) {
                categoria to confianza
            } else {
                null
            }
        }
    }

    private fun vision(motor: MotorClasificacion = MotorFalso(mapOf())) =
        ClasificadorVision(motor)

    @Test
    fun `la pantalla de error nunca se clasifica`() {
        val senales = SenalesVisuales(
            textoOcr = "Video no disponible\nEste video ya no existe",
        )
        assertEquals(Clasificador.OTROS, vision().clasificarPorVision(senales))
    }

    @Test
    fun `la pantalla de error en ingles tampoco se clasifica`() {
        val senales = SenalesVisuales(
            textoOcr = "Video Unavailable\nThis video may no longer exist",
        )
        assertEquals(Clasificador.OTROS, vision().clasificarPorVision(senales))
    }

    @Test
    fun `el texto del OCR con confianza alta clasifica`() {
        val motor = MotorFalso(mapOf("inteligencia artificial" to "Tecnología y celulares"))
        val senales = SenalesVisuales(
            textoOcr = "La inteligencia artificial que vende en línea",
        )
        assertEquals(
            "Tecnología y celulares",
            vision(motor).clasificarPorVision(senales),
        )
    }

    @Test
    fun `sin OCR va a Otros`() {
        val senales = SenalesVisuales(textoOcr = "")
        assertEquals(Clasificador.OTROS, vision().clasificarPorVision(senales))
    }

    @Test
    fun `el OCR que el motor manda a Otros va a Otros`() {
        val motor = MotorFalso(mapOf("receta" to "Cocina"))
        val senales = SenalesVisuales(textoOcr = "Un texto sin tema claro")
        assertEquals(Clasificador.OTROS, vision(motor).clasificarPorVision(senales))
    }

    @Test
    fun `el texto solo-dominio del OCR no va al motor`() {
        val motor = MotorFalso(mapOf("facebook.com" to "Juegos"))
        val senales = SenalesVisuales(textoOcr = "facebook.com")
        // El motor diría Juegos por el dominio, pero el texto
        // solo-dominio no se le consulta: "Otros".
        assertEquals(Clasificador.OTROS, vision(motor).clasificarPorVision(senales))
    }

    @Test
    fun `un fallo del motor da Otros sin romper`() {
        val motor = object : MotorClasificacion {
            override fun clasificar(titulo: String, descripcion: String, url: String): String =
                throw RuntimeException("modelo roto")
        }
        val senales = SenalesVisuales(textoOcr = "Algo de texto")
        assertEquals(Clasificador.OTROS, vision(motor).clasificarPorVision(senales))
    }

    // ---- Umbral OCR (investigación de 1 hora, hilo D) ----

    /**
     * El OCR es solo un respaldo DÉBIL: la basura OCR da confianza
     * alta y equivocada (12/30 cuadros > 0,5 con categoría errónea;
     * el peor, 0,847). Con confianza 0,84 el OCR no decide: "Otros".
     */
    @Test
    fun `el OCR con confianza 0,84 no clasifica`() {
        val motor = MotorFalso(
            mapOf("inteligencia artificial" to "Tecnología y celulares"),
            confianza = 0.84f,
        )
        val senales = SenalesVisuales(
            textoOcr = "La inteligencia artificial que vende en línea",
        )
        assertEquals(Clasificador.OTROS, vision(motor).clasificarPorVision(senales))
    }

    /**
     * Con confianza 0,5 (lo que reporta un motor sin medida real)
     * el OCR tampoco decide: el umbral alto es justo para eso.
     */
    @Test
    fun `el OCR con confianza 0,5 no clasifica`() {
        val motor = MotorFalso(
            mapOf("inteligencia artificial" to "Tecnología y celulares"),
            confianza = 0.5f,
        )
        val senales = SenalesVisuales(
            textoOcr = "La inteligencia artificial que vende en línea",
        )
        assertEquals(Clasificador.OTROS, vision(motor).clasificarPorVision(senales))
    }

    /**
     * Con confianza 0,85 (el umbral) el OCR sí decide: el caso
     * obligatorio (Soylorainegodoy, OCR exacto, 0,963) queda por
     * encima con margen, y el peor OCR erróneo medido (0,847) queda
     * por debajo.
     */
    @Test
    fun `el OCR con confianza 0,85 si clasifica`() {
        val motor = MotorFalso(
            mapOf("inteligencia artificial" to "Tecnología y celulares"),
            confianza = 0.85f,
        )
        val senales = SenalesVisuales(
            textoOcr = "La inteligencia artificial que vende en línea",
        )
        assertEquals(
            "Tecnología y celulares",
            vision(motor).clasificarPorVision(senales),
        )
    }
}
