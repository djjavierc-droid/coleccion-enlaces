package com.coleccionenlaces.app.domain

import org.jsoup.Jsoup

/**
 * Parseo puro de la vista previa de una página (Fase 3).
 *
 * Función pura y testeable sin red: recibe el HTML ya descargado y la
 * URL base, y devuelve la [VistaPrevia]. El servicio de red
 * ([com.coleccionenlaces.app.data.ServicioVistaPrevia]) solo descarga
 * y delega aquí.
 *
 * Reglas de extracción, en orden de prioridad:
 * - Título: `og:title`; respaldo: el `<title>` de la página.
 * - Descripción: `og:description`; respaldo: `meta name=description`.
 * - Imagen: `og:image`; respaldo: la primera `<img>` con `src` no
 *   vacío. Las URLs relativas se resuelven a absolutas contra [urlBase].
 *
 * Los valores en blanco se tratan como ausentes (null). Jsoup tolera
 * HTML vacío o malformado: en ese caso devuelve lo que pueda recuperar
 * y el resto queda en null, sin lanzar excepciones.
 */
fun parsearHtml(html: String, urlBase: String): VistaPrevia {
    val documento = Jsoup.parse(html, urlBase)

    val titulo = documento
        .selectFirst("meta[property=og:title]")
        ?.attr("content").sinBlancos()
        ?: documento.title().sinBlancos()

    val descripcion = documento
        .selectFirst("meta[property=og:description]")
        ?.attr("content").sinBlancos()
        ?: documento
            .selectFirst("meta[name=description]")
            ?.attr("content").sinBlancos()

    val urlImagen = documento
        .selectFirst("meta[property=og:image]")
        ?.attr("content")?.sinBlancos()?.let { resolverUrl(it, urlBase) }
        ?: documento.select("img[src]")
            // El src crudo debe ser no vacío: absUrl("") resuelve a la
            // propia URL base, así que una img con src vacío no se
            // puede descartar solo con absUrl.
            .firstOrNull { it.attr("src").isNotBlank() }
            ?.absUrl("src")?.takeIf { it.isNotBlank() }

    return VistaPrevia(
        titulo = titulo,
        descripcion = descripcion,
        urlImagen = urlImagen,
    )
}

/**
 * Resuelve [url] contra [urlBase] cuando es relativa; si ya es
 * absoluta la devuelve tal cual. Devuelve null si no se puede
 * resolver a una URL no vacía.
 */
private fun resolverUrl(url: String, urlBase: String): String? {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    return try {
        java.net.URI(urlBase).resolve(url).toString().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/** El texto sin espacios en los extremos, o null si queda vacío. */
private fun String?.sinBlancos(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
