package com.coleccionenlaces.app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.data.EnlaceConCategoria
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado
import com.coleccionenlaces.app.ui.theme.FondoDegradado
import com.coleccionenlaces.app.util.crearIntentAbrirEnlace
import com.coleccionenlaces.app.util.crearIntentCompartirEnlace
import kotlinx.coroutines.launch

/**
 * Pantalla principal (Fase 6, sección 6 del plan; Fase 9: huella por
 * carpeta, carpetas propias y pulido visual).
 *
 * Consume el estado único [HomeViewModel.estado]: encabezado grande
 * con el título y el contador total, barra de búsqueda redondeada,
 * fila horizontal de chips de categoría con contador, y cuadrícula
 * agrupada por categoría con encabezados de sección (las `secciones`
 * las deriva el ViewModel, no los composables).
 *
 * - El ícono de favorito de cada tarjeta alterna con
 *   [HomeViewModel.alternarFavorito] y SÍ funciona en esta fase.
 * - El botón flotante abre el diálogo de agregar de la Fase 7
 *   ([HomeViewModel.abrirDialogoAgregar]); el diálogo se muestra
 *   cuando [HomeUiState.mostrarDialogoAgregar] es true y se cierra
 *   solo al guardar con éxito (el ViewModel lo cierra) o con
 *   [HomeViewModel.cerrarDialogoAgregar] al cancelar.
 * - Fase 8: tocar una tarjeta abre el enlace en el navegador o en
 *   la app correspondiente (ACTION_VIEW); si ninguna app puede
 *   abrirlo, se muestra un mensaje amigable en un Snackbar y la
 *   app no se rompe. La pulsación larga abre el menú de tarjeta
 *   ([MenuTarjeta]).
 *
 * ## Decisión de diseño (Fase 8): el estado del menú vive en la UI
 * Qué tarjeta tiene el menú abierto (`enlaceMenu`) es estado local
 * de esta pantalla (`remember`): nada fuera de ella lo necesita
 * (el ViewModel no tiene que saber qué menú está abierto; solo
 * ejecuta las acciones que el menú le pide: cambiar categoría,
 * favorito y borrar). Sigue el patrón del diálogo de agregar al
 * revés: el diálogo SÍ vive en el ViewModel porque MainActivity
 * tiene que abrirlo desde el intent compartido; el menú no tiene
 * ningún abridor externo, así que su estado se queda en la UI.
 *
 * ## Huella por carpeta (Fase 9)
 * Cada categoría está bloqueada por defecto: sus enlaces no aparecen
 * en la cuadrícula, ni en la búsqueda, ni en favoritos (el ViewModel
 * ya los filtra). En la vista "Todos" cada carpeta bloqueada con
 * enlaces muestra un marcador ([SeccionBloqueada]) con su nombre, su
 * ícono y "Toca para abrir con huella". Al tocar el marcador o el
 * chip de una carpeta bloqueada se abre [BiometricPrompt]; con
 * éxito, la carpeta queda desbloqueada solo durante la sesión (el
 * estado vive en el ViewModel, en memoria) y sus tarjetas aparecen.
 * Sin método de bloqueo configurado en el dispositivo se muestra un
 * mensaje amigable en vez del prompt. La huella nunca se pide para
 * abrir la app, solo por carpeta.
 *
 * ## Carpetas propias (Fase 9)
 * El chip "+" al final de la fila abre el diálogo de crear carpeta
 * (nombre, color e ícono; máximo 20 categorías en total). La
 * pulsación larga sobre el chip de una carpeta PROPIA abre su
 * gestión (renombrar o borrar, con confirmación); la pulsación larga
 * sobre una predefinida no hace nada, porque no se pueden renombrar
 * ni borrar.
 *
 * ## Tema (Fase 9)
 * El botón del encabezado cambia entre modo oscuro y claro; la
 * elección se guarda en SharedPreferences y se aplica al arrancar.
 * El modo oscuro sigue siendo el predeterminado.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    temaOscuro: Boolean,
    onAlternarTema: () -> Unit,
) {
    val estado by viewModel.estado.collectAsState()
    var enlaceMenu by remember { mutableStateOf<EnlaceConCategoria?>(null) }
    var carpetaGestionar by remember { mutableStateOf<Categoria?>(null) }
    var carpetaBorrarConfirmar by remember { mutableStateOf<Categoria?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val contexto = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val mensajeSinAppAbrir = stringResource(R.string.menu_open_error)
    val mensajeSinAppCompartir = stringResource(R.string.menu_share_error)
    val tituloCompartir = stringResource(R.string.menu_share_title)
    val mensajeSinBloqueo = stringResource(R.string.lock_no_method)
    val mensajeErrorHuella = stringResource(R.string.lock_error)
    val subtituloHuella = stringResource(R.string.lock_subtitle)

    /** Abre la URL en el navegador o la app correspondiente. */
    val abrirEnlace: (String) -> Unit = { url ->
        try {
            contexto.startActivity(crearIntentAbrirEnlace(url))
        } catch (_: ActivityNotFoundException) {
            alcance.launch { snackbarHostState.showSnackbar(mensajeSinAppAbrir) }
        }
    }

    /** Comparte la URL con ACTION_SEND y chooser (texto plano). */
    val compartirEnlace: (String) -> Unit = { url ->
        try {
            contexto.startActivity(
                Intent.createChooser(crearIntentCompartirEnlace(url), tituloCompartir),
            )
        } catch (_: ActivityNotFoundException) {
            alcance.launch { snackbarHostState.showSnackbar(mensajeSinAppCompartir) }
        }
    }

    /**
     * Pide la huella (o la credencial del dispositivo) para abrir
     * una carpeta. Con éxito la desbloquea para la sesión (con
     * feedback táctil) y, si venía de su chip, además la selecciona.
     * Si el usuario cierra el prompt sin autenticarse, no se muestra
     * ningún error: solo los errores reales llegan al Snackbar.
     */
    val pedirHuella: (Categoria, Boolean) -> Unit = { categoria, seleccionarTras ->
        val actividad = contexto as? FragmentActivity
        if (actividad == null || !Biometria.puedeIntentarAutenticacion(actividad)) {
            alcance.launch { snackbarHostState.showSnackbar(mensajeSinBloqueo) }
        } else {
            Biometria.autenticar(
                actividad = actividad,
                titulo = categoria.nombre,
                subtitulo = subtituloHuella,
                onExito = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.desbloquearCategoria(categoria.id)
                    if (seleccionarTras) viewModel.seleccionarCategoria(categoria.id)
                },
                onError = { codigo, _ ->
                    if (codigo != BiometricPrompt.ERROR_CANCELED &&
                        codigo != BiometricPrompt.ERROR_USER_CANCELED &&
                        codigo != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        alcance.launch { snackbarHostState.showSnackbar(mensajeErrorHuella) }
                    }
                },
            )
        }
    }

    /**
     * Elegir una categoría desde su chip: si está bloqueada, primero
     * pide la huella; si ya está desbloqueada (o es "Todos"), la
     * selecciona directamente.
     */
    val seleccionarCategoria: (Long?) -> Unit = { id ->
        if (id == null) {
            viewModel.seleccionarCategoria(null)
        } else {
            val categoria = estado.categorias.firstOrNull { it.id == id }
            when {
                categoria == null -> viewModel.seleccionarCategoria(id)
                viewModel.estaDesbloqueada(id) -> viewModel.seleccionarCategoria(id)
                else -> pedirHuella(categoria, true)
            }
        }
    }

    FondoDegradado {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.abrirDialogoAgregar() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_link)) },
                )
            },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Espaciado.m,
                    end = Espaciado.m,
                    top = Espaciado.s,
                    bottom = 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(Espaciado.s + Espaciado.xs),
                verticalArrangement = Arrangement.spacedBy(Espaciado.s + Espaciado.xs),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Encabezado(
                        totalEnlaces = estado.totalEnlaces,
                        temaOscuro = temaOscuro,
                        onAlternarTema = onAlternarTema,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BarraBusqueda(
                        texto = estado.busqueda,
                        onTextoCambiado = viewModel::cambiarBusqueda,
                        onLimpiar = viewModel::limpiarBusqueda,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilaChips(
                        categorias = estado.categorias,
                        contadores = estado.contadores,
                        totalEnlaces = estado.totalEnlaces,
                        categoriaSeleccionadaId = estado.categoriaSeleccionadaId,
                        soloFavoritos = estado.soloFavoritos,
                        categoriasDesbloqueadas = estado.categoriasDesbloqueadas,
                        onSeleccionarCategoria = seleccionarCategoria,
                        onAlternarSoloFavoritos = viewModel::alternarSoloFavoritos,
                        onCrearCarpeta = { viewModel.abrirDialogoCarpeta() },
                        onGestionarCarpeta = { categoria ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            carpetaGestionar = categoria
                        },
                    )
                }

                if (estado.cargando) {
                    // Primera carga: shimmer, no un spinner genérico.
                    items(count = 6) { TarjetaShimmer(modifier = Modifier.animateItem()) }
                } else {
                    if (estado.guardandoEnlace) {
                        // Se está agregando un enlace (vista previa en
                        // curso): una tarjeta shimmer al principio de
                        // la cuadrícula mientras llega el enlace nuevo.
                        item { TarjetaShimmer(modifier = Modifier.animateItem()) }
                    }
                    // Los marcadores de bloqueado solo se muestran sin
                    // búsqueda ni filtro de favoritos: con búsqueda
                    // activa o en favoritos, las carpetas bloqueadas
                    // simplemente no aparecen.
                    val mostrarMarcadores = estado.busqueda.isBlank() &&
                        !estado.soloFavoritos
                    for (categoria in estado.categorias) {
                        val seccion = estado.secciones.firstOrNull {
                            it.categoria.id == categoria.id
                        }
                        if (seccion != null) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + slideInVertically(
                                        initialOffsetY = { -it / 4 },
                                    ),
                                    modifier = Modifier.animateItem(),
                                ) {
                                    EncabezadoSeccion(
                                        categoria = seccion.categoria,
                                        cantidad = seccion.enlaces.size,
                                    )
                                }
                            }
                            items(
                                items = seccion.enlaces,
                                key = { it.enlace.id },
                            ) { itemEnlace ->
                                TarjetaEnlace(
                                    item = itemEnlace,
                                    onAlternarFavorito = viewModel::alternarFavorito,
                                    onAbrir = { abrirEnlace(itemEnlace.enlace.url) },
                                    onAbrirMenu = { enlaceMenu = itemEnlace },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        } else if (
                            mostrarMarcadores &&
                            estado.seccionesBloqueadas.any { it.id == categoria.id } &&
                            (estado.categoriaSeleccionadaId == null ||
                                estado.categoriaSeleccionadaId == categoria.id)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + slideInVertically(
                                        initialOffsetY = { -it / 4 },
                                    ),
                                    modifier = Modifier.animateItem(),
                                ) {
                                    SeccionBloqueada(
                                        categoria = categoria,
                                        cantidad = estado.contadores[categoria.id] ?: 0,
                                        onAbrir = { pedirHuella(categoria, false) },
                                    )
                                }
                            }
                        }
                    }
                    if (estado.enlaces.isEmpty() &&
                        estado.seccionesBloqueadas.none {
                            mostrarMarcadores &&
                                (estado.categoriaSeleccionadaId == null ||
                                    estado.categoriaSeleccionadaId == it.id)
                        }
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EstadoVacio(
                                hayFiltroActivo = estado.busqueda.isNotBlank() ||
                                    estado.categoriaSeleccionadaId != null ||
                                    estado.soloFavoritos,
                                modifier = Modifier.padding(top = Espaciado.xl),
                            )
                        }
                    }
                }
            }
        }
        if (estado.mostrarDialogoAgregar) {
            DialogoAgregarEnlace(
                urlInicial = estado.urlInicialDialogo,
                guardando = estado.guardandoEnlace,
                error = estado.errorAgregar,
                onGuardar = viewModel::agregarEnlace,
                onCerrar = viewModel::cerrarDialogoAgregar,
                onLimpiarError = viewModel::limpiarErrorAgregar,
            )
        }
        if (estado.mostrarDialogoCarpeta) {
            DialogoCarpeta(
                carpeta = estado.carpetaEnEdicion,
                error = estado.errorCarpeta,
                onCrear = { nombre, color, icono ->
                    viewModel.crearCarpeta(nombre, color, icono)
                },
                onRenombrar = { categoriaId, nombre ->
                    viewModel.renombrarCarpeta(categoriaId, nombre)
                },
                onCerrar = viewModel::cerrarDialogoCarpeta,
                onLimpiarError = viewModel::limpiarErrorCarpeta,
            )
        }
        carpetaGestionar?.let { categoria ->
            DialogoGestionarCarpeta(
                carpeta = categoria,
                onRenombrar = {
                    carpetaGestionar = null
                    viewModel.abrirDialogoCarpeta(categoria)
                },
                onBorrar = {
                    carpetaGestionar = null
                    carpetaBorrarConfirmar = categoria
                },
                onCerrar = { carpetaGestionar = null },
            )
        }
        carpetaBorrarConfirmar?.let { categoria ->
            DialogoConfirmarBorrarCarpeta(
                carpeta = categoria,
                onConfirmar = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.borrarCarpeta(categoria.id)
                    carpetaBorrarConfirmar = null
                },
                onCerrar = { carpetaBorrarConfirmar = null },
            )
        }
        enlaceMenu?.let { itemMenu ->
            MenuTarjeta(
                item = itemMenu,
                categorias = estado.categorias,
                onCambiarCategoria = { categoriaId ->
                    viewModel.cambiarCategoriaManual(itemMenu.enlace.id, categoriaId)
                },
                onAlternarFavorito = { viewModel.alternarFavorito(itemMenu.enlace.id) },
                onCompartir = {
                    enlaceMenu = null
                    compartirEnlace(itemMenu.enlace.url)
                },
                onBorrar = { viewModel.borrarEnlace(itemMenu.enlace.id) },
                onCerrar = { enlaceMenu = null },
            )
        }
    }
}

/** Encabezado grande: título de la app y contador total, con el interruptor de tema. */
@Composable
private fun Encabezado(
    totalEnlaces: Int,
    temaOscuro: Boolean,
    onAlternarTema: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Espaciado.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_title),
                style = ColeccionTipografia.tituloGrande,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = ColeccionTipografia.cuerpo,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Espaciado.xs))
            Text(
                text = when (totalEnlaces) {
                    0 -> stringResource(R.string.home_total_none)
                    1 -> stringResource(R.string.home_total_one)
                    else -> stringResource(R.string.home_total_many, totalEnlaces)
                },
                style = ColeccionTipografia.etiqueta,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onAlternarTema) {
            Icon(
                imageVector = if (temaOscuro) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = stringResource(
                    if (temaOscuro) R.string.theme_to_light else R.string.theme_to_dark,
                ),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Barra de búsqueda redondeada, con borrado rápido. */
@Composable
private fun BarraBusqueda(
    texto: String,
    onTextoCambiado: (String) -> Unit,
    onLimpiar: () -> Unit,
) {
    TextField(
        value = texto,
        onValueChange = onTextoCambiado,
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (texto.isNotEmpty()) {
                IconButton(onClick = onLimpiar) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.home_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Fila horizontal de chips: "Todos", solo favoritos, uno por
 * categoría y el "+" para crear una carpeta propia.
 *
 * Los chips de categoría son propios ([ChipCategoria]) en vez de
 * [FilterChip]: necesitan pulsación larga (gestionar las carpetas
 * propias) y un candado cuando la carpeta sigue bloqueada, y
 * [FilterChip] no expone pulsación larga.
 */
@Composable
private fun FilaChips(
    categorias: List<Categoria>,
    contadores: Map<Long, Int>,
    totalEnlaces: Int,
    categoriaSeleccionadaId: Long?,
    soloFavoritos: Boolean,
    categoriasDesbloqueadas: Set<Long>,
    onSeleccionarCategoria: (Long?) -> Unit,
    onAlternarSoloFavoritos: () -> Unit,
    onCrearCarpeta: () -> Unit,
    onGestionarCarpeta: (Categoria) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Espaciado.s),
        contentPadding = PaddingValues(vertical = Espaciado.xs),
    ) {
        item {
            ChipTexto(
                texto = "${stringResource(R.string.home_chip_todos)} · $totalEnlaces",
                seleccionado = categoriaSeleccionadaId == null,
                color = MaterialTheme.colorScheme.primary,
                onClick = { onSeleccionarCategoria(null) },
            )
        }
        item {
            FilterChip(
                selected = soloFavoritos,
                onClick = onAlternarSoloFavoritos,
                label = { Text(stringResource(R.string.home_chip_favoritos)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
                ),
            )
        }
        items(categorias, key = { it.id }) { categoria ->
            val cantidad = contadores[categoria.id] ?: 0
            ChipCategoria(
                categoria = categoria,
                cantidad = cantidad,
                seleccionado = categoriaSeleccionadaId == categoria.id,
                bloqueada = categoria.id !in categoriasDesbloqueadas,
                onClick = { onSeleccionarCategoria(categoria.id) },
                onLongClick = if (categoria.esPredefinida) {
                    null
                } else {
                    { onGestionarCarpeta(categoria) }
                },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onCrearCarpeta,
                label = { Text(stringResource(R.string.carpeta_add_chip)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** Chip sin ícono (el "Todos"), con el color de acento principal. */
@Composable
private fun ChipTexto(
    texto: String,
    seleccionado: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = color,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/**
 * Chip de categoría con toque (seleccionar o pedir huella si está
 * bloqueada) y pulsación larga (gestionar, solo carpetas propias).
 * Muestra un candado cuando la carpeta sigue bloqueada en la sesión.
 */
@Composable
private fun ChipCategoria(
    categoria: Categoria,
    cantidad: Int,
    seleccionado: Boolean,
    bloqueada: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val color = colorCategoria(categoria)
    val fondo = if (seleccionado) color else MaterialTheme.colorScheme.surfaceVariant
    val contenido = if (seleccionado) {
        colorSobreCategoria(color)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val forma = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(forma)
            .background(fondo)
            .then(
                if (!seleccionado) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, forma)
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = Espaciado.s + Espaciado.xs,
                vertical = Espaciado.s,
            ),
    ) {
        Icon(
            imageVector = iconoCategoria(categoria.icono),
            contentDescription = null,
            tint = if (seleccionado) contenido else color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(Espaciado.xs))
        Text(
            text = "${categoria.nombre} · $cantidad",
            color = contenido,
            style = MaterialTheme.typography.labelLarge,
        )
        if (bloqueada) {
            Spacer(modifier = Modifier.size(Espaciado.xs))
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = contenido,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Encabezado de sección: ícono y nombre de la categoría + cantidad. */
@Composable
private fun EncabezadoSeccion(categoria: Categoria, cantidad: Int) {
    val color = colorCategoria(categoria)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Espaciado.s),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconoCategoria(categoria.icono),
                contentDescription = null,
                tint = colorSobreCategoria(color),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.size(Espaciado.s))
        Text(
            text = categoria.nombre,
            style = ColeccionTipografia.tituloSeccion,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.size(Espaciado.s))
        Text(
            text = "· $cantidad",
            style = ColeccionTipografia.etiqueta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Marcador de sección bloqueada (Fase 9): en vez de las tarjetas de
 * la carpeta, una tarjeta con candado, el nombre e ícono de la
 * categoría y "Toca para abrir con huella". No muestra ningún
 * contenido de la carpeta (ni títulos, ni descripciones, ni
 * imágenes, ni URLs); la cantidad es solo un número. Al tocarla se
 * pide la huella.
 */
@Composable
private fun SeccionBloqueada(
    categoria: Categoria,
    cantidad: Int,
    onAbrir: () -> Unit,
) {
    val color = colorCategoria(categoria)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.clickable(onClick = onAbrir),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Espaciado.m),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colorSobreCategoria(color),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.size(Espaciado.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoria.nombre,
                    style = ColeccionTipografia.tituloSeccion,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.lock_open_hint),
                    style = ColeccionTipografia.descripcion,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(Espaciado.s))
            Text(
                text = "· $cantidad",
                style = ColeccionTipografia.etiqueta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
