package com.coleccionenlaces.app.domain

/**
 * Clasificación por visión + OCR (Fix 5): decide la categoría de un
 * enlace a partir del texto quemado en su cuadro de vídeo (OCR).
 *
 * La señal principal de clasificación es el og:title COMPLETO en
 * una sola entrada, limpio con estilo D (ver
 * [limpiarTextoParaClasificar]: 3/3 medido con el .tflite real en la
 * ola 2, hilo I). El OCR es solo un respaldo DÉBIL y el ÚLTIMO: la
 * ola 2 midió que las etiquetas de visión NO sirven como clasificador
 * (MobileNet, equivalente a ML Kit Image Labeling: 0/3 vídeos; CLIP
 * en español 2/3 pero con un falso positivo de Contenido XXX a 0,62
 * en un vídeo musical; hilo E) y que el OCR solo tampoco (0/30
 * cuadros, 0/3 vídeos, hilo D de la ola 1). Por eso la clasificación
 * por etiquetas se QUITÓ del build (ver `MapeoEtiquetas`, borrado):
 * aquí solo queda el OCR, y solo decide cuando el modelo está muy
 * seguro ([UMBRAL_OCR]); si no llega, "Otros".
 *
 * Reglas que se mantienen:
 * - La pantalla de error ("Video no disponible") NUNCA se clasifica
 *   como contenido: [SenalesVisuales.esPantallaError] la detecta por
 *   OCR de los píxeles (el texto vive en un iframe que el WebView no
 *   puede leer — límite de Fix 4, ver fix-5-diagnostico.md) y aquí
 *   devuelve "Otros" sin consultar nada más.
 * - Sin señal suficiente o con confianza baja → "Otros" (nunca se
 *   adivina con palabras clave en ejecución; las palabras clave solo
 *   son datos de entrenamiento).
 * - El texto solo-dominio (p. ej. "facebook.com") no va al modelo:
 *   el motor lo manda a la cabeza o a "Otros", igual que con el
 *   título (ver [ClasificadorInteligente]).
 *
 * @param motor el motor de clasificación de texto (modelo + cabeza).
 */
class ClasificadorVision(
    private val motor: MotorClasificacion,
) {

    /**
     * Categoría de [senales], o [Clasificador.OTROS] si no hay señal
     * suficiente. Nunca lanza: si el motor falla, "Otros".
     */
    fun clasificarPorVision(senales: SenalesVisuales): String {
        if (senales.esPantallaError) return Clasificador.OTROS
        val textoOcr = senales.textoOcr.trim()
        if (textoOcr.isNotBlank() && !esTextoSoloDominio(textoOcr)) {
            val porTexto = try {
                motor.clasificarTextoConConfianza(textoOcr)
            } catch (_: Exception) {
                null
            }
            // El OCR es solo un respaldo DÉBIL (investigación de 1
            // hora, hilo D): la basura OCR da confianza alta y
            // equivocada (12/30 cuadros > 0,5 con categoría errónea;
            // el peor, 0,847). Solo se confía en él cuando el modelo
            // está muy seguro; si no llega al umbral alto, "Otros".
            if (porTexto != null && porTexto.second >= UMBRAL_OCR) {
                return porTexto.first
            }
        }
        return Clasificador.OTROS
    }

    companion object {
        /**
         * Confianza mínima para aceptar una categoría sacada del OCR.
         * Calibrada con lo medido en la investigación de 1 hora
         * (hilo D): el peor OCR erróneo midió 0,847 (meme de Free
         * Fire → Entretenimiento) y el caso obligatorio
         * (Soylorainegodoy, OCR exacto) mide 0,963. El umbral queda
         * entre ambos: bloquea todos los OCR erróneos medidos y
         * deja pasar el caso que debe funcionar.
         */
        const val UMBRAL_OCR = 0.85f
    }
}
