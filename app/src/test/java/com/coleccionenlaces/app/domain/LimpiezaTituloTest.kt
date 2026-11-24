package com.coleccionenlaces.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias de [limpiarTextoParaClasificar] y
 * [construirTextoClasificacion] (ola 2, hilo I, limpieza estilo D).
 *
 * Los 3 casos principales usan los og:title CRUDOS exactos de la
 * investigación (`investigacion-ola2/I/resultados-consolidado.json`,
 * variante `a_crudo`) y exigen el texto limpio EXACTO de la variante
 * `i_limpieza_estilo_D`: si el puerto Kotlin no reproduce el script
 * de Python al carácter, estos tests lo cazan. Las confianzas
 * medidas con el .tflite real sobre los textos limpios (Yofer
 * Entretenimiento 0,643, TEC Tecnología 0,986, Free Fire Juegos
 * 0,670) se verifican en [ClasificadorModeloTest] con el modelo real.
 *
 * Los emojis se escriben con escapes Unicode explícitos (p. ej.
 * `\uD83E\uDD87` = 🦇) para que el archivo fuente no dependa de cómo
 * el editor guarde los caracteres fuera del BMP ni de las secuencias
 * ZWJ (❤️‍🔥 = U+2764 U+FE0F U+200D U+1F525).
 */
class LimpiezaTituloTest {

    // ---- Los 3 og:title de la investigación (crudo -> limpio) ----

    @Test
    fun `el og_title de Yofer se limpia estilo D exacto`() {
        val crudo = "\uD83E\uDD87\uD83D\uDD25 ¡¡¡50.000 VISITAS!!! " +
            "\uD83D\uDDA4\uD83E\uDD40 \u2764\uFE0F\u200D\uD83D\uDD25 " +
            "¡¡¡LO HEMOS CONSEGUIDO!!! «LÁGRIMAS DE MI SOLEDAD» sigue " +
            "conquistando corazones. ¡¡¡GRACIAS POR HACERLO POSIBLE!!! " +
            "\uD83D\uDE80\u267E\uFE0F ¡¡¡AHORA A POR LAS 60.000!!! " +
            "\uD83C\uDFAC YOFER – LÁGRIMAS DE MI SOLEDAD " +
            "\u25B6\uFE0F https://youtu.be/2SIT1qqNVEA #Yofer " +
            "#LágrimasDeMiSoledad #50k #Youtube #Spotify #itunes | Yofer"
        assertEquals(
            "¡¡¡50.000 VISITAS!!! ¡¡¡LO HEMOS CONSEGUIDO!!! LÁGRIMAS " +
                "DE MI SOLEDAD sigue conquistando corazones. ¡¡¡GRACIAS " +
                "POR HACERLO POSIBLE!!! ¡¡¡AHORA A POR LAS 60.000!!! " +
                "YOFER LÁGRIMAS DE MI SOLEDAD Yofer LágrimasDeMiSoledad " +
                "50k Youtube Spotify itunes",
            limpiarTextoParaClasificar(crudo),
        )
    }

    @Test
    fun `el og_title de TEC se limpia estilo D exacto`() {
        val crudo = "4.7K views · 990 reactions | \uD83D\uDCF1 La empresa " +
            "StepFun presentó el STEPX Neo, el primer smartphone creado " +
            "desde cero para trabajar con inteligencia artificial basada " +
            "en agentes, acompañado de un nuevo sistema operativo y un " +
            "asistente inteligente. \uD83E\uDD16 | TEC"
        assertEquals(
            "La empresa StepFun presentó el STEPX Neo, el primer " +
                "smartphone creado desde cero para trabajar con " +
                "inteligencia artificial basada en agentes, acompañado " +
                "de un nuevo sistema operativo y un asistente inteligente.",
            limpiarTextoParaClasificar(crudo),
        )
    }

    @Test
    fun `el og_title de Free Fire se limpia estilo D exacto`() {
        val crudo = "173K views · 13K reactions | A ustedes le ha pasado " +
            "lo mismo, Confirmen \uD83D\uDE02\uD83E\uDD23 #freefire " +
            "#freefirelatam #freefireedit #garenafreefire #reels #viral " +
            "#humor | El ingeniero FF"
        assertEquals(
            "A ustedes le ha pasado lo mismo, Confirmen freefire " +
                "freefirelatam freefireedit garenafreefire reels viral humor",
            limpiarTextoParaClasificar(crudo),
        )
    }

    // ---- Reglas sueltas de estilo D ----

    @Test
    fun `los signos de exclamacion se conservan`() {
        // Medido (hilo I): quitar los `!` hunde Yofer de 0,643 a
        // 0,477, bajo el umbral. La limpieza NO los toca.
        assertEquals("¡¡¡Vamos!!!", limpiarTextoParaClasificar("¡¡¡Vamos!!!"))
    }

    @Test
    fun `la almohadilla se quita pero la palabra se conserva`() {
        // Hallazgo crítico del hilo I: quitar los hashtags es lo
        // peor que se puede hacer (Free Fire: Juegos 0,670 con la
        // palabra, Entretenimiento 0,788 sin ella).
        assertEquals(
            "Mira Minecraft y Fortnite",
            limpiarTextoParaClasificar("Mira #Minecraft y #Fortnite"),
        )
    }

    @Test
    fun `las URLs se quitan`() {
        assertEquals(
            "Mira y sigue",
            limpiarTextoParaClasificar("Mira https://ejemplo.com/video y sigue"),
        )
    }

    @Test
    fun `el prefijo de views y reactions se quita`() {
        assertEquals(
            "El título",
            limpiarTextoParaClasificar("4.7K views · 990 reactions | El título"),
        )
        assertEquals(
            "El título",
            limpiarTextoParaClasificar("173k views · 13k reactions | El título"),
        )
    }

    @Test
    fun `el sufijo de autor se quita, solo el ultimo segmento`() {
        assertEquals(
            "El título",
            limpiarTextoParaClasificar("El título | Autor"),
        )
        // Solo se quita el último `| xxx`: con dos, queda uno.
        assertEquals(
            "A | B",
            limpiarTextoParaClasificar("A | B | C"),
        )
    }

    @Test
    fun `las comillas angulares se quitan y los guiones pasan a espacio`() {
        assertEquals("Hola", limpiarTextoParaClasificar("«Hola»"))
        assertEquals("A B C", limpiarTextoParaClasificar("A – B — C"))
    }

    @Test
    fun `una secuencia ZWJ con selector de variacion se elimina entera`() {
        // ❤️‍🔥 = U+2764 U+FE0F U+200D U+1F525: los cuatro puntos son
        // emoji según EMOJI_RE; no debe quedar ni el corazón.
        assertEquals("", limpiarTextoParaClasificar("\u2764\uFE0F\u200D\uD83D\uDD25"))
        assertEquals("Vamos", limpiarTextoParaClasificar("\u2764\uFE0F\u200D\uD83D\uDD25 Vamos"))
    }

    @Test
    fun `los saltos de linea y espacios multiples colapsan`() {
        assertEquals("A B", limpiarTextoParaClasificar("A\n\nB"))
        assertEquals("A B", limpiarTextoParaClasificar("  A   B  "))
        assertEquals("", limpiarTextoParaClasificar(""))
        assertEquals("", limpiarTextoParaClasificar("   "))
    }

    // ---- Construcción de la entrada del motor ----

    @Test
    fun `una descripcion redundante no se anade`() {
        // Free Fire: la og:description es el mismo cuerpo del
        // og:title. Añadirla duplicaba el texto y tumbaba la
        // predicción (Juegos 0,670 -> Otros 0,453, medido).
        val titulo = "173K views · 13K reactions | A ustedes le ha pasado " +
            "lo mismo, Confirmen \uD83D\uDE02\uD83E\uDD23 #freefire " +
            "#freefirelatam #freefireedit #garenafreefire #reels #viral " +
            "#humor | El ingeniero FF"
        val descripcion = "A ustedes le ha pasado lo mismo, Confirmen " +
            "\uD83D\uDE02\uD83E\uDD23 #freefire #freefirelatam " +
            "#freefireedit #garenafreefire #reels #viral #humor"
        assertEquals(
            "A ustedes le ha pasado lo mismo, Confirmen freefire " +
                "freefirelatam freefireedit garenafreefire reels viral humor",
            construirTextoClasificacion(titulo, descripcion),
        )
    }

    @Test
    fun `una descripcion con palabras nuevas se conserva`() {
        // EterniCodIA: la descripción añade palabras ("hoy", "hemos")
        // que no están en el título: se mantiene (Tecnología 0,971).
        assertEquals(
            "Contratamos a una IA... y el primer día ya vendía croquetas " +
                "en Marte... Hoy hemos contratado a una",
            construirTextoClasificacion(
                "Contratamos a una IA... y el primer día ya vendía croquetas en Marte...",
                "Hoy hemos contratado a una",
            ),
        )
    }

    @Test
    fun `una descripcion que es subconjunto exacto no se anade`() {
        assertEquals(
            "Gameplay de Minecraft",
            construirTextoClasificacion("Gameplay de Minecraft", "Minecraft"),
        )
    }

    @Test
    fun `una descripcion parcialmente nueva se conserva entera`() {
        // "en", "15" y "minutos" no están en el título: la
        // descripción aporta señal y no se recorta.
        assertEquals(
            "Receta de pasta casera Receta de pasta casera en 15 minutos",
            construirTextoClasificacion(
                "Receta de pasta casera",
                "Receta de pasta casera en 15 minutos",
            ),
        )
    }

    @Test
    fun `titulo o descripcion en blanco`() {
        assertEquals("El título", construirTextoClasificacion("El título", ""))
        assertEquals("La descripción", construirTextoClasificacion("", "La descripción"))
        assertEquals("", construirTextoClasificacion("", ""))
        assertEquals("", construirTextoClasificacion("   ", "  "))
    }
}
