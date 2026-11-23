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
import com.coleccionenlaces.app.util.esUrlValida
import com.coleccionenlaces.app.util.extraerUrl

/**
 * Diálogo de agregar enlace (Fase 7, sección 6 del plan).
 *
 * - Campo de texto para pegar la URL, con botón de pegar desde el
 *   portapapeles: si el portapapeles trae un texto con una URL dentro
 *   (p. ej. lo que comparte YouTube), se pega solo la URL extraída.
 * - Validación de URL en tiempo real: con el campo vacío o una URL
 *   inválida, el botón Guardar está desactivado y se muestra el error
 *   en el campo; una URL válida muestra la nota de que sin vista
 *   previa se guarda igual con el dominio como título.
 * - Estado de carga mientras se obtiene la vista previa: barra de
 *   progreso, campo y botones desactivados y cierre bloqueado.
 * - Si la URL resulta inválida al guardar, el error se muestra dentro
 *   del diálogo y NO se cierra (lo trae [error] del ViewModel).
 * - Al guardar con éxito, el ViewModel cierra el diálogo solo: este
 *   composable no decide el cierre. Al cerrarse, su campo se limpia
 *   solo porque el diálogo deja de componerse.
 *
 * Sin lógica de negocio aquí: la validación es la función pura
 * [esUrlValida] y el guardado es [HomeViewModel.agregarEnlace].
 */
@Composable
fun DialogoAgregarEnlace(
    urlInicial: String?,
    guardando: Boolean,
    error: TipoErrorAgregarEnlace?,
    onGuardar: (String) -> Unit,
    onCerrar: () -> Unit,
    onLimpiarError: () -> Unit,
) {
    var url by remember(urlInicial) { mutableStateOf(urlInicial ?: "") }
    val portapapeles = LocalClipboardManager.current
    val urlLimpia = url.trim()
    val esValida = esUrlValida(urlLimpia)
    val guardar: () -> Unit = { if (esValida && !guardando) onGuardar(urlLimpia) }

    AlertDialog(
        onDismissRequest = { if (!guardando) onCerrar() },
        title = { Text(stringResource(R.string.add_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { nuevo ->
                        url = nuevo
                        if (error != null) onLimpiarError()
                    },
                    label = { Text(stringResource(R.string.add_dialog_url_label)) },
                    placeholder = { Text(stringResource(R.string.add_dialog_url_placeholder)) },
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    url = ""
                                    if (error != null) onLimpiarError()
                                },
                                enabled = !guardando,
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
                                enabled = !guardando,
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
                            esValida -> {
                                Text(stringResource(R.string.add_dialog_url_hint))
                            }
                        }
                    },
                    isError = urlLimpia.isNotEmpty() && !esValida,
                    enabled = !guardando,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { guardar() }),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                if (guardando) {
                    Spacer(modifier = Modifier.height(Espaciado.m))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(Espaciado.s))
                    Text(
                        text = stringResource(R.string.add_dialog_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = guardar, enabled = esValida && !guardando) {
                Text(stringResource(R.string.add_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !guardando) {
                Text(stringResource(R.string.add_dialog_cancel))
            }
        },
        modifier = Modifier.padding(Espaciado.s),
    )
}
