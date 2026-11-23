package com.coleccionenlaces.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coleccionenlaces.app.data.AppDatabase
import com.coleccionenlaces.app.data.ServicioAprendizaje
import com.coleccionenlaces.app.domain.CabezaAprendizaje
import com.coleccionenlaces.app.domain.ClasificadorModelo
import java.util.concurrent.TimeUnit

/**
 * Reentrenamiento de la cabeza en reposo (Fix 3, mecanismo (b)).
 *
 * Pasa varias épocas sobre todas las correcciones guardadas y
 * persiste los pesos. Corre solo con cargador y dispositivo en reposo
 * (ver [Constraints] en [programar]): el reentrenamiento nunca se
 * nota mientras se usa la app. No toca la red ni el modelo .tflite.
 *
 * Si no hay correcciones, no hace nada. Ningún fallo rompe nada: el
 * trabajo devuelve `Result.success()` siempre que la base se pueda
 * abrir (un fallo de aprendizaje no reintenta).
 */
class ReentrenamientoWorker(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        return try {
            val baseDatos = AppDatabase.obtenerInstancia(applicationContext)
            val etiquetas = ClasificadorModelo.cargarEtiquetas(applicationContext)
            if (etiquetas.isEmpty()) return Result.success()
            val cabeza = CabezaAprendizaje(etiquetas)
            val servicio = ServicioAprendizaje(baseDatos.aprendizajeDao(), cabeza)
            servicio.cargarPesos()
            servicio.reentrenarEnReposo()
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        /** Nombre único del trabajo periódico. */
        const val NOMBRE_TRABAJO = "reentrenamiento-cabeza"

        /**
         * Programa el trabajo periódico (una vez al día, con cargador
         * y en reposo). Idempotente: si ya existe, se conserva.
         */
        fun programar(contexto: Context) {
            val restricciones = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val peticion = PeriodicWorkRequestBuilder<ReentrenamientoWorker>(
                1,
                TimeUnit.DAYS,
            )
                .setConstraints(restricciones)
                .build()
            WorkManager.getInstance(contexto.applicationContext)
                .enqueueUniquePeriodicWork(
                    NOMBRE_TRABAJO,
                    ExistingPeriodicWorkPolicy.KEEP,
                    peticion,
                )
        }
    }
}
