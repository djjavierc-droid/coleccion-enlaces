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
}
