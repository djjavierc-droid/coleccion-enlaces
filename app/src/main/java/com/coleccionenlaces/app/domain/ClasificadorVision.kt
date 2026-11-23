package com.coleccionenlaces.app.domain

/**
 * Clasificación por visión + OCR (Fix 5): decide la categoría de un
 * enlace a partir de las señales visuales de su cuadro de vídeo.
 *
 * Orden de señales (de más a menos fiable):
 * 1. **Texto del OCR** → el mismo motor de texto que ya existe
 *    (DistilBERT → cabeza aprendida → "Otros"). Es la señal
 *    principal: el texto quemado en el vídeo dice de qué va.
 * 2. **Etiquetas de imagen** → voto por [MapeoEtiquetas]. Solo
 *    decide si el OCR no dio nada: una foto dice menos que el texto.
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
                motor.clasificar(textoOcr, "", "")
            } catch (_: Exception) {
                Clasificador.OTROS
            }
            if (porTexto != Clasificador.OTROS) return porTexto
        }
        MapeoEtiquetas.votar(senales.etiquetas)?.let { return it }
        return Clasificador.OTROS
    }
}
