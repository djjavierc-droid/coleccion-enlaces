package com.coleccionenlaces.app.data

import android.graphics.Bitmap
import com.coleccionenlaces.app.domain.AnalizadorVisual
import com.coleccionenlaces.app.domain.EtiquetaImagen
import com.coleccionenlaces.app.domain.SenalesVisuales
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Analizador visual con ML Kit EMPAQUETADO (Fix 5).
 *
 * - Image Labeling: `com.google.mlkit:image-labeling:17.0.9`
 *   (modelo base dentro del APK, ~5,7 MB).
 * - Text Recognition v2 (latino): `com.google.mlkit:text-recognition:16.0.1`
 *   (modelo dentro del APK, ~4 MB por escritura y arquitectura).
 *
 * ¿Por qué empaquetado y no por Play Services? La app se instala por
 * sideload (no por Play Store), así que la descarga en la instalación
 * del manifiesto (`com.google.mlkit.vision.DEPENDENCIES`) no se
 * dispara: con el modelo sin empaquetar, la primera captura no
 * devolvería nada hasta que el modelo se descargara (y la descarga
 * necesita internet y Google Play Services). Empaquetado, los modelos
 * están disponibles desde la primera ejecución, sin internet y sin
 * Play Services. Coste medido: ~9,7 MB más de APK (105 MB → ~115 MB).
 * Las llamadas a la API son idénticas en ambas variantes: si algún
 * día la app se publica en Play Store, el cambio es solo de
 * dependencia, no de código.
 *
 * El análisis corre en [Dispatchers.IO] (las esperas de `Tasks.await`
 * bloquean el hilo). Nunca lanza: cualquier fallo (modelo sin cargar,
 * imagen no válida, timeout) devuelve null (sin señal → "Otros").
 * Los clientes se crean y se cierran en cada análisis: el póster se
 * analiza una vez por enlace, así que no hay recursos que retener.
 */
class AnalizadorVisualMlKit : AnalizadorVisual {

    override suspend fun analizar(bitmap: Bitmap): SenalesVisuales? {
        return try {
            withContext(Dispatchers.IO) {
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                    return@withContext null
                }
                val imagen = InputImage.fromBitmap(bitmap, 0)
                val etiquetas = etiquetar(imagen)
                val textoOcr = reconocerTexto(imagen)
                SenalesVisuales(etiquetas = etiquetas, textoOcr = textoOcr)
            }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    /** Etiquetas de lo que se ve (confianza >= 0,5). */
    private fun etiquetar(imagen: InputImage): List<EtiquetaImagen> {
        val etiquetador = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(UMBRAL_ETIQUETA)
                .build(),
        )
        return try {
            val tarea = etiquetador.process(imagen)
            val etiquetas = Tasks.await(tarea, TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            etiquetas.map { EtiquetaImagen(it.text, it.confidence) }
        } finally {
            try {
                etiquetador.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Texto quemado en el vídeo (OCR), o "" si no hay texto legible. */
    private fun reconocerTexto(imagen: InputImage): String {
        val reconocedor = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS,
        )
        return try {
            val tarea = reconocedor.process(imagen)
            val texto = Tasks.await(tarea, TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
            texto.text.take(LIMITE_TEXTO_OCR)
        } finally {
            try {
                reconocedor.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /** Confianza mínima de una etiqueta para tenerla en cuenta. */
        const val UMBRAL_ETIQUETA = 0.5f

        /** Espera máxima por modelo (etiquetas y OCR por separado). */
        const val TIMEOUT_SEGUNDOS = 10L

        /** El OCR se trunca: el modelo solo lee 64 tokens de todos modos. */
        const val LIMITE_TEXTO_OCR = 2000
    }
}
