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
    suspend fun agregarEnlace(url: String): ResultadoAgregarEnlace {
        val urlLimpia = url.trim()
        return try {
            val resultadoVistaPrevia = servicioVistaPrevia.obtenerVistaPrevia(urlLimpia)
            if (resultadoVistaPrevia is ResultadoVistaPrevia.Error &&
                resultadoVistaPrevia.tipo == TipoErrorVistaPrevia.URL_INVALIDA
            ) {
                return ResultadoAgregarEnlace.Error(TipoErrorAgregarEnlace.URL_INVALIDA)
            }
            val vistaPrevia = resultadoVistaPrevia.vistaPrevia
            val host = extraerHost(urlLimpia) ?: urlLimpia
            val titulo = vistaPrevia.titulo?.trim().takeUnless { it.isNullOrBlank() } ?: host
            val enlaceSinCategoria = Enlace(
                url = urlLimpia,
                titulo = titulo,
                descripcion = vistaPrevia.descripcion?.trim().orEmpty(),
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
