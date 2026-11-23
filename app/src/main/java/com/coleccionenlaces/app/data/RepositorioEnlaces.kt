package com.coleccionenlaces.app.data

import android.graphics.Bitmap
import com.coleccionenlaces.app.domain.Clasificador
import com.coleccionenlaces.app.domain.ResultadoOEmbed
import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
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
     */
    suspend fun prepararEnlace(url: String): EnlacePreparado {
        val urlLimpia = url.trim()
        val resultado = servicioVistaPrevia.obtenerVistaPrevia(urlLimpia)
        val oembed = if (resultado.vistaPrevia.estaVacia) {
            obtenerOEmbed(urlLimpia)
        } else {
            null
        }
        return EnlacePreparado(resultado, oembed)
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
                preparado?.oembed ?: obtenerOEmbed(urlLimpia)
            } else {
                null
            }
            val oembedExito = oembed as? ResultadoOEmbed.Exito
            val host = extraerHost(urlLimpia) ?: urlLimpia
            val tituloCompartido = primeraLinea(textoCompartido)
            val titulo = oembedExito?.titulo?.trim().takeUnless { it.isNullOrBlank() }
                ?: vistaPrevia.titulo?.trim().takeUnless { it.isNullOrBlank() }
                ?: tituloCompartido
                ?: host
            val tituloEsTextoCompartido = titulo == tituloCompartido
            val descripcion = vistaPrevia.descripcion?.trim().takeUnless { it.isNullOrBlank() }
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
            val nombreCategoria = Clasificador.clasificarEnlace(enlaceSinCategoria)
            val categoriaId = nombreCategoria?.let { categoriaDao.obtenerPorNombre(it)?.id }
                ?: categoriaDao.obtenerPorNombre(Clasificador.OTROS)?.id
            val id = enlaceDao.insertar(enlaceSinCategoria.copy(categoriaId = categoriaId))
            // Miniatura local: la del oEmbed (YouTube) siempre, y la
            // de la vista previa solo si su URL lleva firma temporal.
            val imagenADescargar = oembedExito?.urlMiniatura
                ?: vistaPrevia.urlImagen?.takeIf { esUrlImagenFirmada(it) }
            if (imagenADescargar != null) {
                val ruta = descargarImagenLocal(id, imagenADescargar)
                if (ruta != null) {
                    enlaceDao.actualizar(
                        enlaceSinCategoria.copy(id = id, categoriaId = categoriaId, imagenLocal = ruta),
                    )
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
        return true
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
            val nombre = Clasificador.clasificarEnlace(actualizado)
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
        val nombreCategoria = Clasificador.clasificarEnlace(enlace) ?: return false
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
     * Devuelve `false` si el enlace o la categoría no existen.
     */
    suspend fun cambiarCategoriaManual(enlaceId: Long, categoriaId: Long): Boolean {
        val enlace = enlaceDao.obtenerPorId(enlaceId) ?: return false
        if (categoriaDao.obtenerPorId(categoriaId) == null) return false
        enlaceDao.actualizar(enlace.copy(categoriaId = categoriaId, categoriaManual = true))
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
