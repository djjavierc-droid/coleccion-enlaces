package com.coleccionenlaces.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.data.EnlaceConCategoria
import com.coleccionenlaces.app.data.RepositorioEnlaces
import com.coleccionenlaces.app.data.ResultadoAgregarEnlace
import com.coleccionenlaces.app.data.TipoErrorAgregarEnlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una sección de la cuadrícula: una categoría con sus enlaces. */
data class SeccionEnlaces(
    val categoria: Categoria,
    val enlaces: List<EnlaceConCategoria>,
)

/**
 * Estado único de la pantalla principal (Fase 5).
 *
 * ## Decisión de diseño: un solo StateFlow
 * Toda la pantalla se alimenta de este único [HomeUiState], expuesto
 * como [HomeViewModel.estado]. La lista, el filtro, la búsqueda y la
 * carga no son flujos separados: un solo estado evita estados
 * intermedios incoherentes (p. ej. lista de una categoría con el
 * contador de otra) y la UI de la Fase 6 solo observa un flujo.
 *
 * - [enlaces]: ya filtrados por categoría, favoritos y búsqueda, con
 *   su categoría resuelta ([EnlaceConCategoria]).
 * - [totalEnlaces]: total sin filtrar (el contador del encabezado).
 * - [categoriaSeleccionadaId]: null = todas las categorías.
 * - [cargando]: true solo hasta la primera emisión de la base; luego
 *   los cambios llegan solos por el Flow de Room.
 * - [guardandoEnlace] / [errorAgregar]: estado transitorio de
 *   [HomeViewModel.agregarEnlace] (lo usará el diálogo de la Fase 7).
 */
data class HomeUiState(
    val enlaces: List<EnlaceConCategoria> = emptyList(),
    val totalEnlaces: Int = 0,
    val categorias: List<Categoria> = emptyList(),
    val contadores: Map<Long, Int> = emptyMap(),
    val categoriaSeleccionadaId: Long? = null,
    val soloFavoritos: Boolean = false,
    val busqueda: String = "",
    val cargando: Boolean = true,
    val guardandoEnlace: Boolean = false,
    val errorAgregar: TipoErrorAgregarEnlace? = null,
) {
    /**
     * Secciones por categoría, en el orden de los chips, solo con
     * enlaces. La Fase 6 las dibuja como encabezados de sección de
     * la cuadrícula; el agrupado vive aquí, no en los composables.
     */
    val secciones: List<SeccionEnlaces>
        get() {
            val orden = categorias.mapIndexed { indice, categoria -> categoria.id to indice }.toMap()
            return enlaces.groupBy { it.categoria.id }
                .entries.sortedBy { orden[it.key] ?: Int.MAX_VALUE }
                .map { (_, lista) -> SeccionEnlaces(lista.first().categoria, lista) }
        }
}

/**
 * ViewModel principal (Fase 5): la pantalla principal observa
 * [estado] y llama a estas funciones; no hay lógica de negocio en
 * los composables.
 *
 * El filtrado (categoría, favoritos, búsqueda) se hace en memoria
 * sobre el único Flow de todos los enlaces del repositorio: una sola
 * suscripción a la base, filtro instantáneo al escribir o tocar un
 * chip, y sin consultas nuevas por cada cambio de filtro. Las
 * consultas filtradas del repositorio (`buscarEnlaces`,
 * `obtenerEnlacesPorCategoria`, `obtenerFavoritos`) quedan como API
 * de datos para usos directos y para las pruebas.
 *
 * El filtro por categoría compara el id de la categoría RESUELTA
 * ([EnlaceConCategoria.categoria]): un enlace sin categoría guardada
 * se muestra como "Otros", así que al elegir el chip "Otros" aparece
 * (comparar el `categoriaId` guardado, que es null, lo dejaría fuera).
 */
class HomeViewModel(
    private val repositorio: RepositorioEnlaces,
) : ViewModel() {

    private val categoriaSeleccionadaId = MutableStateFlow<Long?>(null)
    private val soloFavoritos = MutableStateFlow(false)
    private val busqueda = MutableStateFlow("")
    private val guardandoEnlace = MutableStateFlow(false)
    private val errorAgregar = MutableStateFlow<TipoErrorAgregarEnlace?>(null)

    val estado: StateFlow<HomeUiState> = combine(
        repositorio.enlacesConCategoria,
        repositorio.categorias,
        repositorio.contadoresPorCategoria,
        categoriaSeleccionadaId,
        soloFavoritos,
        busqueda,
        guardandoEnlace,
        errorAgregar,
    ) { todos, categorias, contadores, categoriaId, soloFav, textoBusqueda, guardando, error ->
        val texto = textoBusqueda.trim().lowercase()
        val filtrados = todos.filter { item ->
            val porCategoria = categoriaId == null || item.categoria.id == categoriaId
            val porFavorito = !soloFav || item.enlace.esFavorito
            val porBusqueda = texto.isEmpty() ||
                item.enlace.titulo.lowercase().contains(texto) ||
                item.enlace.descripcion.lowercase().contains(texto) ||
                item.categoria.nombre.lowercase().contains(texto)
            porCategoria && porFavorito && porBusqueda
        }
        HomeUiState(
            enlaces = filtrados,
            totalEnlaces = todos.size,
            categorias = categorias,
            contadores = contadores,
            categoriaSeleccionadaId = categoriaId,
            soloFavoritos = soloFav,
            busqueda = textoBusqueda,
            cargando = false,
            guardandoEnlace = guardando,
            errorAgregar = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** Elige la categoría del chip (null = todas). */
    fun seleccionarCategoria(categoriaId: Long?) {
        this.categoriaSeleccionadaId.value = categoriaId
    }

    /** Invierte el filtro de solo favoritos. */
    fun alternarSoloFavoritos() {
        soloFavoritos.value = !soloFavoritos.value
    }

    /** Texto de búsqueda (la filtra en vivo; vacía la quita). */
    fun cambiarBusqueda(texto: String) {
        busqueda.value = texto
    }

    /** Limpia la búsqueda. */
    fun limpiarBusqueda() {
        busqueda.value = ""
    }

    /**
     * Agrega un enlace con la URL dada. Mientras se obtiene la vista
     * previa, [HomeUiState.guardandoEnlace] es true; si la URL es
     * inválida, [HomeUiState.errorAgregar] lleva el tipo de error
     * (la UI lo muestra y lo limpia con [limpiarErrorAgregar]).
     * Un fallo de vista previa NO es error: el enlace se guarda igual.
     */
    fun agregarEnlace(url: String) {
        viewModelScope.launch {
            guardandoEnlace.value = true
            errorAgregar.value = null
            val resultado = repositorio.agregarEnlace(url)
            if (resultado is ResultadoAgregarEnlace.Error) {
                errorAgregar.value = resultado.tipo
            }
            guardandoEnlace.value = false
        }
    }

    /** Limpia el error de agregar, tras mostrarlo. */
    fun limpiarErrorAgregar() {
        errorAgregar.value = null
    }

    /** Invierte el favorito de un enlace. */
    fun alternarFavorito(enlaceId: Long) {
        viewModelScope.launch {
            repositorio.alternarFavorito(enlaceId)
        }
    }

    /** Borra un enlace (la confirmación la pide la UI, Fase 8). */
    fun borrarEnlace(enlaceId: Long) {
        viewModelScope.launch {
            repositorio.borrarEnlace(enlaceId)
        }
    }

    /** Cambia la categoría de un enlace a mano (prioridad manual). */
    fun cambiarCategoriaManual(enlaceId: Long, categoriaId: Long) {
        viewModelScope.launch {
            repositorio.cambiarCategoriaManual(enlaceId, categoriaId)
        }
    }

    /** Vuelve a clasificar un enlace no manual. */
    fun reclasificarEnlace(enlaceId: Long) {
        viewModelScope.launch {
            repositorio.reclasificarEnlace(enlaceId)
        }
    }
}

/** Factoría manual del [HomeViewModel] (sin Hilt ni Koin). */
class HomeViewModelFactory(
    private val repositorio: RepositorioEnlaces,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repositorio) as T
        }
        throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    }
}
