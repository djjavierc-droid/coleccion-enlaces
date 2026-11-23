package com.coleccionenlaces.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.coleccionenlaces.app.domain.AnalizadorVisual
import com.coleccionenlaces.app.domain.CabezaAprendizaje
import com.coleccionenlaces.app.domain.ClasificadorVision
import com.coleccionenlaces.app.domain.EtiquetaImagen
import com.coleccionenlaces.app.domain.MotorPalabrasClave
import com.coleccionenlaces.app.domain.PosterResuelto
import com.coleccionenlaces.app.domain.ResultadoOEmbed
import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.SenalesVisuales
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
import com.coleccionenlaces.app.domain.VistaPrevia
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.UnknownHostException
import java.nio.file.Files

/**
 * Pruebas del repositorio (Fase 5) sobre una base Room en memoria
 * (también sembrada con las 17 categorías predefinidas).
 *
 * Ninguna prueba toca internet: el servicio de vista previa usa un
 * OkHttpClient con interceptores que simulan la respuesta del
 * servidor (mismo patrón que `ServicioVistaPreviaTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositorioEnlacesTest {

    private lateinit var db: AppDatabase
    private lateinit var enlaceDao: EnlaceDao
    private lateinit var categoriaDao: CategoriaDao

    private var cocinaId: Long = 0
    private var entretenimientoId: Long = 0
    private var tecnologiaId: Long = 0
    private var otrosId: Long = 0
    private var juegosId: Long = 0

    /** URLs oEmbed pedidas en cada prueba (para probar que no se piden de más). */
    private val oembedPedidas = mutableListOf<String>()

    /** Carpeta temporal donde se guardan las miniaturas locales en estas pruebas. */
    private lateinit var directorioMiniaturas: File

    private val htmlReceta = """
        <html><head>
        <meta property="og:title" content="Receta de paella valenciana">
        <meta property="og:description" content="La receta tradicional paso a paso.">
        <meta property="og:image" content="https://ejemplo.com/img/paella.jpg">
        </head><body></body></html>
    """.trimIndent()

    /** Respuesta oEmbed de YouTube (forma real verificada con curl). */
    private val jsonYouTube = """
        {"type":"video","version":"1.0","title":"Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)","author_name":"Rick Astley","provider_name":"YouTube","thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg","html":"<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\"></iframe>","width":480,"height":270}
    """.trimIndent()

    /** Respuesta oEmbed de Facebook (forma real verificada con curl). */
    private val jsonFacebook = """
        {"html":"<div class=\"fb-video\" data-href=\"https://www.facebook.com/share/r/1JqVPz7de\"></div><script src=\"https://connect.facebook.net/es_LA/sdk.js#xfbml=1&version=v27.0\"></script>","provider_name":"Facebook","type":"video","version":"1.0","width":500}
    """.trimIndent()

    /** Respuesta oEmbed de Instagram (forma real verificada con curl). */
    private val jsonInstagram = """
        {"version":"1.0","provider_name":"Instagram","provider_url":"https://www.instagram.com/","type":"rich","width":658,"html":"<blockquote class=\"instagram-media\" data-instgrm-permalink=\"https://www.instagram.com/reel/Df2uUvyt_S4/\"></blockquote><script async src=\"//www.instagram.com/embed.js\"></script>"}
    """.trimIndent()

    /** Respuesta oEmbed de X (forma real verificada con curl). */
    private val jsonX = """
        {"url":"https://x.com/Interior/status/507185938620219395","author_name":"U.S. Department of the Interior","html":"<blockquote class=\"twitter-tweet\"><p>Happy 50th anniversary to the Wilderness Act!</p></blockquote><script async src=\"https://platform.x.com/widgets.js\" charset=\"utf-8\"></script>","width":550,"height":null,"type":"rich","provider_name":"X","provider_url":"https://x.com","version":"1.0"}
    """.trimIndent()

    /** Respuesta oEmbed de Vimeo (forma real verificada con curl). */
    private val jsonVimeo = """
        {"type":"video","version":"1.0","provider_name":"Vimeo","title":"The New Vimeo Player (You Know, For Videos)","html":"<iframe src=\"https://player.vimeo.com/video/76979871\"></iframe>","width":480,"height":270,"thumbnail_url":"https://i.vimeocdn.com/video/452001751-d_295x166?region=us"}
    """.trimIndent()

    /** Respuesta oEmbed de Dailymotion (forma real verificada con curl). */
    private val jsonDailymotion = """
        {"type":"video","version":"1.0","provider_name":"Dailymotion","title":"Así se vivió el Home Run Derby X en la CDMX","html":"<iframe src=\"https://www.dailymotion.com/embed/video/x8ek5sr\"></iframe>","width":480,"height":270,"thumbnail_url":"https://s1.dmcdn.net/v/UIaXR1g4gkw6X7Bnf/x720"}
    """.trimIndent()

    private val jsonError100 = """
        {"error":{"message":"(#100) Invalid parameter","type":"OAuthException","code":100}}
    """.trimIndent()

    @Before
    fun crearBase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.crearEnMemoria(context)
        enlaceDao = db.enlaceDao()
        categoriaDao = db.categoriaDao()
        cocinaId = categoriaDao.obtenerPorNombre("Cocina")!!.id
        entretenimientoId = categoriaDao.obtenerPorNombre("Entretenimiento")!!.id
        tecnologiaId = categoriaDao.obtenerPorNombre("Tecnología y celulares")!!.id
        otrosId = categoriaDao.obtenerPorNombre("Otros")!!.id
        juegosId = categoriaDao.obtenerPorNombre("Juegos")!!.id
        oembedPedidas.clear()
        directorioMiniaturas = Files.createTempDirectory("miniaturas-test").toFile()
    }

    @After
    fun cerrarBase() {
        db.close()
        directorioMiniaturas.deleteRecursively()
    }

    /** Cliente que responde 200 con el HTML indicado según la URL pedida. */
    private fun clienteFalso(respuestas: Map<String, String>): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val cuerpo = respuestas[cadena.request().url.toString()] ?: ""
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()

    /** Cliente oEmbed falso: responde según el prefijo de la URL pedida; sin prefijo, 404 con `{}`. */
    private fun clienteOEmbedFalso(respuestas: Map<String, Pair<Int, String>>): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val url = cadena.request().url.toString()
                oembedPedidas.add(url)
                val (codigo, cuerpo) = respuestas.entries
                    .firstOrNull { (prefijo, _) -> url.startsWith(prefijo) }?.value
                    ?: (404 to "{}")
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(codigo)
                    .message(if (codigo == 200) "OK" else "Error")
                    .body(cuerpo.toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()

    private fun repositorio(
        respuestas: Map<String, String> = emptyMap(),
        oembed: Map<String, Pair<Int, String>> = emptyMap(),
        directorio: File? = null,
    ): RepositorioEnlaces =
        RepositorioEnlaces(
            enlaceDao,
            categoriaDao,
            ServicioVistaPrevia(cliente = clienteFalso(respuestas)),
            ServicioOEmbed(cliente = clienteOEmbedFalso(oembed)),
            directorio,
        )

    private fun repositorioSinInternet(): RepositorioEnlaces =
        RepositorioEnlaces(
            enlaceDao,
            categoriaDao,
            ServicioVistaPrevia(
                cliente = OkHttpClient.Builder()
                    .addInterceptor { throw UnknownHostException("ejemplo.com") }
                    .build(),
            ),
            ServicioOEmbed(
                cliente = OkHttpClient.Builder()
                    .addInterceptor { throw UnknownHostException("graph.facebook.com") }
                    .build(),
            ),
        )

    /** Analizador visual falso (Fix 5): devuelve siempre [senales]. */
    private class AnalizadorFalso(
        private val senales: SenalesVisuales?,
    ) : AnalizadorVisual {
        override suspend fun analizar(bitmap: Bitmap): SenalesVisuales? = senales
    }

    /** Repositorio con visión + OCR (Fix 5), con el motor por palabras clave (determinista). */
    private fun repositorioConVision(
        senales: SenalesVisuales?,
        directorio: File? = null,
    ): RepositorioEnlaces =
        RepositorioEnlaces(
            enlaceDao,
            categoriaDao,
            ServicioVistaPrevia(cliente = clienteFalso(emptyMap())),
            ServicioOEmbed(cliente = clienteOEmbedFalso(emptyMap())),
            directorio,
            motorClasificacion = MotorPalabrasClave,
            analizadorVisual = AnalizadorFalso(senales),
            clasificadorVision = ClasificadorVision(MotorPalabrasClave),
        )

    @Test
    fun `agregarEnlace con vista previa simulada guarda titulo descripcion imagen y clasifica`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
            )

            val resultado = repo.agregarEnlace("https://ejemplo.com/receta")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertTrue(id > 0)
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Receta de paella valenciana", guardado.titulo)
            assertEquals("La receta tradicional paso a paso.", guardado.descripcion)
            assertEquals("https://ejemplo.com/img/paella.jpg", guardado.urlImagen)
            assertEquals(cocinaId, guardado.categoriaId)
            assertFalse(guardado.categoriaManual)
            assertFalse(guardado.esFavorito)
        }

    @Test
    fun `agregarEnlace con vista previa vacia guarda el dominio como titulo y clasifica Otros`() =
        runBlocking {
            val repo = repositorio()

            val resultado = repo.agregarEnlace("https://www.ejemplo.com/pagina")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("ejemplo.com", guardado.titulo)
            assertEquals("", guardado.descripcion)
            assertNull(guardado.urlImagen)
            assertEquals(otrosId, guardado.categoriaId)
        }

    @Test
    fun `agregarEnlace con URL invalida devuelve error y no guarda nada`() = runBlocking {
        val repo = repositorio()

        val resultado = repo.agregarEnlace("no-es-una-url")

        assertEquals(
            ResultadoAgregarEnlace.Error(TipoErrorAgregarEnlace.URL_INVALIDA),
            resultado,
        )
        assertTrue(enlaceDao.obtenerTodos().first().isEmpty())
    }

    @Test
    fun `agregarEnlace sin internet guarda igual con el dominio como titulo`() = runBlocking {
        val repo = repositorioSinInternet()

        val resultado = repo.agregarEnlace("https://www.ejemplo.com/receta")

        assertTrue(resultado is ResultadoAgregarEnlace.Exito)
        val id = (resultado as ResultadoAgregarEnlace.Exito).id
        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals("ejemplo.com", guardado.titulo)
        assertEquals(otrosId, guardado.categoriaId)
    }

    @Test
    fun `agregarEnlace clasifica por la descripcion cuando el titulo no dice nada`() =
        runBlocking {
            val html = """
                <html><head>
                <meta property="og:title" content="Mira esto">
                <meta property="og:description" content="Receta con horno y sarten.">
                </head><body></body></html>
            """.trimIndent()
            val repo = repositorio(mapOf("https://ejemplo.com/mira" to html))

            val resultado = repo.agregarEnlace("https://ejemplo.com/mira")

            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertEquals(cocinaId, enlaceDao.obtenerPorId(id)!!.categoriaId)
        }

    @Test
    fun `reclasificarEnlace no sobrescribe la categoria manual`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        assertTrue(repo.cambiarCategoriaManual(id, entretenimientoId))
        // El título ahora habla de fútbol, pero la categoría es manual.
        enlaceDao.actualizar(
            enlaceDao.obtenerPorId(id)!!.copy(titulo = "Partido de futbol en directo"),
        )

        val aplicado = repo.reclasificarEnlace(id)

        assertFalse(aplicado)
        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals(entretenimientoId, guardado.categoriaId)
        assertTrue(guardado.categoriaManual)
    }

    @Test
    fun `reclasificarEnlace si reclasifica cuando no es manual`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        assertEquals(cocinaId, enlaceDao.obtenerPorId(id)!!.categoriaId)
        enlaceDao.actualizar(
            enlaceDao.obtenerPorId(id)!!.copy(titulo = "Partido de futbol en directo"),
        )

        val aplicado = repo.reclasificarEnlace(id)

        assertTrue(aplicado)
        assertEquals(entretenimientoId, enlaceDao.obtenerPorId(id)!!.categoriaId)
    }

    @Test
    fun `actualizarEnlace conserva la categoria y el flag manual al editar`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id
        assertTrue(repo.cambiarCategoriaManual(id, entretenimientoId))
        val editado = enlaceDao.obtenerPorId(id)!!.copy(
            titulo = "Mi receta favorita",
            descripcion = "La hago los domingos",
        )

        repo.actualizarEnlace(editado)

        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals("Mi receta favorita", guardado.titulo)
        assertEquals(entretenimientoId, guardado.categoriaId)
        assertTrue(guardado.categoriaManual)
    }

    @Test
    fun `cambiarCategoriaManual pone la categoria elegida y marca manual`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )
        val id = (repo.agregarEnlace("https://ejemplo.com/receta")
            as ResultadoAgregarEnlace.Exito).id

        val cambiado = repo.cambiarCategoriaManual(id, entretenimientoId)

        assertTrue(cambiado)
        val guardado = enlaceDao.obtenerPorId(id)!!
        assertEquals(entretenimientoId, guardado.categoriaId)
        assertTrue(guardado.categoriaManual)
    }

    @Test
    fun `cambiarCategoriaManual con enlace o categoria inexistentes devuelve false`() =
        runBlocking {
            val repo = repositorio()

            assertFalse(repo.cambiarCategoriaManual(9999L, cocinaId))
            val id = enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/x", titulo = "X", categoriaId = cocinaId),
            )
            assertFalse(repo.cambiarCategoriaManual(id, 9999L))
            assertEquals(cocinaId, enlaceDao.obtenerPorId(id)!!.categoriaId)
        }

    @Test
    fun `alternarFavorito invierte el flag y devuelve el nuevo estado`() = runBlocking {
        val repo = repositorio()
        val id = enlaceDao.insertar(
            Enlace(url = "https://ejemplo.com/x", titulo = "X", categoriaId = cocinaId),
        )

        assertEquals(true, repo.alternarFavorito(id))
        assertTrue(enlaceDao.obtenerPorId(id)!!.esFavorito)
        assertEquals(false, repo.alternarFavorito(id))
        assertFalse(enlaceDao.obtenerPorId(id)!!.esFavorito)
        assertNull(repo.alternarFavorito(9999L))
    }

    @Test
    fun `borrarEnlace elimina el enlace`() = runBlocking {
        val repo = repositorio()
        val id = enlaceDao.insertar(
            Enlace(url = "https://ejemplo.com/x", titulo = "X", categoriaId = cocinaId),
        )

        repo.borrarEnlace(id)

        assertNull(enlaceDao.obtenerPorId(id))
    }

    @Test
    fun `buscarEnlaces encuentra por titulo descripcion y nombre de categoria`() =
        runBlocking {
            val repo = repositorio()
            enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/a",
                    titulo = "Receta de paella",
                    descripcion = "Arroz y azafran",
                    categoriaId = cocinaId,
                ),
            )
            enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/b",
                    titulo = "Partido de futbol",
                    descripcion = "El clasico",
                    categoriaId = entretenimientoId,
                ),
            )

            assertEquals(1, repo.buscarEnlaces("paella").first().size)
            assertEquals(1, repo.buscarEnlaces("clasico").first().size)
            assertEquals(1, repo.buscarEnlaces("Cocina").first().size)
            assertEquals(1, repo.buscarEnlaces("entretenimiento").first().size)
            assertEquals(0, repo.buscarEnlaces("no existe nada").first().size)
            assertEquals(2, repo.buscarEnlaces("").first().size)
        }

    @Test
    fun `obtenerEnlacesPorCategoria y obtenerFavoritos filtran con categoria resuelta`() =
        runBlocking {
            val repo = repositorio()
            val idCocina = enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/a",
                    titulo = "Receta",
                    categoriaId = cocinaId,
                    esFavorito = true,
                ),
            )
            enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/b",
                    titulo = "Partido",
                    categoriaId = entretenimientoId,
                ),
            )

            val deCocina = repo.obtenerEnlacesPorCategoria(cocinaId).first()
            assertEquals(1, deCocina.size)
            assertEquals(idCocina, deCocina.first().enlace.id)
            assertEquals("Cocina", deCocina.first().categoria.nombre)
            val favoritos = repo.obtenerFavoritos().first()
            assertEquals(1, favoritos.size)
            assertEquals("Cocina", favoritos.first().categoria.nombre)
        }

    @Test
    fun `enlacesConCategoria resuelve categoriaId null como Otros`() = runBlocking {
        val repo = repositorio()
        enlaceDao.insertar(
            Enlace(
                url = "https://ejemplo.com/sin-categoria",
                titulo = "Sin carpeta",
                categoriaId = null,
            ),
        )

        val lista = repo.enlacesConCategoria.first()

        assertEquals(1, lista.size)
        assertEquals("Otros", lista.first().categoria.nombre)
        assertEquals(otrosId, lista.first().categoria.id)
        assertEquals(0xFF90A4AE, lista.first().categoria.color)
        assertEquals("category", lista.first().categoria.icono)
    }

    @Test
    fun `contadoresPorCategoria cuenta por categoria y los sin categoria en Otros`() =
        runBlocking {
            val repo = repositorio()
            enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/a", titulo = "A", categoriaId = cocinaId),
            )
            enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/b", titulo = "B", categoriaId = cocinaId),
            )
            enlaceDao.insertar(
                Enlace(url = "https://ejemplo.com/c", titulo = "C", categoriaId = null),
            )

            val contadores = repo.contadoresPorCategoria.first()

            assertEquals(2, contadores[cocinaId])
            assertEquals(1, contadores[otrosId])
            assertNull(contadores[entretenimientoId])
        }

    @Test
    fun `categorias expone las 17 predefinidas en orden`() = runBlocking {
        val repo = repositorio()

        val categorias = repo.categorias.first()

        assertEquals(17, categorias.size)
        assertEquals("Cocina", categorias.first().nombre)
        assertEquals("Otros", categorias.last().nombre)
    }

    @Test
    fun `agregarEnlace con espacios alrededor de la URL la limpia antes de guardar`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
            )

            val resultado = repo.agregarEnlace("  https://ejemplo.com/receta  ")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertEquals("https://ejemplo.com/receta", enlaceDao.obtenerPorId(id)!!.url)
        }

    // ---- Corrección 1.0.1: título 100% automático, sin edición manual ----

    @Test
    fun `agregarEnlace con vista previa fallida y texto compartido usa el texto como titulo y clasifica por el`() =
        runBlocking {
            val repo = repositorio()

            val resultado = repo.agregarEnlace(
                "https://www.facebook.com/share/r/1JqVPz7de",
                textoCompartido = "Receta de paella valenciana\nLa receta tradicional paso a paso.",
                preparado = null,
            )

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Receta de paella valenciana", guardado.titulo)
            assertEquals("La receta tradicional paso a paso.", guardado.descripcion)
            assertNull(guardado.urlImagen)
            assertEquals(cocinaId, guardado.categoriaId)
        }

    @Test
    fun `agregarEnlace con texto compartido vacio usa el titulo de la vista previa`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
            )

            val resultado = repo.agregarEnlace(
                "https://ejemplo.com/receta",
                textoCompartido = "   ",
                preparado = null,
            )

            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            assertEquals("Receta de paella valenciana", enlaceDao.obtenerPorId(id)!!.titulo)
            assertEquals(
                "La receta tradicional paso a paso.",
                enlaceDao.obtenerPorId(id)!!.descripcion,
            )
        }

    @Test
    fun `agregarEnlace con vista previa usa su titulo antes que el texto compartido`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
            )

            val resultado = repo.agregarEnlace(
                "https://ejemplo.com/receta",
                textoCompartido = "Mira esta receta que encontré",
                preparado = null,
            )

            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Receta de paella valenciana", guardado.titulo)
            // La vista previa trae título y descripción: el texto
            // compartido no hace falta y se descarta.
            assertEquals("La receta tradicional paso a paso.", guardado.descripcion)
        }

    @Test
    fun `agregarEnlace con vista previa ya comprobada la reutiliza sin descargar de nuevo`() =
        runBlocking {
            val repo = repositorioSinInternet()
            val preparado = EnlacePreparado(
                vistaPrevia = ResultadoVistaPrevia.Exito(
                    VistaPrevia(
                        titulo = "Receta de paella valenciana",
                        descripcion = "La receta tradicional paso a paso.",
                        urlImagen = "https://ejemplo.com/img/paella.jpg",
                    ),
                ),
            )

            // Sin internet y sin redescargar: si intentara descargar,
            // el cliente lanzaría UnknownHostException.
            val resultado = repo.agregarEnlace(
                "https://ejemplo.com/receta",
                textoCompartido = null,
                preparado = preparado,
            )

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Receta de paella valenciana", guardado.titulo)
            assertEquals(cocinaId, guardado.categoriaId)
        }

    @Test
    fun `prepararEnlace con vista previa fallida en Facebook pide el oEmbed`() =
        runBlocking {
            val repo = repositorio(
                oembed = mapOf(
                    "https://graph.facebook.com/v27.0/oembed_video" to (200 to jsonFacebook),
                ),
            )

            val preparado = repo.prepararEnlace("https://www.facebook.com/share/r/1JqVPz7de")

            assertTrue(preparado.vistaPrevia is ResultadoVistaPrevia.Error)
            assertEquals(
                TipoErrorVistaPrevia.SIN_VISTA_PREVIA,
                (preparado.vistaPrevia as ResultadoVistaPrevia.Error).tipo,
            )
            assertTrue(preparado.oembed is ResultadoOEmbed.Exito)
            assertTrue(
                (preparado.oembed as ResultadoOEmbed.Exito).html.contains("fb-video"),
            )
            assertEquals(1, oembedPedidas.size)
        }

    @Test
    fun `prepararEnlace con vista previa correcta no pide el oEmbed`() = runBlocking {
        val repo = repositorio(
            mapOf("https://ejemplo.com/receta" to htmlReceta),
        )

        val preparado = repo.prepararEnlace("https://ejemplo.com/receta")

        assertTrue(preparado.vistaPrevia is ResultadoVistaPrevia.Exito)
        assertNull(preparado.oembed)
        assertTrue(oembedPedidas.isEmpty())
    }

    @Test
    fun `prepararEnlace con URL invalida devuelve URL_INVALIDA`() = runBlocking {
        val repo = repositorio()

        val preparado = repo.prepararEnlace("no-es-una-url")

        assertEquals(
            TipoErrorVistaPrevia.URL_INVALIDA,
            (preparado.vistaPrevia as ResultadoVistaPrevia.Error).tipo,
        )
    }

    // ---- Corrección 1.0.1, punto 3: oEmbed al guardar ----

    @Test
    fun `agregarEnlace de YouTube guarda el titulo oEmbed y descarga la miniatura a local`() =
        runBlocking {
            val repo = repositorio(
                respuestas = mapOf(
                    "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" to "bytes-falsos-miniatura",
                ),
                oembed = mapOf(
                    "https://www.youtube.com/oembed" to (200 to jsonYouTube),
                ),
                directorio = directorioMiniaturas,
            )

            val resultado = repo.agregarEnlace("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals(
                "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
                guardado.titulo,
            )
            assertTrue(guardado.htmlVideo!!.contains("youtube.com/embed"))
            val imagenLocal = guardado.imagenLocal!!
            assertTrue(File(imagenLocal).exists())
            assertTrue(File(imagenLocal).length() > 0)
        }

    @Test
    fun `agregarEnlace de Facebook guarda el html del reproductor y el dominio como titulo`() =
        runBlocking {
            val repo = repositorio(
                oembed = mapOf(
                    "https://graph.facebook.com/v27.0/oembed_video" to (200 to jsonFacebook),
                ),
            )

            val resultado = repo.agregarEnlace("https://www.facebook.com/share/r/1JqVPz7de")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("facebook.com", guardado.titulo)
            assertTrue(guardado.htmlVideo!!.contains("fb-video"))
            assertNull(guardado.imagenLocal)
            assertEquals(otrosId, guardado.categoriaId)
        }

    @Test
    fun `agregarEnlace de Facebook con oEmbed en error 100 guarda sin htmlVideo`() =
        runBlocking {
            val repo = repositorio(
                oembed = mapOf(
                    "https://graph.facebook.com/v27.0/oembed_video" to (400 to jsonError100),
                    "https://graph.facebook.com/v27.0/oembed_post" to (400 to jsonError100),
                ),
            )

            val resultado = repo.agregarEnlace("https://www.facebook.com/share/r/1JqVPz7de")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("facebook.com", guardado.titulo)
            assertNull(guardado.htmlVideo)
            assertNull(guardado.imagenLocal)
        }

    @Test
    fun `agregarEnlace de Instagram guarda el html del incrustado y el dominio como titulo`() =
        runBlocking {
            val repo = repositorio(
                oembed = mapOf(
                    "https://graph.facebook.com/v27.0/instagram_oembed" to (200 to jsonInstagram),
                ),
            )

            val resultado = repo.agregarEnlace("https://www.instagram.com/reel/Df2uUvyt_S4/")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("instagram.com", guardado.titulo)
            assertTrue(guardado.htmlVideo!!.contains("instagram-media"))
            assertNull(guardado.imagenLocal)
            assertEquals(otrosId, guardado.categoriaId)
        }

    @Test
    fun `agregarEnlace de X guarda el html del tuit y el dominio como titulo`() =
        runBlocking {
            val repo = repositorio(
                oembed = mapOf(
                    "https://publish.twitter.com/oembed" to (200 to jsonX),
                ),
            )

            val resultado = repo.agregarEnlace("https://x.com/Interior/status/507185938620219395")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("x.com", guardado.titulo)
            assertTrue(guardado.htmlVideo!!.contains("twitter-tweet"))
            assertNull(guardado.imagenLocal)
            assertEquals(otrosId, guardado.categoriaId)
        }

    @Test
    fun `agregarEnlace de Vimeo guarda el titulo oEmbed y descarga la miniatura a local`() =
        runBlocking {
            val repo = repositorio(
                respuestas = mapOf(
                    "https://i.vimeocdn.com/video/452001751-d_295x166?region=us" to "bytes-falsos-miniatura",
                ),
                oembed = mapOf(
                    "https://vimeo.com/api/oembed.json" to (200 to jsonVimeo),
                ),
                directorio = directorioMiniaturas,
            )

            val resultado = repo.agregarEnlace("https://vimeo.com/76979871")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("The New Vimeo Player (You Know, For Videos)", guardado.titulo)
            assertTrue(guardado.htmlVideo!!.contains("player.vimeo.com/video/76979871"))
            val imagenLocal = guardado.imagenLocal!!
            assertTrue(File(imagenLocal).exists())
            assertTrue(File(imagenLocal).length() > 0)
        }

    @Test
    fun `agregarEnlace de Dailymotion guarda el titulo oEmbed y descarga la miniatura a local`() =
        runBlocking {
            val repo = repositorio(
                respuestas = mapOf(
                    "https://s1.dmcdn.net/v/UIaXR1g4gkw6X7Bnf/x720" to "bytes-falsos-miniatura",
                ),
                oembed = mapOf(
                    "https://www.dailymotion.com/services/oembed" to (200 to jsonDailymotion),
                ),
                directorio = directorioMiniaturas,
            )

            val resultado = repo.agregarEnlace("https://www.dailymotion.com/video/x8ek5sr")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Así se vivió el Home Run Derby X en la CDMX", guardado.titulo)
            assertTrue(guardado.htmlVideo!!.contains("dailymotion.com/embed/video/x8ek5sr"))
            val imagenLocal = guardado.imagenLocal!!
            assertTrue(File(imagenLocal).exists())
            assertTrue(File(imagenLocal).length() > 0)
        }

    @Test
    fun `agregarEnlace descarga a local la imagen de vista previa con firma temporal`() =
        runBlocking {
            val htmlFirmado = """
                <html><head>
                <meta property="og:title" content="Foto firmada">
                <meta property="og:image" content="https://scontent.fbcdn.net/v/foto.jpg?oe=68ABC123&oh=00_xyz">
                </head><body></body></html>
            """.trimIndent()
            val repo = repositorio(
                respuestas = mapOf(
                    "https://ejemplo.com/foto" to htmlFirmado,
                    "https://scontent.fbcdn.net/v/foto.jpg?oe=68ABC123&oh=00_xyz" to "bytes-falsos-foto",
                ),
                directorio = directorioMiniaturas,
            )

            val resultado = repo.agregarEnlace("https://ejemplo.com/foto")

            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Foto firmada", guardado.titulo)
            val imagenLocal = guardado.imagenLocal!!
            assertTrue(File(imagenLocal).exists())
        }

    @Test
    fun `agregarEnlace no descarga a local la imagen de vista previa sin firma`() =
        runBlocking {
            val repo = repositorio(
                mapOf("https://ejemplo.com/receta" to htmlReceta),
                directorio = directorioMiniaturas,
            )

            val resultado = repo.agregarEnlace("https://ejemplo.com/receta")

            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertNull(guardado.imagenLocal)
            assertEquals("https://ejemplo.com/img/paella.jpg", guardado.urlImagen)
        }

    @Test
    fun `guardarMiniaturaVideo guarda el cuadro como PNG local`() = runBlocking {
        val repo = repositorio(directorio = directorioMiniaturas)
        val id = enlaceDao.insertar(
            Enlace(
                url = "https://www.facebook.com/share/r/1JqVPz7de",
                titulo = "facebook.com",
                categoriaId = otrosId,
                htmlVideo = "<div class=\"fb-video\"></div>",
            ),
        )
        val bitmap = android.graphics.Bitmap.createBitmap(
            10, 10, android.graphics.Bitmap.Config.ARGB_8888,
        )

        val guardado = repo.guardarMiniaturaVideo(id, bitmap)

        assertTrue(guardado)
        val enlace = enlaceDao.obtenerPorId(id)!!
        val imagenLocal = enlace.imagenLocal!!
        assertTrue(File(imagenLocal).exists())
        assertTrue(imagenLocal.endsWith(".png"))
        assertFalse(repo.guardarMiniaturaVideo(9999L, bitmap))
    }

    // ---- Fix 5: visión + OCR al guardar la miniatura ----

    /**
     * El cuadro de un enlace en "Otros" se analiza al guardarse: el
     * texto del OCR ("Receta de pasta casera...") lo mueve a Cocina
     * y queda como descripción (así la clasificación es estable). La
     * miniatura local NO se borra con la reclasificación.
     */
    @Test
    fun `guardarMiniaturaVideo con OCR clasifica un enlace en Otros`() = runBlocking {
        val repo = repositorioConVision(
            SenalesVisuales(
                etiquetas = emptyList(),
                textoOcr = "Receta de pasta casera en 15 minutos",
            ),
            directorio = directorioMiniaturas,
        )
        val id = enlaceDao.insertar(
            Enlace(
                url = "https://www.facebook.com/share/r/1JqVPz7de",
                titulo = "facebook.com",
                categoriaId = otrosId,
                htmlVideo = "<div class=\"fb-video\"></div>",
            ),
        )
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        assertTrue(repo.guardarMiniaturaVideo(id, bitmap))

        val enlace = enlaceDao.obtenerPorId(id)!!
        assertEquals(cocinaId, enlace.categoriaId)
        assertEquals("Receta de pasta casera en 15 minutos", enlace.descripcion)
        assertTrue("la miniatura local no debe borrarse al reclasificar", enlace.imagenLocal != null)
        assertTrue(File(enlace.imagenLocal!!).exists())
    }

    /**
     * Si el cuadro es la pantalla de error ("Video no disponible"),
     * la visión NO reclasifica: el enlace queda en "Otros" y la
     * miniatura se guarda igual (M3 la rechaza antes en el WebView;
     * aquí se comprueba que la clasificación nunca usa el error).
     */
    @Test
    fun `guardarMiniaturaVideo con pantalla de error no reclasifica`() = runBlocking {
        val repo = repositorioConVision(
            SenalesVisuales(
                etiquetas = listOf(EtiquetaImagen("Video game", 0.95f)),
                textoOcr = "Video no disponible\nEste video ya no existe",
            ),
            directorio = directorioMiniaturas,
        )
        val id = enlaceDao.insertar(
            Enlace(
                url = "https://www.facebook.com/share/r/1JqVPz7de",
                titulo = "facebook.com",
                categoriaId = otrosId,
                htmlVideo = "<div class=\"fb-video\"></div>",
            ),
        )
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        assertTrue(repo.guardarMiniaturaVideo(id, bitmap))

        val enlace = enlaceDao.obtenerPorId(id)!!
        assertEquals(otrosId, enlace.categoriaId)
        assertTrue(enlace.descripcion.isBlank())
    }

    /**
     * Sin señal visual (el analizador no devuelve nada), el enlace
     * queda en "Otros": la visión nunca adivina.
     */
    @Test
    fun `guardarMiniaturaVideo sin senal visual no reclasifica`() = runBlocking {
        val repo = repositorioConVision(
            senales = null,
            directorio = directorioMiniaturas,
        )
        val id = enlaceDao.insertar(
            Enlace(
                url = "https://www.facebook.com/share/r/1JqVPz7de",
                titulo = "facebook.com",
                categoriaId = otrosId,
                htmlVideo = "<div class=\"fb-video\"></div>",
            ),
        )
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        assertTrue(repo.guardarMiniaturaVideo(id, bitmap))

        val enlace = enlaceDao.obtenerPorId(id)!!
        assertEquals(otrosId, enlace.categoriaId)
    }

    /**
     * La visión solo actúa sobre enlaces en "Otros": uno ya
     * clasificado en Cocina no se toca aunque el OCR diga otra cosa.
     */
    @Test
    fun `guardarMiniaturaVideo no toca un enlace ya clasificado`() = runBlocking {
        val repo = repositorioConVision(
            SenalesVisuales(
                etiquetas = listOf(EtiquetaImagen("Video game", 0.9f)),
                textoOcr = "Gameplay de Minecraft construyendo mi base",
            ),
            directorio = directorioMiniaturas,
        )
        val id = enlaceDao.insertar(
            Enlace(
                url = "https://www.facebook.com/share/r/1JqVPz7de",
                titulo = "Receta de paella valenciana",
                categoriaId = cocinaId,
                htmlVideo = "<div class=\"fb-video\"></div>",
            ),
        )
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        assertTrue(repo.guardarMiniaturaVideo(id, bitmap))

        val enlace = enlaceDao.obtenerPorId(id)!!
        assertEquals(cocinaId, enlace.categoriaId)
    }

    // ---- Corrección 1.0.1: texto visible del reproductor ----

    @Test
    fun `actualizarTextoDesdeWebView rellena el titulo de dominio y reclasifica a Tecnologia`() =
        runBlocking {
            val repo = repositorio(
                oembed = mapOf(
                    "https://graph.facebook.com/v27.0/oembed_video" to (200 to jsonFacebook),
                ),
            )
            val id = (repo.agregarEnlace("https://www.facebook.com/share/r/1JqVPz7de")
                as ResultadoAgregarEnlace.Exito).id
            assertEquals(otrosId, enlaceDao.obtenerPorId(id)!!.categoriaId)

            val actualizado = repo.actualizarTextoDesdeWebView(
                id,
                "Contratamos a una IA... y el primer día ya vendía croquetas en Marte...\n" +
                    "Hoy hemos contratado a una",
            )

            assertTrue(actualizado)
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals(
                "Contratamos a una IA... y el primer día ya vendía croquetas en Marte...",
                guardado.titulo,
            )
            assertTrue(guardado.descripcion.contains("Hoy hemos contratado a una"))
            assertEquals(tecnologiaId, guardado.categoriaId)
        }

    @Test
    fun `actualizarTextoDesdeWebView no sobrescribe un titulo que ya venia de oEmbed`() =
        runBlocking {
            val repo = repositorio(
                respuestas = mapOf(
                    "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" to "bytes-falsos",
                ),
                oembed = mapOf(
                    "https://www.youtube.com/oembed" to (200 to jsonYouTube),
                ),
                directorio = directorioMiniaturas,
            )
            val id = (repo.agregarEnlace("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                as ResultadoAgregarEnlace.Exito).id

            val actualizado = repo.actualizarTextoDesdeWebView(id, "Texto del reproductor")

            assertTrue(actualizado)
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals(
                "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
                guardado.titulo,
            )
            assertEquals("Texto del reproductor", guardado.descripcion)
        }

    @Test
    fun `actualizarTextoDesdeWebView no toca nada con texto vacio o enlace inexistente`() =
        runBlocking {
            val repo = repositorio()
            val id = enlaceDao.insertar(
                Enlace(
                    url = "https://ejemplo.com/x",
                    titulo = "ejemplo.com",
                    categoriaId = otrosId,
                ),
            )

            assertFalse(repo.actualizarTextoDesdeWebView(id, "   "))
            assertFalse(repo.actualizarTextoDesdeWebView(9999L, "Algo"))
            assertEquals("ejemplo.com", enlaceDao.obtenerPorId(id)!!.titulo)
            assertEquals("", enlaceDao.obtenerPorId(id)!!.descripcion)
        }

    @Test
    fun `actualizarTextoDesdeWebView no reclasifica un enlace de categoria manual`() =
        runBlocking {
            val repo = repositorio()
            val id = enlaceDao.insertar(
                Enlace(
                    url = "https://www.facebook.com/share/r/1JqVPz7de",
                    titulo = "facebook.com",
                    categoriaId = cocinaId,
                    categoriaManual = true,
                ),
            )

            assertTrue(
                repo.actualizarTextoDesdeWebView(
                    id,
                    "Contratamos a una IA... y el primer día ya vendía croquetas en Marte...",
                ),
            )

            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals(
                "Contratamos a una IA... y el primer día ya vendía croquetas en Marte...",
                guardado.titulo,
            )
            assertEquals(cocinaId, guardado.categoriaId)
            assertTrue(guardado.categoriaManual)
        }

    // ---- Fix 4, bug 3: oEmbed con la URL canónica ----

    /**
     * prepararEnlace con un /share/r/ resuelve la URL antes de pedir
     * el oEmbed: el endpoint oEmbed se llama con la URL final
     * (m.facebook.com), no con la corta.
     *
     * El cliente falso simula la respuesta DESPUÉS de que OkHttp
     * siga el 302 (ver el comentario del test de
     * `resolverUrlCanonica` en `ServicioVistaPreviaTest`): para la
     * URL corta devuelve una 200 cuya petición es la URL final, con
     * cuerpo vacío (vista previa vacía, como el muro de login real).
     */
    @Test
    fun `prepararEnlace pide el oEmbed con la URL canonica de un share de Facebook`() =
        runBlocking {
            val clienteVistaPrevia = OkHttpClient.Builder()
                .addInterceptor { cadena ->
                    val url = cadena.request().url.toString()
                    val peticionFinal = if (url == "https://www.facebook.com/share/r/1JqVPz7de") {
                        cadena.request().newBuilder()
                            .url("https://m.facebook.com/share/r/1JqVPz7de")
                            .build()
                    } else {
                        cadena.request()
                    }
                    Response.Builder()
                        .request(peticionFinal)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody("text/html; charset=utf-8".toMediaType()))
                        .build()
                }
                .build()
            val repo = RepositorioEnlaces(
                enlaceDao,
                categoriaDao,
                ServicioVistaPrevia(cliente = clienteVistaPrevia),
                ServicioOEmbed(cliente = clienteOEmbedFalso(mapOf(
                    "https://graph.facebook.com/v27.0/oembed_video" to (200 to jsonFacebook),
                ))),
            )

            val preparado = repo.prepararEnlace("https://www.facebook.com/share/r/1JqVPz7de")

            assertTrue(preparado.oembed is ResultadoOEmbed.Exito)
            // La URL pedida al oEmbed lleva la canónica (m.facebook),
            // no la corta (www.facebook.com/share).
            assertTrue(oembedPedidas.any { it.contains("m.facebook.com") })
            assertFalse(oembedPedidas.any { it.contains("www.facebook.com") })
        }

    // ---- Fix 4, bug 2: el movimiento manual actualiza los pesos en Room ----

    /**
     * Mover un enlace a mano guarda la corrección y los pesos en Room,
     * y una cabeza nueva cargada desde esos pesos predice la categoría
     * para ese mismo texto: el aprendizaje sobrevive al reinicio.
     *
     * Fix 5, mina del diagnóstico (§6.3): los enlaces de esta prueba
     * llevan título con CONTENIDO REAL ("Nuevo juego para celular
     * gratis"), no solo el dominio. El caso exacto de Javier (título
     * "facebook.com") ya NO registra corrección: la cabeza aprendería
     * el dominio y, tras el reentrenamiento en reposo, todos los
     * facebook.com sin texto irían a Juegos (medido: ~0,64). Ese caso
     * tiene su propia prueba
     * (`mover un enlace con titulo solo-dominio no registra correccion`).
     *
     * Un paso en línea deja "Juegos" como argmax (por debajo del
     * umbral 0,6 del motor); tras el reentrenamiento en reposo (10
     * épocas) la confianza supera el umbral. Por eso el test comprueba
     * el argmax tras el movimiento y la confianza tras el
     * reentrenamiento.
     */
    @Test
    fun `mover un enlace a mano guarda la correccion y los pesos en Room`() =
        runBlocking {
            val etiquetas = listOf(
                "Cocina", "Videos de mecánica", "Videos de construcción",
                "Contenido XXX", "Entretenimiento", "Tecnología y celulares",
                "Autos y motos", "Salud y ejercicio", "Dinero y finanzas",
                "Noticias", "Juegos", "Hazlo tú mismo (DIY)",
                "Jardín y plantas", "Pesca y campo", "Moda y ropa", "Viajes",
            )
            val cabeza = CabezaAprendizaje(etiquetas)
            val servicioAprendizaje = ServicioAprendizaje(db.aprendizajeDao(), cabeza)
            val repo = RepositorioEnlaces(
                enlaceDao,
                categoriaDao,
                ServicioVistaPrevia(cliente = clienteFalso(emptyMap())),
                ServicioOEmbed(cliente = clienteOEmbedFalso(emptyMap())),
                servicioAprendizaje = servicioAprendizaje,
            )
            val id = enlaceDao.insertar(
                Enlace(
                    url = "https://www.facebook.com/share/r/1Bo58NQ",
                    titulo = "Nuevo juego para celular gratis",
                    descripcion = "",
                    categoriaId = tecnologiaId,
                    categoriaManual = false,
                ),
            )

            assertTrue(repo.cambiarCategoriaManual(id, juegosId))

            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals(juegosId, guardado.categoriaId)
            assertTrue(guardado.categoriaManual)
            assertEquals(1, servicioAprendizaje.contarCorrecciones())
            // Los pesos están en Room y la cabeza ya aprendió en
            // línea: su argmax para ese texto es "Juegos".
            val pesosGuardados = db.aprendizajeDao().obtenerPesos()
            assertTrue(pesosGuardados != null)
            val prediccion = cabeza.predecir("Nuevo juego para celular gratis")
            assertTrue(prediccion != null)
            assertEquals("Juegos", etiquetas[prediccion!!.indiceClase])
            // Un SEGUNDO movimiento también persiste los pesos (Fix 4:
            // guardarPesos era @Insert sin REPLACE y la segunda
            // corrección chocaba con la PK id=1).
            val id2 = enlaceDao.insertar(
                Enlace(
                    url = "https://www.facebook.com/share/r/1JqVPz7de",
                    titulo = "Gameplay de Minecraft construyendo mi base",
                    descripcion = "",
                    categoriaId = tecnologiaId,
                    categoriaManual = false,
                ),
            )
            assertTrue(repo.cambiarCategoriaManual(id2, juegosId))
            assertEquals(2, servicioAprendizaje.contarCorrecciones())
            assertTrue(db.aprendizajeDao().obtenerPesos() != null)
            // Tras el reentrenamiento en reposo, la confianza supera
            // el umbral del motor (0,6): la corrección se aplica.
            assertEquals(2, servicioAprendizaje.reentrenarEnReposo())
            val trasReposo = cabeza.predecir("Nuevo juego para celular gratis")
            assertTrue(trasReposo != null)
            assertEquals("Juegos", etiquetas[trasReposo!!.indiceClase])
            assertTrue(trasReposo.confianza >= 0.6f)
            // Y una cabeza nueva cargada SOLO desde los pesos de Room
            // predice lo mismo: el aprendizaje sobrevive al reinicio.
            val cabezaNueva = CabezaAprendizaje.desdeBytes(
                etiquetas,
                db.aprendizajeDao().obtenerPesos()?.pesos,
            )
            val prediccionNueva = cabezaNueva.predecir("Nuevo juego para celular gratis")
            assertTrue(prediccionNueva != null)
            assertEquals("Juegos", etiquetas[prediccionNueva!!.indiceClase])
        }

    // ---- Fix 5, mina del diagnóstico (§6.3) ----

    /**
     * El caso exacto de Javier (el vídeo de juego que movió a mano):
     * el enlace se guardó con título "facebook.com" (solo el dominio)
     * y se mueve a "Juegos". El movimiento SÍ se aplica (la categoría
     * cambia y queda manual), pero NO se registra corrección: la
     * cabeza aprendería "facebook.com" -> Juegos y, tras el
     * reentrenamiento en reposo, TODOS los enlaces facebook.com sin
     * texto irían a Juegos. Sin corrección registrada, la cabeza no
     * aprende nada del dominio.
     */
    @Test
    fun `mover un enlace con titulo solo-dominio no registra correccion`() =
        runBlocking {
            val etiquetas = listOf(
                "Cocina", "Videos de mecánica", "Videos de construcción",
                "Contenido XXX", "Entretenimiento", "Tecnología y celulares",
                "Autos y motos", "Salud y ejercicio", "Dinero y finanzas",
                "Noticias", "Juegos", "Hazlo tú mismo (DIY)",
                "Jardín y plantas", "Pesca y campo", "Moda y ropa", "Viajes",
            )
            val cabeza = CabezaAprendizaje(etiquetas)
            val servicioAprendizaje = ServicioAprendizaje(db.aprendizajeDao(), cabeza)
            val repo = RepositorioEnlaces(
                enlaceDao,
                categoriaDao,
                ServicioVistaPrevia(cliente = clienteFalso(emptyMap())),
                ServicioOEmbed(cliente = clienteOEmbedFalso(emptyMap())),
                servicioAprendizaje = servicioAprendizaje,
            )
            val id = enlaceDao.insertar(
                Enlace(
                    url = "https://www.facebook.com/share/r/1JqVPz7de",
                    titulo = "facebook.com",
                    descripcion = "",
                    categoriaId = tecnologiaId,
                    categoriaManual = false,
                ),
            )

            assertTrue(repo.cambiarCategoriaManual(id, juegosId))

            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals(juegosId, guardado.categoriaId)
            assertTrue(guardado.categoriaManual)
            assertEquals(0, servicioAprendizaje.contarCorrecciones())
            assertNull(db.aprendizajeDao().obtenerPesos())
            assertNull(cabeza.predecir("facebook.com"))
        }

    // ---- Fix 5: póster resuelto (fuente de cuadros resuelta) ----

    private val urlReel = "https://www.facebook.com/reel/1168661422485798/"
    private val urlOgImage =
        "https://scontent-ord5-1.xx.fbcdn.net/v/t15.5256-10/896273245_abc_n.jpg?oh=123&oe=6B0AA877"

    /** Muro de login de Facebook (forma real: título genérico, sin og tags). */
    private val htmlMuroError =
        "<html><head><title>Error</title></head><body></body></html>"

    /** Página del rastreador con og:title que clasifica (Cocina) y og:image. */
    private val htmlRastreadorPaella = """
        <html><head>
        <meta property="og:title" content="Receta de paella valenciana paso a paso">
        <meta property="og:description" content="La receta tradicional paso a paso.">
        <meta property="og:image" content="$urlOgImage">
        </head><body></body></html>
    """.trimIndent()

    /** Página del rastreador con og:title sin señal y og:image. */
    private val htmlRastreadorMiraEsto = """
        <html><head>
        <meta property="og:title" content="Mira esto">
        <meta property="og:image" content="$urlOgImage">
        </head><body></body></html>
    """.trimIndent()

    /** PNG 1x1 válido (BitmapFactory lo decodifica en Robolectric). */
    private val png1x1: ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    /** Cliente que sirve HTML por URL y bytes de imagen por URL. */
    private fun clientePosterFalso(
        paginas: Map<String, String>,
        imagenes: Map<String, ByteArray>,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { cadena ->
            val url = cadena.request().url.toString()
            val bytes = imagenes[url]
            if (bytes != null) {
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bytes.toResponseBody("image/jpeg".toMediaType()))
                    .build()
            } else {
                val cuerpo = paginas[url] ?: ""
                Response.Builder()
                    .request(cadena.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(cuerpo.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
        }
        .build()

    /** Repositorio con póster resuelto y visión por palabras clave (determinista). */
    private fun repositorioConPoster(
        paginaRastreador: String?,
        senales: SenalesVisuales? = null,
        servicioPoster: ServicioPosterResuelto? = null,
    ): RepositorioEnlaces {
        val poster = servicioPoster ?: paginaRastreador?.let {
            ServicioPosterResuelto(cliente = clienteFalso(mapOf(urlReel to it)))
        }
        return RepositorioEnlaces(
            enlaceDao,
            categoriaDao,
            ServicioVistaPrevia(
                cliente = clientePosterFalso(
                    paginas = mapOf(urlReel to htmlMuroError),
                    imagenes = mapOf(urlOgImage to png1x1),
                ),
            ),
            ServicioOEmbed(cliente = clienteOEmbedFalso(emptyMap())),
            directorioMiniaturas,
            motorClasificacion = MotorPalabrasClave,
            analizadorVisual = AnalizadorFalso(senales),
            clasificadorVision = ClasificadorVision(MotorPalabrasClave),
            servicioPosterResuelto = poster,
        )
    }

    /** Póster falso sin og:image pero con .mp4: extrae el cuadro escribiendo el PNG. */
    private class PosterSoloMp4Falso : ServicioPosterResuelto() {
        override suspend fun resolver(urlEnlace: String): PosterResuelto? {
            if (urlEnlace != "https://www.facebook.com/reel/1168661422485798/") {
                return null
            }
            return PosterResuelto(
                vistaPrevia = VistaPrevia(titulo = "Mira esto"),
                urlsVideo = listOf(
                    "https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/def.mp4?oe=6B0A93AD",
                ),
            )
        }

        override suspend fun extraerCuadroDeVideo(
            urlVideo: String,
            destino: File,
        ): Boolean {
            destino.parentFile?.mkdirs()
            destino.writeBytes(
                java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                ),
            )
            return true
        }
    }

    /**
     * El póster resuelto de un reel canónico guarda el título real,
     * la foto real en local y clasifica por el título: la vista
     * previa normal era el muro de login.
     */
    @Test
    fun `agregarEnlace con poster resuelto guarda titulo real imagen local y clasifica`() =
        runBlocking {
            val repo = repositorioConPoster(htmlRastreadorPaella)

            val resultado = repo.agregarEnlace(urlReel)

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Receta de paella valenciana paso a paso", guardado.titulo)
            assertEquals("La receta tradicional paso a paso.", guardado.descripcion)
            assertEquals(cocinaId, guardado.categoriaId)
            assertTrue(guardado.imagenLocal?.isNotBlank() == true)
            assertTrue(File(guardado.imagenLocal!!).exists())
        }

    /**
     * El póster resuelto sin título útil clasifica por visión + OCR
     * de la foto real: el enlace sale de "Otros" a Cocina por el
     * texto quemado en el vídeo, y ese texto queda como descripción.
     */
    @Test
    fun `agregarEnlace con poster sin titulo util clasifica por vision OCR de la foto real`() =
        runBlocking {
            val repo = repositorioConPoster(
                htmlRastreadorMiraEsto,
                SenalesVisuales(
                    etiquetas = emptyList(),
                    textoOcr = "Receta de pasta casera en 15 minutos",
                ),
            )

            val resultado = repo.agregarEnlace(urlReel)

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Mira esto", guardado.titulo)
            assertEquals(cocinaId, guardado.categoriaId)
            assertEquals("Receta de pasta casera en 15 minutos", guardado.descripcion)
            assertTrue(guardado.imagenLocal?.isNotBlank() == true)
            assertTrue(File(guardado.imagenLocal!!).exists())
        }

    /**
     * Un `/share/r/` no resuelve póster (el hueco pendiente: su
     * redirección no revela la URL canónica): el enlace se guarda
     * con el dominio, en "Otros" y sin imagen local, como antes.
     */
    @Test
    fun `agregarEnlace share r no resuelve poster y guarda con el dominio`() =
        runBlocking {
            val repo = repositorioConPoster(htmlRastreadorPaella)

            val resultado =
                repo.agregarEnlace("https://www.facebook.com/share/r/1JqVPz7de")

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("facebook.com", guardado.titulo)
            assertEquals(otrosId, guardado.categoriaId)
            assertNull(guardado.imagenLocal)
        }

    /**
     * Sin `og:image` pero con `.mp4` directo, el cuadro extraído del
     * vídeo se guarda como miniatura local y la visión + OCR de ese
     * cuadro clasifica el enlace.
     */
    @Test
    fun `agregarEnlace con poster solo mp4 guarda el cuadro extraido y clasifica por vision`() =
        runBlocking {
            val repo = repositorioConPoster(
                paginaRastreador = null,
                senales = SenalesVisuales(
                    etiquetas = emptyList(),
                    textoOcr = "Receta de pasta casera en 15 minutos",
                ),
                servicioPoster = PosterSoloMp4Falso(),
            )

            val resultado = repo.agregarEnlace(urlReel)

            assertTrue(resultado is ResultadoAgregarEnlace.Exito)
            val id = (resultado as ResultadoAgregarEnlace.Exito).id
            val guardado = enlaceDao.obtenerPorId(id)!!
            assertEquals("Mira esto", guardado.titulo)
            assertEquals(cocinaId, guardado.categoriaId)
            assertTrue(guardado.imagenLocal?.isNotBlank() == true)
            assertTrue(File(guardado.imagenLocal!!).exists())
            assertTrue(guardado.imagenLocal!!.endsWith(".jpg"))
        }
}
