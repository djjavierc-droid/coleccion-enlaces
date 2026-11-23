package com.coleccionenlaces.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.TipoErrorAgregarEnlace
import com.coleccionenlaces.app.ui.theme.Espaciado
import com.coleccionenlaces.app.util.esEnlaceFacebook
import com.coleccionenlaces.app.util.esUrlValida
import com.coleccionenlaces.app.util.extraerUrl

/**
 * Diálogo de agregar enlace (Fase 7, sección 6 del plan; Fix 1:
 * vista previa en dos pasos con título y descripción editables).
 *
 * - Campo de texto para pegar la URL, con botón de pegar desde el
 *   portapapeles: si el portapapeles trae un texto con una URL dentro
 *   (p. ej. lo que comparte YouTube), se pega solo la URL extraída.
 * - Validación de URL en tiempo real: con el campo vacío o una URL
 *   inválida, el botón Guardar está desactivado y se muestra el error
 *   en el campo.
 * - Al pulsar Guardar se comprueba primero la vista previa
 *   ([onComprobar]): mientras se descarga, barra de progreso, campo
 *   y botones desactivados y cierre bloqueado.
 * - Vista previa correcta: el enlace se guarda directamente y el
 *   diálogo se cierra solo (lo hace el ViewModel).
 * - Vista previa fallida ([sinVistaPrevia]): el diálogo NO se cierra
 *   ni guarda todavía. Muestra el aviso "Sin vista previa" (para un
 *   enlace de Facebook, el aviso lo dice claro: Facebook no deja
 *   leer la vista previa) y deja EDITAR el título (prellenado con
 *   [tituloSugerido]: el de la vista previa o el dominio) y la
 *   descripción antes de guardar con [onGuardarEditado]. El título y
 *   la descripción editados alimentan al clasificador igual que los
 *   de la vista previa. Si el usuario cambia la URL tras el fallo,
 *   la edición se descarta ([onUrlCambiada]) y hay que comprobar de
 *   nuevo.
 * - Si la URL resulta inválida al guardar, el error se muestra dentro
 *   del diálogo y NO se cierra (lo trae [error] del ViewModel).
 * - Al guardar con éxito, el ViewModel cierra el diálogo solo: este
 *   composable no decide el cierre. Al cerrarse, sus campos se
 *   limpian solos porque el diálogo deja de componerse.
 *
 * Sin lógica de negocio aquí: la validación es la función pura
 * [esUrlValida], la detección de Facebook es [esEnlaceFacebook] y el
 * guardado es [HomeViewModel.comprobarVistaPrevia] /
 * [HomeViewModel.guardarEnlaceEditado].
 */
@Composable
fun DialogoAgregarEnlace(
    urlInicial: String?,
    guardando: Boolean,
    comprobando: Boolean,
    sinVistaPrevia: Boolean,
    tituloSugerido: String?,
    error: TipoErrorAgregarEnlace?,
    onComprobar: (String) -> Unit,
    onGuardarEditado: (String, String, String) -> Unit,
    onUrlCambiada: () -> Unit,
    onCerrar: () -> Unit,
    onLimpiarError: () -> Unit,
) {
    var url by remember(urlInicial) { mutableStateOf(urlInicial ?: "") }
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    // Al aparecer la edición, el título se prellena con el sugerido
    // (el de la vista previa o el dominio) y la descripción vacía.
    LaunchedEffect(sinVistaPrevia) {
        if (sinVistaPrevia) {
            titulo = tituloSugerido ?: ""
            descripcion = ""
        }
    }
    val portapapeles = LocalClipboardManager.current
    val urlLimpia = url.trim()
    val esValida = esUrlValida(urlLimpia)
    val ocupado = guardando || comprobando
    val tituloLimpio = titulo.trim()
    val guardar: () -> Unit = {
        if (esValida && !ocupado) {
            if (sinVistaPrevia) {
                if (tituloLimpio.isNotEmpty()) {
                    onGuardarEditado(urlLimpia, tituloLimpio, descripcion.trim())
                }
            } else {
                onComprobar(urlLimpia)
            }
        }
    }
    val guardarHabilitado = esValida && !ocupado &&
        (!sinVistaPrevia || tituloLimpio.isNotEmpty())

    AlertDialog(
        onDismissRequest = { if (!ocupado) onCerrar() },
        title = { Text(stringResource(R.string.add_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { nuevo ->
                        if (sinVistaPrevia) onUrlCambiada()
                        url = nuevo
                        if (error != null) onLimpiarError()
                    },
                    label = { Text(stringResource(R.string.add_dialog_url_label)) },
                    placeholder = { Text(stringResource(R.string.add_dialog_url_placeholder)) },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (sinVistaPrevia) onUrlCambiada()
                                    url = ""
                                    if (error != null) onLimpiarError()
                                },
                                enabled = !ocupado,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.add_dialog_clear),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val pegado = portapapeles.getText()?.text
                                    if (!pegado.isNullOrBlank()) {
                                        url = extraerUrl(pegado) ?: pegado
                                    }
                                },
                                enabled = !ocupado,
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = stringResource(R.string.add_dialog_paste),
                                )
                            }
                        }
                    },
                    supportingText = {
                        when {
                            urlLimpia.isNotEmpty() && !esValida -> {
                                Text(stringResource(R.string.add_dialog_url_invalid))
                            }
                            esValida && !sinVistaPrevia -> {
                                Text(stringResource(R.string.add_dialog_url_hint))
                            }
                        }
                    },
                    isError = urlLimpia.isNotEmpty() && !esValida,
                    enabled = !ocupado,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { guardar() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sinVistaPrevia) {
                    Spacer(modifier = Modifier.height(Espaciado.m))
                    Text(
                        text = stringResource(R.string.add_dialog_no_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    Text(
                        text = stringResource(
                            if (esEnlaceFacebook(urlLimpia)) {
                                R.string.add_dialog_no_preview_facebook_body
                            } else {
                                R.string.add_dialog_no_preview_body
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Espaciado.m))
                    OutlinedTextField(
                        value = titulo,
                        onValueChange = { titulo = it },
                        label = { Text(stringResource(R.string.add_dialog_title_label)) },
                        enabled = !ocupado,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(Espaciado.s))
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        label = { Text(stringResource(R.string.add_dialog_description_label)) },
                        enabled = !ocupado,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { guardar() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(Espaciado.s))
                    Text(
                        text = stringResource(
                            when (error) {
                                TipoErrorAgregarEnlace.URL_INVALIDA ->
                                    R.string.add_dialog_error_invalid
                                TipoErrorAgregarEnlace.ERROR_AL_GUARDAR ->
                                    R.string.add_dialog_error_save
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (ocupado) {
                    Spacer(modifier = Modifier.height(Espaciado.m))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(Espaciado.s))
                    Text(
                        text = stringResource(
                            if (comprobando) {
                                R.string.add_dialog_loading
                            } else {
                                R.string.add_dialog_saving
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = guardar, enabled = guardarHabilitado) {
                Text(stringResource(R.string.add_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !ocupado) {
                Text(stringResource(R.string.add_dialog_cancel))
            }
        },
        modifier = Modifier.padding(Espaciado.s),
    )
}
