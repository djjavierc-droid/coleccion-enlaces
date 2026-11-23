package com.coleccionenlaces.app.domain

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * Clasificador con el modelo DistilBERT INT8 (Fix 3, opción A).
 *
 * Carga `assets/modelo_clasificador.tflite` con el intérprete base de
 * LiteRT y lo ejecuta sobre el texto tokenizado con
 * [TokenizadorWordPiece]. La salida del modelo son 16 logits (float);
 * se aplica softmax y se devuelve la categoría con mayor probabilidad
 * y su confianza. El llamador ([ClasificadorInteligente]) decide el
 * umbral: si la confianza es baja, el enlace va a "Otros".
 *
 * DistilBERT NO usa token_type_ids: el .tflite tiene 2 entradas
 * (input_ids, attention_mask). El código pasa las entradas según
 * `inputTensorCount`, así que un modelo de 3 entradas (BERT clásico)
 * seguiría funcionando con la tercera (todo ceros).
 *
 * El modelo NO se reentrena en el teléfono (ver
 * [CabezaAprendizaje]): aquí solo se ejecuta. Si el archivo no está,
 * no se puede cargar o la inferencia falla, [clasificar] devuelve
 * null y el pipeline sigue con la cabeza aprendida: la app nunca se
 * rompe por el modelo.
 *
 * El intérprete no es seguro para hilos: el acceso se sincroniza con
 * un candado y la inferencia corre en el hilo que llama (el
 * repositorio llama desde un dispatcher de fondo).
 *
 * @param contexto contexto de la aplicación (para leer los assets).
 * @param longitudMaxima tokens por texto (la del entrenamiento: 64).
 */
class ClasificadorModelo(
    contexto: Context,
    private val longitudMaxima: Int = LONGITUD_MAXIMA,
) {
    private val contextoApp = contexto.applicationContext

    /** Etiquetas en el orden del modelo (las 16, sin "Otros"). */
    val etiquetas: List<String> by lazy { cargarEtiquetas(contextoApp) }

    private val tokenizador: TokenizadorWordPiece? by lazy {
        try {
            contextoApp.assets.open(VOCABULARIO).bufferedReader().use { lector ->
                TokenizadorWordPiece.desdeLineas(lector.readLines())
            }
        } catch (_: Exception) {
            null
        }
    }

    private val interprete: Interpreter? by lazy {
        try {
            val buffer = cargarModelo(contextoApp)
            val opciones = Interpreter.Options().apply {
                setNumThreads(2)
            }
            Interpreter(buffer, opciones).also { interprete ->
                // Las entradas del modelo son de longitud dinámica
                // (None en Keras): se fijan a [1, longitudMaxima].
                for (indice in 0 until interprete.inputTensorCount) {
                    interprete.resizeInput(indice, intArrayOf(1, longitudMaxima))
                }
                interprete.allocateTensors()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** True si el modelo y el vocabulario cargaron bien. */
    val estaDisponible: Boolean
        get() = interprete != null && tokenizador != null && etiquetas.size == NUM_CLASES

    /** Resultado de una inferencia: categoría y confianza (0..1). */
    data class ResultadoModelo(val categoria: String, val confianza: Float)

    /**
     * Clasifica un texto (título + descripción). Devuelve null si el
     * modelo no está disponible o la inferencia falla.
     */
    fun clasificar(texto: String): ResultadoModelo? {
        val interprete = interprete ?: return null
        val tokenizador = tokenizador ?: return null
        if (etiquetas.size != NUM_CLASES) return null
        if (texto.isBlank()) return null
        return try {
            val entrada = tokenizador.codificar(texto, longitudMaxima)
            val logits = FloatArray(NUM_CLASES)
            // DistilBERT tiene 2 entradas; un modelo BERT clásico
            // tiene 3 (la tercera es token_type_ids, todo ceros).
            val entradas: Array<Any> =
                if (interprete.inputTensorCount >= 3) {
                    arrayOf(
                        arrayOf(entrada.inputIds),
                        arrayOf(entrada.attentionMask),
                        arrayOf(entrada.tokenTypeIds),
                    )
                } else {
                    arrayOf(
                        arrayOf(entrada.inputIds),
                        arrayOf(entrada.attentionMask),
                    )
                }
            synchronized(this) {
                interprete.runForMultipleInputsOutputs(
                    entradas,
                    mapOf(0 to arrayOf(logits)),
                )
            }
            val probs = softmax(logits)
            val mejor = probs.indices.maxByOrNull { probs[it] } ?: return null
            ResultadoModelo(etiquetas[mejor], probs[mejor])
        } catch (_: Exception) {
            null
        }
    }

    /** Libera el intérprete. */
    fun cerrar() {
        try {
            interprete?.close()
        } catch (_: Exception) {
        }
    }

    /** Softmax estable. */
    private fun softmax(logits: FloatArray): FloatArray {
        val maximo = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { exp((logits[it] - maximo).toDouble()).toFloat() }
        val suma = exps.sum()
        if (suma <= 0f) return FloatArray(logits.size) { 1f / logits.size }
        return FloatArray(logits.size) { exps[it] / suma }
    }

    /** El modelo como buffer mapeado desde los assets. */
    private fun cargarModelo(contexto: Context): MappedByteBuffer {
        val descriptor = contexto.assets.openFd(MODELO)
        FileInputStream(descriptor.fileDescriptor).channel.use { canal ->
            return canal.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }
    }

    companion object {
        /** Nombre del modelo en assets. */
        const val MODELO = "modelo_clasificador.tflite"

        /** Nombre del vocabulario en assets. */
        const val VOCABULARIO = "vocab.txt"

        /** Nombre de las etiquetas en assets (16 líneas). */
        const val ETIQUETAS = "etiquetas.txt"

        /** Tokens por texto (la longitud del entrenamiento). */
        const val LONGITUD_MAXIMA = 64

        /** Clases del modelo (las 17 predefinidas menos "Otros"). */
        const val NUM_CLASES = 16

        /** Etiquetas del modelo, o la lista vacía si no se puede leer. */
        fun cargarEtiquetas(contexto: Context): List<String> {
            return try {
                contexto.assets.open(ETIQUETAS).bufferedReader().use { lector ->
                    lector.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
