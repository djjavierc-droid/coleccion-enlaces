package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
import com.coleccionenlaces.app.domain.VistaPrevia
import com.coleccionenlaces.app.domain.parsearHtml
import kotlinx.coroutines.Dispatchers
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
 * Descarga la página del enlace con OkHttp (timeout de 10 segundos,
 * User-Agent de navegador) y delega el parseo del HTML en la función
 * pura [parsearHtml], que vive en el paquete domain y se prueba sin red.
 *
 * Nunca lanza excepciones fuera: cualquier fallo se devuelve como
 * [ResultadoVistaPrevia.Error] con su tipo distinguido, y el enlace
 * puede guardarse igual (Fase 7) con la vista previa vacía.
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
     * correspondiente y la vista previa vacía.
     */
    suspend fun obtenerVistaPrevia(url: String): ResultadoVistaPrevia =
        withContext(Dispatchers.IO) {
            val urlLimpia = url.trim()
            if (!esUrlHttpValida(urlLimpia)) {
                return@withContext ResultadoVistaPrevia.Error(
                    TipoErrorVistaPrevia.URL_INVALIDA,
                )
            }
            try {
                val peticion = Request.Builder()
                    .url(urlLimpia)
                    .header("User-Agent", USER_AGENT)
                    .build()
                cliente.newCall(peticion).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) {
                        return@withContext ResultadoVistaPrevia.Error(
                            TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
                        )
                    }
                    val vistaPrevia = parsear(respuesta.body?.string().orEmpty(), urlLimpia)
                    if (vistaPrevia.estaVacia) {
                        ResultadoVistaPrevia.Error(
                            TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
                            vistaPrevia,
                        )
                    } else {
                        ResultadoVistaPrevia.Exito(vistaPrevia)
                    }
                }
            } catch (_: SocketTimeoutException) {
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.TIEMPO_AGOTADO)
            } catch (_: UnknownHostException) {
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_INTERNET)
            } catch (_: ConnectException) {
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_INTERNET)
            } catch (_: IOException) {
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_INTERNET)
            } catch (_: IllegalArgumentException) {
                // OkHttp rechaza la URL al construir la petición.
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.URL_INVALIDA)
            } catch (_: Exception) {
                ResultadoVistaPrevia.Error(TipoErrorVistaPrevia.SIN_VISTA_PREVIA)
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
        /** Timeout del plan (sección 5): 10 segundos en las tres fases. */
        const val TIMEOUT_SEGUNDOS = 10L

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
