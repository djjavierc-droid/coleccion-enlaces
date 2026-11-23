package com.coleccionenlaces.app.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * Cabeza de aprendizaje: la "última capa" que se reentrena en el
 * teléfono con las correcciones de Javier.
 *
 * Es una regresión logística multiclase sobre las palabras del texto:
 * cada palabra distinta (normalizada como en [Clasificador]) cae en
 * uno de [NUM_CUBETAS] cubos por hash, y cada cubo guarda un peso por
 * categoría. La predicción es el softmax de la suma de pesos.
 *
 * ## Por qué no se reentrena el modelo .tflite
 * LiteRT no ofrece ninguna API oficial para reentrenar un clasificador
 * de texto en el dispositivo (la personalización de modelos de
 * TensorFlow Lite solo existe para clasificación de imágenes). El
 * modelo DistilBERT es, por tanto, un extractor fijo: lo que aprende
 * en el teléfono es esta cabeza, que es la última capa efectiva de la
 * decisión. El reentrenamiento completo del modelo NO es posible en
 * el teléfono; esto es el límite honesto (ver fix-3-resumen.md).
 *
 * ## Dos mecanismos (diseño aprobado)
 * - (a) **Inmediata:** [aprenderEnLinea] hace un paso de descenso de
 *   gradiente con cada corrección (mover un enlace a su carpeta) y se
 *   nota desde la primera corrección.
 * - (b) **En reposo:** [entrenarLote] pasa varias épocas sobre todas
 *   las correcciones guardadas; lo ejecuta el trabajo de WorkManager
 *   con cargador y dispositivo en reposo.
 *
 * Los pesos viven en memoria y se persisten como BLOB en Room
 * ([aBytes]/[desdeBytes]). La cabeza nunca aprende "Otros": una
 * corrección hacia "Otros" significa "esto no es ninguna categoría" y
 * no tiene clase propia en la cabeza; esos ejemplos se guardan igual
 * para el futuro, pero no entrenan pesos.
 *
 * @param etiquetas las 16 categorías en el orden del modelo
 *   (`etiquetas.txt`); los índices de clase son los de esta lista.
 */
class CabezaAprendizaje(
    val etiquetas: List<String>,
    pesosIniciales: FloatArray? = null,
) {
    /** Pesos por cubo y clase, en orden: cubo * 16 + clase. */
    private var pesos: FloatArray =
        pesosIniciales?.copyOf() ?: FloatArray(NUM_CUBETAS * etiquetas.size)

    /** Sesgos por clase. */
    private var sesgos: FloatArray = FloatArray(etiquetas.size)

    /** Resultado de [predecir]: índice de clase y confianza (softmax). */
    data class Prediccion(val indiceClase: Int, val confianza: Float)

    /** Normaliza el texto y devuelve sus palabras distintas. */
    fun palabrasDe(texto: String): List<String> {
        val normalizado = Clasificador.normalizar(texto)
        if (normalizado.isBlank()) return emptyList()
        // Sin palabras vacías: "de", "la", "el"... aparecen en todos
        // los títulos y no dicen nada de la categoría; solo añaden
        // ruido a los cubos. (Lo exige la prueba `palabrasDe normaliza
        // acentos y mayusculas`: "Receta de PASTEL de chocolate" da
        // ["receta", "pastel", "chocolate"], sin "de".)
        return normalizado.split(" ").distinct().filter { it !in PALABRAS_VACIAS }
    }

    /** Cubo de una palabra (hash estable entre ejecuciones). */
    internal fun cuboDe(palabra: String): Int =
        (palabra.hashCode() and 0x7fffffff) % NUM_CUBETAS

    /**
     * Predice la clase de un texto. Devuelve null si no hay palabras
     * (texto vacío) o si la cabeza no ha aprendido nada todavía
     * (todos los pesos en cero: el softmax sería uniforme y no dice
     * nada). El llamador aplica su umbral de confianza.
     */
    fun predecir(texto: String): Prediccion? {
        val palabras = palabrasDe(texto)
        if (palabras.isEmpty()) return null
        if (!haAprendido()) return null
        val puntuaciones = puntuaciones(palabras)
        val probs = softmax(puntuaciones)
        val mejor = probs.indices.maxByOrNull { probs[it] } ?: return null
        return Prediccion(mejor, probs[mejor])
    }

    /**
     * Un paso de aprendizaje en línea con un ejemplo corregido.
     * No hace nada si la categoría no es una de las 16 (p. ej.
     * "Otros") o si el texto no tiene palabras.
     */
    fun aprenderEnLinea(texto: String, categoria: String) {
        val indice = etiquetas.indexOf(categoria)
        if (indice < 0) return
        val palabras = palabrasDe(texto)
        if (palabras.isEmpty()) return
        pasoGradiente(palabras, indice, TASA_EN_LINEA)
    }

    /**
     * Reentrena la cabeza sobre todos los ejemplos guardados.
     * Devuelve el número de ejemplos usados (0 si no hay ninguno
     * válido). Los ejemplos con categoría fuera de las 16 se saltan.
     */
    fun entrenarLote(ejemplos: List<Pair<String, String>>, epocas: Int = EPOCAS_LOTE): Int {
        val validos = ejemplos.mapNotNull { (texto, categoria) ->
            val indice = etiquetas.indexOf(categoria)
            if (indice < 0) return@mapNotNull null
            val palabras = palabrasDe(texto)
            if (palabras.isEmpty()) return@mapNotNull null
            palabras to indice
        }
        if (validos.isEmpty()) return 0
        repeat(epocas) {
            for ((palabras, indice) in validos.shuffled()) {
                pasoGradiente(palabras, indice, TASA_LOTE)
            }
        }
        return validos.size
    }

    /** Un paso de descenso de gradiente (entropía cruzada + softmax). */
    private fun pasoGradiente(palabras: List<String>, indiceVerdadero: Int, tasa: Float) {
        val puntuaciones = puntuaciones(palabras)
        val probs = softmax(puntuaciones)
        val cubos = palabras.map { cuboDe(it) }.distinct()
        for (clase in etiquetas.indices) {
            val error = (if (clase == indiceVerdadero) 1f else 0f) - probs[clase]
            if (error == 0f) continue
            for (cubo in cubos) {
                pesos[cubo * etiquetas.size + clase] += tasa * error
            }
            sesgos[clase] += tasa * error
        }
    }

    /** Puntuaciones (logits) por clase para unas palabras. */
    private fun puntuaciones(palabras: List<String>): FloatArray {
        val resultado = sesgos.copyOf()
        for (palabra in palabras) {
            val cubo = cuboDe(palabra)
            val base = cubo * etiquetas.size
            for (clase in etiquetas.indices) {
                resultado[clase] += pesos[base + clase]
            }
        }
        return resultado
    }

    /** Softmax estable. */
    private fun softmax(logits: FloatArray): FloatArray {
        val maximo = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { exp((logits[it] - maximo).toDouble()).toFloat() }
        val suma = exps.sum()
        if (suma <= 0f) return FloatArray(logits.size) { 1f / logits.size }
        return FloatArray(logits.size) { exps[it] / suma }
    }

    /** True si algún peso o sesgo es distinto de cero. */
    fun haAprendido(): Boolean =
        pesos.any { it != 0f } || sesgos.any { it != 0f }

    /**
     * Copia los pesos y sesgos de otra cabeza con las mismas
     * etiquetas (para cargar los pesos guardados en Room).
     */
    fun copiarPesosDesde(otra: CabezaAprendizaje) {
        if (otra.etiquetas != etiquetas) return
        pesos = otra.pesos.copyOf()
        sesgos = otra.sesgos.copyOf()
    }

    /** Pesos + sesgos serializados (para guardar en Room como BLOB). */
    fun aBytes(): ByteArray {
        val buffer = ByteBuffer
            .allocate((pesos.size + sesgos.size) * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (valor in pesos) buffer.putFloat(valor)
        for (valor in sesgos) buffer.putFloat(valor)
        return buffer.array()
    }

    companion object {
        /** Cubos del truco de hash (memoria acotada: 1024 × 16 floats). */
        const val NUM_CUBETAS = 1024

        /** Tasa de aprendizaje del paso en línea (una corrección). */
        const val TASA_EN_LINEA = 0.5f

        /** Tasa de aprendizaje del reentrenamiento por lotes. */
        const val TASA_LOTE = 0.1f

        /** Épocas del reentrenamiento por lotes. */
        const val EPOCAS_LOTE = 10

        /**
         * Palabras vacías del español (artículos, preposiciones,
         * pronombres y auxiliares): aparecen en casi todos los
         * títulos y no aportan señal de categoría. `palabrasDe` las
         * quita antes de entrenar o predecir.
         */
        val PALABRAS_VACIAS = setOf(
            "a", "al", "ante", "bajo", "con", "contra", "de", "del",
            "desde", "durante", "el", "en", "entre", "es", "esa", "ese",
            "eso", "esta", "este", "esto", "fue", "ha", "han", "hay",
            "la", "las", "le", "les", "lo", "los", "mas", "me", "mi",
            "mis", "muy", "no", "nos", "para", "pero", "por", "que",
            "se", "ser", "si", "son", "su", "sus", "te", "tu", "tus",
            "un", "una", "unas", "uno", "unos", "y", "ya",
        )

        /** Reconstruye una cabeza desde los bytes de [aBytes]. */
        fun desdeBytes(etiquetas: List<String>, bytes: ByteArray?): CabezaAprendizaje {
            if (bytes == null) return CabezaAprendizaje(etiquetas)
            val esperados = (NUM_CUBETAS * etiquetas.size + etiquetas.size) * Float.SIZE_BYTES
            if (bytes.size != esperados) return CabezaAprendizaje(etiquetas)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val pesos = FloatArray(NUM_CUBETAS * etiquetas.size) { buffer.float }
            val cabeza = CabezaAprendizaje(etiquetas, pesos)
            for (i in cabeza.sesgos.indices) cabeza.sesgos[i] = buffer.float
            return cabeza
        }
    }
}
