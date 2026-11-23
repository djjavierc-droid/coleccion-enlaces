package com.coleccionenlaces.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado
import com.coleccionenlaces.app.ui.theme.FondoDegradado

/**
 * Pantalla principal (Fase 6, sección 6 del plan).
 *
 * Consume el estado único [HomeViewModel.estado]: encabezado grande
 * con el título y el contador total, barra de búsqueda redondeada,
 * fila horizontal de chips de categoría con contador, y cuadrícula
 * agrupada por categoría con encabezados de sección (las `secciones`
 * las deriva el ViewModel, no los composables).
 *
 * - El ícono de favorito de cada tarjeta alterna con
 *   [HomeViewModel.alternarFavorito] y SÍ funciona en esta fase.
 * - El botón flotante abre el estado `mostrarDialogoAgregar`: el
 *   CONTENIDO del diálogo (campo, validación, vista previa en curso
 *   y guardado) es la Fase 7 y no se inventa aquí; solo queda el
 *   estado preparado.
 * - Tocar una tarjeta NO abre nada en esta fase: abrir el enlace y
 *   el menú de tarjeta son la Fase 8.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val estado by viewModel.estado.collectAsState()
    // Estado preparado para la Fase 7: el diálogo de agregar todavía
    // no tiene contenido; el botón solo deja este estado en true.
    var mostrarDialogoAgregar by remember { mutableStateOf(false) }

    FondoDegradado {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { mostrarDialogoAgregar = true },
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
                    Encabezado(totalEnlaces = estado.totalEnlaces)
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
                        onSeleccionarCategoria = viewModel::seleccionarCategoria,
                        onAlternarSoloFavoritos = viewModel::alternarSoloFavoritos,
                    )
                }

                if (estado.cargando) {
                    // Primera carga: shimmer, no un spinner genérico.
                    items(count = 6) { TarjetaShimmer(modifier = Modifier.animateItem()) }
                } else if (estado.enlaces.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EstadoVacio(
                            hayFiltroActivo = estado.busqueda.isNotBlank() ||
                                estado.categoriaSeleccionadaId != null ||
                                estado.soloFavoritos,
                            modifier = Modifier.padding(top = Espaciado.xl),
                        )
                    }
                } else {
                    if (estado.guardandoEnlace) {
                        // Se está agregando un enlace (vista previa en
                        // curso): una tarjeta shimmer al principio de
                        // la cuadrícula mientras llega el enlace nuevo.
                        item { TarjetaShimmer(modifier = Modifier.animateItem()) }
                    }
                    for (seccion in estado.secciones) {
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
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Encabezado grande: título de la app y contador total. */
@Composable
private fun Encabezado(totalEnlaces: Int) {
    Column(modifier = Modifier.padding(top = Espaciado.s)) {
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

/** Fila horizontal de chips: "Todos", solo favoritos y categorías. */
@Composable
private fun FilaChips(
    categorias: List<Categoria>,
    contadores: Map<Long, Int>,
    totalEnlaces: Int,
    categoriaSeleccionadaId: Long?,
    soloFavoritos: Boolean,
    onSeleccionarCategoria: (Long?) -> Unit,
    onAlternarSoloFavoritos: () -> Unit,
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
            val color = colorCategoria(categoria)
            val cantidad = contadores[categoria.id] ?: 0
            FilterChip(
                selected = categoriaSeleccionadaId == categoria.id,
                onClick = { onSeleccionarCategoria(categoria.id) },
                label = { Text("${categoria.nombre} · $cantidad") },
                leadingIcon = {
                    Icon(
                        iconoCategoria(categoria.icono),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = color,
                    selectedContainerColor = color,
                    selectedLabelColor = colorSobreCategoria(color),
                    selectedLeadingIconColor = colorSobreCategoria(color),
                ),
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
