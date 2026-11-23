package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.data.EnlaceConCategoria
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bloqueo por carpeta (Fase 9, orden de Javier).
 *
 * Cada carpeta (categoría) está bloqueada por defecto y solo se abre
 * con huella dactilar (o credencial del dispositivo). El desbloqueo
 * vive SOLO en memoria: [desbloqueadas] es un [StateFlow] de este
 * objeto, que vive en el [com.coleccionenlaces.app.ui.home.HomeViewModel]
 * y se pierde al cerrar la app (al arrancar, todo vuelve a estar
 * bloqueado). No se guarda nada en la base de datos ni en
 * SharedPreferences: que una carpeta se abriera ayer no dice nada
 * de hoy.
 *
 * Esta clase NO toca BiometricPrompt: es lógica pura y se prueba en
 * la JVM sin dispositivo. El llamado real a BiometricPrompt es una
 * capa fina de UI ([com.coleccionenlaces.app.ui.home.autenticarConHuella])
 * que, al tener éxito, llama a [desbloquear].
 */
class BloqueoCarpetas {

    private val _desbloqueadas = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids de las categorías desbloqueadas en esta sesión. */
    val desbloqueadas: StateFlow<Set<Long>> = _desbloqueadas.asStateFlow()

    /** ¿Está desbloqueada esta categoría en esta sesión? */
    fun estaDesbloqueada(categoriaId: Long): Boolean =
        categoriaId in _desbloqueadas.value

    /** Desbloquea una categoría para lo que queda de sesión. */
    fun desbloquear(categoriaId: Long) {
        _desbloqueadas.update { it + categoriaId }
    }

    /** Vuelve a bloquear todas las categorías (p. ej. al cerrar sesión). */
    fun bloquearTodas() {
        _desbloqueadas.value = emptySet()
    }
}

/**
 * Filtra una lista de enlaces con su categoría resuelta, dejando
 * solo los de categorías desbloqueadas en esta sesión.
 *
 * Es la única regla de visibilidad: la búsqueda, los favoritos y la
 * cuadrícula pasan por aquí (el ViewModel la aplica al final, sobre
 * la lista ya filtrada por categoría, favoritos y búsqueda), así que
 * de una carpeta bloqueada no se ve ningún contenido en ningún sitio
 * de la app: ni títulos, ni descripciones, ni imágenes, ni URLs.
 * Los contadores por categoría y el total del encabezado SÍ se
 * siguen mostrando (son solo un número, no contenido del enlace), y
 * la propia sección bloqueada muestra su nombre e ícono para que se
 * pueda tocar y abrir con huella.
 */
fun filtrarEnlacesVisibles(
    enlaces: List<EnlaceConCategoria>,
    desbloqueadas: Set<Long>,
): List<EnlaceConCategoria> =
    enlaces.filter { it.categoria.id in desbloqueadas }
