package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.util.extraerHost
import java.net.URLEncoder

/**
 * oEmbed sin token ni cuenta (corrección 1.0.1, punto 3).
 *
 * La mayoría de enlaces que se guardan son Shorts, Reels y vídeos
 * de TikTok: contenido PÚBLICO por lo general, así que oEmbed cubre
 * la mayoría de casos. Los endpoints no exigen token, cuenta ni
 * clave (verificado con llamadas reales, ver fix-1-resumen.md).
 * El mecanismo es UNO genérico para todas las plataformas: oEmbed
 * oficial sin token -> HTML del reproductor en un WebView sin
 * autoplay -> captura del cuadro como miniatura local.
 *
 * - YouTube (`www.youtube.com/oembed`): devuelve título,
 *   `thumbnail_url` y el HTML del reproductor. La miniatura se usa
 *   directa, sin captura.
 * - Vimeo (`vimeo.com/api/oembed.json`) y Dailymotion
 *   (`www.dailymotion.com/services/oembed`): también devuelven
 *   título y `thumbnail_url` directos.
 * - TikTok (`www.tiktok.com/oembed`): devuelve SOLO el HTML del
 *   reproductor (sin título ni miniatura en las respuestas probadas:
 *   ni siquiera para un vídeo existente). Se trata como Facebook:
 *   se captura el cuadro del reproductor en un WebView.
 * - Facebook (`graph.facebook.com/v27.0/oembed_video`, con respaldo
 *   a `oembed_post`): desde el 2026-06-15 ya no exige token. Solo
 *   devuelve `html`, `width`, `provider_name`, `type` y `version`
 *   (sin título ni miniatura: los quitaron el 2025-11-03). Solo
 *   funciona con contenido PÚBLICO.
 * - Instagram (`graph.facebook.com/v27.0/instagram_oembed`): solo
 *   devuelve el HTML del incrustado (blockquote + embed.js), sin
 *   título ni miniatura. Solo contenido PÚBLICO.
 * - X (`publish.twitter.com/oembed`): devuelve el HTML del tuit
 *   (blockquote + widgets.js) y `author_name`, sin título ni
 *   miniatura. Solo contenido PÚBLICO.
 *
 * Nada de fingir ser el rastreador de Meta, nada de yt-dlp (probado:
 * timeout contra facebook.com y exigiría backend o runtime en el
 * APK, contra la regla sin servidor) y ningún servicio externo de
 * vistas previas (Microlink tampoco puede con Facebook: devuelve el
 * mismo muro de login). Si oEmbed falla, el enlace se guarda igual:
 * el título sale automáticamente del texto que acompañe al enlace
 * al compartirlo, del texto visible del reproductor o, en último
 * recurso, del dominio. Nadie escribe títulos (corrección 1.0.1).
 */

/** Proveedores con endpoint oEmbed sin token. */
enum class ProveedorOEmbed {
    YOUTUBE,
    TIKTOK,
    FACEBOOK,
    INSTAGRAM,
    X,
    VIMEO,
    DAILYMOTION,
}

/** Tipos de error que puede devolver oEmbed. */
enum class TipoErrorOEmbed {
    /** El contenido no es público, no existe o el proveedor no lo sirve. */
    NO_DISPONIBLE,

    /** Parámetro inválido (error 100 de Graph: la URL no es de una publicación o vídeo). */
    PARAMETRO_INVALIDO,

    /** Sin red, timeout u otro fallo de transporte. */
    RED,

    /** La respuesta llegó pero no se pudo interpretar (o no trae `html`). */
    RESPUESTA_INVALIDA,
}

/**
 * Resultado de [com.coleccionenlaces.app.data.ServicioOEmbed.obtenerVideo].
 *
 * [Exito] lleva el HTML del reproductor oficial y, cuando el
 * proveedor los da (YouTube, Vimeo, Dailymotion), el título y la URL
 * de la miniatura. Facebook, TikTok, Instagram y X no dan título ni
 * miniatura: el título sale automáticamente del texto que acompañe
 * al enlace al compartirlo o del texto visible del reproductor (o
 * del dominio, en último recurso) y la miniatura se captura del
 * reproductor en un WebView y se guarda en local.
 */
sealed interface ResultadoOEmbed {
    data class Exito(
        val html: String,
        val titulo: String? = null,
        val urlMiniatura: String? = null,
        val ancho: Int? = null,
    ) : ResultadoOEmbed

    data class Error(
        val tipo: TipoErrorOEmbed,
        val mensaje: String? = null,
    ) : ResultadoOEmbed
}

/**
 * ¿Qué proveedor oEmbed sirve esta URL? Null si ninguno: solo
 * YouTube, TikTok, Facebook, Instagram, X, Vimeo y Dailymotion
 * tienen endpoint oEmbed sin token. Añadir otra plataforma es una
 * entrada más en este `when` y su URL en [construirUrlOEmbed].
 */
fun proveedorOEmbedDe(url: String): ProveedorOEmbed? {
    val host = extraerHost(url) ?: return null
    return when {
        host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be") -> ProveedorOEmbed.YOUTUBE
        host == "tiktok.com" || host.endsWith(".tiktok.com") -> ProveedorOEmbed.TIKTOK
        host == "facebook.com" || host.endsWith(".facebook.com") ||
            host == "fb.com" || host.endsWith(".fb.com") ||
            host == "fb.watch" || host.endsWith(".fb.watch") -> ProveedorOEmbed.FACEBOOK
        host == "instagram.com" || host.endsWith(".instagram.com") -> ProveedorOEmbed.INSTAGRAM
        host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com") -> ProveedorOEmbed.X
        host == "vimeo.com" || host.endsWith(".vimeo.com") -> ProveedorOEmbed.VIMEO
        host == "dailymotion.com" || host.endsWith(".dailymotion.com") -> ProveedorOEmbed.DAILYMOTION
        else -> null
    }
}

/** URL del endpoint oEmbed de [proveedor] para [urlPublicacion] (sin token). */
fun construirUrlOEmbed(proveedor: ProveedorOEmbed, urlPublicacion: String): String {
    val codificada = URLEncoder.encode(urlPublicacion, "UTF-8")
    return when (proveedor) {
        ProveedorOEmbed.YOUTUBE ->
            "https://www.youtube.com/oembed?url=$codificada&format=json"
        ProveedorOEmbed.TIKTOK ->
            "https://www.tiktok.com/oembed?url=$codificada"
        ProveedorOEmbed.FACEBOOK ->
            "https://graph.facebook.com/v27.0/oembed_video?url=$codificada"
        ProveedorOEmbed.INSTAGRAM ->
            "https://graph.facebook.com/v27.0/instagram_oembed?url=$codificada"
        ProveedorOEmbed.X ->
            "https://publish.twitter.com/oembed?url=$codificada"
        ProveedorOEmbed.VIMEO ->
            "https://vimeo.com/api/oembed.json?url=$codificada"
        ProveedorOEmbed.DAILYMOTION ->
            "https://www.dailymotion.com/services/oembed?url=$codificada"
    }
}

/** URL del endpoint `oembed_post` de Facebook (respaldo cuando `oembed_video` da error 100). */
fun construirUrlOEmbedPostFacebook(urlPublicacion: String): String =
    "https://graph.facebook.com/v27.0/oembed_post?url=" +
        URLEncoder.encode(urlPublicacion, "UTF-8")

/**
 * Interpreta una respuesta oEmbed (JSON) sin ninguna librería de
 * JSON: la app no añade dependencias para esto. Extrae `html`,
 * `title`, `thumbnail_url` y `width`; si trae un objeto `error`,
 * extrae su `code` y su `message` (código 100 = parámetro inválido).
 * Un `html` ausente o vacío es [TipoErrorOEmbed.RESPUESTA_INVALIDA].
 */
fun parsearOEmbed(json: String): ResultadoOEmbed {
    val texto = json.trim()
    if (texto.isEmpty()) return ResultadoOEmbed.Error(TipoErrorOEmbed.RESPUESTA_INVALIDA)
    val error = extraerObjeto(texto, "error")
    if (error != null) {
        val codigo = extraerEntero(error, "code")
        val mensaje = extraerCadena(error, "message")
        return ResultadoOEmbed.Error(
            tipo = if (codigo == 100) {
                TipoErrorOEmbed.PARAMETRO_INVALIDO
            } else {
                TipoErrorOEmbed.NO_DISPONIBLE
            },
            mensaje = mensaje,
        )
    }
    val html = extraerCadena(texto, "html")?.takeIf { it.isNotBlank() }
        ?: return ResultadoOEmbed.Error(TipoErrorOEmbed.RESPUESTA_INVALIDA)
    return ResultadoOEmbed.Exito(
        html = html,
        titulo = extraerCadena(texto, "title")?.takeIf { it.isNotBlank() },
        urlMiniatura = extraerCadena(texto, "thumbnail_url")?.takeIf { it.isNotBlank() },
        ancho = extraerEntero(texto, "width"),
    )
}

/**
 * Quita el autoplay del reproductor de TikTok: su HTML trae
 * `autoplay=1&muted=1` en la URL del iframe y el reproductor no
 * debe reproducir solo al capturar la miniatura en el WebView.
 * Facebook no trae autoplay en su HTML, así que no se le aplica.
 */
fun quitarAutoplayTikTok(html: String): String =
    html.replace("autoplay=1", "autoplay=0")

/** Valor de la clave [clave] como cadena JSON (con escapes resueltos), o null si no está. */
private fun extraerCadena(json: String, clave: String): String? {
    val inicio = indiceValor(json, clave) ?: return null
    if (inicio >= json.length || json[inicio] != '"') return null
    val (valor, _) = leerCadenaJson(json, inicio) ?: return null
    return valor
}

/** Valor de la clave [clave] como entero (admitiendo `"width":"325"`), o null si no está. */
private fun extraerEntero(json: String, clave: String): Int? {
    val inicio = indiceValor(json, clave) ?: return null
    if (inicio >= json.length) return null
    if (json[inicio] == '"') {
        val (valor, _) = leerCadenaJson(json, inicio) ?: return null
        return valor.toIntOrNull()
    }
    val fin = json.indexOfFirstAFast(inicio) { !it.isDigit() && it != '-' }
    val texto = if (fin == -1) json.substring(inicio) else json.substring(inicio, fin)
    return texto.toIntOrNull()
}

/** Objeto JSON de la clave [clave] (con sus llaves), o null si no está o no es un objeto. */
private fun extraerObjeto(json: String, clave: String): String? {
    val inicio = indiceValor(json, clave) ?: return null
    if (inicio >= json.length || json[inicio] != '{') return null
    var profundidad = 0
    var i = inicio
    var entreComillas = false
    var escape = false
    while (i < json.length) {
        val c = json[i]
        if (entreComillas) {
            if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') entreComillas = false
        } else {
            when (c) {
                '"' -> entreComillas = true
                '{' -> profundidad++
                '}' -> {
                    profundidad--
                    if (profundidad == 0) return json.substring(inicio, i + 1)
                }
            }
        }
        i++
    }
    return null
}

/**
 * Índice donde empieza el valor de `"clave":` en [json] (después de
 * los dos puntos y los espacios), o null si la clave no aparece
 * como `"clave"` seguida de `:`.
 */
private fun indiceValor(json: String, clave: String): Int? {
    var desde = 0
    while (true) {
        val claveIndice = json.indexOf("\"$clave\"", desde)
        if (claveIndice == -1) return null
        var i = claveIndice + clave.length + 2
        while (i < json.length && json[i].isWhitespace()) i++
        if (i < json.length && json[i] == ':') {
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            return i
        }
        desde = claveIndice + 1
    }
}

/** Lee una cadena JSON desde la comilla de apertura en [inicio]: valor sin escapes y fin. */
private fun leerCadenaJson(json: String, inicio: Int): Pair<String, Int>? {
    if (inicio >= json.length || json[inicio] != '"') return null
    val salida = StringBuilder()
    var i = inicio + 1
    while (i < json.length) {
        val c = json[i]
        when {
            c == '\\' && i + 1 < json.length -> {
                when (val siguiente = json[i + 1]) {
                    '"' -> salida.append('"')
                    '\\' -> salida.append('\\')
                    '/' -> salida.append('/')
                    'b' -> salida.append('\b')
                    'f' -> salida.append('\u000C')
                    'n' -> salida.append('\n')
                    'r' -> salida.append('\r')
                    't' -> salida.append('\t')
                    'u' -> {
                        if (i + 5 < json.length) {
                            val codigo = json.substring(i + 2, i + 6).toIntOrNull(16)
                            if (codigo != null) salida.append(codigo.toChar())
                            i += 4
                        }
                    }
                    else -> salida.append(siguiente)
                }
                i += 2
            }
            c == '"' -> return salida.toString() to i + 1
            else -> {
                salida.append(c)
                i++
            }
        }
    }
    return null
}

/** Como `indexOfFirst` pero desde [desde] (para no crear subcadenas). */
private inline fun String.indexOfFirstAFast(desde: Int, predicado: (Char) -> Boolean): Int {
    for (i in desde until length) {
        if (predicado(this[i])) return i
    }
    return -1
}
