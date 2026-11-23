package com.coleccionenlaces.app.ui.theme

import android.content.Context

/**
 * Preferencia del tema claro/oscuro (Fase 9, sección 6 del plan).
 *
 * Se guarda con SharedPreferences del framework de Android, sin
 * librería nueva. La clave se lee al arrancar la app en
 * [com.coleccionenlaces.app.MainActivity] y se aplica al tema; el
 * interruptor del encabezado la cambia. El modo oscuro es el
 * predeterminado (si no hay nada guardado, [leerTemaOscuro]
 * devuelve true).
 */
object PreferenciasTema {

    const val NOMBRE_PREFERENCIAS = "coleccion_preferencias"

    const val CLAVE_TEMA_OSCURO = "tema_oscuro"

    /** true = oscuro (predeterminado), false = claro. */
    fun leerTemaOscuro(contexto: Context): Boolean =
        contexto
            .getSharedPreferences(NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)
            .getBoolean(CLAVE_TEMA_OSCURO, true)

    fun guardarTemaOscuro(contexto: Context, oscuro: Boolean) {
        contexto
            .getSharedPreferences(NOMBRE_PREFERENCIAS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CLAVE_TEMA_OSCURO, oscuro)
            .apply()
    }
}
