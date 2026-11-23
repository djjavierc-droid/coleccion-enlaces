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
 * Pruebas del DAO de enlaces sobre una base Room en memoria (también
 * sembrada con las 17 categorías predefinidas). Corren en
 * testDebugUnitTest con Robolectric: Room necesita un Context de
 * Android y SQLite real, que Robolectric aporta en la JVM sin emulador.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnlaceDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EnlaceDao
    private lateinit var categoriaDao: CategoriaDao

    private var cocinaId: Long = 0
    private var juegosId: Long = 0

    @Before
    fun crearBase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        dao = db.enlaceDao()
        categoriaDao = db.categoriaDao()
        cocinaId = categoriaDao.obtenerPorNombre("Cocina")!!.id
        juegosId = categoriaDao.obtenerPorNombre("Juegos")!!.id
    }

    @After
    fun cerrarBase() {
        db.close()
    }

    private fun enlace(
        url: String = "https://ejemplo.com/receta",
        titulo: String = "Receta de paella",
        descripcion: String = "Arroz, azafrán y mariscos",
        categoriaId: Long? = cocinaId,
        esFavorito: Boolean = false,
        categoriaManual: Boolean = false,
        fechaCreacion: Long = 1_000L,
    ) = Enlace(
        url = url,
        titulo = titulo,
        descripcion = descripcion,
        urlImagen = "https://ejemplo.com/foto.jpg",
        categoriaId = categoriaId,
        esFavorito = esFavorito,
        categoriaManual = categoriaManual,
        fechaCreacion = fechaCreacion,
    )

    @Test
    fun `insertar devuelve id y el enlace se lee por id`() = runBlocking {
        val id = dao.insertar(enlace())
        assertTrue(id > 0)

        val leido = dao.obtenerPorId(id)
        assertEquals("https://ejemplo.com/receta", leido?.url)
        assertEquals("Receta de paella", leido?.titulo)
        assertEquals("Arroz, azafrán y mariscos", leido?.descripcion)
        assertEquals("https://ejemplo.com/foto.jpg", leido?.urlImagen)
        assertEquals(cocinaId, leido?.categoriaId)
        assertEquals(1_000L, leido?.fechaCreacion)
    }

    @Test
    fun `insertar aplica los valores por defecto`() = runBlocking {
        val id = dao.insertar(
            Enlace(url = "https://ejemplo.com/x", titulo = "Solo titulo"),
        )
        val leido = dao.obtenerPorId(id)!!
        assertEquals("", leido.descripcion)
        assertNull(leido.urlImagen)
        assertNull(leido.categoriaId)
        assertFalse(leido.esFavorito)
        assertFalse(leido.categoriaManual)
        assertTrue(leido.fechaCreacion > 0)
    }

    @Test
    fun `actualizar cambia titulo descripcion y favorito`() = runBlocking {
        val id = dao.insertar(enlace())
        dao.actualizar(enlace().copy(id = id, titulo = "Paella valenciana", descripcion = "Nueva", esFavorito = true))

        val leido = dao.obtenerPorId(id)!!
        assertEquals("Paella valenciana", leido.titulo)
        assertEquals("Nueva", leido.descripcion)
        assertTrue(leido.esFavorito)
    }

    @Test
    fun `borrar elimina el enlace`() = runBlocking {
        val id1 = dao.insertar(enlace(fechaCreacion = 1_000L))
        val id2 = dao.insertar(enlace(url = "https://ejemplo.com/otro", titulo = "Otro", fechaCreacion = 2_000L))
        dao.borrar(dao.obtenerPorId(id1)!!)

        assertNull(dao.obtenerPorId(id1))
        assertEquals(listOf(id2), dao.obtenerTodos().first().map { it.id })
    }

    @Test
    fun `borrarPorId elimina el enlace`() = runBlocking {
        val id = dao.insertar(enlace())
        dao.borrarPorId(id)
        assertNull(dao.obtenerPorId(id))
        assertTrue(dao.obtenerTodos().first().isEmpty())
    }

    @Test
    fun `listar todo ordena por fecha descendente`() = runBlocking {
        dao.insertar(enlace(titulo = "Viejo", fechaCreacion = 1_000L))
        dao.insertar(enlace(titulo = "Nuevo", fechaCreacion = 3_000L))
        dao.insertar(enlace(titulo = "Medio", fechaCreacion = 2_000L))

        val titulos = dao.obtenerTodos().first().map { it.titulo }
        assertEquals(listOf("Nuevo", "Medio", "Viejo"), titulos)
    }

    @Test
    fun `filtrar por categoria devuelve solo sus enlaces`() = runBlocking {
        dao.insertar(enlace(titulo = "De cocina", categoriaId = cocinaId))
        dao.insertar(enlace(titulo = "De juegos", categoriaId = juegosId, url = "https://ejemplo.com/juego"))
        dao.insertar(enlace(titulo = "Otra cocina", categoriaId = cocinaId, url = "https://ejemplo.com/otra"))

        val cocina = dao.obtenerPorCategoria(cocinaId).first().map { it.titulo }
        assertEquals(listOf("Otra cocina", "De cocina"), cocina)
        val juegos = dao.obtenerPorCategoria(juegosId).first().map { it.titulo }
        assertEquals(listOf("De juegos"), juegos)
    }

    @Test
    fun `solo favoritos devuelve solo los marcados`() = runBlocking {
        dao.insertar(enlace(titulo = "Favorito", esFavorito = true, fechaCreacion = 2_000L))
        dao.insertar(enlace(titulo = "Normal", esFavorito = false, fechaCreacion = 3_000L))

        val favoritos = dao.obtenerFavoritos().first().map { it.titulo }
        assertEquals(listOf("Favorito"), favoritos)
    }

    @Test
    fun `buscar encuentra por texto en el titulo`() = runBlocking {
        dao.insertar(enlace(titulo = "Receta de paella"))
        dao.insertar(enlace(titulo = "Guía de pesca", categoriaId = juegosId, url = "https://ejemplo.com/pesca"))

        val resultados = dao.buscar("paella").first().map { it.titulo }
        assertEquals(listOf("Receta de paella"), resultados)
    }

    @Test
    fun `buscar encuentra por texto en la descripcion`() = runBlocking {
        dao.insertar(enlace(descripcion = "Arroz, azafrán y mariscos"))
        dao.insertar(enlace(titulo = "Sin coincidencia", descripcion = "Nada que ver", url = "https://ejemplo.com/nada"))

        val resultados = dao.buscar("azafrán").first().map { it.titulo }
        assertEquals(listOf("Receta de paella"), resultados)
    }

    @Test
    fun `buscar encuentra por nombre de categoria`() = runBlocking {
        dao.insertar(enlace(titulo = "Video sin palabras clave", descripcion = "Sin palabras clave"))
        dao.insertar(enlace(titulo = "De juegos", categoriaId = juegosId, url = "https://ejemplo.com/juego"))

        val resultados = dao.buscar("Cocina").first().map { it.titulo }
        assertEquals(listOf("Video sin palabras clave"), resultados)
    }

    @Test
    fun `buscar no distingue mayusculas de minusculas`() = runBlocking {
        dao.insertar(enlace(titulo = "Receta de PAELLA"))

        assertEquals(1, dao.buscar("paella").first().size)
        assertEquals(1, dao.buscar("PAELLA").first().size)
    }

    @Test
    fun `buscar sin coincidencias devuelve lista vacia`() = runBlocking {
        dao.insertar(enlace())
        assertTrue(dao.buscar("zzz-no-existe").first().isEmpty())
    }

    @Test
    fun `buscar con texto vacio devuelve todo`() = runBlocking {
        dao.insertar(enlace(fechaCreacion = 1_000L))
        dao.insertar(enlace(titulo = "Otro", url = "https://ejemplo.com/otro", fechaCreacion = 2_000L))
        assertEquals(2, dao.buscar("").first().size)
    }

    @Test
    fun `categoriaManual se conserva al actualizar`() = runBlocking {
        val id = dao.insertar(enlace(categoriaManual = true))
        dao.actualizar(dao.obtenerPorId(id)!!.copy(titulo = "Título editado"))

        val leido = dao.obtenerPorId(id)!!
        assertTrue(leido.categoriaManual)
        assertEquals("Título editado", leido.titulo)
    }

    @Test
    fun `contar por categoria cuenta los enlaces de cada carpeta`() = runBlocking {
        dao.insertar(enlace(categoriaId = cocinaId))
        dao.insertar(enlace(categoriaId = cocinaId, url = "https://ejemplo.com/otro"))
        dao.insertar(enlace(categoriaId = juegosId, url = "https://ejemplo.com/juego"))

        val conteos = dao.contarPorCategoria().first().associate { it.categoriaId to it.total }
        assertEquals(2, conteos[cocinaId])
        assertEquals(1, conteos[juegosId])
    }
}
