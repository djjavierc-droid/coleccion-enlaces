package com.coleccionenlaces.app.ui.home

import android.content.Context

/**
 * Pista de apertura del menú lateral (corrección 1.0.1, punto 2).
 *
 * La primera vez que se abre la app, el cajón se abre solo
 * brevemente para que se vea que existe y de dónde sale. Solo una
 * vez: la marca queda guardada en SharedPreferences (el mismo
 * archivo que el tema) y no se vuelve a mostrar.
 */
object PreferenciasMenu {

    const val NOMBRE_PREFERENCIAS = "coleccion_preferencias"

    const val CLAVE_PISTA_MENU_VISTA = "pista_menu_vista"

    /** true si la pista del menú ya se mostró una vez. */
    fun leerPistaMenuVista(contexto: Context): Boolean =
        contexto
            .getSharedPreferences(NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)
            .getBoolean(CLAVE_PISTA_MENU_VISTA, false)

    fun guardarPistaMenuVista(contexto: Context) {
        contexto
            .getSharedPreferences(NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CLAVE_PISTA_MENU_VISTA, true)
            .apply()
    }
}
