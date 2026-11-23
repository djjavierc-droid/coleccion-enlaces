package com.coleccionenlaces.app.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Pruebas de la red de seguridad M3 (Fix 5): la riqueza del bitmap
 * ([esBitmapSinCuadroReal]) distingue la caja de error de Facebook
 * de un cuadro real de vídeo. La lectura del texto y la captura en el
 * WebView no se pueden probar en la JVM: solo verificable en
 * dispositivo. Robolectric aporta el Bitmap.
 *
 * Los umbrales se calibraron con recortes JPEG de la captura de
 * Javier (diagnóstico §5.3, indicativo): caja de error 810–1.030
 * colores, luminancia 42,7–48,2; cuadro real 4.088–62.542 colores.
 * El umbral de 500 colores es conservador (el JPEG infla el conteo
 * del error; el PNG real tendrá menos): puede dejar pasar alguna
 * caja, pero no rechaza cuadros reales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniaturaWebViewTest {

    /** Bitmap casi uniforme y oscuro, con unas líneas blancas (texto). */
    private fun bitmapCajaError(ancho: Int = 100, alto: Int = 100): Bitmap {
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(24, 24, 24))
        // Unas líneas de texto blanco (pocos píxeles, pocos colores).
        for (x in 10 until 90) {
            bitmap.setPixel(x, 40, Color.WHITE)
            bitmap.setPixel(x, 60, Color.WHITE)
        }
        return bitmap
    }

    /** Bitmap con muchos colores (ruido: un cuadro real de vídeo). */
    private fun bitmapCuadroReal(ancho: Int = 100, alto: Int = 100): Bitmap {
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val azar = Random(42)
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                bitmap.setPixel(
                    x, y,
                    Color.rgb(azar.nextInt(256), azar.nextInt(256), azar.nextInt(256)),
                )
            }
        }
        return bitmap
    }

    @Test
    fun `la caja de error es un bitmap sin cuadro real`() {
        val bitmap = bitmapCajaError()
        val riqueza = medirRiquezaBitmap(bitmap)
        assertTrue("colores ${riqueza.coloresDistintos} >= 500", riqueza.coloresDistintos < 500)
        assertTrue("luminancia ${riqueza.luminanciaMedia} >= 60", riqueza.luminanciaMedia < 60.0)
        assertTrue(esBitmapSinCuadroReal(bitmap))
    }

    @Test
    fun `un cuadro real con muchos colores no es la caja de error`() {
        val bitmap = bitmapCuadroReal()
        val riqueza = medirRiquezaBitmap(bitmap)
        assertTrue("colores ${riqueza.coloresDistintos} < 500", riqueza.coloresDistintos >= 500)
        assertFalse(esBitmapSinCuadroReal(bitmap))
    }

    @Test
    fun `un bitmap claro uniforme no es la caja de error`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        assertFalse(esBitmapSinCuadroReal(bitmap))
    }

    @Test
    fun `un cuadro casi negro con pocos colores si se trata como sin cuadro real`() {
        // Límite honesto de M3 (diagnóstico §5.3): un cuadro de vídeo
        // muy oscuro o fundido a negro tiene pocos colores y da falso
        // positivo. Por eso M3 es solo la red de seguridad (M5 y M2
        // deciden antes) y la captura es a los 2 s, con el póster ya
        // pintado, no con un fundido a negro.
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val azar = Random(7)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val v = azar.nextInt(60)
                bitmap.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        val riqueza = medirRiquezaBitmap(bitmap)
        assertTrue("colores ${riqueza.coloresDistintos} >= 500", riqueza.coloresDistintos < 500)
        assertTrue(esBitmapSinCuadroReal(bitmap))
    }

    @Test
    fun `el JS del sondeo lee dentro de los iframes`() {
        // M2: el texto del reproductor vive en un iframe; el JS debe
        // leer contentDocument (mismo origen) y tolerar otro origen.
        assertTrue(JS_TEXTO_REPRODUCTOR.contains("contentDocument"))
        assertTrue(JS_TEXTO_REPRODUCTOR.contains("getElementsByTagName('iframe')"))
        assertTrue(JS_TEXTO_REPRODUCTOR.contains("catch"))
        // Y sigue leyendo el documento principal como antes.
        assertTrue(JS_TEXTO_REPRODUCTOR.contains("document.body.innerText"))
    }

    @Test
    fun `medirRiquezaBitmap cuenta colores y luminancia`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        val riqueza = medirRiquezaBitmap(bitmap)
        assertEquals(1, riqueza.coloresDistintos)
        assertEquals(0.0, riqueza.luminanciaMedia, 0.001)
    }
}
