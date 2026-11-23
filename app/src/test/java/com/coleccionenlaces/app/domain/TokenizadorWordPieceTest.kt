package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de [TokenizadorWordPiece] (Fix 3): tokenización
 * BERT en Kotlin puro. JVM pura, con un vocabulario pequeño de
 * juguete: lo que se comprueba es el algoritmo (especiales, máscara,
 * acolchado, subtoken más largo, desconocidas), no el vocabulario
 * real de DistilBERT (eso lo comprueba ClasificadorModeloTest con el
 * vocabulario de los assets).
 */
class TokenizadorWordPieceTest {

    private val vocabulario = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]",
        "hola", "mun", "##do", "ia", "tecnologia", ",", ".",
    )

    private val tokenizador = TokenizadorWordPiece(vocabulario)

    @Test
    fun `codificar anade especiales y acolcha con PAD`() {
        val entrada = tokenizador.codificar("hola ia", 8)
        // [CLS] hola ia [SEP] [PAD] [PAD] [PAD] [PAD]
        assertEquals(listOf(2, 5, 8, 3, 0, 0, 0, 0), entrada.inputIds.toList())
        assertEquals(listOf(1, 1, 1, 1, 0, 0, 0, 0), entrada.attentionMask.toList())
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0), entrada.tokenTypeIds.toList())
    }

    @Test
    fun `codificar trunca a la longitud maxima`() {
        val entrada = tokenizador.codificar("hola hola hola hola hola", 4)
        assertEquals(4, entrada.inputIds.size)
        assertEquals(2, entrada.inputIds[0])
        assertEquals(3, entrada.inputIds[3])
    }

    @Test
    fun `una palabra se divide por el subtoken mas largo`() {
        // "mundo" no está en el vocabulario: mun + ##do.
        assertEquals(listOf(6, 7), tokenizador.tokenizarPalabra("mundo"))
    }

    @Test
    fun `una palabra sin subtoken valido es UNK`() {
        assertEquals(listOf(1), tokenizador.tokenizarPalabra("zzzqqq"))
    }

    @Test
    fun `una palabra de mas de 100 caracteres es UNK`() {
        assertEquals(listOf(1), tokenizador.tokenizarPalabra("a".repeat(101)))
    }

    @Test
    fun `dividirBasico separa puntuacion y quita acentos`() {
        assertEquals(
            listOf("hola", ",", "tecnologia", "."),
            tokenizador.dividirBasico("Hola, tecnología."),
        )
    }

    @Test
    fun `desdeLineas ignora lineas vacias y espacios`() {
        val tokenizador2 = TokenizadorWordPiece.desdeLineas(
            listOf("  [PAD]  ", "", "[UNK]", "  ", "[CLS]"),
        )
        assertEquals(0, tokenizador2.idPad)
        assertEquals(1, tokenizador2.idUnk)
        assertEquals(2, tokenizador2.idCls)
    }

    @Test
    fun `un texto vacio solo tiene especiales`() {
        val entrada = tokenizador.codificar("", 4)
        assertEquals(listOf(2, 3, 0, 0), entrada.inputIds.toList())
        assertEquals(listOf(1, 1, 0, 0), entrada.attentionMask.toList())
    }

    @Test
    fun `los ids especiales por defecto son los de BERT`() {
        val vacio = TokenizadorWordPiece(emptyList())
        assertEquals(100, vacio.idUnk)
        assertEquals(101, vacio.idCls)
        assertEquals(102, vacio.idSep)
        assertEquals(0, vacio.idPad)
    }
}
