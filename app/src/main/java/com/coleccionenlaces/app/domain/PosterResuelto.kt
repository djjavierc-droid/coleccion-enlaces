package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.util.extraerHost
import org.jsoup.Jsoup

/**
 * Póster resuelto de un vídeo (Fix 5, fuente de cuadros resuelta).
 *
 * Es lo que devuelve la ruta del rastreador (ver
 * `miniatura-real-curl.md`): pedir la página canónica del vídeo con
 * User-Agent de rastreador entrega `og:title`, `og:image` (el póster
 * real) y, con Googlebot, las URLs `.mp4` directas del vídeo. La
 * [vistaPrevia] trae el título, la descripción y la `og:image`; los
 * [urlsVideo] son los `.mp4` directos (máximo 3), para sacar un
 * cuadro del vídeo cuando no hay `og:image`.
 *
 * Solo existe cuando hay una fuente real de cuadro (`og:image` o
 * `.mp4`): el muro de login de Facebook no trae ninguno de los dos,
 * así que nunca se confunde con un póster.
 */
data class PosterResuelto(
    val vistaPrevia: VistaPrevia,
    val urlsVideo: List<String> = emptyList(),
)

/**
 * True si [url] ya es la URL canónica de un vídeo de Facebook, es
 * decir, una de las formas que la ruta del rastreador sí puede leer
 * (medido en `miniatura-real-curl.md` §8):
 *
 * - `facebook.com/reel/<id>/`
 * - `facebook.com/watch/?v=<id>`
 * - `facebook.com/<página>/videos/<id>/` (también `/videos/<id>/`)
 * - `facebook.com/video.php?v=<id>` (forma antigua)
 *
 * Un enlace `/share/r/...` o `fb.watch/...` NO es canónico: es
 * opaco por diseño y nunca revela la URL del vídeo (ver
 * [resolverUrlCanonicaFacebook]).
 */
fun esUrlVideoFacebookCanonica(url: String): Boolean {
    val urlLimpia = url.trim()
    val host = extraerHost(urlLimpia) ?: return false
    if (host != "facebook.com" && !host.endsWith(".facebook.com")) return false
    val uri = try {
        java.net.URI(urlLimpia)
    } catch (_: Exception) {
        return false
    }
    val ruta = uri.path ?: ""
    if (ruta.startsWith("/reel/")) return true
    if (ruta == "/watch/" || ruta == "/watch") {
        return consulta(uri.query)["v"]?.isNotBlank() == true
    }
    if (ruta == "/video.php") {
        return consulta(uri.query)["v"]?.isNotBlank() == true
    }
    // /<página>/videos/<id>/ o /videos/<id>/
    if (ruta.contains("/videos/")) return true
    return false
}

/**
 * URL canónica del vídeo de Facebook de [url], o null si no se
 * conoce.
 *
 * Hoy solo devuelve la propia URL cuando ya es canónica (ver
 * [esUrlVideoFacebookCanonica]). Un enlace `/share/r/...` devuelve
 * null: su redirección 302 no revela la URL canónica y la página que
 * ve un rastreador es el muro de login (medido en
 * `miniatura-real-curl.md` §2). LA RESOLUCIÓN `/share/r/` ->
 * canónica es el hueco que está atacando la investigación de una
 * hora: cuando sus resultados lleguen, se implementan AQUÍ (esta
 * función es el único punto de entrada) y el resto del flujo —póster,
 * cuadro del `.mp4`, visión/OCR— ya funciona sin tocar nada más.
 */
fun resolverUrlCanonicaFacebook(url: String): String? {
    val urlLimpia = url.trim()
    return if (esUrlVideoFacebookCanonica(urlLimpia)) urlLimpia else null
}

/**
 * La `og:image` de [html] (solo el meta, sin respaldo): la URL del
 * póster real del vídeo, o null si la página no la trae. El respaldo
 * de [parsearHtml] (primera `<img>`) NO vale aquí: el muro de login
 * trae un icono de aviso y un píxel de seguimiento que no son el
 * póster del vídeo.
 */
fun extraerOgImage(html: String, urlBase: String): String? {
    val contenido = try {
        Jsoup.parse(html, urlBase)
            .selectFirst("meta[property=og:image]")
            ?.attr("content")?.trim().takeUnless { it.isNullOrBlank() }
    } catch (_: Exception) {
        null
    } ?: return null
    if (contenido.startsWith("http://") || contenido.startsWith("https://")) {
        return contenido
    }
    return try {
        java.net.URI(urlBase).resolve(contenido).toString().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/**
 * URLs `.mp4` directas dentro de [html] (las que trae la página
 * canónica del vídeo en sus datos incrustados), sin duplicados y
 * como mucho 3. El HTML de Facebook escapa las barras (`\/`) y el
 * `&` (`\u0026`) dentro del JSON: se desescapan antes de buscar.
 * Devuelve lista vacía si no hay ninguna (muro de login, página de
 * error, HTML vacío).
 */
fun extraerUrlsVideoMp4(html: String): List<String> {
    val limpio = html.replace("\\/", "/").replace("\\u0026", "&")
    return URL_MP4.findAll(limpio)
        .map { it.value.replace("&amp;", "&").trimEnd('.', ',', ';') }
        .filter { it.isNotBlank() }
        .distinct()
        .take(3)
        .toList()
}

private val URL_MP4 =
    Regex("""https://[^\s"'<>\\]+?\.mp4(?:\?[^\s"'<>\\]*)?""")

/** Parámetros de consulta de [consulta] (`a=1&b=2`) como mapa. */
private fun consulta(consulta: String?): Map<String, String> {
    if (consulta.isNullOrBlank()) return emptyMap()
    val resultado = mutableMapOf<String, String>()
    for (par in consulta.split("&")) {
        val partes = par.split("=", limit = 2)
        if (partes.size == 2) resultado[partes[0]] = partes[1]
    }
    return resultado
}
