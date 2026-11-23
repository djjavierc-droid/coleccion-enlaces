package com.coleccionenlaces.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import java.net.UnknownHostException

/**
 * Pruebas del repositorio (Fase 5) sobre una base Room en memoria
 * (también sembrada con las 17 categorías predefinidas).
 *
 * Ninguna prueba toca internet: el servicio de vista previa usa un
 * OkHttpClient con interceptores que simulan la respuesta del
 * servidor (mismo patrón que `ServicioVistaPreviaTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositorioEnlacesTest {

    private lateinit var db: AppDatabase
    private lateinit var enlaceDao: EnlaceDao
    private lateinit var categoriaDao: CategoriaDao

    private var cocinaId: Long = 0
    private var entretenimientoId: Long = 0
    private var otrosId: Long = 0

    private val htmlReceta = """
        <html><head>
        <meta property="og:title" content="Receta de paella valenciana">
        <meta property="og:description" content="La receta tradicional paso a paso.">
        <meta property="og:image" content="https://ejemplo.com/img/paella.jpg">
        </head><body></body></html>
    """.trimIndent()

    @Before
    fun crearBase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        enlaceDao = db.enlaceDao()
        categoriaDao = db.categoriaDao()
        cocinaId = categoriaDao.obtenerPorNombre("Cocina")!!.id
        entretenimientoId = categoriaDao.obtenerPorNombre("Entretenimiento")!!.id
        otrosId = categoriaDao.obtenerPorNombre("Otros")!!.id
    }

    @After
    fun cerrarBase() {
        db.close()
    }

    /** Cliente que responde 200 con el HTML indicado según la URL pedida. */
    private fun clienteFalso(respuestas: Map<String, String>): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val cuerpo = respuestas[cadena.request().url.toString()] ?: ""
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()

    private fun repositorio(respuestas: Map<String, String> = emptyMap()): RepositorioEnlaces =
        RepositorioEnlaces(
            enlaceDao,
            categoriaDao,
            ServicioVistaPrevia(cliente = clienteFalso(respuestas)),
        )

    private fun repositorioSinInternet(): RepositorioEnlaces =
        RepositorioEnlaces(
            enlaceDao,
            categoriaDao,
            ServicioVistaPrevia(
                cliente = OkHttpClient.Builder()
                    .addInterceptor { throw UnknownHostException("ejemplo.com") }
                    .build(),
            ),
        )

    @Test
    fun `agregarEnlace con vista previa simulada guarda titulo descripcion imagen y clasifica`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
            )

            val resultado = repo.agregarEnlace("https://ejemplo.com/receta")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertTrue(id > 0)
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Receta de paella valenciana", guardado.titulo)
            assertEquals("La receta tradicional paso a paso.", guardado.descripcion)
            assertEquals("https://ejemplo.com/img/paella.jpg", guardado.urlImagen)
            assertEquals(cocinaId, guardado.categoriaId)
            assertFalse(guardado.categoriaManual)
            assertFalse(guardado.esFavorito)
        }

    @Test
    fun `agregarEnlace con vista previa vacia guarda el dominio como titulo y clasifica Otros`() =
        runBlocking {
            val repo = repositorio()

            val resultado = repo.agregarEnlace("https://www.ejemplo.com/pagina")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("ejemplo.com", guardado.titulo)
            assertEquals("", guardado.descripcion)
            assertNull(guardado.urlImagen)
            assertEquals(otrosId, guardado.categoriaId)
        }

    @Test
    fun `agregarEnlace con URL invalida devuelve error y no guarda nada`() = runBlocking {
        val repo = repositorio()

        val resultado = repo.agregarEnlace("no-es-una-url")

        assertEquals(
            ResultadoAgregarEnlace.Error(TipoErrorAgregarEnlace.URL_INVALIDA),
            resultado,
        )
        assertTrue(enlaceDao.obtenerTodos().first().isEmpty())
    }

    @Test
    fun `agregarEnlace sin internet guarda igual con el dominio como titulo`() = runBlocking {
        val repo = repositorioSinInternet()

        val resultado = repo.agregarEnlace("https://www.ejemplo.com/receta")

        assertTrue(resultado is ResultadoAgregarEnlace.Exito)
        val id = (resultado as ResultadoAgregarEnlace.Exito).id
        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals("ejemplo.com", guardado.titulo)
        assertEquals(otrosId, guardado.categoriaId)
    }

    @Test
    fun `agregarEnlace clasifica por la descripcion cuando el titulo no dice nada`() =
        runBlocking {
            val html = """
                <html><head>
                <meta property="og:title" content="Mira esto">
                <meta property="og:description" content="Receta con horno y sarten.">
                </head><body></body></html>
            """.trimIndent()
            val repo = repositorio(mapOf("https://ejemplo.com/mira" to html))

            val resultado = repo.agregarEnlace("https://ejemplo.com/mira")

            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertEquals(cocinaId, enlaceDao.obtenerPorId(id)!!.categoriaId)
        }

    @Test
    fun `reclasificarEnlace no sobrescribe la categoria manual`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        assertTrue(repo.cambiarCategoriaManual(id, entretenimientoId))
        // El título ahora habla de fútbol, pero la categoría es manual.
        enlaceDao.actualizar(
            enlaceDao.obtenerPorId(id)!!.copy(titulo = "Partido de futbol en directo"),
        )

        val aplicado = repo.reclasificarEnlace(id)

        assertFalse(aplicado)
        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals(entretenimientoId, guardado.categoriaId)
        assertTrue(guardado.categoriaManual)
    }

    @Test
    fun `reclasificarEnlace si reclasifica cuando no es manual`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        assertEquals(cocinaId, enlaceDao.obtenerPorId(id)!!.categoriaId)
        enlaceDao.actualizar(
            enlaceDao.obtenerPorId(id)!!.copy(titulo = "Partido de futbol en directo"),
        )

        val aplicado = repo.reclasificarEnlace(id)

        assertTrue(aplicado)
        assertEquals(entretenimientoId, enlaceDao.obtenerPorId(id)!!.categoriaId)
    }

    @Test
    fun `actualizarEnlace conserva la categoria y el flag manual al editar`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        assertTrue(repo.cambiarCategoriaManual(id, entretenimientoId))
        val editado = enlaceDao.obtenerPorId(id)!!.copy(
            titulo = "Mi receta favorita",
            descripcion = "La hago los domingos",
        )

        repo.actualizarEnlace(editado)

        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals("Mi receta favorita", guardado.titulo)
        assertEquals(entretenimientoId, guardado.categoriaId)
        assertTrue(guardado.categoriaManual)
    }

    @Test
    fun `cambiarCategoriaManual pone la categoria elegida y marca manual`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id

        val cambiado = repo.cambiarCategoriaManual(id, entretenimientoId)

        assertTrue(cambiado)
        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals(entretenimientoId, guardado.categoriaId)
        assertTrue(guardado.categoriaManual)
    }

    @Test
    fun `cambiarCategoriaManual con enlace o categoria inexistentes devuelve false`() =
        runBlocking {
            val repo = repositorio()

            assertFalse(repo.cambiarCategoriaManual(9999L, cocinaId))
            val id = enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/x", titulo = "X", categoriaId = cocinaId),
            )
            assertFalse(repo.cambiarCategoriaManual(id, 9999L))
            assertEquals(cocinaId, enlaceDao.obtenerPorId(id)!!.categoriaId)
        }

    @Test
    fun `alternarFavorito invierte el flag y devuelve el nuevo estado`() = runBlocking {
        val repo = repositorio()
        val id = enlaceDao.insertar(
            Enlace(url = "https://ejemplo.com/x", titulo = "X", categoriaId = cocinaId),
        )

        assertEquals(true, repo.alternarFavorito(id))
        assertTrue(enlaceDao.obtenerPorId(id)!!.esFavorito)
        assertEquals(false, repo.alternarFavorito(id))
        assertFalse(enlaceDao.obtenerPorId(id)!!.esFavorito)
        assertNull(repo.alternarFavorito(9999L))
    }

    @Test
    fun `borrarEnlace elimina el enlace`() = runBlocking {
        val repo = repositorio()
        val id = enlaceDao.insertar(
            Enlace(url = "https://ejemplo.com/x", titulo = "X", categoriaId = cocinaId),
        )

        repo.borrarEnlace(id)

        assertNull(enlaceDao.obtenerPorId(id))
    }

    @Test
    fun `buscarEnlaces encuentra por titulo descripcion y nombre de categoria`() =
        runBlocking {
            val repo = repositorio()
            enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/a",
                    titulo = "Receta de paella",
                    descripcion = "Arroz y azafran",
                    categoriaId = cocinaId,
                ),
            )
            enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/b",
                    titulo = "Partido de futbol",
                    descripcion = "El clasico",
                    categoriaId = entretenimientoId,
                ),
            )

            assertEquals(1, repo.buscarEnlaces("paella").first().size)
            assertEquals(1, repo.buscarEnlaces("clasico").first().size)
            assertEquals(1, repo.buscarEnlaces("Cocina").first().size)
            assertEquals(1, repo.buscarEnlaces("entretenimiento").first().size)
            assertEquals(0, repo.buscarEnlaces("no existe nada").first().size)
            assertEquals(2, repo.buscarEnlaces("").first().size)
        }

    @Test
    fun `obtenerEnlacesPorCategoria y obtenerFavoritos filtran con categoria resuelta`() =
        runBlocking {
            val repo = repositorio()
            val idCocina = enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/a",
                    titulo = "Receta",
                    categoriaId = cocinaId,
                    esFavorito = true,
                ),
            )
            enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/b",
                    titulo = "Partido",
                    categoriaId = entretenimientoId,
                ),
            )

            val deCocina = repo.obtenerEnlacesPorCategoria(cocinaId).first()
            assertEquals(1, deCocina.size)
            assertEquals(idCocina, deCocina.first().enlace.id)
            assertEquals("Cocina", deCocina.first().categoria.nombre)
            val favoritos = repo.obtenerFavoritos().first()
            assertEquals(1, favoritos.size)
            assertEquals("Cocina", favoritos.first().categoria.nombre)
        }

    @Test
    fun `enlacesConCategoria resuelve categoriaId null como Otros`() = runBlocking {
        val repo = repositorio()
        enlaceDao.insertar(
            Enlace(
                url = "https://ejemplo.com/sin-categoria",
                titulo = "Sin carpeta",
                categoriaId = null,
            ),
        )

        val lista = repo.enlacesConCategoria.first()

        assertEquals(1, lista.size)
        assertEquals("Otros", lista.first().categoria.nombre)
        assertEquals(otrosId, lista.first().categoria.id)
        assertEquals(0xFF90A4AE, lista.first().categoria.color)
        assertEquals("category", lista.first().categoria.icono)
    }

    @Test
    fun `contadoresPorCategoria cuenta por categoria y los sin categoria en Otros`() =
        runBlocking {
            val repo = repositorio()
            enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/a", titulo = "A", categoriaId = cocinaId),
            )
            enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/b", titulo = "B", categoriaId = cocinaId),
            )
            enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/c", titulo = "C", categoriaId = null),
            )

            val contadores = repo.contadoresPorCategoria.first()

            assertEquals(2, contadores[cocinaId])
            assertEquals(1, contadores[otrosId])
            assertNull(contadores[entretenimientoId])
        }

    @Test
    fun `categorias expone las 17 predefinidas en orden`() = runBlocking {
        val repo = repositorio()

        val categorias = repo.categorias.first()

        assertEquals(17, categorias.size)
        assertEquals("Cocina", categorias.first().nombre)
        assertEquals("Otros", categorias.last().nombre)
    }

    @Test
    fun `agregarEnlace con espacios alrededor de la URL la limpia antes de guardar`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
            )

            val resultado = repo.agregarEnlace("  https://ejemplo.com/receta  ")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertEquals("https://ejemplo.com/receta", enlaceDao.obtenerPorId(id)!!.url)
        }
}
