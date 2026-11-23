package com.coleccionenlaces.app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
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
 * menú lateral deslizante con las categorías ([MenuLateral]) y
 * cuadrícula agrupada por categoría con encabezados de sección (las
 * `secciones` las deriva el ViewModel, no los composables). La fila
 * horizontal de chips de la Fase 6 se eliminó en la corrección 1.0.1
 * (punto 2): se cortaba en el borde; las categorías viven ahora en
 * el menú lateral, que se abre con el icono de las dos rayitas
 * arriba a la izquierda.
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
 * ícono y "Toca para abrir con huella". Al tocar el marcador o la
 * fila de una carpeta bloqueada en el menú lateral se abre
 * [BiometricPrompt]; con éxito, la carpeta queda desbloqueada solo
 * durante la sesión (el estado vive en el ViewModel, en memoria) y
 * sus tarjetas aparecen. Sin método de bloqueo configurado en el
 * dispositivo se muestra un mensaje amigable en vez del prompt. La
 * huella nunca se pide para abrir la app, solo por carpeta.
 *
 * ## Carpetas propias (Fase 9)
 * La fila "Nueva carpeta" al final del menú lateral abre el diálogo
 * de crear carpeta (nombre, color e ícono; máximo 20 categorías en
 * total). La pulsación larga sobre la fila de una carpeta PROPIA
 * abre su gestión (renombrar o borrar, con confirmación); la
 * pulsación larga sobre una predefinida no hace nada, porque no se
 * pueden renombrar ni borrar.
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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
     * feedback táctil) y, si venía de su fila del menú, además la selecciona.
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
     * Elegir una categoría desde el menú lateral: si está bloqueada,
     * primero pide la huella; si ya está desbloqueada (o es "Todos"),
     * la selecciona directamente. En el menú, Todos / Favoritos /
     * categoría son vistas excluyentes: elegir una quita el filtro
     * de solo favoritos si estaba puesto (los chips antiguos
     * combinaban categoría y favoritos; el menú no).
     */
    val seleccionarCategoria: (Long?) -> Unit = { id ->
        if (estado.soloFavoritos) viewModel.alternarSoloFavoritos()
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
        alcance.launch { drawerState.close() }
    }

    /** Elegir "Favoritos" desde el menú: quita la categoría y activa solo favoritos. */
    val seleccionarFavoritos: () -> Unit = {
        viewModel.seleccionarCategoria(null)
        if (!estado.soloFavoritos) viewModel.alternarSoloFavoritos()
        alcance.launch { drawerState.close() }
    }

    FondoDegradado {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                MenuLateral(
                    categorias = estado.categorias,
                    contadores = estado.contadores,
                    totalEnlaces = estado.totalEnlaces,
                    categoriaSeleccionadaId = estado.categoriaSeleccionadaId,
                    soloFavoritos = estado.soloFavoritos,
                    categoriasDesbloqueadas = estado.categoriasDesbloqueadas,
                    onSeleccionarTodos = { seleccionarCategoria(null) },
                    onAlternarSoloFavoritos = seleccionarFavoritos,
                    onSeleccionarCategoria = seleccionarCategoria,
                    onCrearCarpeta = {
                        viewModel.abrirDialogoCarpeta()
                        alcance.launch { drawerState.close() }
                    },
                    onGestionarCarpeta = { categoria ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        carpetaGestionar = categoria
                    },
                )
            },
        ) {
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
                        onAbrirMenu = { alcance.launch { drawerState.open() } },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BarraBusqueda(
                        texto = estado.busqueda,
                        temaOscuro = temaOscuro,
                        onTextoCambiado = viewModel::cambiarBusqueda,
                        onLimpiar = viewModel::limpiarBusqueda,
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
        }
        if (estado.mostrarDialogoAgregar) {
            DialogoAgregarEnlace(
                urlInicial = estado.urlInicialDialogo,
                guardando = estado.guardandoEnlace,
                comprobando = estado.comprobandoVistaPrevia,
                sinVistaPrevia = estado.sinVistaPrevia,
                tituloSugerido = estado.tituloSugerido,
                error = estado.errorAgregar,
                onComprobar = viewModel::comprobarVistaPrevia,
                onGuardarEditado = viewModel::guardarEnlaceEditado,
                onUrlCambiada = viewModel::limpiarSinVistaPrevia,
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

/** Encabezado grande: menú lateral, título de la app y contador total, con el interruptor de tema. */
@Composable
private fun Encabezado(
    totalEnlaces: Int,
    temaOscuro: Boolean,
    onAlternarTema: () -> Unit,
    onAbrirMenu: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Espaciado.s),
    ) {
        IconButton(onClick = onAbrirMenu) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.home_menu_open),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_title),
                style = ColeccionTipografia.tituloGrande,
                // Título vivo con el color primario (corrección 1.0.1,
                // punto 1): en claro 5.02:1 sobre el tono superior del
                // degradado y 5.66:1 sobre el fondo; en oscuro 6.92:1.
                color = MaterialTheme.colorScheme.primary,
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
    temaOscuro: Boolean,
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
            // En modo claro la barra es blanca (surface): sobre el
            // degradado claro la variante (#E9E7F8) casi no se
            // distinguía del fondo. En oscuro no cambia.
            focusedContainerColor = if (temaOscuro) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            unfocusedContainerColor = if (temaOscuro) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            disabledContainerColor = if (temaOscuro) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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
