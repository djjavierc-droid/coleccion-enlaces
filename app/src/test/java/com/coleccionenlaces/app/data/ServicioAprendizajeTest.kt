package com.coleccionenlaces.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.coleccionenlaces.app.domain.CabezaAprendizaje
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas de [ServicioAprendizaje] (Fix 5, mina del diagnóstico
 * §6.3): las correcciones solo-dominio no se registran, y las ya
 * guardadas se purgan al arrancar con la cabeza reconstruida desde
 * los ejemplos limpios. Room en memoria con Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServicioAprendizajeTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AprendizajeDao

    private val etiquetas = listOf(
        "Cocina",
        "Videos de mecánica",
        "Tecnología y celulares",
        "Entretenimiento",
    )

    @Before
    fun crearBase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        dao = db.aprendizajeDao()
    }

    @After
    fun cerrarBase() {
        db.close()
    }

    private fun servicio(cabeza: CabezaAprendizaje = CabezaAprendizaje(etiquetas)) =
        ServicioAprendizaje(dao, cabeza)

    @Test
    fun `una correccion solo-dominio no se registra`() = runBlocking {
        val cabeza = CabezaAprendizaje(etiquetas)
        val servicio = servicio(cabeza)
        assertFalse(servicio.registrarCorreccion("facebook.com", "Juegos"))
        assertEquals(0, servicio.contarCorrecciones())
        assertFalse("la cabeza no debe aprender el dominio", cabeza.haAprendido())
        assertNull(cabeza.predecir("facebook.com"))
    }

    @Test
    fun `una correccion con contenido real si se registra`() = runBlocking {
        val cabeza = CabezaAprendizaje(etiquetas)
        val servicio = servicio(cabeza)
        assertTrue(
            servicio.registrarCorreccion(
                "Nuevo juego para celular gratis",
                "Tecnología y celulares",
            ),
        )
        assertEquals(1, servicio.contarCorrecciones())
    }

    @Test
    fun `la purga borra las correcciones solo-dominio y conserva las buenas`() = runBlocking {
        val cabeza = CabezaAprendizaje(etiquetas)
        val servicio = servicio(cabeza)
        // Una corrección buena (por la vía normal).
        assertTrue(
            servicio.registrarCorreccion(
                "Receta de pastel de chocolate",
                "Cocina",
            ),
        )
        // Una corrección mala, insertada directa (simula la versión
        // anterior, que sí la registraba: el vídeo de juego que
        // Javier movió a mano con título "facebook.com").
        dao.insertarCorreccion(
            CorreccionAprendizaje(texto = "facebook.com", categoria = "Videos de mecánica"),
        )
        assertEquals(2, servicio.contarCorrecciones())

        val purgadas = servicio.purgarCorreccionesSoloDominio()

        assertEquals(1, purgadas)
        assertEquals(1, servicio.contarCorrecciones())
        val restantes = dao.obtenerCorrecciones()
        assertEquals("Receta de pastel de chocolate", restantes[0].texto)
        assertEquals("Cocina", restantes[0].categoria)
        // La cabeza se reconstruyó desde los ejemplos limpios: el
        // aprendizaje bueno sobrevive a la purga.
        val prediccion = cabeza.predecir("Receta de pastel de chocolate")
        assertNotNull(prediccion)
        assertEquals("Cocina", etiquetas[prediccion!!.indiceClase])
        assertTrue(
            "confianza ${prediccion.confianza} < 0,6 tras la reconstrucción",
            prediccion.confianza > 0.6f,
        )
    }

    @Test
    fun `la purga sin correcciones malas no toca nada`() = runBlocking {
        val cabeza = CabezaAprendizaje(etiquetas)
        val servicio = servicio(cabeza)
        repeat(5) {
            cabeza.aprenderEnLinea(
                "Contratamos a una IA y ya vendía croquetas en Marte",
                "Tecnología y celulares",
            )
        }
        val antes = cabeza.predecir(
            "Contratamos a una IA y ya vendía croquetas en Marte",
        )
        assertEquals(0, servicio.purgarCorreccionesSoloDominio())
        val despues = cabeza.predecir(
            "Contratamos a una IA y ya vendía croquetas en Marte",
        )
        assertNotNull(despues)
        assertEquals(antes!!.indiceClase, despues!!.indiceClase)
        assertEquals(antes.confianza, despues.confianza, 0.001f)
    }

    @Test
    fun `reiniciar deja la cabeza en cero`() {
        val cabeza = CabezaAprendizaje(etiquetas)
        repeat(5) {
            cabeza.aprenderEnLinea("Receta de pastel", "Cocina")
        }
        assertTrue(cabeza.haAprendido())
        cabeza.reiniciar()
        assertFalse(cabeza.haAprendido())
        assertNull(cabeza.predecir("Receta de pastel"))
    }
}
