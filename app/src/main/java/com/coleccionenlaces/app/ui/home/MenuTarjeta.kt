package com.coleccionenlaces.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.data.EnlaceConCategoria
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado

/**
 * Menú de tarjeta (Fase 8, sección 6 del plan): bottom sheet de
 * Material 3 que se abre con pulsación larga sobre la tarjeta.
 *
 * - Cambiar categoría: las categorías del estado (las 17
 *   predefinidas MÁS las carpetas creadas por el usuario, que
 *   llegan en [categorias]); la actual lleva marca de verificación.
 *   Al elegir, [onCambiarCategoria] recibe el id y la pantalla
 *   llama a `ViewModel.cambiarCategoriaManual` (pone la categoría
 *   y `categoriaManual = true`, así la elección manual no se
 *   sobrescribe jamás).
 * - Favorito: alterna con `ViewModel.alternarFavorito`.
 * - Compartir: comparte la URL con ACTION_SEND y chooser.
 * - Borrar: pide confirmación con un AlertDialog antes de borrar
 *   con `ViewModel.borrarEnlace`.
 *
 * Sin lógica de negocio aquí: solo llama a los callbacks que le
 * pasa la pantalla. El estado de la confirmación de borrado es
 * local de este composable (nada fuera de él lo necesita).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTarjeta(
    item: EnlaceConCategoria,
    categorias: List<Categoria>,
    onCambiarCategoria: (Long) -> Unit,
    onAlternarFavorito: () -> Unit,
    onCompartir: () -> Unit,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit,
) {
    var mostrarConfirmarBorrar by remember { mutableStateOf(false) }
    val enlace = item.enlace

    ModalBottomSheet(onDismissRequest = onCerrar) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Column(modifier = Modifier.padding(horizontal = Espaciado.m)) {
                    Text(
                        text = enlace.titulo,
                        style = ColeccionTipografia.tituloTarjeta,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.size(Espaciado.xs))
                    Text(
                        text = enlace.url,
                        style = ColeccionTipografia.descripcion,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.menu_change_category),
                    style = ColeccionTipografia.etiqueta,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = Espaciado.m,
                        end = Espaciado.m,
                        top = Espaciado.m,
                        bottom = Espaciado.xs,
                    ),
                )
            }
            items(categorias, key = { it.id }) { categoria ->
                val color = colorCategoria(categoria)
                val esActual = categoria.id == item.categoria.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCambiarCategoria(categoria.id)
                            onCerrar()
                        }
                        .padding(
                            horizontal = Espaciado.m,
                            vertical = Espaciado.s + Espaciado.xs,
                        ),
                ) {
                    Icon(
                        imageVector = iconoCategoria(categoria.icono),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(Espaciado.s))
                    Text(
                        text = categoria.nombre,
                        style = ColeccionTipografia.cuerpo,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (esActual) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Espaciado.s),
                )
            }
            item {
                FilaMenu(
                    icono = if (enlace.esFavorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    texto = stringResource(
                        if (enlace.esFavorito) R.string.home_favorite_remove
                        else R.string.home_favorite_add,
                    ),
                    colorIcono = if (enlace.esFavorito) Color(0xFFFF5C8A)
                    else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onAlternarFavorito()
                        onCerrar()
                    },
                )
            }
            item {
                FilaMenu(
                    icono = Icons.Filled.Share,
                    texto = stringResource(R.string.menu_share),
                    colorIcono = MaterialTheme.colorScheme.onSurface,
                    onClick = onCompartir,
                )
            }
            item {
                FilaMenu(
                    icono = Icons.Filled.Delete,
                    texto = stringResource(R.string.menu_delete),
                    colorIcono = MaterialTheme.colorScheme.error,
                    onClick = { mostrarConfirmarBorrar = true },
                )
            }
            item {
                // Hueco inferior para que la última fila no quede
                // pegada al borde del sheet ni a la barra de navegación.
                Spacer(modifier = Modifier.size(Espaciado.xl))
            }
        }
    }

    if (mostrarConfirmarBorrar) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarBorrar = false },
            title = { Text(stringResource(R.string.menu_delete_title)) },
            text = {
                Text(stringResource(R.string.menu_delete_body, enlace.titulo))
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarConfirmarBorrar = false
                        onBorrar()
                        onCerrar()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.menu_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarBorrar = false }) {
                    Text(stringResource(R.string.add_dialog_cancel))
                }
            },
        )
    }
}

/** Una fila de acción del menú (favorito, compartir, borrar). */
@Composable
private fun FilaMenu(
    icono: ImageVector,
    texto: String,
    colorIcono: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = Espaciado.m,
                vertical = Espaciado.s + Espaciado.xs,
            ),
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = colorIcono,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(Espaciado.s))
        Text(
            text = texto,
            style = ColeccionTipografia.cuerpo,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
