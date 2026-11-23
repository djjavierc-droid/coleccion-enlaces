package com.coleccionenlaces.app.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.coleccionenlaces.app.data.AppDatabase
import com.coleccionenlaces.app.data.Enlace
import com.coleccionenlaces.app.data.RepositorioEnlaces
import com.coleccionenlaces.app.data.ResultadoAgregarEnlace
import com.coleccionenlaces.app.data.ServicioVistaPrevia
import com.coleccionenlaces.app.data.TipoErrorAgregarEnlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas del ViewModel principal (Fase 5).
 *
 * La base es Room en memoria (sembrada) y la vista previa es falsa
 * (interceptor OkHttp sin red). El Main de las corrutinas es el de
 * prueba (`StandardTestDispatcher`); el estado se espera con
 * `first { ... }`, que suspende hasta que el Flow de Room emite el
 * valor buscado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: RepositorioEnlaces
    private lateinit var viewModel: HomeViewModel

    private var cocinaId: Long = 0
    private var entretenimientoId: Long = 0

    private val htmlReceta = """
        <html><head>
        <meta property="og:title" content="Receta de paella valenciana">
        <meta property="og:description" content="La receta tradicional paso a paso.">
        <meta property="og:image" content="https://ejemplo.com/img/paella.jpg">
        </head><body></body></html>
    """.trimIndent()

    @Before
    fun crear() = runBlocking {
        // Unconfined: el viewModelScope ejecuta sus tareas en cuanto se
        // lanzan, sin depender del scheduler virtual del runTest.
        // Se pone ANTES de crear el ViewModel: su viewModelScope usa
        // el Main en cuanto se accede a `estado`.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        cocinaId = db.categoriaDao().obtenerPorNombre("Cocina")!!.id
        entretenimientoId = db.categoriaDao().obtenerPorNombre("Entretenimiento")!!.id
        val cliente = OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val cuerpo =
                    if (cadena.request().url.toString() == "https://ejemplo.com/receta") {
                        htmlReceta
                    } else {
                        ""
                    }
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
        repositorio = RepositorioEnlaces(
            db.enlaceDao(),
            db.categoriaDao(),
            ServicioVistaPrevia(cliente = cliente),
        )
        viewModel = HomeViewModel(repositorio)
    }

    @After
    fun cerrar() {
        db.close()
    }

    @After
    fun quitarMainDePrueba() {
        Dispatchers.resetMain()
    }

    /** Espera a que el estado cumpla la condición y lo devuelve. */
    private suspend fun esperarEstado(predicado: (HomeUiState) -> Boolean): HomeUiState =
        viewModel.estado.first(predicado)

    @Test
    fun `estado inicial cargado con lista vacia y 17 categorias`() = runTest {
        val estado = esperarEstado { !it.cargando }

        assertTrue(estado.enlaces.isEmpty())
        assertEquals(0, estado.totalEnlaces)
        assertEquals(17, estado.categorias.size)
        assertEquals("Cocina", estado.categorias.first().nombre)
        assertFalse(estado.soloFavoritos)
        assertEquals("", estado.busqueda)
        assertEquals(null, estado.categoriaSeleccionadaId)
    }

    @Test
    fun `agregarEnlace actualiza la lista con su categoria resuelta`() = runTest {
        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()

        // El estado se espera ya asentado: los contadores son otra
        // consulta de Room y pueden llegar después que la lista, y
        // `guardandoEnlace` se apaga en una actualización posterior
        // a la inserción. Esperar solo `totalEnlaces == 1` es una
        // carrera (la misma clase que la corregida en la Fase 5):
        // el primer estado con total 1 puede traer los contadores
        // aún vacíos o `guardandoEnlace` aún en true.
        val estado = esperarEstado {
            it.totalEnlaces == 1 &&
                it.contadores[cocinaId] == 1 &&
                !it.guardandoEnlace &&
                it.errorAgregar == null &&
                it.secciones.size == 1
        }

        assertEquals(1, estado.enlaces.size)
        assertEquals("Receta de paella valenciana", estado.enlaces.first().enlace.titulo)
        assertEquals("Cocina", estado.enlaces.first().categoria.nombre)
        assertEquals(1, estado.contadores[cocinaId])
        assertFalse(estado.guardandoEnlace)
        assertEquals(null, estado.errorAgregar)
        assertEquals(1, estado.secciones.size)
        assertEquals("Cocina", estado.secciones.first().categoria.nombre)
    }

    @Test
    fun `agregarEnlace con URL invalida pone el error y no guarda`() = runTest {
        viewModel.agregarEnlace("no-es-una-url")
        advanceUntilIdle()

        // Misma carrera que arriba: el error se pone en una
        // actualización y `guardandoEnlace` se apaga en la siguiente;
        // se espera el estado con ambos asentados.
        val estado = esperarEstado { it.errorAgregar != null && !it.guardandoEnlace }

        assertEquals(TipoErrorAgregarEnlace.URL_INVALIDA, estado.errorAgregar)
        assertEquals(0, estado.totalEnlaces)
        assertFalse(estado.guardandoEnlace)

        viewModel.limpiarErrorAgregar()
        advanceUntilIdle()
        assertEquals(null, esperarEstado { it.errorAgregar == null }.errorAgregar)
    }

    @Test
    fun `la busqueda filtra por titulo descripcion y nombre de categoria`() = runTest {
        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()
        esperarEstado { it.totalEnlaces == 1 }
        db.enlaceDao().insertar(
            Enlace(
                url = "https://ejemplo.com/partido",
                titulo = "Partido de futbol",
                descripcion = "El clasico",
                categoriaId = entretenimientoId,
            ),
        )

        viewModel.cambiarBusqueda("paella")
        val porPaella = esperarEstado {
            it.enlaces.size == 1 &&
                it.enlaces.first().enlace.titulo == "Receta de paella valenciana"
        }
        assertEquals(1, porPaella.enlaces.size)
        assertEquals("Receta de paella valenciana", porPaella.enlaces.first().enlace.titulo)

        viewModel.cambiarBusqueda("clasico")
        val porClasico = esperarEstado {
            it.enlaces.size == 1 &&
                it.enlaces.first().enlace.titulo == "Partido de futbol"
        }
        assertEquals(1, porClasico.enlaces.size)
        assertEquals("Partido de futbol", porClasico.enlaces.first().enlace.titulo)

        viewModel.cambiarBusqueda("entretenimiento")
        val porCategoria = esperarEstado {
            it.enlaces.size == 1 &&
                it.enlaces.first().categoria.nombre == "Entretenimiento"
        }
        assertEquals(1, porCategoria.enlaces.size)

        viewModel.cambiarBusqueda("no existe nada")
        assertEquals(0, esperarEstado { it.enlaces.isEmpty() }.enlaces.size)

        viewModel.limpiarBusqueda()
        assertEquals(2, esperarEstado { it.enlaces.size == 2 }.enlaces.size)
    }

    @Test
    fun `el filtro por categoria muestra solo esa categoria`() = runTest {
        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()
        esperarEstado { it.totalEnlaces == 1 }
        db.enlaceDao().insertar(
            Enlace(
                url = "https://ejemplo.com/partido",
                titulo = "Partido de futbol",
                categoriaId = entretenimientoId,
            ),
        )
        esperarEstado { it.totalEnlaces == 2 }

        viewModel.seleccionarCategoria(cocinaId)
        val filtrado = esperarEstado { it.enlaces.size == 1 && it.categoriaSeleccionadaId == cocinaId }
        assertEquals("Cocina", filtrado.enlaces.first().categoria.nombre)

        viewModel.seleccionarCategoria(null)
        assertEquals(2, esperarEstado { it.enlaces.size == 2 }.enlaces.size)
    }

    @Test
    fun `solo favoritos muestra solo los favoritos`() = runTest {
        val idReceta = (repositorio.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        db.enlaceDao().insertar(
            Enlace(
                url = "https://ejemplo.com/partido",
                titulo = "Partido de futbol",
                categoriaId = entretenimientoId,
            ),
        )
        esperarEstado { it.totalEnlaces == 2 }

        viewModel.alternarSoloFavoritos()
        assertEquals(0, esperarEstado { it.soloFavoritos }.enlaces.size)

        viewModel.alternarFavorito(idReceta)
        val soloFav = esperarEstado { it.enlaces.size == 1 }
        assertEquals("Receta de paella valenciana", soloFav.enlaces.first().enlace.titulo)

        viewModel.alternarSoloFavoritos()
        assertEquals(2, esperarEstado { it.enlaces.size == 2 }.enlaces.size)
    }

    @Test
    fun `cambiarCategoriaManual desde el ViewModel marca manual y cambia la seccion`() =
        runTest {
            val id = (repositorio.agregarEnlace("https://ejemplo.com/receta")
                as ResultadoAgregarEnlace.Exito).id
            esperarEstado { it.totalEnlaces == 1 }

            viewModel.cambiarCategoriaManual(id, entretenimientoId)

            val estado = esperarEstado {
                it.enlaces.firstOrNull()?.categoria?.nombre == "Entretenimiento"
            }
            assertEquals("Entretenimiento", estado.enlaces.first().categoria.nombre)
            assertEquals("Entretenimiento", estado.secciones.first().categoria.nombre)
            assertTrue(db.enlaceDao().obtenerPorId(id)!!.categoriaManual)
        }

    @Test
    fun `borrarEnlace desde el ViewModel quita el enlace de la lista`() = runTest {
        val id = (repositorio.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        esperarEstado { it.totalEnlaces == 1 }

        viewModel.borrarEnlace(id)

        assertEquals(0, esperarEstado { it.totalEnlaces == 0 }.totalEnlaces)
    }

    @Test
    fun `la categoria manual no se sobrescribe al reclasificar desde el ViewModel`() =
        runTest {
            val id = (repositorio.agregarEnlace("https://ejemplo.com/receta")
                as ResultadoAgregarEnlace.Exito).id
            esperarEstado { it.totalEnlaces == 1 }
            viewModel.cambiarCategoriaManual(id, entretenimientoId)
            esperarEstado { it.enlaces.first().categoria.nombre == "Entretenimiento" }
            db.enlaceDao().actualizar(
                db.enlaceDao().obtenerPorId(id)!!.copy(titulo = "Receta de paella"),
            )

            viewModel.reclasificarEnlace(id)
            advanceUntilIdle()

            val estado = esperarEstado { !it.cargando }
            assertEquals("Entretenimiento", estado.enlaces.first().categoria.nombre)
        }
}
