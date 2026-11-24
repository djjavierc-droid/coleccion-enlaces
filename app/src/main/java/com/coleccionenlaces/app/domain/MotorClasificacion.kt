package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.data.Enlace

/**
 * Motor de clasificación: decide la categoría de un enlace por su
 * título, descripción y URL. El repositorio (Fase 5) usa esta
 * interfaz para clasificar al guardar y al reclasificar; así el motor
 * se puede cambiar sin tocar el repositorio.
 *
 * ## Implementaciones
 * - [ClasificadorInteligente]: el motor de producción (Fix 3):
 *   modelo DistilBERT -> cabeza aprendida -> "Otros".
 * - [MotorPalabrasClave]: adaptador del clasificador por palabras
 *   clave (Fase 4). SOLO para las pruebas del repositorio, que
 *   comprueban el cableado con resultados deterministas. En
 *   producción NUNCA se usa: por orden de Javier, en ejecución no se
 *   adivina con palabras clave (ver fix-3-resumen.md).
 */
interface MotorClasificacion {

    /** Categoría de un enlace por título, descripción y URL. */
    fun clasificar(titulo: String, descripcion: String, url: String): String

    /**
     * Categoría de [texto] con su confianza (0..1), o null si el
     * motor no decide ninguna ("Otros"). La implementación por
     * defecto deriva de [clasificar]: si el motor decide una
     * categoría, la confianza es el umbral mínimo
     * ([CONFIANZA_SIN_MEDIR]); si no decide, null. Los motores con
     * confianza real (DistilBERT) la sobrescriben con su medida.
     * El clasificador por visión la usa para exigirle más al texto
     * del OCR que al título (ver [ClasificadorVision]).
     */
    fun clasificarTextoConConfianza(texto: String): Pair<String, Float>? {
        val categoria = clasificar(texto, "", "")
        return if (categoria != Clasificador.OTROS) {
            categoria to CONFIANZA_SIN_MEDIR
        } else {
            null
        }
    }

    companion object {
        /** Confianza que reporta un motor sin medida real cuando decide. */
        const val CONFIANZA_SIN_MEDIR = 0.5f
    }

    /**
     * Categoría a aplicar a un enlace guardado.
     *
     * - Si `enlace.categoriaManual` es true, devuelve `null`: la
     *   elección manual tiene prioridad y NO se sobrescribe.
     * - Si es false, devuelve el nombre que da [clasificar].
     */
    fun clasificarEnlace(enlace: Enlace): String? {
        if (enlace.categoriaManual) return null
        return clasificar(enlace.titulo, enlace.descripcion, enlace.url)
    }
}

/** Adaptador del clasificador por palabras clave (SOLO pruebas). */
object MotorPalabrasClave : MotorClasificacion {
    override fun clasificar(titulo: String, descripcion: String, url: String): String =
        Clasificador.clasificar(titulo, descripcion, url)
}

/**
 * Motor de producción (Fix 3): el modelo DistilBERT decide primero;
 * si no está seguro, decide la cabeza aprendida con las correcciones
 * de Javier; si tampoco está segura, el enlace va a "Otros".
 *
 * La URL no alimenta al modelo (solo título + descripción, como en el
 * entrenamiento); se recibe por la interfaz y se ignora.
 *
 * La entrada se limpia ANTES de decidir (ola 2, hilo I): el texto se
 * pasa por [limpiarTextoParaClasificar] (estilo D: sin emojis,
 * `#palabra` → `palabra`, sin URLs, sin prefijo de views ni sufijo de
 * autor) y el título y la descripción se unen con
 * [construirTextoClasificacion] (la descripción redundante no se
 * añade: duplicar el texto cambia la predicción — Free Fire, título
 * solo → Juegos 0,670; título + descripción duplicada → Otros 0,453,
 * medido con el .tflite real). El título guardado no cambia: la
 * limpieza es solo para clasificar.
 *
 * @param modelo el DistilBERT INT8 (null si no cargó: se salta).
 * @param cabeza la cabeza aprendida (pesos de Room, en memoria).
 * @param etiquetas las 16 categorías en el orden del modelo y la cabeza.
 * @param umbralModelo confianza mínima del modelo para aceptar su categoría.
 * @param umbralCabeza confianza mínima de la cabeza para aceptar su categoría.
 */
class ClasificadorInteligente(
    private val modelo: ClasificadorModelo?,
    private val cabeza: CabezaAprendizaje,
    private val etiquetas: List<String>,
    private val umbralModelo: Float = UMBRAL_MODELO,
    private val umbralCabeza: Float = UMBRAL_CABEZA,
) : MotorClasificacion {

    override fun clasificar(titulo: String, descripcion: String, url: String): String =
        clasificarLimpio(construirTextoClasificacion(titulo, descripcion))?.first
            ?: Clasificador.OTROS

    /**
     * Categoría de [texto] con su confianza real (la del modelo o la
     * de la cabeza, la que haya decidido), o null si ninguna llega a
     * su umbral ("Otros"). Es la misma decisión que [clasificar],
     * pero devolviendo la confianza: el clasificador por visión la
     * necesita para exigirle más al texto del OCR que al título.
     */
    override fun clasificarTextoConConfianza(texto: String): Pair<String, Float>? =
        clasificarLimpio(limpiarTextoParaClasificar(texto))

    /**
     * Decisión sobre un texto YA limpio (estilo D). Es el único punto
     * que decide: [clasificar] y [clasificarTextoConConfianza] solo
     * preparan la entrada y llaman aquí, así que la limpieza se hace
     * exactamente una vez (no es idempotente: el sufijo `| Autor` se
     * comería un segmento `|` más en cada pasada).
     */
    private fun clasificarLimpio(textoLimpio: String): Pair<String, Float>? {
        val limpio = textoLimpio.trim()
        if (limpio.isBlank()) return null
        // Fix 4, bug 2: el texto es SOLO un dominio ("facebook.com").
        // Facebook no dio título y el modelo nunca vio dominios
        // sueltos en entrenamiento: su respuesta es espuria (medido
        // con el .tflite real: "facebook.com" -> "Tecnología y
        // celulares" 0,624, "Juegos" 0,222; "youtube.com" ->
        // "Juegos" 0,674). El modelo NO decide aquí: solo la cabeza
        // aprendida (la corrección manual de Javier) y, si no ha
        // aprendido nada sobre ese dominio, "Otros".
        if (esTextoSoloDominio(limpio)) {
            val prediccionCabeza = try {
                cabeza.predecir(limpio)
            } catch (_: Exception) {
                null
            }
            if (prediccionCabeza != null && prediccionCabeza.confianza >= umbralCabeza) {
                return etiquetas[prediccionCabeza.indiceClase] to prediccionCabeza.confianza
            }
            return null
        }
        // 1. El modelo DistilBERT.
        val resultadoModelo = try {
            modelo?.clasificar(limpio)
        } catch (_: Exception) {
            null
        }
        if (resultadoModelo != null && resultadoModelo.confianza >= umbralModelo) {
            return resultadoModelo.categoria to resultadoModelo.confianza
        }
        // 2. La cabeza aprendida con las correcciones de Javier.
        val prediccionCabeza = try {
            cabeza.predecir(limpio)
        } catch (_: Exception) {
            null
        }
        if (prediccionCabeza != null && prediccionCabeza.confianza >= umbralCabeza) {
            return etiquetas[prediccionCabeza.indiceClase] to prediccionCabeza.confianza
        }
        // 3. Sin seguridad: null ("Otros"). Nunca se adivina con palabras clave.
        return null
    }

    companion object {
        /** Confianza mínima del modelo (calibrada en validación). */
        const val UMBRAL_MODELO = 0.5f

        /** Confianza mínima de la cabeza aprendida. */
        const val UMBRAL_CABEZA = 0.6f
    }
}

/**
 * True si [texto] es SOLO un dominio (p. ej. "facebook.com" o
 * "www.youtube.com"): un único token con al menos un punto, sin
 * espacios ni ruta. Es el título de respaldo que pone el repositorio
 * cuando el sitio no dio título: no describe ningún contenido y el
 * modelo no debe decidir sobre él (ver [ClasificadorInteligente]).
 */
fun esTextoSoloDominio(texto: String): Boolean {
    val limpio = texto.trim().lowercase()
    if (limpio.isEmpty() || limpio.contains(' ') || limpio.contains('/')) return false
    return DOMINIO_REGEX.matches(limpio)
}

private val DOMINIO_REGEX = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
