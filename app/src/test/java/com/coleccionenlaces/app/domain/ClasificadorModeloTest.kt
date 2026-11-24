package com.coleccionenlaces.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tensorflow.lite.TensorFlowLite

/**
 * Pruebas de [ClasificadorModelo] con el .tflite REAL (Fix 3,
 * DistilBERT, cuantización de rango dinámico): corre en
 * testDebugUnitTest con Robolectric, que aporta el Context para leer
 * los assets y el SQLite que Room necesita.
 *
 * - El caso EterniCodIA DEBE clasificar en "Tecnología y celulares"
 *   con confianza >= 0,5 (el umbral del motor).
 * - El tokenizador Kotlin debe producir los mismos ids que el
 *   tokenizador de Python con el que se entrenó el modelo.
 *
 * LÍMITE HONESTO — la .so de LiteRT NO carga en la JVM de escritorio
 * (medido 2026-11-23): `libtensorflowlite_jni.so` del AAR está
 * enlazada para Android (NEEDED: libm.so, libdl.so, liblog.so,
 * libc.so sin versión) y en Linux `libm.so` es un script de enlace
 * ("invalid ELF header") y `liblog.so` no existe. La carga directa
 * en una JVM Linux falla con UnsatisfiedLinkError aunque la .so esté
 * en java.library.path (probado con `java` directo). Por eso las
 * pruebas que construyen el intérprete usan [modeloDisponible]: si
 * la carga nativa falla, la prueba se SALTA (JUnit Assume), no falla.
 * En un teléfono real (o emulador) la .so sí carga y las pruebas
 * corren de verdad. La conducta del modelo está verificada en Python
 * con los MISMOS bytes del .tflite (convertir.py: EterniCodIA 0,873,
 * validación 0,931) y el tokenizador Kotlin se verifica aquí contra
 * los ids de Python sin necesitar la .so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClasificadorModeloTest {

    private val contexto: Context
        get() = ApplicationProvider.getApplicationContext()

    private val textoEterniCodIA =
        "Contratamos a una IA... y el primer día ya vendía croquetas en Marte... " +
            "Hoy hemos contratado a una"

    /** Ids del tokenizador de Python para el texto EterniCodIA. */
    private val idsEterniCodIA = listOf(
        101, 24528, 15464, 2891, 1037, 14477, 24264, 1012, 1012, 1012,
        1061, 3449, 3539, 2099, 22939, 8038, 2310, 16089, 2050, 13675,
        2080, 12647, 3022, 4372, 20481, 2063, 1012, 1012, 1012, 7570,
        2100, 19610, 2891, 24528, 17713, 2080, 1037, 14477, 102,
    )

    /**
     * ¿Carga la .so de LiteRT en esta JVM? `TensorFlowLite.init()`
     * carga la .so (o relanza el error guardado si la carga falló).
     * El bloque estático de TensorFlowLite NO lanza: intenta la carga
     * y guarda la excepción en LOAD_LIBRARY_EXCEPTION; es `init()`
     * quien la relanza como UnsatisfiedLinkError ("Failed to load
     * native TensorFlow Lite methods..."). Por eso un simple
     * Class.forName NO sirve como sondeo (verificado en el bytecode
     * de litert-api-1.0.1.aar).
     */
    private fun cargaNativaDisponible(): Boolean {
        return try {
            TensorFlowLite.init()
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: ExceptionInInitializerError) {
            false
        }
    }

    /**
     * Construye el modelo o SALTA la prueba si la librería nativa no
     * carga en esta JVM (ver el comentario de la clase). En un
     * teléfono o emulador la .so carga y la prueba corre de verdad.
     */
    private fun modeloDisponible(): ClasificadorModelo {
        Assume.assumeTrue(
            "LiteRT nativo no carga en esta JVM",
            cargaNativaDisponible(),
        )
        return ClasificadorModelo(contexto)
    }

    @Test
    fun `el modelo y sus assets cargan`() {
        val modelo = modeloDisponible()
        assertTrue(modelo.estaDisponible)
        assertEquals(ClasificadorModelo.NUM_CLASES, modelo.etiquetas.size)
        assertTrue(modelo.etiquetas.contains("Tecnología y celulares"))
        modelo.cerrar()
    }

    @Test
    fun `el video de EterniCodIA clasifica en Tecnologia`() {
        val modelo = modeloDisponible()
        val resultado = modelo.clasificar(textoEterniCodIA)
        assertNotNull(resultado)
        assertEquals("Tecnología y celulares", resultado!!.categoria)
        assertTrue(
            "confianza ${resultado.confianza} < 0,5",
            resultado.confianza >= 0.5f,
        )
        modelo.cerrar()
    }

    @Test
    fun `el texto OCR del video de Soylorainegodoy clasifica en Entretenimiento`() {
        // Fix 5, caso real de Javier: el vídeo no tiene título ni
        // descripción; el texto quemado en el vídeo (OCR) es la única
        // señal. Necesita el modelo REENTRENADO con prosa reflexiva
        // (Fix 5): con el modelo de la v1.0.3 daba "Hazlo tú mismo
        // (DIY)" 0,445 -> "Otros" (medido con el .tflite real).
        val modelo = modeloDisponible()
        val textoOcr =
            "Y casi se me saltan las lágrimas cuando alguien dijo: " +
                "Llega un momento en el que tienes la misma edad que tenían " +
                "tus padres en los recuerdos de tu infancia. Y, aun así, tú " +
                "sigues sin sentirte una persona adulta. Entonces te das " +
                "cuenta de que tus padres también eran solo unos niños " +
                "intentando descubrir cómo funciona la vida."
        val resultado = modelo.clasificar(textoOcr)
        assertNotNull(resultado)
        assertEquals("Entretenimiento", resultado!!.categoria)
        assertTrue(
            "confianza ${resultado.confianza} < 0,5",
            resultado.confianza >= 0.5f,
        )
        modelo.cerrar()
    }

    @Test
    fun `un video de juego sin titulo clasifica en Juegos`() {
        // Fix 5: el texto que trae un vídeo de juego (título o OCR)
        // debe ir a Juegos, no a Tecnología (bug 2 de Fix 4).
        val modelo = modeloDisponible()
        val resultado = modelo.clasificar("Nuevo juego para celular gratis")
        assertNotNull(resultado)
        assertEquals("Juegos", resultado!!.categoria)
        assertTrue(
            "confianza ${resultado.confianza} < 0,5",
            resultado.confianza >= 0.5f,
        )
        modelo.cerrar()
    }

    @Test
    fun `un texto generico no alcanza el umbral del motor`() {
        val modelo = modeloDisponible()
        // "No te lo vas a creer": medido en Python con el .tflite
        // final -> "Viajes" con confianza 0,174 (< 0,5), así que el
        // motor lo manda a "Otros".
        // NOTA: "Mira esto" NO sirve aquí: el modelo le da 0,729 a
        // "Viajes" (sobreconfianza en texto sin tema; ver la tabla
        // de umbrales en fix-3-resumen.md). No es un fallo del
        // tokenizador: es el clasificador softmax siendo seguro de
        // algo que no sabe.
        val resultado = modelo.clasificar("No te lo vas a creer")
        // El modelo siempre responde una categoría; lo que importa es
        // que la confianza sea baja para que el motor lo mande a
        // "Otros" (umbral 0,5).
        assertNotNull(resultado)
        assertTrue(
            "confianza ${resultado!!.confianza} >= 0,5",
            resultado.confianza < 0.5f,
        )
        modelo.cerrar()
    }

    @Test
    fun `el tokenizador Kotlin iguala al de Python`() {
        val lineas = contexto.assets.open("vocab.txt").bufferedReader().use {
            it.readLines()
        }
        assertTrue(lineas.size >= 30000)
        val tokenizador = TokenizadorWordPiece.desdeLineas(lineas)
        val entrada = tokenizador.codificar(textoEterniCodIA, 64)
        val idsReales = entrada.inputIds.take(idsEterniCodIA.size)
        assertEquals(idsEterniCodIA, idsReales)
        // El resto es relleno con [PAD] y máscara en 0.
        for (i in idsEterniCodIA.size until 64) {
            assertEquals(0, entrada.inputIds[i])
            assertEquals(0, entrada.attentionMask[i])
        }
        for (i in idsEterniCodIA.indices) {
            assertEquals(1, entrada.attentionMask[i])
        }
    }

    @Test
    fun `un texto en blanco devuelve null`() {
        val modelo = modeloDisponible()
        assertEquals(null, modelo.clasificar("   "))
        modelo.cerrar()
    }

    // ---- Señal principal: og:title limpio estilo D (ola 2, hilo I) ----

    /**
     * Motor completo (modelo + cabeza vacía) sobre el og:title CRUDO
     * y la og:description CRUDA: la limpieza estilo D y la
     * deduplicación de la descripción redundante las hace el motor.
     * Free Fire es el caso que solo funciona limpio: crudo va a
     * "Otros" (0,468) y título + descripción duplicada también
     * (Otros 0,453); con la limpieza y sin duplicar, Juegos 0,670
     * (medido con el .tflite real, /tmp/verificar-estilo-D.py).
     */
    @Test
    fun `el og_title de Free Fire con su descripcion clasifica en Juegos por el motor`() {
        val modelo = modeloDisponible()
        val motor = ClasificadorInteligente(
            modelo,
            CabezaAprendizaje(modelo.etiquetas),
            modelo.etiquetas,
        )
        val titulo = "173K views · 13K reactions | A ustedes le ha pasado " +
            "lo mismo, Confirmen \uD83D\uDE02\uD83E\uDD23 #freefire " +
            "#freefirelatam #freefireedit #garenafreefire #reels #viral " +
            "#humor | El ingeniero FF"
        val descripcion = "A ustedes le ha pasado lo mismo, Confirmen " +
            "\uD83D\uDE02\uD83E\uDD23 #freefire #freefirelatam " +
            "#freefireedit #garenafreefire #reels #viral #humor"
        assertEquals(
            "Juegos",
            motor.clasificar(
                titulo,
                descripcion,
                "https://www.facebook.com/reel/914666554561073/",
            ),
        )
        modelo.cerrar()
    }

    @Test
    fun `el og_title de Yofer con su descripcion clasifica en Entretenimiento por el motor`() {
        val modelo = modeloDisponible()
        val motor = ClasificadorInteligente(
            modelo,
            CabezaAprendizaje(modelo.etiquetas),
            modelo.etiquetas,
        )
        val titulo = "\uD83E\uDD87\uD83D\uDD25 ¡¡¡50.000 VISITAS!!! " +
            "\uD83D\uDDA4\uD83E\uDD40 \u2764\uFE0F\u200D\uD83D\uDD25 " +
            "¡¡¡LO HEMOS CONSEGUIDO!!! «LÁGRIMAS DE MI SOLEDAD» sigue " +
            "conquistando corazones. ¡¡¡GRACIAS POR HACERLO POSIBLE!!! " +
            "\uD83D\uDE80\u267E\uFE0F ¡¡¡AHORA A POR LAS 60.000!!! " +
            "\uD83C\uDFAC YOFER – LÁGRIMAS DE MI SOLEDAD " +
            "\u25B6\uFE0F https://youtu.be/2SIT1qqNVEA #Yofer " +
            "#LágrimasDeMiSoledad #50k #Youtube #Spotify #itunes | Yofer"
        val descripcion = "\uD83E\uDD87\uD83D\uDD25 ¡¡¡50.000 VISITAS!!! " +
            "\uD83D\uDDA4\uD83E\uDD40\n\n\u2764\uFE0F\u200D\uD83D\uDD25 " +
            "¡¡¡LO HEMOS CONSEGUIDO!!! «LÁGRIMAS DE MI SOLEDAD» sigue " +
            "conquistando corazones. ¡¡¡GRACIAS POR HACERLO POSIBLE!!!\n\n" +
            "\uD83D\uDE80\u267E\uFE0F ¡¡¡AHORA A POR LAS 60.000!!!\n\n" +
            "\uD83C\uDFAC YOFER –..."
        assertEquals(
            "Entretenimiento",
            motor.clasificar(
                titulo,
                descripcion,
                "https://www.facebook.com/reel/1168661422485798/",
            ),
        )
        modelo.cerrar()
    }
}
