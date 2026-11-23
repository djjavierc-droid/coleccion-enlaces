package com.coleccionenlaces.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado

/**
 * Estado vacío (Fase 6, sección 6 del plan): ilustración hecha solo
 * con Compose — formas y degradados, sin imágenes externas — y texto
 * invitando a pegar el primer enlace. Cuando hay filtro o búsqueda
 * activos y no hay resultados, el texto cambia a "sin resultados".
 */
@Composable
fun EstadoVacio(
    hayFiltroActivo: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Espaciado.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IlustracionVacia()
        Spacer(modifier = Modifier.height(Espaciado.l))
        Text(
            text = stringResource(
                if (hayFiltroActivo) R.string.home_no_results_title else R.string.home_empty_title,
            ),
            style = ColeccionTipografia.tituloPantalla,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Espaciado.s))
        Text(
            text = stringResource(
                if (hayFiltroActivo) R.string.home_no_results_body else R.string.home_empty_body,
            ),
            style = ColeccionTipografia.cuerpo,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IlustracionVacia() {
    val esquema = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(208.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Círculo grande con degradado del acento principal al terciario.
        Box(
            modifier = Modifier
                .size(208.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            esquema.primary.copy(alpha = 0.35f),
                            esquema.tertiary.copy(alpha = 0.30f),
                        ),
                    ),
                    CircleShape,
                ),
        )
        // Círculo interior claro con el ícono de enlace.
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(esquema.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = esquema.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        // Adornos: dos círculos pequeños de acento.
        Box(
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = 24.dp)
                .background(esquema.secondary, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.BottomStart)
                .offset(x = 20.dp, y = (-28).dp)
                .background(esquema.tertiary, CircleShape),
        )
    }
}
