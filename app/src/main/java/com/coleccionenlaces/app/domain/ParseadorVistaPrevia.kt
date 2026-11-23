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
 *   Un título genérico (p. ej. "Error", "Error Facebook" o
 *   "Facebook": lo que devuelve el muro de login de Facebook)
 *   NO sirve como título del enlace y se descarta (null), para que
 *   el enlace se guarde con el dominio o con el título que escriba
 *   el usuario (punto 3 de la corrección 1.0.1).
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
    // Un título genérico no describe el enlace: se descarta para que
    // el enlace se guarde con el dominio o con el título que escriba
    // el usuario (ver TITULOS_GENERICOS).
    val tituloUtil = titulo?.takeUnless { esTituloGenerico(it) }

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
        titulo = tituloUtil,
        descripcion = descripcion,
        urlImagen = urlImagen,
    )
}

/**
 * Títulos que no describen el enlace y nunca se usan como título
 * (punto 3 de la corrección 1.0.1). Son los que devuelve un muro de
 * login o de error en vez de la página real: Facebook responde
 * `<title>Error</title>` (escritorio) o `<title>Error Facebook</title>`
 * (móvil) con HTTP 400 y cero og tags (ver fix-1-resumen.md), y un
 * `og:title` con solo el nombre del sitio ("Facebook") tampoco dice
 * nada del contenido. Comparación en minúsculas y sin espacios.
 */
private val TITULOS_GENERICOS = setOf(
    "error",
    "error facebook",
    "facebook",
    "log into facebook",
    "log in to facebook",
    "facebook - log in or sign up",
    "facebook – log in or sign up",
    "inicia sesion en facebook",
    "iniciar sesion en facebook",
    "accede a facebook",
    "entrar a facebook",
    "pagina no encontrada",
    "page not found",
    "404 not found",
)

/** True si el título es uno de los genéricos de [TITULOS_GENERICOS]. */
private fun esTituloGenerico(titulo: String): Boolean =
    titulo.trim().lowercase() in TITULOS_GENERICOS

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
