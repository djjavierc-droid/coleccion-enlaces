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
import com.coleccionenlaces.app.util.extraerTextoAcompanante
import com.coleccionenlaces.app.util.extraerUrl

/**
 * Diálogo de agregar enlace (Fase 7, sección 6 del plan).
 *
 * - Campo de texto para pegar la URL, con botón de pegar desde el
 *   portapapeles: si el portapapeles trae un texto con una URL dentro
 *   (p. ej. lo que comparte YouTube o Facebook), se pega solo la URL
 *   extraída y el texto que la acompañaba se guarda aparte como
 *   fuente automática de título/descripción.
 * - Validación de URL en tiempo real: con el campo vacío o una URL
 *   inválida, el botón Guardar está desactivado y se muestra el error
 *   en el campo.
 * - Al pulsar Guardar se comprueba primero la vista previa
 *   ([onComprobar]): mientras se descarga, barra de progreso, campo
 *   y botones desactivados y cierre bloqueado. El enlace se guarda
 *   SIEMPRE (aunque la vista previa falle: Facebook no deja leer
 *   sus enlaces) con el título 100% automático — oEmbed, título de
 *   la vista previa, texto que acompañaba al enlace, texto visible
 *   del reproductor o dominio — y la tarjeta muestra "Sin vista
 *   previa" si no hubo vista previa.
 *
 * Corrección 1.0.1: NO hay título ni descripción editables. El
 * proceso es 100% automático: nadie escribe títulos en este
 * diálogo, por orden de Javier (la edición manual del Fix 1 quedó
 * eliminada del alcance).
 *
 * - Si la URL resulta inválida al guardar, el error se muestra dentro
 *   del diálogo y NO se cierra (lo trae [error] del ViewModel).
 * - Al guardar con éxito, el ViewModel cierra el diálogo solo: este
 *   composable no decide el cierre. Al cerrarse, sus campos se
 *   limpian solos porque el diálogo deja de componerse.
 *
 * Sin lógica de negocio aquí: la validación es la función pura
 * [esUrlValida], la extracción de URL y texto acompañante son
 * [extraerUrl] y [extraerTextoAcompanante], y el guardado es
 * [HomeViewModel.comprobarVistaPrevia].
 */
@Composable
fun DialogoAgregarEnlace(
    urlInicial: String?,
    textoCompartidoInicial: String?,
    guardando: Boolean,
    comprobando: Boolean,
    error: TipoErrorAgregarEnlace?,
    onComprobar: (String, String?) -> Unit,
    onCerrar: () -> Unit,
    onLimpiarError: () -> Unit,
) {
    var url by remember(urlInicial) { mutableStateOf(urlInicial ?: "") }
    var textoCompartido by remember(textoCompartidoInicial) {
        mutableStateOf(textoCompartidoInicial)
    }
    val portapapeles = LocalClipboardManager.current
    val urlLimpia = url.trim()
    val esValida = esUrlValida(urlLimpia)
    val ocupado = guardando || comprobando
    val guardar: () -> Unit = {
        if (esValida && !ocupado) {
            onComprobar(urlLimpia, textoCompartido)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!ocupado) onCerrar() },
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
                                    textoCompartido = null
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
                                        val urlPegada = extraerUrl(pegado)
                                        url = urlPegada ?: pegado
                                        textoCompartido =
                                            extraerTextoAcompanante(pegado, urlPegada)
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
                            esValida -> {
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
            Button(onClick = guardar, enabled = esValida && !ocupado) {
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
