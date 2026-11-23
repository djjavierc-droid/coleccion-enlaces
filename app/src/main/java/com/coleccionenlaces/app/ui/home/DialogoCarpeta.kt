package com.coleccionenlaces.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.data.TipoErrorCarpeta
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado

/** Paleta de colores predefinidos para las carpetas propias. */
val COLORES_CARPETA: List<Long> = listOf(
    0xFF7E57C2, // violeta
    0xFF00897B, // verde azulado
    0xFFD81B60, // rosa oscuro
    0xFFFB8C00, // naranja
    0xFF1E88E5, // azul
    0xFF43A047, // verde
    0xFF5E35B1, // púrpura
    0xFF6D4C41, // marrón
)

/** Íconos Material ofrecidos para las carpetas propias. */
val ICONOS_CARPETA: List<String> = listOf(
    "category",
    "favorite",
    "bookmark",
    "star",
    "home",
    "work",
    "school",
    "music_note",
    "photo_camera",
    "pets",
    "sports_soccer",
    "restaurant",
)

/**
 * Diálogo de carpeta propia (Fase 9, orden de Javier).
 *
 * - Crear ([carpeta] null): nombre obligatorio, elección de color
 *   (paleta predefinida) y de ícono (lista de íconos Material).
 * - Renombrar ([carpeta] con valor): solo el nombre, prellenado; el
 *   color y el ícono no se cambian aquí.
 *
 * Sin lógica de negocio aquí: el guardado es
 * [HomeViewModel.crearCarpeta] o [HomeViewModel.renombrarCarpeta].
 * Con éxito el ViewModel cierra el diálogo solo; con error
 * ([error]) el diálogo se queda abierto y muestra el mensaje
 * amigable dentro de él. El botón de guardar está desactivado con
 * el nombre vacío.
 */
@Composable
fun DialogoCarpeta(
    carpeta: Categoria?,
    error: TipoErrorCarpeta?,
    onCrear: (nombre: String, color: Long, icono: String) -> Unit,
    onRenombrar: (categoriaId: Long, nombre: String) -> Unit,
    onCerrar: () -> Unit,
    onLimpiarError: () -> Unit,
) {
    val esRenombrar = carpeta != null
    var nombre by remember(carpeta) { mutableStateOf(carpeta?.nombre ?: "") }
    var colorElegido by remember { mutableStateOf(COLORES_CARPETA.first()) }
    var iconoElegido by remember { mutableStateOf(ICONOS_CARPETA.first()) }

    AlertDialog(
        onDismissRequest = onCerrar,
        modifier = Modifier.widthIn(max = 560.dp),
        title = {
            Text(
                stringResource(
                    if (esRenombrar) R.string.carpeta_rename_title
                    else R.string.carpeta_create_title,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        if (error != null) onLimpiarError()
                    },
                    label = { Text(stringResource(R.string.carpeta_name_label)) },
                    placeholder = {
                        Text(stringResource(R.string.carpeta_name_placeholder))
                    },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    Text(
                        text = stringResource(textoErrorCarpeta(error)),
                        style = ColeccionTipografia.descripcion,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!esRenombrar) {
                    Spacer(modifier = Modifier.height(Espaciado.m))
                    Text(
                        text = stringResource(R.string.carpeta_color_label),
                        style = ColeccionTipografia.etiqueta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Espaciado.s),
                        contentPadding = PaddingValues(vertical = Espaciado.xs),
                    ) {
                        items(COLORES_CARPETA) { colorLong ->
                            val color = Color(colorLong)
                            val elegido = colorLong == colorElegido
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .then(
                                        if (elegido) {
                                            Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { colorElegido = colorLong },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (elegido) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = colorSobreCategoria(color),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Espaciado.s))
                    Text(
                        text = stringResource(R.string.carpeta_icon_label),
                        style = ColeccionTipografia.etiqueta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Espaciado.xs),
                        contentPadding = PaddingValues(vertical = Espaciado.xs),
                    ) {
                        items(ICONOS_CARPETA) { clave ->
                            val elegido = clave == iconoElegido
                            IconButton(
                                onClick = { iconoElegido = clave },
                                modifier = Modifier
                                    .size(44.dp)
                                    .then(
                                        if (elegido) {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Icon(
                                    imageVector = iconoCategoria(clave),
                                    contentDescription = clave,
                                    tint = if (elegido) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (esRenombrar) {
                        onRenombrar(carpeta!!.id, nombre)
                    } else {
                        onCrear(nombre, colorElegido, iconoElegido)
                    }
                },
                enabled = nombre.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (esRenombrar) R.string.carpeta_rename_save
                        else R.string.carpeta_create_save,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.add_dialog_cancel))
            }
        },
    )
}

/** Texto amigable para cada error de carpeta. */
private fun textoErrorCarpeta(error: TipoErrorCarpeta): Int = when (error) {
    TipoErrorCarpeta.NOMBRE_VACIO -> R.string.carpeta_error_empty
    TipoErrorCarpeta.NOMBRE_DUPLICADO -> R.string.carpeta_error_duplicate
    TipoErrorCarpeta.LIMITE_ALCANZADO -> R.string.carpeta_error_limit
    TipoErrorCarpeta.NO_ES_PROPIA -> R.string.carpeta_error_not_own
    TipoErrorCarpeta.NO_EXISTE -> R.string.carpeta_error_not_found
}

/**
 * Diálogo de gestionar una carpeta propia (Fase 9).
 *
 * Se abre con pulsación larga sobre el chip de una carpeta propia
 * (las predefinidas no se pueden renombrar ni borrar, así que su
 * pulsación larga no hace nada). Ofrece Renombrar, Borrar y
 * Cancelar; borrar pide confirmación aparte
 * ([DialogoConfirmarBorrarCarpeta]).
 */
@Composable
fun DialogoGestionarCarpeta(
    carpeta: Categoria,
    onRenombrar: () -> Unit,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text(carpeta.nombre) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.carpeta_manage_body),
                    style = ColeccionTipografia.descripcion,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Espaciado.s))
                FilaGestionarCarpeta(
                    icono = Icons.Filled.Edit,
                    texto = stringResource(R.string.carpeta_manage_rename),
                    colorIcono = MaterialTheme.colorScheme.onSurface,
                    onClick = onRenombrar,
                )
                FilaGestionarCarpeta(
                    icono = Icons.Filled.Delete,
                    texto = stringResource(R.string.carpeta_manage_delete),
                    colorIcono = MaterialTheme.colorScheme.error,
                    onClick = onBorrar,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.add_dialog_cancel))
            }
        },
    )
}

/** Una fila de acción del diálogo de gestionar carpeta. */
@Composable
private fun FilaGestionarCarpeta(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    texto: String,
    colorIcono: Color,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Espaciado.s + Espaciado.xs),
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

/**
 * Confirmación de borrar una carpeta propia (Fase 9).
 *
 * Sus enlaces no se borran: pasan a "Otros" (SET_NULL, decisión de
 * la Fase 2), como avisa el texto del diálogo.
 */
@Composable
fun DialogoConfirmarBorrarCarpeta(
    carpeta: Categoria,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text(stringResource(R.string.carpeta_delete_title)) },
        text = {
            Text(stringResource(R.string.carpeta_delete_body, carpeta.nombre))
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.menu_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.add_dialog_cancel))
            }
        },
    )
}
