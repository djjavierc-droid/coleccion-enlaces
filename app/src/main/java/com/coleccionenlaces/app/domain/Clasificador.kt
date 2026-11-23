package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.data.CategoriasPredefinidas
import com.coleccionenlaces.app.data.Enlace
import java.net.URI
import java.text.Normalizer

/**
 * Clasificación automática de enlaces (Fase 4, sección 4 del plan).
 *
 * Función pura y local: no toca la red ni la base de datos. Dadas el
 * título, la descripción y la URL de un enlace, devuelve el NOMBRE de
 * una de las 17 categorías predefinidas.
 *
 * ## Decisión: se devuelve el nombre, no el id
 * [clasificar] devuelve un `String` con el nombre exacto de
 * [CategoriasPredefinidas] (o `"Otros"`). El repositorio (Fase 5)
 * resuelve ese nombre a `categoriaId` con `CategoriaDao.obtenerPorNombre`.
 * Así el clasificador no depende de Room ni de los ids de la base, y
 * sigue siendo una función pura comprobable sin Android. No se crean
 * categorías automáticamente: el resultado es siempre una de las 17
 * predefinidas, nunca un nombre nuevo.
 *
 * ## Sistema de puntaje
 * - Cada palabra o frase clave distinta que aparece en el título o en
 *   la descripción suma [PUNTOS_POR_PALABRA_CLAVE] puntos a su
 *   categoría. Una misma frase cuenta una sola vez aunque se repita.
 * - El dominio de la URL suma [PUNTOS_POR_DOMINIO] puntos, pero SOLO
 *   para dominios claramente temáticos (ver
 *   [PalabrasClave.dominiosPorCategoria] y `XxxKeywords.dominios`):
 *   un dominio genérico no suma nada.
 * - Gana la categoría con más puntos. En caso de empate gana la
 *   primera en el orden de [CategoriasPredefinidas.lista] (determinista).
 * - Si ninguna categoría alcanza [UMBRAL_MINIMO], el resultado es
 *   `"Otros"`.
 *
 * ## Valores
 * - [PUNTOS_POR_PALABRA_CLAVE] = 2: una sola palabra clave ya
 *   clasifica (el umbral es 2).
 * - [PUNTOS_POR_DOMINIO] = 4: un dominio temático clasifica por sí
 *   solo y pesa el doble que una palabra, pero no decide por encima
 *   de dos o más palabras de otra categoría.
 * - [UMBRAL_MINIMO] = 2.
 *
 * ## Normalización
 * El texto se pasa a minúsculas, se le quitan los acentos y la
 * puntuación se convierte en espacios, y la coincidencia exige la
 * frase completa entre límites de palabra: "moto" no coincide con
 * "motocicleta" ni "cine" con "medicina".
 *
 * ## Prioridad manual
 * [clasificarEnlace] aplica la regla de la especificación: si el
 * enlace tiene `categoriaManual = true`, devuelve `null` y la
 * clasificación NO debe sobrescribir su categoría. El repositorio
 * (Fase 5) solo aplica el resultado cuando no es `null`.
 */
object Clasificador {

    /** Puntos por cada palabra o frase clave distinta coincidente. */
    const val PUNTOS_POR_PALABRA_CLAVE = 2

    /** Puntos por un dominio de URL claramente temático. */
    const val PUNTOS_POR_DOMINIO = 4

    /** Puntaje mínimo para clasificar en una categoría; si no, "Otros". */
    const val UMBRAL_MINIMO = 2

    /** Nombre de la categoría de respaldo (siempre existe). */
    const val OTROS = "Otros"

    /** Nombre de la categoría sensible (su lista vive en `XxxKeywords`). */
    const val NOMBRE_XXX = "Contenido XXX"

    /**
     * Clasifica un enlace y devuelve el nombre de la categoría.
     * Nunca devuelve null: sin coincidencias suficientes, `"Otros"`.
     */
    fun clasificar(titulo: String, descripcion: String, url: String): String {
        val puntajes = puntajes(titulo, descripcion, url)
        val mejor = puntajes.maxByOrNull { it.value } ?: return OTROS
        if (mejor.value < UMBRAL_MINIMO) return OTROS
        // Desempate determinista: en igualdad de puntos gana la primera
        // categoría en el orden de las predefinidas.
        val maximo = mejor.value
        val orden = CategoriasPredefinidas.lista.map { it.nombre }
        return orden.first { (puntajes[it] ?: 0) == maximo }
    }

    /**
     * Puntaje de cada categoría para un enlace. Solo con fines de
     * diagnóstico y pruebas: el orden de las claves sigue el de las
     * predefinidas y "Otros" siempre vale 0.
     */
    fun puntajes(titulo: String, descripcion: String, url: String): Map<String, Int> {
        val texto = normalizar("$titulo $descripcion")
        val textoAcolchado = " $texto "
        val host = extraerHost(url)
        val puntajes = LinkedHashMap<String, Int>()
        for (semilla in CategoriasPredefinidas.lista) {
            val nombre = semilla.nombre
            if (nombre == OTROS) {
                puntajes[nombre] = 0
                continue
            }
            var puntos = 0
            for (palabra in palabrasClaveDe(nombre)) {
                if (textoAcolchado.contains(" ${normalizar(palabra)} ")) {
                    puntos += PUNTOS_POR_PALABRA_CLAVE
                }
            }
            if (host != null) {
                for (dominio in dominiosDe(nombre)) {
                    if (host == dominio || host.endsWith(".$dominio")) {
                        puntos += PUNTOS_POR_DOMINIO
                        break
                    }
                }
            }
            puntajes[nombre] = puntos
        }
        return puntajes
    }

    /**
     * Categoría a aplicar a un enlace guardado.
     *
     * - Si `enlace.categoriaManual` es true, devuelve `null`: la
     *   elección manual tiene prioridad y NO se sobrescribe.
     * - Si es false, devuelve el nombre que da [clasificar] con el
     *   título, la descripción y la URL del enlace.
     *
     * El repositorio (Fase 5) resuelve el nombre a `categoriaId` solo
     * cuando el resultado no es `null`.
     */
    fun clasificarEnlace(enlace: Enlace): String? {
        if (enlace.categoriaManual) return null
        return clasificar(enlace.titulo, enlace.descripcion, enlace.url)
    }

    /** Palabras clave de una categoría (la sensible, desde `XxxKeywords`). */
    internal fun palabrasClaveDe(nombreCategoria: String): List<String> =
        if (nombreCategoria == NOMBRE_XXX) {
            XxxKeywords.palabrasClave
        } else {
            PalabrasClave.porCategoria[nombreCategoria].orEmpty()
        }

    /** Dominios temáticos de una categoría (la sensible, desde `XxxKeywords`). */
    internal fun dominiosDe(nombreCategoria: String): List<String> =
        if (nombreCategoria == NOMBRE_XXX) {
            XxxKeywords.dominios
        } else {
            PalabrasClave.dominiosPorCategoria[nombreCategoria].orEmpty()
        }

    /**
     * Minúsculas, sin acentos y con la puntuación convertida en
     * espacios (los espacios múltiples se colapsan). Así "ALBAÑILERÍA"
     * queda como "albanileria" y coincide con la palabra sin acento.
     */
    internal fun normalizar(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sinAcentos.lowercase()
            .replace(Regex("[^a-z0-9ñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Host en minúsculas de una URL, sin el `www.` inicial. Devuelve
     * null si la URL no tiene host válido.
     */
    internal fun extraerHost(url: String): String? {
        val texto = url.trim().lowercase()
        if (texto.isEmpty()) return null
        val host = try {
            URI(texto).host
        } catch (_: Exception) {
            null
        } ?: texto.substringAfter("://", "").substringBefore("/").substringBefore("?")
        if (host.isBlank()) return null
        return host.removePrefix("www.")
    }
}
