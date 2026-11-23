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

    // ---- Punto 3 de la corrección 1.0.1: reintentos ----

    private fun clienteQueCuenta(responder: (Int) -> Response): Pair<OkHttpClient, java.util.concurrent.atomic.AtomicInteger> {
        val llamadas = java.util.concurrent.atomic.AtomicInteger(0)
        val cliente = OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val numero = llamadas.incrementAndGet()
                responder(numero)
            }
            .build()
        return cliente to llamadas
    }

    private fun respuesta(codigo: Int, cuerpo: String, url: String): Response =
        Response.Builder()
            .request(okhttp3.Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message("OK")
            .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()

    @Test
    fun `error 500 dos veces y luego 200 devuelve Exito tras reintentar`() = runBlocking {
        val html = """
            <html><head>
            <meta property="og:title" content="Receta de paella valenciana">
            </head><body></body></html>
        """.trimIndent()
        val (cliente, llamadas) = clienteQueCuenta { numero ->
            if (numero <= 2) {
                respuesta(500, "Error del servidor", "https://ejemplo.com/receta")
            } else {
                respuesta(200, html, "https://ejemplo.com/receta")
            }
        }
        val servicio = ServicioVistaPrevia(cliente = cliente)

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/receta")

        assertTrue(resultado is ResultadoVistaPrevia.Exito)
        assertEquals(
            "Receta de paella valenciana",
            (resultado as ResultadoVistaPrevia.Exito).vistaPrevia.titulo,
        )
        assertEquals(3, llamadas.get())
    }

    @Test
    fun `error 400 como el de Facebook no se reintenta`() = runBlocking {
        val (cliente, llamadas) = clienteQueCuenta {
            respuesta(400, "<title>Error</title>", "https://www.facebook.com/share/r/abc")
        }
        val servicio = ServicioVistaPrevia(cliente = cliente)

        val resultado = servicio.obtenerVistaPrevia("https://www.facebook.com/share/r/abc")

        assertEquals(
            TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertEquals(1, llamadas.get())
    }

    @Test
    fun `timeout dos veces y luego 200 devuelve Exito tras reintentar`() = runBlocking {
        val html = """
            <html><head>
            <meta property="og:title" content="Receta de paella valenciana">
            </head><body></body></html>
        """.trimIndent()
        val (cliente, llamadas) = clienteQueCuenta { numero ->
            if (numero <= 2) {
                throw SocketTimeoutException("read timed out")
            } else {
                respuesta(200, html, "https://ejemplo.com/receta")
            }
        }
        val servicio = ServicioVistaPrevia(cliente = cliente)

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/receta")

        assertTrue(resultado is ResultadoVistaPrevia.Exito)
        assertEquals(3, llamadas.get())
    }

    @Test
    fun `timeout siempre devuelve TIEMPO_AGOTADO tras 3 intentos`() = runBlocking {
        val (cliente, llamadas) = clienteQueCuenta {
            throw SocketTimeoutException("read timed out")
        }
        val servicio = ServicioVistaPrevia(cliente = cliente)

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/lento")

        assertEquals(
            TipoErrorVistaPrevia.TIEMPO_AGOTADO,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertEquals(3, llamadas.get())
    }

    @Test
    fun `sin internet no se reintenta`() = runBlocking {
        val (cliente, llamadas) = clienteQueCuenta {
            throw UnknownHostException("ejemplo.com")
        }
        val servicio = ServicioVistaPrevia(cliente = cliente)

        val resultado = servicio.obtenerVistaPrevia("https://ejemplo.com/")

        assertEquals(
            TipoErrorVistaPrevia.SIN_INTERNET,
            (resultado as ResultadoVistaPrevia.Error).tipo,
        )
        assertEquals(1, llamadas.get())
    }

    // ---- Fix 4, bug 3: resolución de la URL canónica ----

    /**
     * Un enlace corto de Facebook redirige (302) a su destino: la
     * canónica es la URL final, no la corta. La resolución ES
     * `respuesta.request.url`: OkHttp sigue los redirects por
     * defecto y la petición que responde es la final.
     *
     * Por qué el test NO simula un 302 literal: no se puede con
     * interceptores. Un interceptor de aplicación devuelve su
     * respuesta ANTES de que `RetryAndFollowUpInterceptor` la vea
     * (OkHttp no sigue ese 302) y uno de red DEBE llamar a
     * `proceed()` exactamente una vez (no puede sintetizar
     * respuestas: lanza `IllegalStateException`). Así que el test
     * simula la respuesta DESPUÉS de que OkHttp siga el redirect
     * (una 200 cuya petición es la URL final) y comprueba que el
     * resolvedor devuelve esa URL final, no la pedida. Que OkHttp
     * siga el 302 real está medido con curl contra Facebook
     * (302 a `m.facebook.com`, ver fix-4-resumen.md): es
     * comportamiento del framework, no de este código.
     */
    @Test
    fun `resolverUrlCanonica devuelve la URL final tras la redireccion`() = runBlocking {
        val cliente = OkHttpClient.Builder()
            .addInterceptor { cadena ->
                // Respuesta como la que deja OkHttp tras seguir el
                // 302: 200 y petición = URL final (m.facebook.com).
                Response.Builder()
                    .request(
                        cadena.request().newBuilder()
                            .url("https://m.facebook.com/share/r/1JqVPz7de")
                            .build(),
                    )
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
        val servicio = ServicioVistaPrevia(cliente = cliente)

        val canonica = servicio.resolverUrlCanonica(
            "https://www.facebook.com/share/r/1JqVPz7de",
        )

        assertEquals("https://m.facebook.com/share/r/1JqVPz7de", canonica)
    }

    @Test
    fun `resolverUrlCanonica sin redireccion devuelve la misma URL`() = runBlocking {
        val servicio = ServicioVistaPrevia(cliente = clienteQueResponde(200, "<html></html>"))

        assertEquals(
            "https://ejemplo.com/pagina",
            servicio.resolverUrlCanonica("https://ejemplo.com/pagina"),
        )
    }

    @Test
    fun `resolverUrlCanonica con fallo de red devuelve la URL tal cual`() = runBlocking {
        val servicio = ServicioVistaPrevia(cliente = clienteQueFalla(UnknownHostException("x")))

        assertEquals(
            "https://www.facebook.com/share/r/1JqVPz7de",
            servicio.resolverUrlCanonica("https://www.facebook.com/share/r/1JqVPz7de"),
        )
    }

    @Test
    fun `resolverUrlCanonica con URL invalida la devuelve sin tocar la red`() = runBlocking {
        val servicio = ServicioVistaPrevia(cliente = clienteQueFalla(UnknownHostException("x")))

        assertEquals("no-es-una-url", servicio.resolverUrlCanonica("no-es-una-url"))
    }
}
