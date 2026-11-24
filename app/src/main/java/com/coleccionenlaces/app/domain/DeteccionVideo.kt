package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.util.extraerHost

/**
 * Detección de la página de error de vídeo de Facebook (Fix 4, bug 3).
 *
 * Cuando el reproductor oEmbed de Facebook no puede mostrar el vídeo
 * (no existe, es privado, o el visor no tiene permiso), pinta en vez
 * del vídeo la pantalla "Video no disponible — Es posible que este
 * video ya no exista o que no tengas permiso para verlo". Esa pantalla
 * NO es una miniatura: [MiniaturaWebView] la detecta por su texto
 * visible ANTES de capturar el cuadro y no la guarda; la tarjeta
 * muestra entonces su marcador propio ("Video no disponible").
 *
 * El texto se normaliza como en [Clasificador] (minúsculas, sin
 * acentos, puntuación a espacios), así que las frases se buscan sin
 * acentos y sin importar mayúsculas. La lista cubre el español y el
 * inglés de Facebook; una frase parcial ("video no disponible") basta
 * porque la página de error siempre la trae.
 *
 * Error nuevo de copyright (investigación de 1 hora, hilo C, §5):
 * "Facebook Unavailable — This video can't be embedded because it
 * may contain content owned by someone else". No contiene "Video
 * Unavailable", así que tiene sus propias frases. Un vídeo real
 * nunca muestra este texto: solo lo muestra la pantalla de error.
 */
fun detectarVideoNoDisponible(textoVisible: String?): Boolean {
    if (textoVisible.isNullOrBlank()) return false
    val normalizado = " ${Clasificador.normalizar(textoVisible)} "
    return FRASES_VIDEO_NO_DISPONIBLE.any { frase -> normalizado.contains(frase) }
}

/** Frases (ya normalizadas) de la página de error de vídeo. */
private val FRASES_VIDEO_NO_DISPONIBLE = listOf(
    "video no disponible",
    "video unavailable",
    "este video ya no existe",
    "this video may no longer exist",
    "no tienes permiso para verlo",
    "you may not have permission",
    "contenido no disponible",
    "content isn t available",
    "this video is unavailable",
    "el video no esta disponible",
    // Error nuevo de copyright (investigación de 1 hora, hilo C).
    "facebook unavailable",
    "can t be embedded",
    "content owned by someone else",
)

/**
 * True si [url] es un enlace corto de compartir de Facebook
 * (`facebook.com/share/r/...`, `/share/v/...`) o de `fb.watch`.
 * Son URLs de redirección: el destino real no es la URL que se
 * compartió. El repositorio las resuelve a su URL final antes de
 * pedir el oEmbed (ver
 * [com.coleccionenlaces.app.data.ServicioVistaPrevia.resolverUrlCanonica]).
 */
fun esUrlCortaFacebookCompartida(url: String): Boolean {
    val host = extraerHost(url.trim()) ?: return false
    if (host != "facebook.com" && !host.endsWith(".facebook.com") &&
        host != "fb.watch" && !host.endsWith(".fb.watch")
    ) {
        return false
    }
    if (host == "fb.watch" || host.endsWith(".fb.watch")) return true
    val ruta = try {
        java.net.URI(url.trim()).path ?: ""
    } catch (_: Exception) {
        ""
    }
    return ruta.startsWith("/share/")
}
