package com.coleccionenlaces.app.ui.legal

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.ui.theme.Espaciado

/**
 * Pantalla de un texto legal de "Acerca de" (corrección 1.0.1,
 * punto 2): política de privacidad, acuerdo del usuario o
 * eliminación de datos. Se abre desde el final del menú lateral y
 * muestra el texto completo y real dentro de la aplicación, sin
 * salir al navegador. La flecha vuelve a la pantalla principal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLegal(
    tipo: TipoLegal,
    onVolver: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tituloLegalRes(tipo))) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.legal_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Text(
            text = textoLegal(tipo),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(
                    horizontal = Espaciado.m,
                    vertical = Espaciado.s,
                ),
        )
    }
}
