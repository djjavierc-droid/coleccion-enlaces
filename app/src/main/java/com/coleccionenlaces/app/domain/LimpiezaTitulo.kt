package com.coleccionenlaces.app.domain

/**
 * Limpieza estilo D del texto antes de clasificar (ola 2, hilo I).
 *
 * La señal principal de clasificación es el og:title COMPLETO en una
 * sola entrada, limpio con la variante (i) del hilo I — la única con
 * 3/3 aciertos medidos con el .tflite real (Yofer 0,532→0,643,
 * TEC 0,984→0,986, Free Fire 0,468→Otros pasa a Juegos 0,670).
 * El modelo es 100 % determinista; la inestabilidad entre hilos era
 * sensibilidad al texto exacto de entrada, no aleatoriedad.
 *
 * Reglas exactas (reproducción de
 * `investigacion-ola2/I/clasificar_variantes.py::limpieza_estilo_D`,
 * en el mismo orden):
 * 1. Quitar emojis (los mismos rangos Unicode del script).
 * 2. `#palabra` → `palabra`: la almohadilla se quita pero la palabra
 *    SE CONSERVA. Hallazgo crítico del hilo I: quitar los hashtags
 *    es lo peor que se puede hacer — en Free Fire llevan la señal de
 *    Juegos (quitarlos convierte un casi-acierto en fallo con
 *    confianza alta: Entretenimiento 0,788).
 * 3. Quitar URLs.
 * 4. Quitar el prefijo `N views · N reactions |`.
 * 5. Quitar el sufijo `| Autor`.
 * 6. Quitar `«»“”`; `–` y `—` → espacio.
 * 7. Colapsar espacios.
 *
 * Lo que NO se quita (medido): los signos `!` — quitarlos hunde
 * Yofer de 0,643 a 0,477 (bajo el umbral).
 *
 * La limpieza se aplica en el motor ([ClasificadorInteligente]), no
 * al guardar: el título guardado y visible sigue siendo el original.
 */
fun limpiarTextoParaClasificar(texto: String): String {
    var limpio = sinEmojis(texto)
    limpio = HASHTAG_PALABRA_REGEX.replace(limpio, "$1")
    limpio = URL_REGEX.replace(limpio, "")
    limpio = PREFIJO_VIEWS_REGEX.replace(limpio, "")
    limpio = SUFIJO_AUTOR_REGEX.replace(limpio, "")
    limpio = limpio.replace("«", "").replace("»", "")
        .replace("“", "").replace("”", "")
    limpio = limpio.replace("–", " ").replace("—", " ")
    return colapsarEspacios(limpio)
}

/**
 * Texto que alimenta al motor a partir de [titulo] y [descripcion],
 * ya limpios (estilo D). La descripción se añade solo si aporta
 * alguna palabra nueva: la `og:description` de Facebook suele ser
 * un duplicado del cuerpo del `og:title`, y duplicar el texto cambia
 * la predicción del modelo (medido con el .tflite real: Free Fire,
 * título solo → Juegos 0,670; título + descripción duplicada →
 * Otros 0,453). Si la descripción no es redundante se conserva
 * (EterniCodIA: título + descripción → Tecnología 0,971).
 *
 * La comparación usa las mismas palabras normalizadas que la cabeza
 * aprendida ([Clasificador.normalizar]): una descripción redundante
 * tampoco cambia nada para la cabeza (sus palabras ya están en el
 * título), así que la regla es neutra para el aprendizaje.
 */
fun construirTextoClasificacion(titulo: String, descripcion: String): String {
    val tituloLimpio = limpiarTextoParaClasificar(titulo)
    val descripcionLimpia = limpiarTextoParaClasificar(descripcion)
    if (descripcionLimpia.isBlank()) return tituloLimpio
    if (tituloLimpio.isBlank()) return descripcionLimpia
    val palabrasTitulo = palabrasContenido(tituloLimpio)
    val palabrasDescripcion = palabrasContenido(descripcionLimpia)
    if (palabrasDescripcion.all { it in palabrasTitulo }) return tituloLimpio
    return "$tituloLimpio $descripcionLimpia"
}

/** Palabras de contenido de [texto] (normalizadas, sin duplicados). */
private fun palabrasContenido(texto: String): Set<String> =
    Clasificador.normalizar(texto).split(" ").filter { it.isNotBlank() }.toSet()

/** Quita los emojis y colapsa los espacios (paso 1 de estilo D). */
private fun sinEmojis(texto: String): String {
    val builder = StringBuilder(texto.length)
    texto.codePoints().forEach { punto ->
        if (!esEmoji(punto)) builder.appendCodePoint(punto)
    }
    return colapsarEspacios(builder.toString())
}

/**
 * True si [punto] (punto de código Unicode) es un emoji según los
 * rangos exactos de `EMOJI_RE` en `clasificar_variantes.py`. Se
 * recorre por puntos de código (no por chars UTF-16) para que los
 * emojis fuera del BMP (U+1F000–U+1FAFF) se traten como uno solo,
 * igual que en Python. Incluye el selector de variación U+FE0F y el
 * conector U+200D, así que las secuencias ZWJ (p. ej. ❤️‍🔥) se
 * eliminan enteras.
 */
private fun esEmoji(punto: Int): Boolean {
    return punto in 0x1F000..0x1FAFF ||
        punto in 0x2600..0x27BF ||
        punto in 0x2B00..0x2BFF ||
        punto in 0x2190..0x21FF ||
        punto in 0x2300..0x23FF ||
        punto in 0x25A0..0x25FF ||
        punto == 0x00A9 || punto == 0x00AE ||
        punto == 0x203C || punto == 0x2049 ||
        punto == 0x3030 || punto == 0x303D ||
        punto == 0x3297 || punto == 0x3299 ||
        punto == 0xFE0F || punto == 0x200D
}

/** `#palabra` → `palabra` (la palabra se conserva). */
private val HASHTAG_PALABRA_REGEX = Regex("#(\\S+)")

/** URLs `http(s)://...` hasta el siguiente espacio. */
private val URL_REGEX = Regex("https?://\\S+")

/**
 * Prefijo `N views · N reactions |` al inicio (p. ej. "4.7K views ·
 * 990 reactions |"): el `·` es U+00B7. Insensible a mayúsculas,
 * como `re.I` en el script.
 */
private val PREFIJO_VIEWS_REGEX = Regex(
    "^\\s*[\\d.,]+[KM]?\\s+views\\s*·\\s*[\\d.,]+[KM]?\\s+reactions\\s*\\|\\s*",
    RegexOption.IGNORE_CASE,
)

/** Sufijo `| Autor` al final (el último segmento tras `|`). */
private val SUFIJO_AUTOR_REGEX = Regex("\\s*\\|\\s*[^|]+$")

/** Varios espacios (incluidos saltos de línea) → uno solo, sin bordes. */
private fun colapsarEspacios(texto: String): String =
    texto.replace(Regex("\\s+"), " ").trim()
