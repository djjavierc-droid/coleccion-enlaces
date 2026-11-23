package com.coleccionenlaces.app.domain

import com.coleccionenlaces.app.data.Enlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas unitarias de [Clasificador] (Fase 4, sección 4 del plan).
 *
 * 28 casos: una categoría por prueba con palabras clave, dominio,
 * umbral, empate, casos ambiguos que deben caer en "Otros" y la
 * prioridad manual. Los casos de la categoría sensible construyen sus
 * entradas EN TIEMPO DE EJECUCIÓN a partir de la lista del archivo
 * local (nunca se escriben esas palabras aquí) y llevan nombres
 * genéricos. Corren solo en CI (con el secreto) y en la copia local.
 */
class ClasificadorTest {

    @Test
    fun `cocina por palabras clave del titulo y la descripcion`() {
        assertEquals(
            "Cocina",
            Clasificador.clasificar(
                "Receta de pastel de chocolate",
                "Ingredientes y preparacion paso a paso",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `cocina solo por dominio tematico`() {
        assertEquals(
            "Cocina",
            Clasificador.clasificar(
                "Mira esto",
                "",
                "https://www.allrecipes.com/receta-de-pollo",
            ),
        )
    }

    @Test
    fun `videos de mecanica por frases especificas`() {
        assertEquals(
            "Videos de mecánica",
            Clasificador.clasificar(
                "Cómo cambiar aceite y pastillas de freno",
                "",
                "https://ejemplo.com/video",
            ),
        )
    }

    @Test
    fun `videos de construccion sin distinguir mayusculas ni acentos`() {
        assertEquals(
            "Videos de construcción",
            Clasificador.clasificar(
                "ALBAÑILERÍA: levantar pared con ladrillo y cemento",
                "",
                "https://ejemplo.com/video",
            ),
        )
    }

    @Test
    fun `entretenimiento por varias palabras clave`() {
        assertEquals(
            "Entretenimiento",
            Clasificador.clasificar(
                "Tráiler oficial de la nueva película",
                "Concierto en vivo del cantante",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `tecnologia y celulares por palabras clave`() {
        assertEquals(
            "Tecnología y celulares",
            Clasificador.clasificar(
                "Nuevo smartphone Android con pantalla OLED y memoria RAM",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `autos y motos por palabras clave`() {
        assertEquals(
            "Autos y motos",
            Clasificador.clasificar(
                "Prueba de manejo: auto nuevo con frenos ABS en el concesionario",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `salud y ejercicio por palabras clave`() {
        assertEquals(
            "Salud y ejercicio",
            Clasificador.clasificar(
                "Entrenamiento en el gimnasio para perder peso y ganar musculo",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `dinero y finanzas por palabras clave`() {
        assertEquals(
            "Dinero y finanzas",
            Clasificador.clasificar(
                "Cómo invertir en la bolsa de valores y ahorrar para la jubilación",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `noticias por palabras clave`() {
        assertEquals(
            "Noticias",
            Clasificador.clasificar(
                "Última hora: el gobierno anuncia elecciones y el congreso vota",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `juegos por palabras clave`() {
        assertEquals(
            "Juegos",
            Clasificador.clasificar(
                "Gameplay de Minecraft en PlayStation con modo multijugador",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `hazlo tu mismo por palabras clave`() {
        assertEquals(
            "Hazlo tú mismo (DIY)",
            Clasificador.clasificar(
                "Hazlo tú mismo: mueble casero con taladro y herramientas",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `jardin y plantas por palabras clave`() {
        assertEquals(
            "Jardín y plantas",
            Clasificador.clasificar(
                "Cómo sembrar un huerto en maceta: abono y poda",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `pesca y campo por palabras clave`() {
        assertEquals(
            "Pesca y campo",
            Clasificador.clasificar(
                "Pesca con caña de pescar y anzuelo en el río",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `moda y ropa por palabras clave`() {
        assertEquals(
            "Moda y ropa",
            Clasificador.clasificar(
                "Tendencia de moda: vestido y bolso para el desfile",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `viajes por palabras clave`() {
        assertEquals(
            "Viajes",
            Clasificador.clasificar(
                "Vacaciones en la playa: hotel y vuelo con reserva de hotel",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `una sola palabra clave ya alcanza el umbral`() {
        assertEquals(
            "Salud y ejercicio",
            Clasificador.clasificar("Yoga para principiantes", "", "https://ejemplo.com/pagina"),
        )
    }

    @Test
    fun `ambiguo sin palabras clave cae en Otros`() {
        assertEquals(
            "Otros",
            Clasificador.clasificar(
                "El mejor video de la semana",
                "Mira esto, te va a encantar",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `texto vacio y url sin tema caen en Otros`() {
        assertEquals("Otros", Clasificador.clasificar("", "", ""))
    }

    @Test
    fun `dominio generico no clasifica nada`() {
        assertEquals(
            "Otros",
            Clasificador.clasificar(
                "Mira esto",
                "",
                "https://www.youtube.com/watch?v=abc123",
            ),
        )
    }

    @Test
    fun `palabras de otra categoria pesan mas que un dominio tematico`() {
        assertEquals(
            "Cocina",
            Clasificador.clasificar(
                "Receta de pastel de chocolate",
                "Ingredientes y preparacion",
                "https://www.netflix.com/title/123",
            ),
        )
    }

    @Test
    fun `empate lo gana la primera categoria en el orden de las predefinidas`() {
        assertEquals(
            "Cocina",
            Clasificador.clasificar(
                "Receta de pastel y partido de futbol",
                "",
                "https://ejemplo.com/pagina",
            ),
        )
    }

    @Test
    fun `una palabra dentro de otra no coincide`() {
        assertEquals(
            "Otros",
            Clasificador.clasificar("Medicina moderna", "", "https://ejemplo.com/pagina"),
        )
    }

    @Test
    fun `puntajes muestra la ganadora y Otros en cero`() {
        val puntajes = Clasificador.puntajes(
            "Receta de pastel",
            "",
            "https://ejemplo.com/pagina",
        )
        assertEquals("Cocina", Clasificador.clasificar("Receta de pastel", "", "https://ejemplo.com/pagina"))
        assertEquals(0, puntajes["Otros"])
        assertEquals(4, puntajes["Cocina"])
    }

    @Test
    fun `clasificarEnlace respeta la categoria manual y devuelve null`() {
        val enlace = Enlace(
            url = "https://ejemplo.com/pagina",
            titulo = "Receta de pastel de chocolate",
            descripcion = "Ingredientes y preparacion",
            categoriaId = 1,
            categoriaManual = true,
        )
        assertNull(Clasificador.clasificarEnlace(enlace))
    }

    @Test
    fun `clasificarEnlace clasifica cuando la categoria no es manual`() {
        val enlace = Enlace(
            url = "https://ejemplo.com/pagina",
            titulo = "Receta de pastel de chocolate",
            descripcion = "Ingredientes y preparacion",
            categoriaManual = false,
        )
        assertEquals("Cocina", Clasificador.clasificarEnlace(enlace))
    }

    @Test
    fun `caso XXX 1 - palabra de la lista clasifica en la categoria sensible`() {
        val palabra = XxxKeywords.palabrasClave.first()
        assertEquals(
            Clasificador.NOMBRE_XXX,
            Clasificador.clasificar("Mira esto: $palabra", "", "https://ejemplo.com/pagina"),
        )
    }

    @Test
    fun `caso XXX 2 - palabra de la lista en la descripcion clasifica`() {
        val palabra = XxxKeywords.palabrasClave[1]
        assertEquals(
            Clasificador.NOMBRE_XXX,
            Clasificador.clasificar("Mira esto", "Contenido sobre $palabra", "https://ejemplo.com/pagina"),
        )
    }

    @Test
    fun `caso XXX 3 - dominio de la lista clasifica solo por la url`() {
        val dominio = XxxKeywords.dominios.first()
        assertEquals(
            Clasificador.NOMBRE_XXX,
            Clasificador.clasificar("Mira esto", "", "https://$dominio/pagina"),
        )
    }
}
