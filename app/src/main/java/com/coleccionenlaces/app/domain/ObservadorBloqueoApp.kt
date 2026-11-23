package com.coleccionenlaces.app.domain

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Bloqueo automático al cerrar o pasar a segundo plano (Fix 4,
 * requisito 4, orden de Javier).
 *
 * [ProcessLifecycleOwner] avisa con `ON_STOP` cuando la app entera
 * deja de estar visible (pasa a segundo plano o se cierra). Este
 * observador vuelve a bloquear TODAS las carpetas en ese momento,
 * así que al volver a abrir la app cada carpeta bloqueada pide la
 * huella de nuevo. El desbloqueo sigue viviendo solo en memoria
 * ([BloqueoCarpetas]): al arrancar, todo ya estaba bloqueado.
 *
 * La clase es lógica pura sobre [BloqueoCarpetas] y se prueba en la
 * JVM llamando a [onStop] directamente: no necesita dispositivo ni
 * huella.
 */
class ObservadorBloqueoApp(
    private val bloqueo: BloqueoCarpetas,
) : DefaultLifecycleObserver {

    /** La app pasa a segundo plano: todas las carpetas se bloquean. */
    override fun onStop(owner: LifecycleOwner) {
        bloqueo.bloquearTodas()
    }
}
