package com.coleccionenlaces.app.domain

import android.graphics.Bitmap

/**
 * Señales visuales de un cuadro de vídeo (Fix 5): lo que se ve en
 * la miniatura/poster que la app ya captura, para clasificar enlaces
 * que no traen título ni descripción (solo el dominio).
 *
 * Las señales se sacan EN EL TELÉFONO, sin servidor y sin pagar
 * (ML Kit en el dispositivo): etiquetas de lo que se ve en la
 * imagen + el texto quemado en el vídeo (OCR). El texto del OCR
 * alimenta al clasificador DistilBERT que ya existe; las etiquetas
 * se mapean a mano a las 16 categorías ([MapeoEtiquetas]).
 *
 * @param etiquetas lo que ML Kit ve en la imagen (texto en inglés,
 *   confianza 0..1).
 * @param textoOcr texto quemado en el vídeo (OCR), o "" si no hay
 *   texto legible.
 */
data class SenalesVisuales(
    val etiquetas: List<EtiquetaImagen>,
    val textoOcr: String,
) {
    /**
     * True si el cuadro es la pantalla de error de Facebook
     * ("Video no disponible"): el OCR lee los píxeles, así que lo
     * detecta aunque el texto viva dentro de un iframe de otro
     * origen que el WebView no puede leer (límite honesto de Fix 4:
     * `innerText` del documento principal no ve dentro del iframe).
     * Un cuadro así NUNCA se clasifica como contenido.
     */
    val esPantallaError: Boolean
        get() = detectarVideoNoDisponible(textoOcr)
}

/** Una etiqueta de imagen: texto y confianza (0..1). */
data class EtiquetaImagen(
    val texto: String,
    val confianza: Float,
)

/**
 * Analiza un cuadro de vídeo y devuelve sus señales visuales, o
 * null si no se pudo analizar (ML Kit no disponible, modelo sin
 * descargar, imagen no válida): sin señal no se clasifica, el enlace
 * queda en "Otros". Nunca lanza: cualquier fallo es null.
 */
interface AnalizadorVisual {

    /** Señales visuales de [bitmap], o null si no se pudo analizar. */
    suspend fun analizar(bitmap: Bitmap): SenalesVisuales?
}
