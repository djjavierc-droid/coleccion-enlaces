package com.coleccionenlaces.app.data

import android.graphics.Bitmap
import com.coleccionenlaces.app.domain.AnalizadorVisual
import com.coleccionenlaces.app.domain.SenalesVisuales
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Analizador visual con ML Kit EMPAQUETADO (Fix 5): solo
 * Text Recognition v2 (latino), `com.google.mlkit:text-recognition:16.0.1`
 * (modelo dentro del APK, ~4 MB por escritura y arquitectura).
 *
 * La Image Labeling se QUITÓ (ola 2, hilo E): su equivalente,
 * MobileNet, midió 0/3 vídeos como clasificador, así que el coste
 * del modelo y del código no aportaba nada. Solo queda el OCR, como
 * respaldo débil del og:title limpio (ver [com.coleccionenlaces.app.domain.ClasificadorVision]).
 *
 * ¿Por qué empaquetado y no por Play Services? La app se instala por
 * sideload (no por Play Store), así que la descarga en la instalación
 * del manifiesto (`com.google.mlkit.vision.DEPENDENCIES`) no se
 * dispara: con el modelo sin empaquetar, la primera captura no
 * devolvería nada hasta que el modelo se descargara (y la descarga
 * necesita internet y Google Play Services). Empaquetado, el modelo
 * está disponible desde la primera ejecución, sin internet y sin
 * Play Services. Las llamadas a la API son idénticas en ambas
 * variantes: si algún día la app se publica en Play Store, el cambio
 * es solo de dependencia, no de código.
 *
 * El análisis corre en [Dispatchers.IO] (la espera de `Tasks.await`
 * bloquea el hilo). Nunca lanza: cualquier fallo (modelo sin cargar,
 * imagen no válida, timeout) devuelve null (sin señal → "Otros").
 * El cliente se crea y se cierra en cada análisis: el póster se
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
                val textoOcr = reconocerTexto(imagen)
                SenalesVisuales(textoOcr = textoOcr)
            }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
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
        /** Espera máxima del OCR. */
        const val TIMEOUT_SEGUNDOS = 10L

        /** El OCR se trunca: el modelo solo lee 64 tokens de todos modos. */
        const val LIMITE_TEXTO_OCR = 2000
    }
}
