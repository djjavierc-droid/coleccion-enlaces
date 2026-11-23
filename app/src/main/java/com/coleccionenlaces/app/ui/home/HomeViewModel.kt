package com.coleccionenlaces.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.data.EnlaceConCategoria
import com.coleccionenlaces.app.data.RepositorioEnlaces
import com.coleccionenlaces.app.data.ResultadoAgregarEnlace
import com.coleccionenlaces.app.data.ResultadoCarpeta
import com.coleccionenlaces.app.data.TipoErrorAgregarEnlace
import com.coleccionenlaces.app.data.TipoErrorCarpeta
import com.coleccionenlaces.app.domain.BloqueoCarpetas
import com.coleccionenlaces.app.domain.filtrarEnlacesVisibles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
 *   [HomeViewModel.agregarEnlace] (lo usa el diálogo de la Fase 7).
 * - [mostrarDialogoAgregar] / [urlInicialDialogo]: el diálogo de
 *   agregar (Fase 7). El botón flotante lo abre vacío; un enlace
 *   compartido (ACTION_SEND) lo abre prellenado con la URL extraída.
 *   Al guardar con éxito el ViewModel lo cierra solo; con error se
 *   queda abierto para que la UI muestre el error dentro del diálogo.
 * - [categoriasDesbloqueadas]: ids de las carpetas desbloqueadas con
 *   huella en esta sesión (Fase 9). [enlaces] ya viene filtrado: solo
 *   trae enlaces de esas categorías, así que la búsqueda y los
 *   favoritos tampoco muestran nada de una carpeta bloqueada.
 *   [seccionesBloqueadas] son las categorías con enlaces que siguen
 *   bloqueadas: la UI dibuja su marcador ("Toca para abrir con
 *   huella") en vez de sus tarjetas.
 * - [mostrarDialogoCarpeta] / [carpetaEnEdicion] / [errorCarpeta]:
 *   el diálogo de crear/renombrar carpeta propia (Fase 9).
 *   `carpetaEnEdicion` null = crear; con valor = renombrar esa
 *   carpeta. Al crear con éxito el ViewModel lo cierra solo; con
 *   error se queda abierto para mostrar el error dentro del diálogo.
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
    val mostrarDialogoAgregar: Boolean = false,
    val urlInicialDialogo: String? = null,
    val categoriasDesbloqueadas: Set<Long> = emptySet(),
    val mostrarDialogoCarpeta: Boolean = false,
    val carpetaEnEdicion: Categoria? = null,
    val errorCarpeta: TipoErrorCarpeta? = null,
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

    /**
     * Categorías con enlaces que siguen bloqueadas en esta sesión
     * (Fase 9). La UI dibuja su marcador de bloqueado en vez de sus
     * tarjetas. Solo cuentan las que tienen al menos un enlace: una
     * carpeta vacía y bloqueada no necesita marcador.
     */
    val seccionesBloqueadas: List<Categoria>
        get() = categorias.filter { categoria ->
            categoria.id !in categoriasDesbloqueadas &&
                (contadores[categoria.id] ?: 0) > 0
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
/**
 * Controles mutables de la pantalla (filtro y alta de enlace) en un
 * solo flujo. Así el estado se construye encadenando `combine` de
 * 2 flujos, que el compilador resuelve sin ambigüedad (el `combine`
 * de 8 flujos no compila: la resolución de sobrecargas elige la
 * variante vararg).
 */
private data class ControlesUi(
    val categoriaId: Long? = null,
    val soloFavoritos: Boolean = false,
    val busqueda: String = "",
    val guardandoEnlace: Boolean = false,
    val errorAgregar: TipoErrorAgregarEnlace? = null,
    val mostrarDialogoAgregar: Boolean = false,
    val urlInicialDialogo: String? = null,
    val mostrarDialogoCarpeta: Boolean = false,
    val carpetaEnEdicion: Categoria? = null,
    val errorCarpeta: TipoErrorCarpeta? = null,
)

class HomeViewModel(
    private val repositorio: RepositorioEnlaces,
) : ViewModel() {

    /**
     * Bloqueo por carpeta (Fase 9): qué categorías están
     * desbloqueadas en esta sesión. Vive solo en memoria (ver
     * [BloqueoCarpetas]): al cerrar la app se pierde y todo vuelve
     * a estar bloqueado. Se expone para que la UI lo consulte y
     * para las pruebas; el desbloqueo real lo pide la UI con huella
     * y luego llama a [desbloquearCategoria].
     */
    val bloqueo = BloqueoCarpetas()

    private val controles = MutableStateFlow(ControlesUi())

    val estado: StateFlow<HomeUiState> = combine(
        repositorio.enlacesConCategoria,
        repositorio.categorias,
    ) { todos, categorias -> todos to categorias }
        .combine(repositorio.contadoresPorCategoria) { (todos, categorias), contadores ->
            Triple(todos, categorias, contadores)
        }
        .combine(controles) { (todos, categorias, contadores), c ->
            val texto = c.busqueda.trim().lowercase()
            val filtrados = todos.filter { item ->
                val porCategoria = c.categoriaId == null || item.categoria.id == c.categoriaId
                val porFavorito = !c.soloFavoritos || item.enlace.esFavorito
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
                categoriaSeleccionadaId = c.categoriaId,
                soloFavoritos = c.soloFavoritos,
                busqueda = c.busqueda,
                cargando = false,
                guardandoEnlace = c.guardandoEnlace,
                errorAgregar = c.errorAgregar,
                mostrarDialogoAgregar = c.mostrarDialogoAgregar,
                urlInicialDialogo = c.urlInicialDialogo,
                mostrarDialogoCarpeta = c.mostrarDialogoCarpeta,
                carpetaEnEdicion = c.carpetaEnEdicion,
                errorCarpeta = c.errorCarpeta,
            )
        }.combine(bloqueo.desbloqueadas) { estado, desbloqueadas ->
            // Último filtro (Fase 9): de una carpeta bloqueada no se
            // ve ningún enlace, ni en la cuadrícula, ni en la
            // búsqueda, ni en favoritos. El total y los contadores
            // se dejan como están: son solo números, no contenido.
            estado.copy(
                enlaces = filtrarEnlacesVisibles(estado.enlaces, desbloqueadas),
                categoriasDesbloqueadas = desbloqueadas,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    /** Elige la categoría del chip (null = todas). */
    fun seleccionarCategoria(categoriaId: Long?) {
        controles.update { it.copy(categoriaId = categoriaId) }
    }

    /** Invierte el filtro de solo favoritos. */
    fun alternarSoloFavoritos() {
        controles.update { it.copy(soloFavoritos = !it.soloFavoritos) }
    }

    /** Texto de búsqueda (la filtra en vivo; vacía la quita). */
    fun cambiarBusqueda(texto: String) {
        controles.update { it.copy(busqueda = texto) }
    }

    /** Limpia la búsqueda. */
    fun limpiarBusqueda() {
        controles.update { it.copy(busqueda = "") }
    }

    /**
     * Agrega un enlace con la URL dada. Mientras se obtiene la vista
     * previa, [HomeUiState.guardandoEnlace] es true; si la URL es
     * inválida, [HomeUiState.errorAgregar] lleva el tipo de error
     * (la UI lo muestra y lo limpia con [limpiarErrorAgregar]).
     * Un fallo de vista previa NO es error: el enlace se guarda igual.
     *
     * Al guardar con éxito, el diálogo se cierra solo (y se limpia su
     * URL inicial): la tarjeta nueva aparece por el Flow. Con error,
     * el diálogo se queda abierto para mostrar el error dentro de él.
     */
    fun agregarEnlace(url: String) {
        viewModelScope.launch {
            controles.update { it.copy(guardandoEnlace = true, errorAgregar = null) }
            when (val resultado = repositorio.agregarEnlace(url)) {
                is ResultadoAgregarEnlace.Exito -> controles.update {
                    it.copy(
                        guardandoEnlace = false,
                        mostrarDialogoAgregar = false,
                        urlInicialDialogo = null,
                    )
                }
                is ResultadoAgregarEnlace.Error -> controles.update {
                    it.copy(guardandoEnlace = false, errorAgregar = resultado.tipo)
                }
            }
        }
    }

    /** Limpia el error de agregar, tras mostrarlo. */
    fun limpiarErrorAgregar() {
        controles.update { it.copy(errorAgregar = null) }
    }

    /**
     * Abre el diálogo de agregar, vacío o prellenado con [urlInicial]
     * (enlace recibido desde el menú de compartir). Abrirlo limpia
     * cualquier error anterior de agregar.
     */
    fun abrirDialogoAgregar(urlInicial: String? = null) {
        controles.update {
            it.copy(
                mostrarDialogoAgregar = true,
                urlInicialDialogo = urlInicial,
                errorAgregar = null,
            )
        }
    }

    /** Cierra el diálogo de agregar y limpia su URL inicial y el error. */
    fun cerrarDialogoAgregar() {
        controles.update {
            it.copy(
                mostrarDialogoAgregar = false,
                urlInicialDialogo = null,
                errorAgregar = null,
            )
        }
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

    // ---- Huella por carpeta (Fase 9) ----

    /** ¿Está desbloqueada esta categoría en esta sesión? */
    fun estaDesbloqueada(categoriaId: Long): Boolean =
        bloqueo.estaDesbloqueada(categoriaId)

    /**
     * Marca una categoría como desbloqueada para lo que queda de
     * sesión. La UI solo llama a esto tras un BiometricPrompt con
     * éxito; nunca se desbloquea nada sin huella.
     */
    fun desbloquearCategoria(categoriaId: Long) {
        bloqueo.desbloquear(categoriaId)
    }

    // ---- Carpetas propias del usuario (Fase 9) ----

    /**
     * Abre el diálogo de carpeta: crear (sin [categoria]) o
     * renombrar (con la carpeta propia a editar). Abrirlo limpia
     * cualquier error anterior de carpeta.
     */
    fun abrirDialogoCarpeta(categoria: Categoria? = null) {
        controles.update {
            it.copy(
                mostrarDialogoCarpeta = true,
                carpetaEnEdicion = categoria,
                errorCarpeta = null,
            )
        }
    }

    /** Cierra el diálogo de carpeta y limpia su error y su edición. */
    fun cerrarDialogoCarpeta() {
        controles.update {
            it.copy(
                mostrarDialogoCarpeta = false,
                carpetaEnEdicion = null,
                errorCarpeta = null,
            )
        }
    }

    /** Limpia el error de carpeta, tras mostrarlo. */
    fun limpiarErrorCarpeta() {
        controles.update { it.copy(errorCarpeta = null) }
    }

    /**
     * Crea una carpeta propia con nombre, color e ícono. Con éxito,
     * el diálogo se cierra solo; con error (nombre vacío, duplicado
     * o límite de 20) el diálogo se queda abierto con el error.
     */
    fun crearCarpeta(nombre: String, color: Long, icono: String) {
        viewModelScope.launch {
            when (val resultado = repositorio.crearCarpeta(nombre, color, icono)) {
                is ResultadoCarpeta.Exito -> controles.update {
                    it.copy(
                        mostrarDialogoCarpeta = false,
                        carpetaEnEdicion = null,
                        errorCarpeta = null,
                    )
                }
                is ResultadoCarpeta.Error -> controles.update {
                    it.copy(errorCarpeta = resultado.tipo)
                }
            }
        }
    }

    /**
     * Renombra una carpeta propia. Solo las propias se pueden
     * renombrar: con una predefinida el repositorio devuelve el
     * error y el diálogo se queda abierto con el error.
     */
    fun renombrarCarpeta(categoriaId: Long, nuevoNombre: String) {
        viewModelScope.launch {
            when (val resultado = repositorio.renombrarCarpeta(categoriaId, nuevoNombre)) {
                is ResultadoCarpeta.Exito -> controles.update {
                    it.copy(
                        mostrarDialogoCarpeta = false,
                        carpetaEnEdicion = null,
                        errorCarpeta = null,
                    )
                }
                is ResultadoCarpeta.Error -> controles.update {
                    it.copy(errorCarpeta = resultado.tipo)
                }
            }
        }
    }

    /**
     * Borra una carpeta propia (la confirmación la pide la UI). Si
     * era la categoría del filtro activo, el filtro vuelve a "Todos"
     * para no quedarse apuntando a una carpeta que ya no existe.
     */
    fun borrarCarpeta(categoriaId: Long) {
        viewModelScope.launch {
            repositorio.borrarCarpeta(categoriaId)
            controles.update {
                if (it.categoriaId == categoriaId) it.copy(categoriaId = null) else it
            }
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
