package com.coleccionenlaces.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Espaciado consistente en múltiplos de 4/8 dp.
object Espaciado {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
}

private val esquemaOscuro = darkColorScheme(
    primary = VioletaPrimarioOscuro,
    onPrimary = FondoOscuro,
    primaryContainer = VioletaContenedorOscuro,
    onPrimaryContainer = TextoOscuro,
    secondary = CoralSecundarioOscuro,
    onSecondary = FondoOscuro,
    tertiary = CianTerciarioOscuro,
    onTertiary = FondoOscuro,
    background = FondoOscuro,
    onBackground = TextoOscuro,
    surface = SuperficieOscura,
    onSurface = TextoOscuro,
    surfaceVariant = SuperficieVarianteOscura,
    onSurfaceVariant = TextoSecundarioOscuro,
    outline = BordeOscuro
)

private val esquemaClaro = lightColorScheme(
    primary = VioletaPrimarioClaro,
    onPrimary = SuperficieClara,
    primaryContainer = VioletaContenedorClaro,
    onPrimaryContainer = TextoClaro,
    secondary = CoralSecundarioClaro,
    onSecondary = SuperficieClara,
    tertiary = CianTerciarioClaro,
    onTertiary = SuperficieClara,
    background = FondoClaro,
    onBackground = TextoClaro,
    surface = SuperficieClara,
    onSurface = TextoClaro,
    surfaceVariant = SuperficieVarianteClara,
    onSurfaceVariant = TextoSecundarioClaro,
    outline = BordeClaro
)

/**
 * Tema de la app. Modo oscuro predeterminado; modo claro disponible.
 * La tipografía propia se aplica por composable desde [ColeccionTipografia]
 * (MaterialTheme.typography queda con los valores por defecto de M3).
 */
@Composable
fun ColeccionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) esquemaOscuro else esquemaClaro,
        content = content
    )
}

/**
 * Fondo con degradado sutil (nunca color plano): del color primario
 * con transparencia arriba al fondo del tema abajo.
 */
@Composable
fun FondoDegradado(content: @Composable BoxScope.() -> Unit) {
    val esquema = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        esquema.primary.copy(alpha = 0.14f),
                        esquema.background,
                        esquema.background
                    )
                )
            ),
        content = content
    )
}
