package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.data.Enlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas unitarias de [ClasificadorInteligente] (Fix 3): el motor de
 * producción decide modelo -> cabeza aprendida -> "Otros". JVM pura:
 * el modelo se pasa como null (su prueba con el .tflite real vive en
 * ClasificadorModeloTest, con Robolectric) y la cabeza se entrena en
 * el propio test.
 */
class ClasificadorInteligenteTest {

    private val etiquetas = listOf(
        "Cocina",
        "Videos de mecánica",
        "Videos de construcción",
        "Contenido XXX",
        "Entretenimiento",
        "Tecnología y celulares",
        "Autos y motos",
        "Salud y ejercicio",
        "Dinero y finanzas",
        "Noticias",
        "Juegos",
        "Hazlo tú mismo (DIY)",
        "Jardín y plantas",
        "Pesca y campo",
        "Moda y ropa",
        "Viajes",
    )

    private fun motorSinModelo(cabeza: CabezaAprendizaje = CabezaAprendizaje(etiquetas)) =
        ClasificadorInteligente(modelo = null, cabeza = cabeza, etiquetas = etiquetas)

    @Test
    fun `sin modelo y sin cabeza todo va a Otros`() {
        assertEquals(
            Clasificador.OTROS,
            motorSinModelo().clasificar(
                "Contratamos a una IA y ya vendía croquetas en Marte",
                "Hoy hemos contratado a una",
                "https://www.facebook.com/EterniCodIA/",
            ),
        )
    }

    @Test
    fun `un texto vacio va a Otros`() {
        assertEquals(
            Clasificador.OTROS,
            motorSinModelo().clasificar("", "", "https://ejemplo.com/video"),
        )
    }

    @Test
    fun `la cabeza aprendida clasifica tras una correccion`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea(
                "Contratamos a una IA y ya vendía croquetas en Marte",
                "Tecnología y celulares",
            )
        }
        assertEquals(
            "Tecnología y celulares",
            motorSinModelo(cabeza).clasificar(
                "Contratamos a una IA y ya vendía croquetas en Marte",
                "Hoy hemos contratado a una",
                "https://www.facebook.com/EterniCodIA/",
            ),
        )
    }

    @Test
    fun `un texto sin palabras aprendidas va a Otros`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        cabeza.aprenderEnLinea(
            "Contratamos a una IA y ya vendía croquetas en Marte",
            "Tecnología y celulares",
        )
        // No comparte ninguna palabra con el ejemplo aprendido: la
        // cabeza solo tiene sus sesgos y no llega al umbral, así que
        // no se adivina.
        assertEquals(
            Clasificador.OTROS,
            motorSinModelo(cabeza).clasificar(
                "Video de un perro",
                "El mejor video de la semana",
                "https://ejemplo.com/video",
            ),
        )
    }

    @Test
    fun `clasificarEnlace respeta la categoria manual`() {
        val enlace = Enlace(
            url = "https://ejemplo.com/receta",
            titulo = "Receta de pastel",
            descripcion = "",
            categoriaId = 1L,
            categoriaManual = true,
        )
        assertNull(motorSinModelo().clasificarEnlace(enlace))
    }

    @Test
    fun `clasificarEnlace sin manual devuelve la clasificacion`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea("Receta de pastel de chocolate", "Cocina")
        }
        val enlace = Enlace(
            url = "https://ejemplo.com/receta",
            titulo = "Receta de pastel de chocolate",
            descripcion = "",
            categoriaId = 1L,
            categoriaManual = false,
        )
        assertEquals("Cocina", motorSinModelo(cabeza).clasificarEnlace(enlace))
    }

    // ---- Fix 4, bug 2: el texto que es solo un dominio ----

    @Test
    fun `esTextoSoloDominio detecta solo dominios`() {
        assertTrue(esTextoSoloDominio("facebook.com"))
        assertTrue(esTextoSoloDominio("www.youtube.com"))
        assertTrue(esTextoSoloDominio("  facebook.com  "))
        assertFalse(esTextoSoloDominio("facebook.com/video"))
        assertFalse(esTextoSoloDominio("Mira este video"))
        assertFalse(esTextoSoloDominio("facebook"))
        assertFalse(esTextoSoloDominio(""))
        assertFalse(esTextoSoloDominio("facebook.com es lo mejor"))
    }

    /**
     * El caso exacto de Javier: título "facebook.com", descripción
     * vacía. Sin cabeza entrenada, va a "Otros": el modelo nunca
     * debe decidir sobre un dominio solo (medido con el .tflite
     * real: "facebook.com" -> "Tecnología y celulares" 0,624).
     */
    @Test
    fun `un titulo que es solo el dominio va a Otros sin cabeza entrenada`() {
        assertEquals(
            Clasificador.OTROS,
            motorSinModelo().clasificar(
                "facebook.com",
                "",
                "https://www.facebook.com/share/r/1Bo58NQ",
            ),
        )
        assertEquals(
            Clasificador.OTROS,
            motorSinModelo().clasificar(
                "youtube.com",
                "",
                "https://www.youtube.com/watch?v=x",
            ),
        )
    }

    /**
     * Y con la cabeza entrenada (el movimiento manual de Javier:
     * ese enlace de "facebook.com" a "Juegos"), la corrección SÍ
     * se aplica: antes del Fix 4 el modelo decidía primero (0,624
     * para Tecnología) y la cabeza nunca llegaba a hablar.
     */
    @Test
    fun `un titulo que es solo el dominio lo decide la cabeza aprendida`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea("facebook.com", "Juegos")
        }
        assertEquals(
            "Juegos",
            motorSinModelo(cabeza).clasificar(
                "facebook.com",
                "",
                "https://www.facebook.com/share/r/1Bo58NQ",
            ),
        )
    }

    /**
     * Un título real con palabras sigue el camino normal
     * (modelo -> cabeza -> "Otros"): el atajo de dominio solo
     * actúa cuando el texto ES solo el dominio.
     */
    @Test
    fun `un titulo real con palabras no usa el atajo de dominio`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea("Receta de pastel de chocolate", "Cocina")
        }
        assertEquals(
            "Cocina",
            motorSinModelo(cabeza).clasificar(
                "Receta de pastel de chocolate",
                "",
                "https://ejemplo.com/receta",
            ),
        )
    }
}
