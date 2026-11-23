package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.Clasificador
import com.coleccionenlaces.app.domain.ResultadoVistaPrevia
import com.coleccionenlaces.app.domain.TipoErrorVistaPrevia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * fallo distinguido) y el título sugerido para el diálogo (el de la vista previa
 * cuando lo hay; si no, el dominio). El diálogo usa esto para pedir al usuario
 * el título y la descripción cuando la vista previa falló (Fix 1): un sitio
 * como Facebook no deja leer el enlace, pero el enlace se guarda igual con
 * los datos que escriba el usuario. */
data class EnlacePreparado(
    val vistaPrevia: ResultadoVistaPrevia,
    val tituloSugerido: String,
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
 *   (Fix 1: el diálogo comprueba primero la vista previa con
 *   [prepararEnlace] y, si falló, deja que el usuario escriba el
 *   título y la descripción antes de guardar con
 *   [agregarEnlace] con título manual.)
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
     * correcta) o si pide al usuario el título y la descripción
     * (vista previa fallida: el sitio no dejó leer el enlace). El
     * título sugerido es el de la vista previa cuando lo hay y el
     * dominio en caso contrario. La URL inválida se distingue aquí
     * igual que en [agregarEnlace]: con ella no se guarda nada.
     */
    suspend fun prepararEnlace(url: String): EnlacePreparado {
        val urlLimpia = url.trim()
        val resultado = servicioVistaPrevia.obtenerVistaPrevia(urlLimpia)
        val tituloSugerido = resultado.vistaPrevia.titulo?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: extraerHost(urlLimpia) ?: urlLimpia
        return EnlacePreparado(resultado, tituloSugerido)
    }

    /**
     * Agrega un enlace: valida la URL, obtiene la vista previa,
     * clasifica y guarda.
     *
     * - URL inválida -> [ResultadoAgregarEnlace.Error] con
     *   `URL_INVALIDA`, sin guardar nada.
     * - Cualquier otro fallo de vista previa (sin internet, timeout,
     *   sitio sin vista previa) -> el enlace se guarda igual, con el
     *   dominio como título y sin imagen.
     * - La categoría es la del clasificador (el enlace nuevo no es
     *   manual); si el nombre no se resuelve en la base, "Otros".
     */
    suspend fun agregarEnlace(url: String): ResultadoAgregarEnlace =
        agregarEnlace(url, tituloManual = null, descripcionManual = null, preparado = null)

    /**
     * Agrega un enlace con título y descripción escritos por el
     * usuario y/o con una vista previa ya comprobada (Fix 1).
     *
     * - [tituloManual] / [descripcionManual]: lo que el usuario
     *   escribió en el diálogo cuando la vista previa falló. Un
     *   título manual no vacío tiene prioridad sobre el de la vista
     *   previa (y sobre el dominio); una descripción manual tiene
     *   prioridad sobre la de la vista previa. Con ambos en null se
     *   comporta exactamente como [agregarEnlace].
     * - [preparado]: el resultado de [prepararEnlace] para esta
     *   misma URL, para no descargar la página dos veces (una al
     *   comprobar y otra al guardar). Con null, la vista previa se
     *   descarga aquí.
     * - El título y la descripción EFECTIVOS (manuales o de la vista
     *   previa) son los que alimentan al clasificador: un título
     *   escrito por el usuario clasifica igual que uno leído del
     *   sitio.
     */
    suspend fun agregarEnlace(
        url: String,
        tituloManual: String?,
        descripcionManual: String?,
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
            val host = extraerHost(urlLimpia) ?: urlLimpia
            val titulo = tituloManual?.trim().takeUnless { it.isNullOrBlank() }
                ?: vistaPrevia.titulo?.trim().takeUnless { it.isNullOrBlank() } ?: host
            val descripcion = descripcionManual?.trim()
                ?: vistaPrevia.descripcion?.trim().orEmpty()
            val enlaceSinCategoria = Enlace(
                url = urlLimpia,
                titulo = titulo,
                descripcion = descripcion,
                urlImagen = vistaPrevia.urlImagen,
                categoriaId = null,
                categoriaManual = false,
            )
            val nombreCategoria = Clasificador.clasificarEnlace(enlaceSinCategoria)
            val categoriaId = nombreCategoria?.let { categoriaDao.obtenerPorNombre(it)?.id }
                ?: categoriaDao.obtenerPorNombre(Clasificador.OTROS)?.id
            val id = enlaceDao.insertar(enlaceSinCategoria.copy(categoriaId = categoriaId))
            ResultadoAgregarEnlace.Exito(id)
        } catch (_: Exception) {
            ResultadoAgregarEnlace.Error(TipoErrorAgregarEnlace.ERROR_AL_GUARDAR)
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
