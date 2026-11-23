package com.coleccionenlaces.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.ui.theme.Espaciado
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.FondoDegradado

/**
 * Pantalla mínima real de la Fase 1: encabezado con el título de la
 * app y texto de marcador. La cuadrícula, la búsqueda y los chips
 * llegan en la Fase 6, que consumirá `viewModel.estado`.
 *
 * @param viewModel ViewModel principal (Fase 5): la Fase 6 observará
 *   su estado único; en esta fase solo se recibe para dejar el
 *   cableado hecho.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    FondoDegradado {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Espaciado.m, vertical = Espaciado.l),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = ColeccionTipografia.tituloGrande,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Espaciado.s))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = ColeccionTipografia.cuerpo,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Espaciado.xl))
            Text(
                text = stringResource(R.string.home_empty),
                style = ColeccionTipografia.descripcion,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Espaciado.l))
            Text(
                text = stringResource(R.string.home_phase_marker),
                style = ColeccionTipografia.etiquetaPequena,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
