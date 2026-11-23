package com.coleccionenlaces.app.domain

/**
 * Vista previa de un enlace: imagen, título y descripción, como al
 * pegar un enlace en WhatsApp.
 *
 * Los tres campos son nullables: un sitio puede no ofrecer alguno (o
 * ninguno). Una vista previa con todo en null es una vista previa
 * vacía; el enlace se guarda igual (Fase 7) con el dominio como
 * título, así que "sin vista previa" nunca rompe el flujo.
 */
data class VistaPrevia(
    val titulo: String? = null,
    val descripcion: String? = null,
    val urlImagen: String? = null,
) {
    /** True cuando el sitio no ofreció título, descripción ni imagen. */
    val estaVacia: Boolean
        get() = titulo == null && descripcion == null && urlImagen == null
}

/** Tipos de error que el servicio de vista previa puede distinguir. */
enum class TipoErrorVistaPrevia {
    /** Sin conexión (DNS, conexión rechazada u otro fallo de red). */
    SIN_INTERNET,

    /** La URL no es una http(s) válida (vacía, sin host, otro esquema). */
    URL_INVALIDA,

    /** La descarga superó el timeout de 15 segundos (tras los reintentos). */
    TIEMPO_AGOTADO,

    /** El sitio respondió pero no ofreció vista previa usable. */
    SIN_VISTA_PREVIA,
}

/**
 * Resultado de [com.coleccionenlaces.app.data.ServicioVistaPrevia.obtenerVistaPrevia].
 *
 * Nunca se lanza una excepción fuera del servicio: los fallos llegan
 * aquí como [Error]. Ambas variantes llevan la [vistaPrevia] obtenida
 * (vacía cuando no hubo ninguna), para que quien guarda el enlace
 * (Fase 7) pueda usarla tal cual sin distinguir ramas.
 */
sealed interface ResultadoVistaPrevia {
    val vistaPrevia: VistaPrevia

    /** El sitio ofreció al menos un dato de vista previa. */
    data class Exito(override val vistaPrevia: VistaPrevia) : ResultadoVistaPrevia

    /** Fallo distinguido por [tipo]; [vistaPrevia] queda vacía. */
    data class Error(
        val tipo: TipoErrorVistaPrevia,
        override val vistaPrevia: VistaPrevia = VistaPrevia(),
    ) : ResultadoVistaPrevia
}
