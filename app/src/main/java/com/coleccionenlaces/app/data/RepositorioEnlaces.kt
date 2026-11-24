package com.coleccionenlaces.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.coleccionenlaces.app.domain.AnalizadorVisual
import com.coleccionenlaces.app.domain.Clasificador
import com.coleccionenlaces.app.domain.ClasificadorVision
import com.coleccionenlaces.app.domain.MotorClasificacion
import com.coleccionenlaces.app.domain.MotorPalabrasClave
import com.coleccionenlaces.app.domain.PosterResuelto
import com.coleccionenlaces.app.domain.ResultadoOEmbed
import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
import com.coleccionenlaces.app.domain.detectarVideoNoDisponible
import com.coleccionenlaces.app.domain.esTextoSoloDominio
import com.coleccionenlaces.app.domain.esUrlCortaFacebookCompartida
import com.coleccionenlaces.app.domain.proveedorOEmbedDe
import com.coleccionenlaces.app.util.esUrlImagenFirmada
import com.coleccionenlaces.app.util.primeraLinea
import com.coleccionenlaces.app.util.restoTrasPrimeraLinea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI

/**
 * Resultado de [RepositorioEnlaces.agregarEnlace]: o se guardó el
 * enlace ([Exito] con su id) o no se pudo guardar ([Error] con el
 * tipo). El error nunca rompe el flujo: la UI lo muestra y sigue.
 */
sealed interface ResultadoAgregarEnlace {
    data class Exito(val id: Long) : ResultadoAgregarEnlace
    data class Error(val tipo: TipoErrorAgregarEnlace) : ResultadoAgregarEnlace
}

/** Tipos de error al agregar un enlace. */
enum class TipoErrorAgregarEnlace {
    /** La URL no es una http(s) válida: no se guarda nada. */
    URL_INVALIDA,

    /** Fallo al guardar en la base de datos. */
    ERROR_AL_GUARDAR,
}

/** Resultado de [RepositorioEnlaces.prepararEnlace]: la vista previa obtenida (o su
 * fallo distinguido) y el resultado oEmbed para esta misma URL. El diálogo usa
 * esto para guardar sin descargar la página dos veces (una al comprobar y
 * otra al guardar). No hay título sugerido para editar: el proceso es 100%
 * automático (corrección 1.0.1) — nadie escribe títulos. */
data class EnlacePreparado(
    val vistaPrevia: ResultadoVistaPrevia,
    /**
     * Resultado oEmbed para esta misma URL (corrección 1.0.1, punto 3),
     * o null si la URL no es de YouTube, TikTok, Facebook, Instagram,
     * X, Vimeo ni Dailymotion o si la vista previa ya trajo título
     * (entonces no hizo falta pedirlo). Se guarda aquí para no
     * pedirlo dos veces (una al comprobar y otra al guardar).
     */
    val oembed: ResultadoOEmbed? = null,
    /**
     * Póster resuelto de un vídeo de Facebook (Fix 5, fuente de
     * cuadros resuelta), o null si la URL no es un vídeo canónico de
     * Facebook o no se pudo resolver. Se guarda aquí para no
     * resolverlo dos veces (una al comprobar y otra al guardar).
     */
    val posterResuelto: PosterResuelto? = null,
)
sealed interface ResultadoCarpeta {
    data class Exito(val id: Long) : ResultadoCarpeta
    data class Error(val tipo: TipoErrorCarpeta) : ResultadoCarpeta
}

/** Tipos de error al crear o renombrar una carpeta propia. */
enum class TipoErrorCarpeta {
    /** El nombre está vacío (o solo tiene espacios). */
    NOMBRE_VACIO,

    /** Ya existe otra carpeta con ese nombre (sin distinguir mayúsculas). */
    NOMBRE_DUPLICADO,

    /** Ya hay 20 categorías: no cabe ninguna carpeta más. */
    LIMITE_ALCANZADO,

    /** La carpeta es una de las 17 predefinidas: no se puede renombrar ni borrar. */
    NO_ES_PROPIA,

    /** La carpeta no existe. */
    NO_EXISTE,
}

/**
 * Repositorio (Fase 5): ÚNICA fuente de datos de la app.
 *
 * Une [AppDatabase] ([EnlaceDao] y [CategoriaDao]), el
 * [ServicioVistaPrevia] y el [Clasificador]. La UI (Fase 6) nunca
 * toca los DAOs ni el servicio directamente: todo pasa por aquí.
 *
 * ## Decisiones de diseño
 * - **Validación de URL delegada al servicio.** [agregarEnlace] no
 *   reimplementa la validación: llama al servicio de vista previa y,
 *   si este devuelve `URL_INVALIDA`, no guarda nada. El servicio ya
 *   valida sin tocar la red, así que hay una sola regla en un solo
 *   sitio.
 * - **Vista previa vacía = dominio como título.** Si el sitio no
 *   ofreció título (o la descarga falló por cualquier motivo que no
 *   sea URL inválida), el enlace se guarda igual con el host de la
 *   URL como título y sin imagen. Solo `URL_INVALIDA` impide guardar.
 *   (Corrección 1.0.1: el título es 100% automático, sin edición
 *   manual — ver el orden de fuentes en [agregarEnlace].)
 * - **Título automático, en orden.** El título sale de la primera
 *   fuente que lo tenga: título oEmbed (YouTube/TikTok) -> título
 *   de la vista previa (og:title) -> primera línea del texto que
 *   acompañe al enlace al compartirlo -> texto visible del
 *   reproductor oEmbed leído en el WebView ([actualizarTextoDesdeWebView])
 *   -> dominio. Para Facebook el título de la vista previa nunca
 *   existe (muro de login), así que el orden efectivo es el pedido
 *   por Javier: oEmbed -> texto compartido -> texto del reproductor
 *   -> dominio.
 * - **Clasificación solo al agregar y al reclasificar.** Un enlace
 *   nuevo tiene `categoriaManual = false`, así que se clasifica con
 *   [Clasificador.clasificarEnlace] y el nombre resultante se
 *   resuelve a `categoriaId` con `obtenerPorNombre`; si el nombre no
 *   se resuelve (no debería pasar: las 17 están sembradas), se usa
 *   "Otros". [actualizarEnlace] NO reclasifica: guarda el enlace tal
 *   como llega, así la categoría manual nunca se sobrescribe al
 *   editar. [reclasificarEnlace] es la única vía que vuelve a
 *   clasificar, y devuelve `false` sin tocar nada si el enlace es
 *   manual.
 * - **Categoría siempre resuelta al exponer.** [enlacesConCategoria]
 *   (y las consultas de búsqueda, categoría y favoritos) devuelven
 *   [EnlaceConCategoria]: cada enlace acompañado de su [Categoria]
 *   con nombre, color e ícono. Un `categoriaId` null (carpeta
 *   borrada) se expone como "Otros" (ver [EnlaceConCategoria]).
 * - **Contadores resueltos.** [contadoresPorCategoria] es un mapa
 *   `categoriaId -> total` donde los enlaces sin categoría cuentan
 *   en "Otros". La UI lee el contador de cada chip con `map[id] ?: 0`.
 */
class RepositorioEnlaces(
    private val enlaceDao: EnlaceDao,
    private val categoriaDao: CategoriaDao,
    private val servicioVistaPrevia: ServicioVistaPrevia,
    private val servicioOEmbed: ServicioOEmbed = ServicioOEmbed(),
    /**
     * Carpeta donde se guardan las miniaturas locales (corrección
     * 1.0.1, punto 3): el cuadro capturado del reproductor oEmbed y
     * las imágenes de vista previa con firma temporal descargadas al
     * guardar. Null en las pruebas que no la necesitan: sin carpeta
     * no se guarda nada en local y la tarjeta usa la URL remota.
     */
    private val directorioMiniaturas: File? = null,
    /**
     * Motor de clasificación (Fix 3). En producción es
     * [com.coleccionenlaces.app.domain.ClasificadorInteligente]
     * (modelo DistilBERT -> cabeza aprendida -> "Otros"). El valor
     * por defecto es el clasificador por palabras clave y existe SOLO
     * para las pruebas del repositorio, que comprueban el cableado
     * con resultados deterministas; en la app nunca se usa.
     */
    private val motorClasificacion: MotorClasificacion = MotorPalabrasClave,
    /**
     * Servicio de aprendizaje (Fix 3). Null en las pruebas que no lo
     * necesitan: sin él, mover un enlace no registra corrección.
     */
    private val servicioAprendizaje: ServicioAprendizaje? = null,
    /**
     * Pre-comprobación del vídeo de Facebook (M5, Fix 5). Null en las
     * pruebas que no la necesitan: sin él, el vídeo se considera
     * disponible y el WebView se monta como antes.
     */
    private val servicioComprobacionVideo: ServicioComprobacionVideo? = null,
    /**
     * Analizador visual (Fix 5, ML Kit en el dispositivo). Null en
     * las pruebas que no lo necesitan: sin él no hay clasificación
     * por visión y el cuadro se guarda sin más.
     */
    private val analizadorVisual: AnalizadorVisual? = null,
    /**
     * Clasificador por visión (Fix 5): decide la categoría a partir
     * de las señales del analizador. Null sin analizador.
     */
    private val clasificadorVision: ClasificadorVision? = null,
    /**
     * Póster resuelto de un vídeo de Facebook (Fix 5, fuente de
     * cuadros resuelta). Null en las pruebas que no lo necesitan: sin
     * él no se resuelve ningún póster y el flujo sigue como antes
     * (vista previa + oEmbed + WebView).
     */
    private val servicioPosterResuelto: ServicioPosterResuelto? = null,
) {

    /** Todas las categorías, en el orden de los chips y secciones. */
    val categorias: Flow<List<Categoria>> = categoriaDao.obtenerTodas()

    /** Todos los enlaces con su categoría resuelta, del más reciente al más antiguo. */
    val enlacesConCategoria: Flow<List<EnlaceConCategoria>> =
        enlaceDao.obtenerTodos().conCategoria()

    /**
     * Búsqueda por texto en título, descripción y nombre de categoría
     * (la hace el DAO; texto vacío devuelve todo), con la categoría
     * de cada resultado ya resuelta.
     */
    fun buscarEnlaces(texto: String): Flow<List<EnlaceConCategoria>> =
        enlaceDao.buscar(texto).conCategoria()

    /** Enlaces de una categoría, con la categoría ya resuelta. */
    fun obtenerEnlacesPorCategoria(categoriaId: Long): Flow<List<EnlaceConCategoria>> =
        enlaceDao.obtenerPorCategoria(categoriaId).conCategoria()

    /** Solo los favoritos, con la categoría ya resuelta. */
    fun obtenerFavoritos(): Flow<List<EnlaceConCategoria>> =
        enlaceDao.obtenerFavoritos().conCategoria()

    /**
     * Contador de enlaces por categoría (`categoriaId -> total`). Los
     * enlaces sin categoría (null) cuentan en la categoría "Otros".
     */
    val contadoresPorCategoria: Flow<Map<Long, Int>> =
        combine(enlaceDao.contarPorCategoria(), categoriaDao.obtenerTodas()) { conteos, categorias ->
            val otrosId = categorias.firstOrNull { it.nombre == Clasificador.OTROS }?.id
                ?: ID_OTROS_SIN_FILA
            val idsValidos = categorias.map { it.id }.toSet()
            val resultado = mutableMapOf<Long, Int>()
            for (conteo in conteos) {
                val id = conteo.categoriaId
                val clave = if (id != null && id in idsValidos) id else otrosId
                resultado[clave] = (resultado[clave] ?: 0) + conteo.total
            }
            resultado
        }

    /** Un enlace guardado, o null si no existe. */
    suspend fun obtenerEnlacePorId(id: Long): Enlace? = enlaceDao.obtenerPorId(id)

    /**
     * Obtiene solo la vista previa de una URL, sin guardar nada.
     *
     * Es el primer paso del diálogo de agregar (Fix 1): con el
     * resultado, la UI decide si guarda directamente (vista previa
     * correcta) o guarda igual con el título automático (vista
     * previa fallida: el sitio no dejó leer el enlace; el título
     * sale del texto compartido o del dominio). La URL inválida se
     * distingue aquí igual que en [agregarEnlace]: con ella no se
     * guarda nada.
     *
     * Si la vista previa viene vacía y la URL es de YouTube, TikTok,
     * Facebook, Instagram, X, Vimeo o Dailymotion, también se pide
     * el oEmbed (corrección 1.0.1, punto 3): YouTube, Vimeo y
     * Dailymotion dan título y miniatura, y Facebook, TikTok,
     * Instagram y X dan el HTML del reproductor para capturar su
     * cuadro.
     *
     * Fix 5: si la vista previa no trajo título (el muro de login de
     * Facebook no trae ninguno), se intenta además el póster
     * resuelto: la ruta del rastreador sobre la URL canónica del
     * vídeo entrega el título real y la foto real del vídeo.
     */
    suspend fun prepararEnlace(url: String): EnlacePreparado {
        val urlLimpia = url.trim()
        val resultado = servicioVistaPrevia.obtenerVistaPrevia(urlLimpia)
        val oembed = if (resultado.vistaPrevia.estaVacia) {
            obtenerOEmbed(urlParaOEmbed(urlLimpia))
        } else {
            null
        }
        val poster = if (resultado.vistaPrevia.titulo == null) {
            servicioPosterResuelto?.resolver(urlLimpia)
        } else {
            null
        }
        return EnlacePreparado(resultado, oembed, poster)
    }

    /**
     * URL que se le pasa al oEmbed (Fix 4, bug 3): un enlace corto de
     * compartir de Facebook se resuelve primero a su URL final; el
     * resto se usa tal cual. La resolución nunca rompe el flujo: si
     * falla, devuelve la URL original.
     */
    private suspend fun urlParaOEmbed(urlLimpia: String): String =
        if (esUrlCortaFacebookCompartida(urlLimpia)) {
            servicioVistaPrevia.resolverUrlCanonica(urlLimpia)
        } else {
            urlLimpia
        }

    /**
     * Pide el oEmbed de una URL si su proveedor lo tiene (YouTube,
     * TikTok, Facebook, Instagram, X, Vimeo o Dailymotion); null en
     * caso contrario, sin tocar la red.
     */
    private suspend fun obtenerOEmbed(urlLimpia: String): ResultadoOEmbed? {
        if (proveedorOEmbedDe(urlLimpia) == null) return null
        return servicioOEmbed.obtenerVideo(urlLimpia)
    }

    /**
     * Agrega un enlace: valida la URL, obtiene la vista previa,
     * clasifica y guarda.
     *
     * - URL inválida -> [ResultadoAgregarEnlace.Error] con
     *   `URL_INVALIDA`, sin guardar nada.
     * - Cualquier otro fallo de vista previa (sin internet, timeout,
     *   sitio sin vista previa) -> el enlace se guarda igual, con el
     *   título automático (ver la sobrecarga) y sin imagen.
     * - La categoría es la del clasificador (el enlace nuevo no es
     *   manual); si el nombre no se resuelve en la base, "Otros".
     */
    suspend fun agregarEnlace(url: String): ResultadoAgregarEnlace =
        agregarEnlace(url, textoCompartido = null, preparado = null)

    /**
     * Agrega un enlace con el texto que acompañaba a la URL al
     * compartirla y/o con una vista previa ya comprobada
     * (corrección 1.0.1: proceso 100% automático, sin edición
     * manual de título ni descripción).
     *
     * - [textoCompartido]: el texto que venía junto a la URL en el
     *   intent de compartir (p. ej. el texto de la publicación de
     *   Facebook) o al pegar. Es una fuente automática de título y
     *   descripción, nunca algo que el usuario escriba en la app.
     * - [preparado]: el resultado de [prepararEnlace] para esta
     *   misma URL, para no descargar la página dos veces (una al
     *   comprobar y otra al guardar). Con null, la vista previa se
     *   descarga aquí.
     * - El título EFECTIVO sale de la primera fuente que lo tenga:
     *   título oEmbed (YouTube, Vimeo, Dailymotion) -> título de la
     *   vista previa (og:title) -> primera línea del texto
     *   compartido -> dominio. La descripción es la de la vista
     *   previa o, si no hay, el texto compartido: completo cuando
     *   no se usó como título, o su resto tras la primera línea
     *   cuando sí. El título y la descripción efectivos son los
     *   que alimentan al clasificador. El texto visible del
     *   reproductor oEmbed llega después, al pintarse la tarjeta,
     *   por [actualizarTextoDesdeWebView].
     *
     * oEmbed (corrección 1.0.1, punto 3): si la vista previa viene
     * vacía y la URL es de YouTube, TikTok, Facebook, Instagram, X,
     * Vimeo o Dailymotion, se usa el oEmbed: su título (YouTube,
     * Vimeo, Dailymotion) tiene prioridad sobre el dominio; su HTML
     * (Facebook, TikTok, Instagram, X) se guarda en
     * [Enlace.htmlVideo] para capturar su cuadro en un WebView; su
     * miniatura (YouTube, Vimeo, Dailymotion) se descarga a local
     * al guardar. Una imagen de vista previa con firma temporal
     * también se descarga a local: su URL se rompe a los días y el
     * archivo local no.
     */
    suspend fun agregarEnlace(
        url: String,
        textoCompartido: String?,
        preparado: EnlacePreparado?,
    ): ResultadoAgregarEnlace {
        val urlLimpia = url.trim()
        return try {
            val resultadoVistaPrevia = preparado?.vistaPrevia
                ?: servicioVistaPrevia.obtenerVistaPrevia(urlLimpia)
            if (resultadoVistaPrevia is ResultadoVistaPrevia.Error &&
                resultadoVistaPrevia.tipo == TipoErrorVistaPrevia.URL_INVALIDA
            ) {
                return ResultadoAgregarEnlace.Error(TipoErrorAgregarEnlace.URL_INVALIDA)
            }
            val vistaPrevia = resultadoVistaPrevia.vistaPrevia
            val oembed = if (vistaPrevia.estaVacia) {
                preparado?.oembed ?: obtenerOEmbed(urlParaOEmbed(urlLimpia))
            } else {
                null
            }
            val oembedExito = oembed as? ResultadoOEmbed.Exito
            // Fix 5: póster resuelto — el de la preparación o, si no se
            // preparó, resolverlo aquí. Solo hace falta cuando la vista
            // previa no trajo título (el muro de login no trae ninguno);
            // para el resto de URLs devuelve null sin tocar la red.
            val poster = preparado?.posterResuelto
                ?: if (vistaPrevia.titulo == null) {
                    servicioPosterResuelto?.resolver(urlLimpia)
                } else {
                    null
                }
            val posterPrevia = poster?.vistaPrevia
            val host = extraerHost(urlLimpia) ?: urlLimpia
            val tituloCompartido = primeraLinea(textoCompartido)
            val titulo = oembedExito?.titulo?.trim().takeUnless { it.isNullOrBlank() }
                ?: vistaPrevia.titulo?.trim().takeUnless { it.isNullOrBlank() }
                ?: posterPrevia?.titulo?.trim().takeUnless { it.isNullOrBlank() }
                ?: tituloCompartido
                ?: host
            val tituloEsTextoCompartido = titulo == tituloCompartido
            val descripcion = vistaPrevia.descripcion?.trim().takeUnless { it.isNullOrBlank() }
                ?: posterPrevia?.descripcion?.trim().takeUnless { it.isNullOrBlank() }
                ?: if (tituloEsTextoCompartido) {
                    restoTrasPrimeraLinea(textoCompartido)
                } else {
                    textoCompartido?.trim().takeUnless { it.isNullOrBlank() }
                }
                ?: ""
            val enlaceSinCategoria = Enlace(
                url = urlLimpia,
                titulo = titulo,
                descripcion = descripcion,
                urlImagen = vistaPrevia.urlImagen,
                categoriaId = null,
                categoriaManual = false,
                htmlVideo = oembedExito?.html,
            )
            val nombreCategoria = motorClasificacion.clasificarEnlace(enlaceSinCategoria)
            val categoriaId = nombreCategoria?.let { categoriaDao.obtenerPorNombre(it)?.id }
                ?: categoriaDao.obtenerPorNombre(Clasificador.OTROS)?.id
            val id = enlaceDao.insertar(enlaceSinCategoria.copy(categoriaId = categoriaId))
            var enlaceGuardado =
                enlaceSinCategoria.copy(id = id, categoriaId = categoriaId)
            // Miniatura local: la del oEmbed (YouTube) siempre, la del
            // póster resuelto (Fix 5: la foto real del vídeo, cuya URL
            // firmada se rompe a los días) antes que la de la vista
            // previa —cuando hay póster, la vista previa era el muro
            // de login y su imagen no es el vídeo—, y la de la vista
            // previa solo si su URL lleva firma temporal.
            val imagenADescargar = oembedExito?.urlMiniatura
                ?: posterPrevia?.urlImagen
                ?: vistaPrevia.urlImagen?.takeIf { esUrlImagenFirmada(it) }
            var cuadroResueltoGuardado = false
            if (imagenADescargar != null) {
                val ruta = descargarImagenLocal(id, imagenADescargar)
                if (ruta != null) {
                    enlaceGuardado = enlaceGuardado.copy(imagenLocal = ruta)
                    enlaceDao.actualizar(enlaceGuardado)
                    cuadroResueltoGuardado =
                        imagenADescargar == posterPrevia?.urlImagen
                }
            }
            // Sin og:image pero con .mp4 directo: sacar un cuadro del
            // vídeo y guardarlo como miniatura local. La rendition se
            // elige por bitrate (investigación de 1 hora, hilo B): la
            // primera .mp4 puede ser solo audio.
            if (!cuadroResueltoGuardado && poster != null &&
                poster.urlsVideo.isNotEmpty()
            ) {
                val destino = directorioMiniaturas?.let { carpeta ->
                    carpeta.mkdirs()
                    File(carpeta, "miniatura-$id.jpg")
                }
                val urlVideo = servicioPosterResuelto
                    ?.elegirVideoPorBitrate(poster.urlsVideo)
                if (destino != null && urlVideo != null &&
                    servicioPosterResuelto?.extraerCuadroDeVideo(
                        urlVideo, destino,
                    ) == true
                ) {
                    enlaceGuardado =
                        enlaceGuardado.copy(imagenLocal = destino.absolutePath)
                    enlaceDao.actualizar(enlaceGuardado)
                    cuadroResueltoGuardado = true
                }
            }
            // Fix 5: visión + OCR sobre la foto real guardada (póster
            // resuelto o cuadro del .mp4): si el enlace quedó en
            // "Otros", mirar el vídeo puede sacarlo de ahí. Se
            // decodifica el archivo local; si no se puede decodificar,
            // no se toca nada.
            if (cuadroResueltoGuardado) {
                val rutaImagen = enlaceGuardado.imagenLocal
                val bitmap = rutaImagen?.let {
                    withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it) }
                }
                if (bitmap != null) {
                    withContext(Dispatchers.IO) {
                        clasificarPorVisionSiOtros(enlaceGuardado, bitmap)
                    }
                    bitmap.recycle()
                }
            }
            ResultadoAgregarEnlace.Exito(id)
        } catch (_: Exception) {
            ResultadoAgregarEnlace.Error(TipoErrorAgregarEnlace.ERROR_AL_GUARDAR)
        }
    }

    /**
     * Guarda el cuadro capturado del reproductor oEmbed como
     * miniatura local del enlace (corrección 1.0.1, punto 3): el
     * WebView de la tarjeta captura el cuadro y lo entrega aquí
     * como bitmap; se guarda como PNG en [directorioMiniaturas] y
     * la ruta queda en [Enlace.imagenLocal], que la tarjeta
     * prefiere a la URL remota. Devuelve false si el enlace no
     * existe o si no se pudo guardar el archivo (sin carpeta
     * configurada, por ejemplo).
     */
    suspend fun guardarMiniaturaVideo(enlaceId: Long, bitmap: Bitmap): Boolean {
        val enlace = enlaceDao.obtenerPorId(enlaceId) ?: return false
        val ruta = withContext(Dispatchers.IO) {
            guardarBytesLocal(enlaceId, bitmapABytes(bitmap), "png")
        } ?: return false
        enlaceDao.actualizar(enlace.copy(imagenLocal = ruta))
        // Fix 5: visión + OCR — el cuadro ya está guardado; si el
        // enlace está en "Otros", mirar el vídeo puede sacarlo de ahí.
        // Se pasa el enlace YA actualizado (con imagenLocal): si el
        // gancho volviera a guardar el enlace original, borraría la
        // ruta de la miniatura recién guardada.
        withContext(Dispatchers.IO) {
            clasificarPorVisionSiOtros(enlace.copy(imagenLocal = ruta), bitmap)
        }
        return true
    }

    /**
     * Pre-comprobación M5 (Fix 5): ¿el vídeo de Facebook de este
     * enlace se puede incrustar? Devuelve DISPONIBLE si el enlace no
     * existe, si no es de Facebook o si no hay servicio de
     * comprobación.
     */
    suspend fun comprobarVideoDisponible(enlaceId: Long): EstadoVideo {
        val enlace = enlaceDao.obtenerPorId(enlaceId) ?: return EstadoVideo.DISPONIBLE
        return servicioComprobacionVideo?.comprobar(enlace.url)
            ?: EstadoVideo.DISPONIBLE
    }

    /**
     * Clasificación por visión + OCR (Fix 5): si el enlace está en
     * "Otros" y su categoría no es manual, analiza el cuadro
     * capturado (OCR del texto quemado en el vídeo) y, si hay señal,
     * lo mueve a la categoría detectada. Las etiquetas de imagen se
     * quitaron (ola 2: ML Kit 0/3 vídeos); el OCR es el único
     * respaldo visual, y solo con confianza >= 0,85.
     *
     * El texto del OCR se guarda como descripción cuando está vacía:
     * así la clasificación es estable (una reclasificación posterior
     * por texto reproduce el mismo resultado) y la tarjeta muestra de
     * qué va el vídeo. La pantalla de error nunca se clasifica: el
     * analizador la detecta por OCR de los píxeles y el clasificador
     * de visión la rechaza. Nunca lanza: sin analizador o sin señal,
     * no se toca nada.
     */
    private suspend fun clasificarPorVisionSiOtros(enlace: Enlace, bitmap: Bitmap) {
        if (enlace.categoriaManual) return
        // categoriaId es Long? (null si la carpeta se borró): solo se
        // actúa si el enlace está en "Otros" o sin carpeta.
        val categoriaActual = enlace.categoriaId?.let { categoriaDao.obtenerPorId(it)?.nombre }
        if (categoriaActual != null && categoriaActual != Clasificador.OTROS) return
        val vision = clasificadorVision ?: return
        val analizador = analizadorVisual ?: return
        val senales = try {
            analizador.analizar(bitmap)
        } catch (_: Exception) {
            null
        } ?: return
        if (senales.esPantallaError) return
        val nombre = vision.clasificarPorVision(senales)
        if (nombre == Clasificador.OTROS) return
        val categoriaId = categoriaDao.obtenerPorNombre(nombre)?.id ?: return
        val descripcionNueva =
            if (enlace.descripcion.isBlank() && senales.textoOcr.isNotBlank()) {
                senales.textoOcr.trim().take(500)
            } else {
                enlace.descripcion
            }
        enlaceDao.actualizar(
            enlace.copy(categoriaId = categoriaId, descripcion = descripcionNueva),
        )
    }

    /** El bitmap como bytes PNG, o null si no se pudo comprimir. */
    private fun bitmapABytes(bitmap: Bitmap): ByteArray? {
        val salida = java.io.ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida)) {
            salida.toByteArray()
        } else {
            null
        }
    }

    /**
     * Enriquece un enlace con el texto visible de su reproductor
     * oEmbed (corrección 1.0.1, punto 3, fuente automática 3): la
     * tarjeta lee el texto del reproductor en el WebView (autor y
     * texto de la publicación) y lo entrega aquí.
     *
     * Solo rellena lo que falta: el título solo cambia si aún es el
     * dominio (último recurso al guardar) y la descripción solo si
     * está vacía. Un título que ya vino de oEmbed, de la vista
     * previa o del texto compartido nunca se sobrescribe. Si el
     * título o la descripción cambian y la categoría no es manual,
     * se vuelve a clasificar con el texto nuevo (así un vídeo de
     * Facebook guardado como dominio puede pasar a "Tecnología y
     * celulares" cuando el reproductor muestra su texto). Devuelve
     * false si el enlace no existe, si el texto está vacío o si no
     * había nada que rellenar.
     */
    suspend fun actualizarTextoDesdeWebView(enlaceId: Long, texto: String): Boolean {
        val enlace = enlaceDao.obtenerPorId(enlaceId) ?: return false
        val textoLimpio = texto.trim().take(500)
        if (textoLimpio.isBlank()) return false
        // Investigación de 1 hora, hilo D (peligro activo): el texto
        // "Video no disponible" clasifica como Tecnología 0,737. Si
        // ese texto llegara al clasificador, clasificaría mal con
        // confianza alta. Nunca se guarda ni se clasifica: se ignora.
        if (detectarVideoNoDisponible(textoLimpio)) return false
        val host = extraerHost(enlace.url)
        val tituloNuevo = if (host != null && enlace.titulo == host) {
            primeraLinea(textoLimpio) ?: enlace.titulo
        } else {
            enlace.titulo
        }
        val descripcionNueva = if (enlace.descripcion.isBlank()) {
            textoLimpio
        } else {
            enlace.descripcion
        }
        if (tituloNuevo == enlace.titulo && descripcionNueva == enlace.descripcion) {
            return false
        }
        val actualizado = enlace.copy(titulo = tituloNuevo, descripcion = descripcionNueva)
        val categoriaId = if (!enlace.categoriaManual) {
            val nombre = motorClasificacion.clasificarEnlace(actualizado)
            nombre?.let { categoriaDao.obtenerPorNombre(it)?.id }
                ?: categoriaDao.obtenerPorNombre(Clasificador.OTROS)?.id
        } else {
            enlace.categoriaId
        }
        enlaceDao.actualizar(actualizado.copy(categoriaId = categoriaId))
        return true
    }

    /**
     * Descarga la imagen de [urlImagen] y la guarda en local como
     * miniatura del enlace [enlaceId]. Devuelve la ruta del archivo
     * o null si no se pudo descargar o guardar (sin carpeta, sin
     * red, respuesta vacía): en ese caso la tarjeta usa la URL
     * remota como antes.
     */
    private suspend fun descargarImagenLocal(enlaceId: Long, urlImagen: String): String? {
        val bytes = servicioVistaPrevia.descargarBytes(urlImagen) ?: return null
        return withContext(Dispatchers.IO) {
            guardarBytesLocal(enlaceId, bytes, "jpg")
        }
    }

    /**
     * Escribe [bytes] como `miniatura-<enlaceId>.<extension>` en
     * [directorioMiniaturas] y devuelve su ruta absoluta, o null si
     * no hay carpeta configurada o la escritura falla.
     */
    private fun guardarBytesLocal(enlaceId: Long, bytes: ByteArray?, extension: String): String? {
        val carpeta = directorioMiniaturas ?: return null
        if (bytes == null) return null
        return try {
            if (!carpeta.exists()) carpeta.mkdirs()
            val archivo = File(carpeta, "miniatura-$enlaceId.$extension")
            FileOutputStream(archivo).use { it.write(bytes) }
            archivo.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Actualiza un enlace tal como llega, sin reclasificar. La
     * categoría (manual o no) se conserva exactamente como la trae el
     * enlace: editar nunca sobrescribe la elección manual.
     */
    suspend fun actualizarEnlace(enlace: Enlace) {
        enlaceDao.actualizar(enlace)
    }

    /**
     * Vuelve a clasificar un enlace guardado.
     *
     * Devuelve `true` si se aplicó la clasificación (el enlace existe
     * y no es manual) y `false` si no existe o si su categoría es
     * manual: en ese caso no se toca nada, la elección manual tiene
     * prioridad y NO se sobrescribe.
     */
    suspend fun reclasificarEnlace(id: Long): Boolean {
        val enlace = enlaceDao.obtenerPorId(id) ?: return false
        val nombreCategoria = motorClasificacion.clasificarEnlace(enlace) ?: return false
        val categoriaId = categoriaDao.obtenerPorNombre(nombreCategoria)?.id
            ?: categoriaDao.obtenerPorNombre(Clasificador.OTROS)?.id
        enlaceDao.actualizar(enlace.copy(categoriaId = categoriaId))
        return true
    }

    /**
     * Cambia la categoría de un enlace a mano: pone la categoría
     * elegida y marca `categoriaManual = true`, para que la
     * clasificación automática no la sobrescriba jamás.
     *
     * Además (Fix 3), registra la corrección para el aprendizaje: el
     * título y la descripción del enlace con la categoría elegida
     * alimentan la cabeza (paso en línea inmediato). El aprendizaje
     * nunca rompe el movimiento: si falla, el enlace queda movido
     * igual.
     *
     * Devuelve `false` si el enlace o la categoría no existen.
     */
    suspend fun cambiarCategoriaManual(enlaceId: Long, categoriaId: Long): Boolean {
        val enlace = enlaceDao.obtenerPorId(enlaceId) ?: return false
        val categoria = categoriaDao.obtenerPorId(categoriaId) ?: return false
        enlaceDao.actualizar(enlace.copy(categoriaId = categoriaId, categoriaManual = true))
        val textoCorreccion = "${enlace.titulo} ${enlace.descripcion}".trim()
        // Fix 5, mina del diagnóstico (§6.3): si el texto es SOLO el
        // dominio ("facebook.com"), NO se registra la corrección: la
        // cabeza aprendería "facebook.com" -> categoría y, tras el
        // reentrenamiento en reposo, TODOS los enlaces facebook.com
        // sin texto irían a esa categoría (medido: el juego movido a
        // mano subiría a ~0,64). Solo se aprende del contenido real.
        // Las correcciones solo-dominio ya guardadas se purgan al
        // arrancar (ServicioAprendizaje.purgarCorreccionesSoloDominio).
        if (!esTextoSoloDominio(textoCorreccion)) {
            servicioAprendizaje?.registrarCorreccion(
                textoCorreccion,
                categoria.nombre,
            )
        }
        return true
    }

    /**
     * Invierte el favorito de un enlace. Devuelve el nuevo estado
     * (`true` = ahora es favorito) o null si el enlace no existe.
     */
    suspend fun alternarFavorito(id: Long): Boolean? {
        val enlace = enlaceDao.obtenerPorId(id) ?: return null
        val nuevo = !enlace.esFavorito
        enlaceDao.actualizar(enlace.copy(esFavorito = nuevo))
        return nuevo
    }

    /** Borra un enlace por id. */
    suspend fun borrarEnlace(id: Long) {
        enlaceDao.borrarPorId(id)
    }

    /**
     * Crea una carpeta propia del usuario (Fase 9, orden de Javier).
     *
     * Reglas: el nombre es obligatorio (sin espacios alrededor) y
     * único sin distinguir mayúsculas; el total no puede pasar de
     * [CategoriasPredefinidas.MAX_CATEGORIAS] (17 predefinidas +
     * propias). La carpeta nueva va al final (`orden` mayor que el
     * mayor guardado) y nace con `esPredefinida = false`.
     */
    suspend fun crearCarpeta(nombre: String, color: Long, icono: String): ResultadoCarpeta {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_VACIO)
        if (categoriaDao.contar() >= CategoriasPredefinidas.MAX_CATEGORIAS) {
            return ResultadoCarpeta.Error(TipoErrorCarpeta.LIMITE_ALCANZADO)
        }
        if (categoriaDao.obtenerPorNombreSinDistinguirMayusculas(limpio) != null) {
            return ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_DUPLICADO)
        }
        val orden = (categoriaDao.ordenMaximo() ?: -1) + 1
        val id = categoriaDao.insertar(
            Categoria(
                nombre = limpio,
                color = color,
                icono = icono,
                esPredefinida = false,
                orden = orden,
            ),
        )
        return ResultadoCarpeta.Exito(id)
    }

    /**
     * Renombra una carpeta propia del usuario (Fase 9).
     *
     * Solo las carpetas propias se pueden renombrar: las predefinidas
     * devuelven [TipoErrorCarpeta.NO_ES_PROPIA] y no se tocan. El
     * nombre nuevo sigue las mismas reglas que al crear (obligatorio
     * y único sin distinguir mayúsculas, sin contar la propia
     * carpeta). Los enlaces no se tocan: referencian la categoría
     * por id, así que el renombre no los mueve.
     */
    suspend fun renombrarCarpeta(id: Long, nuevoNombre: String): ResultadoCarpeta {
        val categoria = categoriaDao.obtenerPorId(id)
            ?: return ResultadoCarpeta.Error(TipoErrorCarpeta.NO_EXISTE)
        if (categoria.esPredefinida) {
            return ResultadoCarpeta.Error(TipoErrorCarpeta.NO_ES_PROPIA)
        }
        val limpio = nuevoNombre.trim()
        if (limpio.isEmpty()) return ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_VACIO)
        val otra = categoriaDao.obtenerPorNombreSinDistinguirMayusculas(limpio)
        if (otra != null && otra.id != id) {
            return ResultadoCarpeta.Error(TipoErrorCarpeta.NOMBRE_DUPLICADO)
        }
        categoriaDao.actualizar(categoria.copy(nombre = limpio))
        return ResultadoCarpeta.Exito(id)
    }

    /**
     * Borra una carpeta propia del usuario (Fase 9).
     *
     * Devuelve `false` si no existe o si es predefinida (las 17 no
     * se pueden borrar). Sus enlaces sobreviven: la clave foránea
     * usa `SET_NULL` y el repositorio los expone como "Otros"
     * (decisión de la Fase 2).
     */
    suspend fun borrarCarpeta(id: Long): Boolean {
        val categoria = categoriaDao.obtenerPorId(id) ?: return false
        if (categoria.esPredefinida) return false
        categoriaDao.borrar(categoria)
        return true
    }

    /**
     * Acompaña cada enlace de su categoría resuelta. Un `categoriaId`
     * null (o que ya no existe en la tabla) se resuelve como "Otros":
     * la fila real sembrada, o una sintética con sus mismos datos si
     * ni siquiera esa existiera.
     */
    private fun Flow<List<Enlace>>.conCategoria(): Flow<List<EnlaceConCategoria>> =
        combine(this, categoriaDao.obtenerTodas()) { enlaces, categorias ->
            val porId = categorias.associateBy { it.id }
            val otros = categorias.firstOrNull { it.nombre == Clasificador.OTROS }
                ?: CATEGORIA_OTROS_SINTETICA
            enlaces.map { enlace ->
                EnlaceConCategoria(enlace, porId[enlace.categoriaId] ?: otros)
            }
        }

    /**
     * Host en minúsculas de una URL, sin el `www.` inicial. Devuelve
     * null si la URL no tiene host válido.
     */
    private fun extraerHost(url: String): String? {
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

    companion object {
        /** Clave de contador para "Otros" cuando su fila no existe (no debería pasar). */
        private const val ID_OTROS_SIN_FILA = -1L

        /** "Otros" sintética, con los mismos datos que la sembrada (Fase 2). */
        private val CATEGORIA_OTROS_SINTETICA = Categoria(
            id = ID_OTROS_SIN_FILA,
            nombre = Clasificador.OTROS,
            color = 0xFF90A4AE,
            icono = "category",
            esPredefinida = true,
            orden = 16,
        )
    }
}
