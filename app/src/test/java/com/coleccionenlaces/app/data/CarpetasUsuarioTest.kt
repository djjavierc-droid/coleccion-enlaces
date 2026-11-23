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
 * Pruebas de las carpetas propias del usuario (Fase 9).
 *
 * Base Room en memoria (sembrada con las 17 predefinidas) y el
 * repositorio real; no hay red ni vista previa en estas pruebas
 * (crear/renombrar/borrar carpetas no descarga nada).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarpetasUsuarioTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: RepositorioEnlaces

    @Before
    fun crear() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        repositorio = RepositorioEnlaces(
            db.enlaceDao(),
            db.categoriaDao(),
            ServicioVistaPrevia(),
        )
    }

    @After
    fun cerrar() {
        db.close()
    }

    @Test
    fun `crear carpeta propia pasa a 18 y no es predefinida`() = runBlocking {
        val resultado = repositorio.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")

        assertTrue(resultado is ResultadoCarpeta.Exito)
        assertEquals(18, db.categoriaDao().contar())
        val carpeta = db.categoriaDao().obtenerPorId((resultado as ResultadoCarpeta.Exito).id)!!
        assertEquals("Mis cosas", carpeta.nombre)
        assertEquals(0xFF7E57C2, carpeta.color)
        assertEquals("star", carpeta.icono)
        assertFalse(carpeta.esPredefinida)
    }

    @Test
    fun `crear carpeta con nombre vacio es rechazada`() = runBlocking {
        val resultado = repositorio.crearCarpeta("   ", 0xFF7E57C2, "star")

        assertEquals(ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_VACIO), resultado)
        assertEquals(17, db.categoriaDao().contar())
    }

    @Test
    fun `crear carpeta con nombre duplicado es rechazada`() = runBlocking {
        assertTrue(repositorio.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
            is ResultadoCarpeta.Exito)

        val duplicada = repositorio.crearCarpeta("Mis cosas", 0xFF1E88E5, "home")

        assertEquals(ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_DUPLICADO), duplicada)
        assertEquals(18, db.categoriaDao().contar())
    }

    @Test
    fun `crear carpeta con nombre duplicado sin distinguir mayusculas es rechazada`() =
        runBlocking {
            assertTrue(repositorio.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
                is ResultadoCarpeta.Exito)

            val duplicada = repositorio.crearCarpeta("MIS COSAS", 0xFF1E88E5, "home")

            assertEquals(
                ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_DUPLICADO),
                duplicada,
            )
            assertEquals(18, db.categoriaDao().contar())
        }

    @Test
    fun `crear carpeta con el nombre de una predefinida es rechazada`() = runBlocking {
        val resultado = repositorio.crearCarpeta("cocina", 0xFF7E57C2, "star")

        assertEquals(ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_DUPLICADO), resultado)
        assertEquals(17, db.categoriaDao().contar())
    }

    @Test
    fun `crear hasta el maximo de 20 rechaza la siguiente con mensaje amigable`() =
        runBlocking {
            assertTrue(repositorio.crearCarpeta("Carpeta 1", 0xFF7E57C2, "star")
                is ResultadoCarpeta.Exito)
            assertTrue(repositorio.crearCarpeta("Carpeta 2", 0xFF1E88E5, "home")
                is ResultadoCarpeta.Exito)
            assertTrue(repositorio.crearCarpeta("Carpeta 3", 0xFF43A047, "work")
                is ResultadoCarpeta.Exito)
            assertEquals(20, db.categoriaDao().contar())

            val cuarta = repositorio.crearCarpeta("Carpeta 4", 0xFFFB8C00, "school")

            assertEquals(
                ResultadoCarpeta.Error(TipoErrorCarpeta.LIMITE_ALCANZADO),
                cuarta,
            )
            assertEquals(20, db.categoriaDao().contar())
        }

    @Test
    fun `renombrar carpeta propia cambia el nombre`() = runBlocking {
        val id = (repositorio.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
            as ResultadoCarpeta.Exito).id

        val resultado = repositorio.renombrarCarpeta(id, "Mis recetas")

        assertEquals(ResultadoCarpeta.Exito(id), resultado)
        assertEquals("Mis recetas", db.categoriaDao().obtenerPorId(id)!!.nombre)
        assertEquals(18, db.categoriaDao().contar())
    }

    @Test
    fun `renombrar carpeta predefinida es rechazado y no la toca`() = runBlocking {
        val cocina = db.categoriaDao().obtenerPorNombre("Cocina")!!

        val resultado = repositorio.renombrarCarpeta(cocina.id, "Cocina nueva")

        assertEquals(ResultadoCarpeta.Error(TipoErrorCarpeta.NO_ES_PROPIA), resultado)
        assertEquals("Cocina", db.categoriaDao().obtenerPorId(cocina.id)!!.nombre)
    }

    @Test
    fun `renombrar a un nombre duplicado es rechazado`() = runBlocking {
        val id = (repositorio.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
            as ResultadoCarpeta.Exito).id

        val resultado = repositorio.renombrarCarpeta(id, "Cocina")

        assertEquals(ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_DUPLICADO), resultado)
        assertEquals("Mis cosas", db.categoriaDao().obtenerPorId(id)!!.nombre)
    }

    @Test
    fun `renombrar carpeta inexistente es rechazado`() = runBlocking {
        val resultado = repositorio.renombrarCarpeta(9999, "Fantasma")

        assertEquals(ResultadoCarpeta.Error(TipoErrorCarpeta.NO_EXISTE), resultado)
    }

    @Test
    fun `borrar carpeta propia mueve sus enlaces a Otros`() = runBlocking {
        val id = (repositorio.crearCarpeta("Mis cosas", 0xFF7E57C2, "star")
            as ResultadoCarpeta.Exito).id
        val enlaceId = db.enlaceDao().insertar(
            Enlace(
                url = "https://ejemplo.com/x",
                titulo = "Enlace en carpeta",
                categoriaId = id,
            ),
        )

        assertTrue(repositorio.borrarCarpeta(id))

        assertEquals(17, db.categoriaDao().contar())
        assertNull(db.enlaceDao().obtenerPorId(enlaceId)!!.categoriaId)
        val visibles = repositorio.enlacesConCategoria.first()
        assertEquals(1, visibles.size)
        assertEquals("Otros", visibles.first().categoria.nombre)
    }

    @Test
    fun `borrar carpeta predefinida es rechazado y no la toca`() = runBlocking {
        val cocina = db.categoriaDao().obtenerPorNombre("Cocina")!!

        assertFalse(repositorio.borrarCarpeta(cocina.id))

        assertEquals(17, db.categoriaDao().contar())
        assertEquals("Cocina", db.categoriaDao().obtenerPorId(cocina.id)!!.nombre)
    }

    @Test
    fun `borrar carpeta inexistente es rechazado`() = runBlocking {
        assertFalse(repositorio.borrarCarpeta(9999))
    }
}
