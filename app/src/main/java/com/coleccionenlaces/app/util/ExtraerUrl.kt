package com.coleccionenlaces.app.util

import java.net.URI

/**
 * Extracción y validación de URLs (Fase 7). Funciones puras, sin
 * Android ni red: se prueban con JUnit simple.
 *
 * La validación ([esUrlValida]) refleja la regla del servicio de
 * vista previa ([com.coleccionenlaces.app.data.ServicioVistaPrevia]:
 * solo http(s) con host); el servicio sigue siendo la autoridad al
 * guardar: si la URL no pasa allí, el enlace no se guarda.
 */

/** Primera URL http(s) que aparece en [texto], o null si no hay ninguna. */
private val URL_EN_TEXTO = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

/**
 * Extrae la primera URL http(s) de un texto compartido (p. ej. el
 * texto que comparte YouTube: título + URL + comentario).
 *
 * - Si hay varias URLs, toma la primera.
 * - Si no hay URL, devuelve null: quien llama no hace nada.
 * - La puntuación final que suele pertenecer al texto (punto, coma,
 *   cierre de paréntesis sin abrir) se quita; la consulta (`?x=1`)
 *   y la ruta se conservan.
 * - La extracción es deliberadamente permisiva: una URL con host
 *   inválido se extrae igual y la rechaza después [esUrlValida] (y
 *   el servicio de vista previa al guardar).
 */
fun extraerUrl(texto: String?): String? {
    if (texto.isNullOrBlank()) return null
    val coincidencia = URL_EN_TEXTO.find(texto) ?: return null
    var url = coincidencia.value.trimEnd('.', ',', ';', ':', '!', '?')
    // Cierres sin su apertura correspondiente: son del texto, no de la URL.
    while (url.endsWith(")") && url.count { it == '(' } < url.count { it == ')' }) {
        url = url.dropLast(1)
    }
    while (url.endsWith("]") && url.count { it == '[' } < url.count { it == ']' }) {
        url = url.dropLast(1)
    }
    return url.ifBlank { null }
}

/**
 * ¿Es [url] una URL http(s) con host? Misma regla que el servicio
 * de vista previa: esquema http o https (sin distinguir mayúsculas)
 * y host no vacío. Los espacios alrededor se ignoran.
 */
fun esUrlValida(url: String): Boolean {
    val limpia = url.trim()
    if (limpia.isEmpty()) return false
    return try {
        val uri = URI(limpia)
        val esquema = uri.scheme?.lowercase()
        (esquema == "http" || esquema == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

/** Host en minúsculas de una URL, sin el `www.` inicial, o null si no lo tiene. */
fun extraerHost(url: String): String? {
    val texto = url.trim().lowercase()
    if (texto.isEmpty()) return null
    return try {
        URI(texto).host?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }?.takeIf { it.isNotBlank() }
}

/**
 * ¿Es [url] un enlace de Facebook (facebook.com, m.facebook.com,
 * fb.com, fb.watch)? Se usa en el diálogo de agregar (Fix 1,
 * punto 3): Facebook no deja leer la vista previa de sus enlaces a
 * nadie que no sea su propio rastreador, así que el aviso para esos
 * enlaces lo dice claro. La coincidencia es por sufijo de host:
 * `no-facebook.com` no cuenta.
 */
fun esEnlaceFacebook(url: String): Boolean {
    val host = extraerHost(url) ?: return false
    return host == "facebook.com" || host.endsWith(".facebook.com") ||
        host == "fb.com" || host.endsWith(".fb.com") ||
        host == "fb.watch" || host.endsWith(".fb.watch")
}
