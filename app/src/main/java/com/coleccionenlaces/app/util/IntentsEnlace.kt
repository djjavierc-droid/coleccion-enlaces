package com.coleccionenlaces.app.util

import android.content.Intent
import android.net.Uri

/**
 * Intents para abrir y compartir un enlace (Fase 8). Funciones
 * puras sobre la URL: construyen el intent, no lo lanzan. Quien
 * llama lo lanza con `startActivity` y captura
 * `ActivityNotFoundException` si ninguna app puede atenderlo, para
 * mostrar un mensaje amigable en lugar de romper la app.
 *
 * Se prueban con Robolectric ([IntentsEnlaceTest]): `Intent` es
 * una clase de Android y en la JVM sin Robolectric es solo un stub.
 */

/** Intent ACTION_VIEW con la URL: la abre el navegador o la app correspondiente. */
fun crearIntentAbrirEnlace(url: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))

/** Intent ACTION_SEND con la URL como texto plano, listo para un chooser. */
fun crearIntentCompartirEnlace(url: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url.trim())
    }
