package com.coleccionenlaces.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.coleccionenlaces.app.ui.theme.Espaciado

/**
 * Efecto shimmer (Fase 6, sección 6 del plan): un degradado que se
 * desplaza sobre el color de superficie, sin spinner genérico. Se
 * usa mientras se carga la primera lista y mientras se agrega un
 * enlace (se obtiene su vista previa).
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transicion = rememberInfiniteTransition(label = "shimmer")
    val desplazamiento by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "desplazamientoShimmer",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.55f),
            base.copy(alpha = 0.95f),
            base.copy(alpha = 0.55f),
        ),
        start = Offset(desplazamiento - 450f, 0f),
        end = Offset(desplazamiento, 0f),
    )
}

/** Tarjeta de carga con la misma forma que [TarjetaEnlace]. */
@Composable
fun TarjetaShimmer(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmerBrush()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .background(shimmer),
            )
            Column(modifier = Modifier.padding(Espaciado.s + Espaciado.xs)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(shimmer, RoundedCornerShape(7.dp)),
                )
                Spacer(modifier = Modifier.height(Espaciado.s))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(shimmer, RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.height(Espaciado.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(14.dp)
                        .background(shimmer, RoundedCornerShape(7.dp)),
                )
            }
        }
    }
}
