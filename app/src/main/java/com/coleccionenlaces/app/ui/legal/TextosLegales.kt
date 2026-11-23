package com.coleccionenlaces.app.ui.legal

import com.coleccionenlaces.app.R

/**
 * Textos legales de "Acerca de" (corrección 1.0.1, punto 2): la
 * sección final del menú lateral, tomada del cajón de Reddit. El
 * cajón en sí sigue el modelo Muse (categorías, contadores,
 * bloqueo por huella, pista en el primer inicio); de Reddit solo
 * viene esta sección, con sus tres elementos. Cada uno abre su
 * pantalla dentro de la aplicación ([PantallaLegal]) con el texto
 * completo y real, no un marcador vacío.
 *
 * Los tres textos dicen lo mismo de tres maneras: la aplicación no
 * tiene cuentas, no recoge datos personales, no usa nube ni
 * anuncios; todo queda en el teléfono y se borra desinstalando la
 * aplicación o desde "Borrar todos los datos" en los ajustes de
 * Android.
 */
enum class TipoLegal(val ruta: String) {
    /** Política de privacidad. */
    PRIVACIDAD("privacidad"),

    /** Acuerdo del usuario (términos de uso). */
    TERMINOS("terminos"),

    /** Eliminación de datos: cómo borrar lo guardado. */
    ELIMINACION("eliminacion"),
    ;

    companion object {
        /** El tipo de la ruta de navegación, o null si no es ninguna. */
        fun deRuta(ruta: String?): TipoLegal? =
            entries.firstOrNull { it.ruta == ruta }
    }
}

/** Recurso de cadena con el título de la pantalla de [tipo]. */
fun tituloLegalRes(tipo: TipoLegal): Int = when (tipo) {
    TipoLegal.PRIVACIDAD -> R.string.legal_privacy
    TipoLegal.TERMINOS -> R.string.legal_terms
    TipoLegal.ELIMINACION -> R.string.legal_deletion
}

/** El texto completo y real de [tipo]. */
fun textoLegal(tipo: TipoLegal): String = when (tipo) {
    TipoLegal.PRIVACIDAD -> TEXTO_PRIVACIDAD
    TipoLegal.TERMINOS -> TEXTO_TERMINOS
    TipoLegal.ELIMINACION -> TEXTO_ELIMINACION
}

/**
 * Política de privacidad.
 *
 * Colección de enlaces no tiene cuentas, no recoge datos
 * personales, no usa nube ni anuncios, y todo lo que guardas queda
 * en tu teléfono.
 */
private const val TEXTO_PRIVACIDAD = """
Colección de enlaces no tiene cuentas. No te pedimos nombre, correo, teléfono ni contraseña, y no hay ningún servidor nuestro donde registrarse: la aplicación funciona entera en tu teléfono.

QUÉ DATOS RECOGE LA APLICACIÓN

Ninguno. No recoge datos personales, no usa analítica, no muestra anuncios y no incluye rastreadores de terceros. No hay nada que enviar a ningún sitio porque no hay ningún sitio al que enviarlo.

QUÉ DATOS GUARDA EN TU TELÉFONO

Solo lo que tú guardas: los enlaces (su dirección, su título, su descripción y su miniatura), tus carpetas y tus favoritos, y tus ajustes (el tema claro u oscuro y si ya viste la pista del menú). Todo queda en la base de datos local del teléfono. Nada sale de él salvo lo descrito en el punto siguiente.

CONEXIONES A INTERNET

Solo cuando guardas un enlace, la aplicación pide la página de ese enlace para leer su vista previa (su título, su descripción y su imagen) y, si es un vídeo de YouTube, TikTok, Facebook, Instagram, X, Vimeo o Dailymotion, pide el reproductor oficial de esa plataforma para mostrar su miniatura. En esas peticiones solo viaja la dirección del enlace que tú guardas; no se envía tu nombre, tu correo, tu ubicación ni ningún otro dato personal. Las miniaturas se descargan y se guardan en el teléfono, no se suben a ningún lado.

PERMISOS

La huella solo se usa para desbloquear tus carpetas: tus datos biométricos nunca salen del teléfono y la aplicación no los ve ni los guarda. La aplicación no pide acceso a tus contactos, a tu ubicación, a tu micrófono ni a tu cámara.

MENORES DE EDAD

La aplicación no recoge datos personales de nadie, tampoco de menores de edad.

CAMBIOS EN ESTA POLÍTICA

Esta es la única versión de la aplicación y no recibe actualizaciones, así que esta política no cambia. Si algún día hubiera una versión nueva con cambios, se avisaría dentro de la aplicación.

CONTACTO

No hay ninguna cuenta ni servidor al que escribir, porque no hay nada que registrar ni que enviar: todo funciona en tu teléfono.
"""

/**
 * Acuerdo del usuario (términos de uso).
 *
 * Reglas de uso de Colección de enlaces: tus enlaces son tuyos,
 * no hay copia de seguridad en la nube y la aplicación se ofrece
 * tal cual.
 */
private const val TEXTO_TERMINOS = """
QUÉ ES COLECCIÓN DE ENLACES

Una aplicación para guardar tus enlaces en tu teléfono, ordenarlos en carpetas y encontrarlos después. Funciona sin cuentas, sin nube y sin anuncios.

TUS ENLACES SON TUYOS

Lo que guardas es responsabilidad tuya. No guardes enlaces a contenido ilegal ni que infrinja derechos de otras personas. La aplicación no revisa ni modera tus enlaces: no los ve nadie más que tú.

SIN COPIA DE SEGURIDAD

Tus enlaces solo existen en tu teléfono. Si desinstalas la aplicación o borras sus datos, se pierden para siempre: no hay ninguna copia en un servidor ni forma de recuperarlos.

VISTAS PREVIAS

La miniatura y el título de un enlace dependen de la página de cada sitio. Algunos sitios, como Facebook, no siempre dejan leer la vista previa; en ese caso la aplicación guarda el enlace igual, con su título automático o con el aviso "Sin vista previa". La aplicación se ofrece tal cual, sin garantía de que cada sitio responda siempre.

USO ACEPTABLE

Usa la aplicación solo para fines legales. No la uses para engañar, acosar ni dañar a nadie.

CAMBIOS

Esta es la única versión de la aplicación y no recibe actualizaciones, así que este acuerdo no cambia.
"""

/**
 * Eliminación de datos.
 *
 * Tus datos solo están en tu teléfono: no hay ninguna copia en un
 * servidor ni en la nube, así que borrarlos del teléfono es
 * borrarlos del todo.
 */
private const val TEXTO_ELIMINACION = """
Tus datos solo están en tu teléfono: no hay ninguna copia en un servidor ni en la nube, así que borrarlos del teléfono es borrarlos del todo.

BORRAR UN ENLACE

Mantén pulsado el enlace y elige Borrar. Se borra también su miniatura guardada.

BORRAR UNA CARPETA

Abre la gestión de la carpeta y elige Borrar. Sus enlaces no se pierden: pasan a la carpeta "Otros".

BORRAR TODOS LOS DATOS

En los ajustes de Android, ve a Aplicaciones, busca Colección de enlaces, entra en Almacenamiento y toca "Borrar todos los datos". Esto borra los enlaces, las carpetas, las miniaturas y los ajustes de la aplicación.

DESINSTALAR LA APLICACIÓN

Al desinstalarla se borra todo lo guardado con ella, porque no hay ninguna copia fuera del teléfono.

SIN VUELTA ATRÁS

Una vez borrados, los datos no se pueden recuperar. No hay copia de seguridad en la nube ni forma de restaurarlos.
"""
