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
 * Texto que acompaña a una URL en un texto compartido (corrección
 * 1.0.1: título automático, sin edición manual). Al compartir desde
 * la app de Facebook (o al pegar un texto con URL), el texto suele
 * traer el texto de la publicación además de la URL; ese texto se
 * usa automáticamente como título/descripción del enlace, sin que
 * nadie lo escriba.
 *
 * Quita la URL del texto (la extraída con [extraerUrl] o la [url]
 * dada), limpia las líneas vacías y las que son solo puntuación, y
 * devuelve el resto unido por saltos de línea, o null si no queda
 * nada. Un texto que es solo la URL devuelve null.
 */
fun extraerTextoAcompanante(texto: String?, url: String?): String? {
    if (texto.isNullOrBlank()) return null
    val urlExtraida = url ?: extraerUrl(texto)
    var resto = texto
    if (urlExtraida != null) {
        resto = resto.replace(urlExtraida, " ")
    }
    val limpio = resto.lines()
        .map { linea ->
            // Se colapsan los espacios y se quitan los tokens que son
            // solo puntuación (el "." que deja la URL al final de una
            // frase, por ejemplo): el título no debe llevarlos.
            linea.trim().replace(Regex("\\s+"), " ")
                .split(" ")
                .filter { token -> token.any { it.isLetterOrDigit() } }
                .joinToString(" ")
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    return limpio.ifBlank { null }
}

/**
 * Primera línea no vacía de [texto], recortada a 200 caracteres
 * (para usarla como título automático). Null si no hay ninguna.
 */
fun primeraLinea(texto: String?): String? {
    if (texto.isNullOrBlank()) return null
    val linea = texto.lines().firstOrNull { it.isNotBlank() }?.trim()
        ?: return null
    return if (linea.length > 200) linea.take(200).trimEnd() else linea
}

/**
 * El resto de [texto] tras su primera línea no vacía (las líneas
 * siguientes, sin vacías, unidas por saltos de línea). Se usa como
 * descripción automática cuando la primera línea se usó como
 * título. Null si no queda nada.
 */
fun restoTrasPrimeraLinea(texto: String?): String? {
    if (texto.isNullOrBlank()) return null
    val lineas = texto.lines()
    val primera = lineas.indexOfFirst { it.isNotBlank() }
    if (primera == -1) return null
    val resto = lineas.drop(primera + 1)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    return resto.ifBlank { null }
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

/**
 * ¿Lleva [url] una firma temporal en su consulta? Las imágenes de
 * Facebook (`fbcdn.net`) llevan `oe=` (y `oh=`) y se rompen a los
 * días; lo mismo pasa con otras CDN que firman sus URLs (`sig=`,
 * `signature=`, `expires=`...). Esas imágenes se descargan a local
 * al guardar el enlace, en vez de referenciar la URL (corrección
 * 1.0.1, punto 3). La coincidencia es por parámetro de consulta:
 * un `oe=` dentro de la ruta no cuenta.
 */
fun esUrlImagenFirmada(url: String): Boolean {
    val consulta = try {
        URI(url.trim()).query
    } catch (_: Exception) {
        null
    } ?: return false
    val parametros = consulta.split("&")
        .map { it.substringBefore("=").lowercase() }.toSet()
    return "oe" in parametros || "oh" in parametros || "sig" in parametros ||
        "signature" in parametros || "x-amz-signature" in parametros ||
        "expires" in parametros || "exp" in parametros || "token" in parametros ||
        "st" in parametros
}
