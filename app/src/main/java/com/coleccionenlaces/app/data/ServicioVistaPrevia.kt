package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
import com.coleccionenlaces.app.domain.VistaPrevia
import com.coleccionenlaces.app.domain.parsearHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Servicio de vista previa (Fase 3, capa data).
 *
 * Descarga la página del enlace con OkHttp (timeout de 15 segundos,
 * User-Agent de navegador) y delega el parseo del HTML en la función
 * pura [parsearHtml], que vive en el paquete domain y se prueba sin red.
 *
 * Nunca lanza excepciones fuera: cualquier fallo se devuelve como
 * [ResultadoVistaPrevia.Error] con su tipo distinguido, y el enlace
 * puede guardarse igual (Fase 7) con la vista previa vacía.
 *
 * ## Reintentos (punto 3 de la corrección 1.0.1)
 * La descarga se reintenta hasta 2 veces (3 intentos en total) solo
 * cuando el fallo puede ser transitorio: timeout, error 5xx del
 * servidor o fallo de red distinto de "sin internet". Un 4xx, una
 * vista previa vacía o la falta de red no se reintentan: volver a
 * pedir lo mismo daría lo mismo (Facebook, por ejemplo, devuelve
 * siempre HTTP 400 con su muro de login, con y sin reintentos: ver
 * fix-1-resumen.md). Entre intentos hay una pausa corta de 500 ms.
 *
 * ## Redirects
 * OkHttp sigue los redirects por defecto: un enlace corto (p. ej.
 * `facebook.com/share/r/...`) se sigue hasta su destino real y la
 * vista previa se intenta ahí. En Facebook el destino real sigue
 * siendo el muro de login (HTTP 400, sin og tags), así que el
 * seguimiento no basta para Facebook, pero sí ayuda con otros
 * acortadores que terminan en una página normal.
 *
 * @param cliente Cliente OkHttp a usar (inyectable para las pruebas,
 *   que lo sustituyen por interceptores sin red).
 * @param parsear Función de parseo (inyectable para las pruebas).
 */
class ServicioVistaPrevia(
    private val cliente: OkHttpClient = crearCliente(),
    private val parsear: (String, String) -> VistaPrevia = ::parsearHtml,
) {

    /**
     * Descarga [url] y devuelve su vista previa.
     * Sin red, con URL inválida, con timeout o sin vista previa en el
     * sitio, devuelve [ResultadoVistaPrevia.Error] con el tipo
     * correspondiente y la vista previa vacía. Los fallos
     * transitorios (timeout, 5xx) se reintentan hasta 2 veces.
     */
    suspend fun obtenerVistaPrevia(url: String): ResultadoVistaPrevia =
        withContext(Dispatchers.IO) {
            val urlLimpia = url.trim()
            if (!esUrlHttpValida(urlLimpia)) {
                return@withContext ResultadoVistaPrevia.Error(
                    TipoErrorVistaPrevia.URL_INVALIDA,
                )
            }
            var ultimo: ResultadoVistaPrevia =
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_VISTA_PREVIA)
            repeat(INTENTOS_MAXIMOS) { intento ->
                val (resultado, reintentable) = intentarDescarga(urlLimpia)
                ultimo = resultado
                if (!reintentable || intento == INTENTOS_MAXIMOS - 1) {
                    return@withContext resultado
                }
                delay(PAUSA_ENTRE_INTENTOS_MS)
            }
            ultimo
        }

    /**
     * Un intento de descarga. Devuelve el resultado y si vale la pena
     * reintentarlo: solo los fallos transitorios (timeout, 5xx, fallo
     * de red que no sea falta de conexión) se reintentan. Una URL
     * inválida, la falta de red y un 4xx o una vista previa vacía
     * son deterministas y no se reintentan.
     */
    private fun intentarDescarga(urlLimpia: String): Pair<ResultadoVistaPrevia, Boolean> {
        return try {
            val peticion = Request.Builder()
                .url(urlLimpia)
                .header("User-Agent", USER_AGENT)
                .build()
            cliente.newCall(peticion).execute().use { respuesta ->
                if (!respuesta.isSuccessful) {
                    val reintentable = respuesta.code in 500..599
                    return ResultadoVistaPrevia.Error(
                        TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
                    ) to reintentable
                }
                val vistaPrevia = parsear(respuesta.body?.string().orEmpty(), urlLimpia)
                if (vistaPrevia.estaVacia) {
                    ResultadoVistaPrevia.Error(
                        TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
                        vistaPrevia,
                    ) to false
                } else {
                    ResultadoVistaPrevia.Exito(vistaPrevia) to false
                }
            }
        } catch (_: SocketTimeoutException) {
            ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.TIEMPO_AGOTADO) to true
        } catch (_: UnknownHostException) {
            ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_INTERNET) to false
        } catch (_: ConnectException) {
            ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_INTERNET) to false
        } catch (_: IOException) {
            ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_INTERNET) to true
        } catch (_: IllegalArgumentException) {
            // OkHttp rechaza la URL al construir la petición.
            ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.URL_INVALIDA) to false
        } catch (_: Exception) {
            ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_VISTA_PREVIA) to false
        }
    }

    /**
     * Descarga los bytes de [url] (una imagen de vista previa para
     * guardarla en local). Devuelve null si la URL no es válida, si
     * el servidor no responde 200 o si hay cualquier fallo de red.
     * Un solo intento, sin reintentos: si la miniatura no se pudo
     * descargar, la tarjeta usa la URL remota como antes.
     */
    suspend fun descargarBytes(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val urlLimpia = url.trim()
            if (!esUrlHttpValida(urlLimpia)) return@withContext null
            try {
                val peticion = Request.Builder()
                    .url(urlLimpia)
                    .header("User-Agent", USER_AGENT)
                    .build()
                cliente.newCall(peticion).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) return@withContext null
                    respuesta.body?.bytes()
                }
            } catch (_: Exception) {
                null
            }
        }

    /** Solo URLs http(s) con host: lo demás es URL inválida sin tocar la red. */
    private fun esUrlHttpValida(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = URI(url)
            val esquema = uri.scheme?.lowercase()
            (esquema == "http" || esquema == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** Timeout por intento (Fase 3: 10 s; punto 3 de la 1.0.1: ampliado a 15 s). */
        const val TIMEOUT_SEGUNDOS = 15L

        /** Intentos de descarga por vista previa: 1 intento + 2 reintentos. */
        const val INTENTOS_MAXIMOS = 3

        /** Pausa entre reintentos de descarga. */
        const val PAUSA_ENTRE_INTENTOS_MS = 500L

        /** User-Agent de navegador (sección 5): algunos sitios bloquean otros. */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

        fun crearCliente(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            .build()
    }
}
