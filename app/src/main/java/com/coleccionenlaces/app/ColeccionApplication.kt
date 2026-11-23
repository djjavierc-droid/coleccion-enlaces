package com.coleccionenlaces.app

import android.app.Application
import com.coleccionenlaces.app.di.AppContainer

/**
 * Application de la app (Fase 5): posee el contenedor de inyección
 * manual de dependencias ([AppContainer]), que vive tanto como la app.
 * `MainActivity` lo toma de aquí para crear el ViewModel principal.
 * Registrada en el manifiesto con `android:name=".ColeccionApplication"`.
 */
class ColeccionApplication : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
        // Aprendizaje (Fix 3): carga los pesos guardados y programa el
        // reentrenamiento diario en reposo. No bloquea el arranque.
        contenedor.iniciarAprendizaje()
    }
}
