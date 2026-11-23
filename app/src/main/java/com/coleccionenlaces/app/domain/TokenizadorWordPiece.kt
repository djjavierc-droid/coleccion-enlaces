package com.coleccionenlaces.app.domain

import java.text.Normalizer

/**
 * Tokenizador WordPiece (BERT) en Kotlin puro, sin Android.
 *
 * Replica el tokenizador de `distilbert-base-uncased` (el mismo que
 * se usó para entrenar el modelo): minúsculas, sin acentos, la
 * puntuación se separa como tokens propios y cada palabra se divide
 * por coincidencia codiciosa del subtoken más largo del vocabulario
 * (`##` marca la continuación). Una palabra de más de 100 caracteres
 * o sin ningún subtoken válido es `[UNK]`.
 *
 * No se usa NLClassifier: la librería de metadatos (tflite-support
 * 0.4.4) no tiene ruedas para Python 3.12 y Model Maker no se instala
 * en este entorno, así que el modelo no lleva metadatos de
 * tokenización (ver fix-3-resumen.md). La tokenización vive aquí y el
 * modelo se ejecuta con el intérprete base de LiteRT.
 *
 * El vocabulario se carga desde `assets/vocab.txt` (una palabra por
 * línea, en el orden del tokenizador original). [codificar] devuelve
 * las tres entradas que espera el modelo, ya acolchadas a
 * [longitudMaxima] con el token `[PAD]` (0).
 */
class TokenizadorWordPiece(
    /** Vocabulario en orden: la línea i es el token con id i. */
    vocabulario: List<String>,
) {
    private val idPorToken: Map<String, Int> =
        vocabulario.withIndex().associate { (indice, token) -> token to indice }

    /** Id de `[UNK]` (palabra desconocida). */
    val idUnk: Int = idPorToken["[UNK]"] ?: 100

    /** Id de `[CLS]` (inicio de secuencia). */
    val idCls: Int = idPorToken["[CLS]"] ?: 101

    /** Id de `[SEP]` (fin de secuencia). */
    val idSep: Int = idPorToken["[SEP]"] ?: 102

    /** Id de `[PAD]` (relleno). */
    val idPad: Int = idPorToken["[PAD]"] ?: 0

    /** Tres entradas del modelo para un texto. */
    data class EntradaTokenizada(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val tokenTypeIds: IntArray,
    )

    /**
     * Codifica un texto en las tres entradas del modelo.
     *
     * - Se añade `[CLS]` al inicio y `[SEP]` al final.
     * - Se trunca a [longitudMaxima] tokens (incluidos los especiales).
     * - El resto se rellena con `[PAD]` y su máscara en 0.
     */
    fun codificar(texto: String, longitudMaxima: Int = 64): EntradaTokenizada {
        val ids = ArrayList<Int>()
        ids.add(idCls)
        for (palabra in dividirBasico(texto)) {
            ids.addAll(tokenizarPalabra(palabra))
            if (ids.size >= longitudMaxima - 1) break
        }
        ids.add(idSep)
        val recortados = ids.take(longitudMaxima)
        val inputIds = IntArray(longitudMaxima) { idPad }
        val attentionMask = IntArray(longitudMaxima) { 0 }
        val tokenTypeIds = IntArray(longitudMaxima) { 0 }
        recortados.forEachIndexed { indice, id ->
            inputIds[indice] = id
            attentionMask[indice] = 1
        }
        return EntradaTokenizada(inputIds, attentionMask, tokenTypeIds)
    }

    /**
     * Tokeniza una sola palabra en ids WordPiece (sin especiales).
     * Palabra vacía -> lista vacía.
     */
    fun tokenizarPalabra(palabra: String): List<Int> {
        if (palabra.isEmpty()) return emptyList()
        if (palabra.length > 100) return listOf(idUnk)
        val ids = ArrayList<Int>()
        var inicio = 0
        while (inicio < palabra.length) {
            val fin = encontrarSubtokenMasLargo(palabra, inicio)
            if (fin == null) return listOf(idUnk)
            val subtoken = if (inicio == 0) {
                palabra.substring(inicio, fin)
            } else {
                "##" + palabra.substring(inicio, fin)
            }
            ids.add(idPorToken[subtoken] ?: return listOf(idUnk))
            inicio = fin
        }
        return ids
    }

    /** Fin (exclusivo) del subtoken más largo desde [inicio], o null si no hay. */
    private fun encontrarSubtokenMasLargo(palabra: String, inicio: Int): Int? {
        var fin = palabra.length
        while (fin > inicio) {
            val candidato = if (inicio == 0) {
                palabra.substring(inicio, fin)
            } else {
                "##" + palabra.substring(inicio, fin)
            }
            if (idPorToken.containsKey(candidato)) return fin
            fin--
        }
        return null
    }

    /**
     * Tokenización básica (la de BERT): minúsculas, sin acentos, la
     * puntuación se separa como tokens propios y el texto se divide
     * por espacios.
     */
    fun dividirBasico(texto: String): List<String> {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val minusculas = sinAcentos.lowercase()
        val resultado = ArrayList<String>()
        val actual = StringBuilder()
        fun vaciar() {
            if (actual.isNotEmpty()) {
                resultado.add(actual.toString())
                actual.clear()
            }
        }
        for (caracter in minusculas) {
            when {
                esControl(caracter) -> {
                    // Los caracteres de control se eliminan (como BERT).
                    vaciar()
                }
                caracter.isWhitespace() -> vaciar()
                esPuntuacion(caracter) -> {
                    vaciar()
                    resultado.add(caracter.toString())
                }
                else -> actual.append(caracter)
            }
        }
        vaciar()
        return resultado
    }

    /** Caracter de control (categorías Cc y Cf), salvo espacios en blanco. */
    private fun esControl(caracter: Char): Boolean {
        if (caracter.isWhitespace()) return false
        val tipo = Character.getType(caracter)
        return tipo == Character.CONTROL.toInt() ||
            tipo == Character.FORMAT.toInt()
    }

    /** Puntuación según la definición de BERT (ASCII + Unicode P/S). */
    private fun esPuntuacion(caracter: Char): Boolean {
        if (caracter in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~") return true
        val tipo = Character.getType(caracter)
        return tipo == Character.CONNECTOR_PUNCTUATION.toInt() ||
            tipo == Character.DASH_PUNCTUATION.toInt() ||
            tipo == Character.START_PUNCTUATION.toInt() ||
            tipo == Character.END_PUNCTUATION.toInt() ||
            tipo == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            tipo == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            tipo == Character.OTHER_PUNCTUATION.toInt()
    }

    companion object {
        /** Carga el vocabulario desde las líneas de `vocab.txt`. */
        fun desdeLineas(lineas: List<String>): TokenizadorWordPiece =
            TokenizadorWordPiece(lineas.map { it.trim() }.filter { it.isNotEmpty() })
    }
}
