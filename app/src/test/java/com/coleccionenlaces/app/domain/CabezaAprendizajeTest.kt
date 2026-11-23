package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de [CabezaAprendizaje] (Fix 3): la última capa
 * que aprende en el teléfono con las correcciones de Javier.
 * JVM pura, sin Android ni modelo: la cabeza es regresión logística
 * sobre palabras con hash.
 */
class CabezaAprendizajeTest {

    private val etiquetas = listOf(
        "Cocina",
        "Videos de mecánica",
        "Tecnología y celulares",
        "Entretenimiento",
    )

    @Test
    fun `sin aprender predecir devuelve null`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        assertFalse(cabeza.haAprendido())
        assertNull(cabeza.predecir("Receta de pastel de chocolate"))
    }

    @Test
    fun `aprender en linea predice la categoria corregida`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea(
                "Contratamos a una IA y ya vendía croquetas en Marte",
                "Tecnología y celulares",
            )
        }
        val prediccion = cabeza.predecir(
            "Contratamos a una IA y ya vendía croquetas en Marte",
        )
        assertNotNull(prediccion)
        assertEquals(2, prediccion!!.indiceClase)
        assertEquals("Tecnología y celulares", etiquetas[prediccion.indiceClase])
        assertTrue(prediccion.confianza > 0.6f)
    }

    @Test
    fun `una correccion hacia Otros no entrena pesos`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        cabeza.aprenderEnLinea("Mira esto", Clasificador.OTROS)
        assertFalse(cabeza.haAprendido())
        assertNull(cabeza.predecir("Mira esto"))
    }

    @Test
    fun `una categoria fuera de las 16 no entrena pesos`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        cabeza.aprenderEnLinea("Receta de pastel", "Categoría inventada")
        assertFalse(cabeza.haAprendido())
    }

    @Test
    fun `entrenar por lotes usa los ejemplos validos`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        val usados = cabeza.entrenarLote(
            listOf(
                "Receta de pastel de chocolate" to "Cocina",
                "Cómo cambiar el aceite del carro" to "Videos de mecánica",
                "Mira esto" to Clasificador.OTROS,
                "Texto sin categoría" to "Categoría inventada",
            ),
        )
        assertEquals(2, usados)
        assertTrue(cabeza.haAprendido())
        val prediccion = cabeza.predecir("Receta de pastel de chocolate")
        assertNotNull(prediccion)
        assertEquals("Cocina", etiquetas[prediccion!!.indiceClase])
    }

    @Test
    fun `los pesos sobreviven a la serializacion`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea("Receta de pastel de chocolate", "Cocina")
        }
        val restaurada = CabezaAprendizaje.desdeBytes(etiquetas, cabeza.aBytes())
        assertTrue(restaurada.haAprendido())
        val antes = cabeza.predecir("Receta de pastel de chocolate")
        val despues = restaurada.predecir("Receta de pastel de chocolate")
        assertNotNull(antes)
        assertNotNull(despues)
        assertEquals(antes!!.indiceClase, despues!!.indiceClase)
        assertEquals(antes.confianza, despues.confianza, 0.0001f)
    }

    @Test
    fun `desdeBytes con tamano incorrecto devuelve cabeza vacia`() {
        val restaurada = CabezaAprendizaje.desdeBytes(etiquetas, byteArrayOf(1, 2, 3))
        assertFalse(restaurada.haAprendido())
    }

    @Test
    fun `palabrasDe normaliza acentos y mayusculas`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        assertEquals(
            listOf("receta", "pastel", "chocolate"),
            cabeza.palabrasDe("Receta de PASTEL de chocolate"),
        )
    }
}
