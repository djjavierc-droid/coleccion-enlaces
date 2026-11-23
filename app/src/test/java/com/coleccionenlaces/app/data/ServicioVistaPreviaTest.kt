package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pruebas del servicio de vista previa (Fase 3).
 *
 * Ninguna prueba toca internet: las URLs inválidas se rechazan antes
 * de cualquier llamada de red, y el resto de casos usa un OkHttpClient
 * con interceptores que simulan la respuesta (o el fallo) del servidor.
 */
class ServicioVistaPreviaTest {

    private fun clienteQueResponde(codigo: Int, cuerpo: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(codigo)
                    .message("OK")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()

    private fun clienteQueFalla(error: IOException): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { throw error }
            .build()

    @Test
    fun `URL que no es URL devuelve URL_INVALIDA sin tocar la red`() = runBlocking {
        val servicio = ServicioVistaPrevia()

        val resultado = servicio.obtenerVistaPrevia("no-es-una-url")

        assertTrue(resultado is ResultadoVistaPrevia.Error)
        assertEquals(
            TipoErrorVistaPrevia.URL_INVALIDA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertTrue(resultado.vistaPrevia.estaVacia)
    }

    @Test
    fun `URL vacia devuelve URL_INVALIDA`() = runBlocking {
        val servicio = ServicioVistaPrevia()

        val resultado = servicio.obtenerVistaPrevia("   ")

        assertEquals(
            TipoErrorVistaPrevia.URL_INVALIDA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
    }

    @Test
    fun `esquema distinto de http devuelve URL_INVALIDA`() = runBlocking {
        val servicio = ServicioVistaPrevia()

        val resultado = servicio.obtenerVistaPrevia("ftp://ejemplo.com/archivo")

        assertEquals(
            TipoErrorVistaPrevia.URL_INVALIDA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
    }

    @Test
    fun `URL sin host devuelve URL_INVALIDA`() = runBlocking {
        val servicio = ServicioVistaPrevia()

        val resultado = servicio.obtenerVistaPrevia("https://")

        assertEquals(
            TipoErrorVistaPrevia.URL_INVALIDA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
    }

    @Test
    fun `timeout de lectura devuelve TIEMPO_AGOTADO`() = runBlocking {
        val servicio = ServicioVistaPrevia(
            cliente = clienteQueFalla(SocketTimeoutException("read timed out")),
        )

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/lento")

        assertEquals(
            TipoErrorVistaPrevia.TIEMPO_AGOTADO,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertTrue(resultado.vistaPrevia.estaVacia)
    }

    @Test
    fun `host desconocido devuelve SIN_INTERNET`() = runBlocking {
        val servicio = ServicioVistaPrevia(
            cliente = clienteQueFalla(UnknownHostException("ejemplo.com")),
        )

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/")

        assertEquals(
            TipoErrorVistaPrevia.SIN_INTERNET,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
    }

    @Test
    fun `respuesta 404 devuelve SIN_VISTA_PREVIA con vista vacia`() = runBlocking {
        val servicio = ServicioVistaPrevia(cliente = clienteQueResponde(404, "No encontrado"))

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/no-existe")

        assertTrue(resultado is ResultadoVistaPrevia.Error)
        assertEquals(
            TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertTrue(resultado.vistaPrevia.estaVacia)
    }

    @Test
    fun `respuesta 200 con OG completa devuelve Exito`() = runBlocking {
        val html = """
            <html><head>
            <meta property="og:title" content="Receta de paella valenciana">
            <meta property="og:description" content="La receta tradicional paso a paso.">
            <meta property="og:image" content="https://ejemplo.com/img/paella.jpg">
            </head><body></body></html>
        """.trimIndent()
        val servicio = ServicioVistaPrevia(cliente = clienteQueResponde(200, html))

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/receta")

        assertTrue(resultado is ResultadoVistaPrevia.Exito)
        val vista = (resultado as ResultadoVistaPrevia.Exito).vistaPrevia
        assertEquals("Receta de paella valenciana", vista.titulo)
        assertEquals("La receta tradicional paso a paso.", vista.descripcion)
        assertEquals("https://ejemplo.com/img/paella.jpg", vista.urlImagen)
    }

    @Test
    fun `respuesta 200 con HTML vacio devuelve SIN_VISTA_PREVIA`() = runBlocking {
        val servicio = ServicioVistaPrevia(cliente = clienteQueResponde(200, ""))

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/vacia")

        assertTrue(resultado is ResultadoVistaPrevia.Error)
        assertEquals(
            TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertTrue(resultado.vistaPrevia.estaVacia)
    }

    @Test
    fun `respuesta 200 sin OG usa los respaldos del parseo`() = runBlocking {
        val html = """
            <html><head>
            <title>Cómo arreglar una bicicleta</title>
            <meta name="description" content="Guía para ajustar los frenos.">
            </head><body><img src="/img/bici.jpg"></body></html>
        """.trimIndent()
        val servicio = ServicioVistaPrevia(cliente = clienteQueResponde(200, html))

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/guias/bici")

        assertTrue(resultado is ResultadoVistaPrevia.Exito)
        val vista = (resultado as ResultadoVistaPrevia.Exito).vistaPrevia
        assertEquals("Cómo arreglar una bicicleta", vista.titulo)
        assertEquals("Guía para ajustar los frenos.", vista.descripcion)
        assertEquals("https://ejemplo.com/img/bici.jpg", vista.urlImagen)
    }
}
