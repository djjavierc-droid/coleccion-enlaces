package com.coleccionenlaces.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas del DAO de categorías y de la siembra inicial: la base en
 * memoria debe nacer con las 17 predefinidas, y el usuario debe poder
 * crear, renombrar y borrar sus propias carpetas (Fase 9).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoriaDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CategoriaDao

    @Before
    fun crearBase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        dao = db.categoriaDao()
    }

    @After
    fun cerrarBase() {
        db.close()
    }

    @Test
    fun `la siembra crea las 17 categorias predefinidas en orden`() = runBlocking {
        val predefinidas = dao.obtenerPredefinidas().first()
        assertEquals(17, predefinidas.size)
        assertEquals(
            listOf(
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
                "Otros",
            ),
            predefinidas.map { it.nombre },
        )
        assertTrue(predefinidas.all { it.esPredefinida })
        assertEquals((0..16).toList(), predefinidas.map { it.orden })
        assertEquals(17, dao.contar())
    }

    @Test
    fun `las predefinidas tienen color e icono y colores distintos`() = runBlocking {
        val predefinidas = dao.obtenerPredefinidas().first()
        assertTrue(predefinidas.all { it.icono.isNotBlank() })
        assertTrue(predefinidas.all { it.color != 0L })
        assertEquals(17, predefinidas.map { it.color }.toSet().size)
    }

    @Test
    fun `insertar crea una carpeta propia del usuario`() = runBlocking {
        val id = dao.insertar(
            Categoria(nombre = "Mis recetas", color = 0xFFFF7043, icono = "folder", orden = 17),
        )
        assertTrue(id > 0)
        val creada = dao.obtenerPorId(id)!!
        assertEquals("Mis recetas", creada.nombre)
        assertFalse(creada.esPredefinida)
        assertEquals(18, dao.contar())
        assertEquals(18, dao.obtenerTodas().first().size)
    }

    @Test
    fun `actualizar renombra una carpeta propia`() = runBlocking {
        val id = dao.insertar(
            Categoria(nombre = "Temporal", color = 0xFFFF7043, icono = "folder", orden = 17),
        )
        dao.actualizar(dao.obtenerPorId(id)!!.copy(nombre = "Renombrada"))

        assertEquals("Renombrada", dao.obtenerPorId(id)?.nombre)
        assertNull(dao.obtenerPorNombre("Temporal"))
    }

    @Test
    fun `borrar una carpeta propia deja sus enlaces sin categoria`() = runBlocking {
        val carpetaId = dao.insertar(
            Categoria(nombre = "Propia", color = 0xFFFF7043, icono = "folder", orden = 17),
        )
        val enlaceId = db.enlaceDao().insertar(
            Enlace(url = "https://ejemplo.com/x", titulo = "Enlace propio", categoriaId = carpetaId),
        )
        dao.borrar(dao.obtenerPorId(carpetaId)!!)

        assertNull(dao.obtenerPorId(carpetaId))
        // La clave foránea es SET_NULL: el enlace sobrevive sin carpeta.
        assertNull(db.enlaceDao().obtenerPorId(enlaceId)?.categoriaId)
    }

    @Test
    fun `obtenerPorNombre devuelve null si no existe`() = runBlocking {
        assertNull(dao.obtenerPorNombre("No existe esta categoría"))
    }
}
