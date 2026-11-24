package com.coleccionenlaces.app.domain

/**
 * Clasificación por visión + OCR (Fix 5): decide la categoría de un
 * enlace a partir de las señales visuales de su cuadro de vídeo.
 *
 * La señal principal de clasificación es el og:title COMPLETO en
 * una sola entrada (el título del vídeo, que el repositorio guarda
 * entero y clasifica como "título descripción" sin fragmentarlo —
 * investigación de 1 hora, hilo B: párrafos sueltos del mismo
 * título dan Viajes 0,920 / Dinero 0,589 / Tecnología 0,927 según
 * el fragmento). El OCR es solo un respaldo DÉBIL (hilo D: 0/30
 * cuadros, 0/3 vídeos; la basura OCR da confianza ALTA y equivocada:
 * 12/30 cuadros > 0,5 con categoría errónea). Por eso aquí el OCR
 * solo decide cuando el modelo está muy seguro ([UMBRAL_OCR]); si
 * no llega, se sigue con las etiquetas de imagen.
 *
 * Orden de señales (de más a menos fiable):
 * 1. **Texto del OCR** → el mismo motor de texto que ya existe
 *    (DistilBERT → cabeza aprendida → "Otros"), pero con el umbral
 *    alto [UMBRAL_OCR]: es un respaldo débil, nunca la señal
 *    principal.
 * 2. **Etiquetas de imagen** → voto por [MapeoEtiquetas]. Solo
 *    decide si el OCR no dio nada con suficiente confianza.
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
     * suficiente. Nunca lanza: si el motor falla, se sigue con las
     * etiquetas; si todo falla, "Otros".
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
            // está muy seguro; si no llega al umbral alto, se sigue
            // con las etiquetas en vez de devolver su categoría.
            if (porTexto != null && porTexto.second >= UMBRAL_OCR) {
                return porTexto.first
            }
        }
        MapeoEtiquetas.votar(senales.etiquetas)?.let { return it }
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
