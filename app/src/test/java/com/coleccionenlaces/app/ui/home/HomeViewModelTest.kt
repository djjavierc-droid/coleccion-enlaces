package com.coleccionenlaces.app.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.coleccionenlaces.app.data.AppDatabase
import com.coleccionenlaces.app.data.Enlace
import com.coleccionenlaces.app.data.RepositorioEnlaces
import com.coleccionenlaces.app.data.ResultadoAgregarEnlace
import com.coleccionenlaces.app.data.ServicioVistaPrevia
import com.coleccionenlaces.app.data.TipoErrorAgregarEnlace
import com.coleccionenlaces.app.data.TipoErrorCarpeta
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
        // Fase 9: por defecto todo está bloqueado y no hay marcadores
        // (no hay enlaces todavía).
        assertTrue(estado.categoriasDesbloqueadas.isEmpty())
        assertTrue(estado.seccionesBloqueadas.isEmpty())
    }

    @Test
    fun `agregarEnlace actualiza la lista con su categoria resuelta`() = runTest {
        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()
        // Fase 9: la carpeta está bloqueada por defecto; sin
        // desbloquearla, el enlace no se ve (ver la prueba de
        // bloqueo). Se desbloquea como haría la huella con éxito.
        viewModel.desbloquearCategoria(cocinaId)

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
        // Fase 9: sin desbloquear, la búsqueda no ve nada de las
        // carpetas bloqueadas (ver la prueba de bloqueo).
        viewModel.desbloquearCategoria(cocinaId)
        viewModel.desbloquearCategoria(entretenimientoId)

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

        viewModel.desbloquearCategoria(cocinaId)
        viewModel.desbloquearCategoria(entretenimientoId)
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

        // Fase 9: sin desbloquear, los favoritos de carpetas
        // bloqueadas no se ven (ver la prueba de bloqueo).
        viewModel.desbloquearCategoria(cocinaId)
        viewModel.desbloquearCategoria(entretenimientoId)
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

            viewModel.desbloquearCategoria(entretenimientoId)
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
    fun `abrirDialogoAgregar con URL compartida prellena el dialogo`() = runTest {
        viewModel.abrirDialogoAgregar("https://youtu.be/dQw4w9WgXcQ")

        val estado = esperarEstado { it.mostrarDialogoAgregar }

        assertTrue(estado.mostrarDialogoAgregar)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", estado.urlInicialDialogo)
    }

    @Test
    fun `cerrarDialogoAgregar cierra y limpia la URL inicial`() = runTest {
        viewModel.abrirDialogoAgregar("https://ejemplo.com/receta")
        esperarEstado { it.mostrarDialogoAgregar }

        viewModel.cerrarDialogoAgregar()

        val estado = esperarEstado { !it.mostrarDialogoAgregar }
        assertFalse(estado.mostrarDialogoAgregar)
        assertEquals(null, estado.urlInicialDialogo)
    }

    @Test
    fun `abrirDialogoAgregar limpia un error anterior`() = runTest {
        viewModel.agregarEnlace("no-es-una-url")
        advanceUntilIdle()
        esperarEstado { it.errorAgregar != null }

        viewModel.abrirDialogoAgregar()

        val estado = esperarEstado { it.mostrarDialogoAgregar && it.errorAgregar == null }
        assertTrue(estado.mostrarDialogoAgregar)
        assertEquals(null, estado.errorAgregar)
    }

    @Test
    fun `agregarEnlace con exito cierra el dialogo`() = runTest {
        viewModel.abrirDialogoAgregar("https://ejemplo.com/receta")
        esperarEstado { it.mostrarDialogoAgregar }

        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()

        val estado = esperarEstado {
            it.totalEnlaces == 1 && !it.guardandoEnlace && !it.mostrarDialogoAgregar
        }
        assertFalse(estado.mostrarDialogoAgregar)
        assertEquals(null, estado.urlInicialDialogo)
        assertEquals(1, estado.totalEnlaces)
    }

    @Test
    fun `agregarEnlace con URL invalida mantiene el dialogo abierto con error`() = runTest {
        viewModel.abrirDialogoAgregar("no-es-una-url")
        esperarEstado { it.mostrarDialogoAgregar }

        viewModel.agregarEnlace("no-es-una-url")
        advanceUntilIdle()

        val estado = esperarEstado { it.errorAgregar != null && !it.guardandoEnlace }
        assertTrue(estado.mostrarDialogoAgregar)
        assertEquals(TipoErrorAgregarEnlace.URL_INVALIDA, estado.errorAgregar)
        assertEquals(0, estado.totalEnlaces)
    }

    @Test
    fun `la categoria manual no se sobrescribe al reclasificar desde el ViewModel`() =
        runTest {
            val id = (repositorio.agregarEnlace("https://ejemplo.com/receta")
                as ResultadoAgregarEnlace.Exito).id
            esperarEstado { it.totalEnlaces == 1 }
            viewModel.cambiarCategoriaManual(id, entretenimientoId)
            esperarEstado { it.totalEnlaces == 1 }
            db.enlaceDao().actualizar(
                db.enlaceDao().obtenerPorId(id)!!.copy(titulo = "Receta de paella"),
            )

            // Fase 9: el enlace cambió a Entretenimiento y esa
            // carpeta está bloqueada; sin desbloquearla no se ve.
            viewModel.desbloquearCategoria(entretenimientoId)
            viewModel.reclasificarEnlace(id)
            advanceUntilIdle()

            val estado = esperarEstado { !it.cargando }
            assertEquals("Entretenimiento", estado.enlaces.first().categoria.nombre)
        }

    // ---- Fase 9: huella por carpeta ----

    @Test
    fun `por defecto las carpetas estan bloqueadas hasta desbloquear con huella`() = runTest {
        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()

        // El enlace existe (total 1) pero no se ve nada de su
        // carpeta: ni lista, ni secciones; solo el marcador de
        // bloqueado, con su nombre y sin contenido.
        val bloqueado = esperarEstado {
            it.totalEnlaces == 1 && it.contadores[cocinaId] == 1
        }
        assertTrue(bloqueado.enlaces.isEmpty())
        assertTrue(bloqueado.secciones.isEmpty())
        assertEquals(1, bloqueado.seccionesBloqueadas.size)
        assertEquals("Cocina", bloqueado.seccionesBloqueadas.first().nombre)
        assertFalse(viewModel.estaDesbloqueada(cocinaId))

        // La huella con éxito desbloquea solo esa carpeta.
        viewModel.desbloquearCategoria(cocinaId)

        val abierto = esperarEstado { it.enlaces.size == 1 }
        assertTrue(viewModel.estaDesbloqueada(cocinaId))
        assertFalse(viewModel.estaDesbloqueada(entretenimientoId))
        assertEquals("Receta de paella valenciana", abierto.enlaces.first().enlace.titulo)
        assertEquals(1, abierto.secciones.size)
        assertTrue(abierto.seccionesBloqueadas.isEmpty())
    }

    @Test
    fun `la busqueda excluye las carpetas bloqueadas`() = runTest {
        viewModel.agregarEnlace("https://ejemplo.com/receta")
        advanceUntilIdle()
        esperarEstado { it.totalEnlaces == 1 && it.contadores[cocinaId] == 1 }

        viewModel.cambiarBusqueda("paella")
        val sinVer = esperarEstado { it.busqueda == "paella" }
        assertEquals(0, sinVer.enlaces.size)

        viewModel.desbloquearCategoria(cocinaId)

        val visto = esperarEstado { it.enlaces.size == 1 }
        assertEquals("Receta de paella valenciana", visto.enlaces.first().enlace.titulo)
    }

    @Test
    fun `los favoritos excluyen las carpetas bloqueadas`() = runTest {
        val id = (repositorio.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        esperarEstado { it.totalEnlaces == 1 }
        repositorio.alternarFavorito(id)

        viewModel.alternarSoloFavoritos()
        val sinVer = esperarEstado { it.soloFavoritos }
        assertEquals(0, sinVer.enlaces.size)

        viewModel.desbloquearCategoria(cocinaId)

        val visto = esperarEstado { it.enlaces.size == 1 }
        assertEquals("Receta de paella valenciana", visto.enlaces.first().enlace.titulo)
    }

    // ---- Fase 9: carpetas propias del usuario ----

    @Test
    fun `crearCarpeta desde el ViewModel crea la carpeta y cierra el dialogo`() = runTest {
        viewModel.abrirDialogoCarpeta()
        esperarEstado { it.mostrarDialogoCarpeta }

        viewModel.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
        advanceUntilIdle()

        val estado = esperarEstado { it.categorias.size == 18 && !it.mostrarDialogoCarpeta }
        assertFalse(estado.mostrarDialogoCarpeta)
        assertEquals(null, estado.errorCarpeta)
        val carpeta = estado.categorias.first { it.nombre == "Mis cosas" }
        assertFalse(carpeta.esPredefinida)
        assertEquals(0xFF7E57C2, carpeta.color)
        assertEquals("star", carpeta.icono)
    }

    @Test
    fun `crearCarpeta duplicada desde el ViewModel pone el error y no crea`() = runTest {
        viewModel.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
        advanceUntilIdle()
        esperarEstado { it.categorias.size == 18 }

        viewModel.abrirDialogoCarpeta()
        viewModel.crearCarpeta("mis cosas", 0xFF1E88E5, "home")
        advanceUntilIdle()

        val estado = esperarEstado { it.errorCarpeta != null }
        assertEquals(TipoErrorCarpeta.NOMBRE_DUPLICADO, estado.errorCarpeta)
        assertTrue(estado.mostrarDialogoCarpeta)
        assertEquals(18, estado.categorias.size)
    }

    @Test
    fun `crearCarpeta hasta el maximo de 20 rechaza la siguiente`() = runTest {
        viewModel.crearCarpeta("Carpeta 1", 0xFF7E57C2, "star")
        advanceUntilIdle()
        viewModel.crearCarpeta("Carpeta 2", 0xFF1E88E5, "home")
        advanceUntilIdle()
        viewModel.crearCarpeta("Carpeta 3", 0xFF43A047, "work")
        advanceUntilIdle()
        esperarEstado { it.categorias.size == 20 }

        viewModel.abrirDialogoCarpeta()
        viewModel.crearCarpeta("Carpeta 4", 0xFFFB8C00, "school")
        advanceUntilIdle()

        val estado = esperarEstado { it.errorCarpeta != null }
        assertEquals(TipoErrorCarpeta.LIMITE_ALCANZADO, estado.errorCarpeta)
        assertTrue(estado.mostrarDialogoCarpeta)
        assertEquals(20, estado.categorias.size)
    }

    @Test
    fun `renombrarCarpeta predefinida desde el ViewModel pone el error`() = runTest {
        viewModel.abrirDialogoCarpeta(db.categoriaDao().obtenerPorNombre("Cocina")!!)

        viewModel.renombrarCarpeta(cocinaId, "Cocina nueva")
        advanceUntilIdle()

        val estado = esperarEstado { it.errorCarpeta != null }
        assertEquals(TipoErrorCarpeta.NO_ES_PROPIA, estado.errorCarpeta)
        assertTrue(estado.mostrarDialogoCarpeta)
        assertEquals("Cocina", db.categoriaDao().obtenerPorId(cocinaId)!!.nombre)
    }

    @Test
    fun `borrarCarpeta propia desde el ViewModel mueve sus enlaces a Otros`() = runTest {
        viewModel.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
        advanceUntilIdle()
        val carpetaId = esperarEstado { it.categorias.size == 18 }
            .categorias.first { it.nombre == "Mis cosas" }.id
        val otrosId = db.categoriaDao().obtenerPorNombre("Otros")!!.id
        db.enlaceDao().insertar(
            Enlace(
                url = "https://ejemplo.com/x",
                titulo = "Enlace en carpeta",
                categoriaId = carpetaId,
            ),
        )
        viewModel.desbloquearCategoria(carpetaId)
        esperarEstado { it.totalEnlaces == 1 && it.enlaces.size == 1 }

        viewModel.borrarCarpeta(carpetaId)
        advanceUntilIdle()

        // El enlace sobrevive en "Otros"; para verlo hay que
        // desbloquear "Otros", también bloqueada por defecto.
        viewModel.desbloquearCategoria(otrosId)
        val estado = esperarEstado {
            it.categorias.size == 17 && it.enlaces.size == 1 &&
                it.enlaces.first().categoria.nombre == "Otros"
        }
        assertEquals("Otros", estado.enlaces.first().categoria.nombre)
        assertEquals(null, estado.enlaces.first().enlace.categoriaId)
    }
}
