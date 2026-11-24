package com.coleccionenlaces.app.domain

import android.graphics.Bitmap

/**
 * Señales visuales de un cuadro de vídeo (Fix 5): el texto quemado
 * en la miniatura/poster que la app ya captura, para clasificar
 * enlaces que no traen título ni descripción (solo el dominio).
 *
 * La señal se saca EN EL TELÉFONO, sin servidor y sin pagar
 * (ML Kit Text Recognition v2 en el dispositivo). El texto del OCR
 * alimenta al clasificador DistilBERT que ya existe, con el umbral
 * alto de [ClasificadorVision] (respaldo débil, nunca señal
 * principal: la principal es el og:title limpio con estilo D).
 *
 * Las etiquetas de imagen se QUITARON (ola 2, hilo E): MobileNet,
 * equivalente a ML Kit Image Labeling, midió 0/3 vídeos, y CLIP en
 * español 2/3 con un falso positivo de Contenido XXX a 0,62. No hay
 * voto de etiquetas en el build.
 *
 * @param textoOcr texto quemado en el vídeo (OCR), o "" si no hay
 *   texto legible.
 */
data class SenalesVisuales(
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
