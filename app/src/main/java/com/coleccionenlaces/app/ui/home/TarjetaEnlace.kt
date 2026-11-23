package com.coleccionenlaces.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.EnlaceConCategoria
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado

/** Estado de la imagen de vista previa de la tarjeta. */
private enum class EstadoImagen { CARGANDO, OK, ERROR }

/**
 * Tarjeta de un enlace (Fase 6, sección 6 del plan).
 *
 * Imagen de vista previa arriba con degradado inferior, título
 * (máx. 2 líneas), descripción (máx. 2 líneas), etiqueta de
 * categoría con su color e ícono de favorito que alterna con
 * [onAlternarFavorito] (esto SÍ funciona en esta fase).
 *
 * Sin imagen (el sitio no ofreció vista previa) se muestra un
 * respaldo con el ícono de enlace sobre el color de la categoría;
 * el enlace se guardó igual, con el dominio como título (Fase 5).
 *
 * La tarjeta NO abre nada al tocarla en esta fase: abrir el enlace
 * y el menú de tarjeta son la Fase 8. Solo el ícono de favorito
 * responde al toque.
 *
 * La animación de entrada (fade y leve desplazamiento) se ejecuta
 * una vez por enlace: el estado vive en `remember` y la cuadrícula
 * usa la clave del id, así que al filtrar no se vuelve a animar.
 */
@Composable
fun TarjetaEnlace(
    item: EnlaceConCategoria,
    onAlternarFavorito: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enlace = item.enlace
    val categoria = item.categoria
    val colorCategoria = colorCategoria(categoria)
    var entrada by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrada = true }

    AnimatedVisibility(
        visible = entrada,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        modifier = modifier,
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                ImagenTarjeta(
                    urlImagen = enlace.urlImagen,
                    titulo = enlace.titulo,
                    colorCategoria = colorCategoria,
                    esFavorito = enlace.esFavorito,
                    onAlternarFavorito = { onAlternarFavorito(enlace.id) },
                )
                Column(
                    modifier = Modifier.padding(Espaciado.s + Espaciado.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = iconoCategoria(categoria.icono),
                            contentDescription = null,
                            tint = colorCategoria,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(Espaciado.xs))
                        Text(
                            text = categoria.nombre,
                            style = ColeccionTipografia.etiquetaPequena,
                            color = colorCategoria,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    Text(
                        text = enlace.titulo,
                        style = ColeccionTipografia.tituloTarjeta.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val descripcion = enlace.descripcion.ifBlank { enlace.url }
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    Text(
                        text = descripcion,
                        style = ColeccionTipografia.descripcion.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagenTarjeta(
    urlImagen: String?,
    titulo: String,
    colorCategoria: Color,
    esFavorito: Boolean,
    onAlternarFavorito: () -> Unit,
) {
    var estadoImagen by remember(urlImagen) { mutableStateOf(EstadoImagen.CARGANDO) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (urlImagen != null) {
            AsyncImage(
                model = urlImagen,
                contentDescription = titulo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { estadoImagen = EstadoImagen.CARGANDO },
                onSuccess = { estadoImagen = EstadoImagen.OK },
                onError = { estadoImagen = EstadoImagen.ERROR },
            )
        }
        if (urlImagen == null || estadoImagen == EstadoImagen.ERROR) {
            // Respaldo sin imagen: ícono de enlace sobre el color de
            // la categoría, sin imagen externa.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorCategoria.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = colorCategoria,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        if (urlImagen != null && estadoImagen == EstadoImagen.CARGANDO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(rememberShimmerBrush()),
            )
        }
        // Degradado inferior para que el texto sea legible sobre la
        // imagen (sección 6 del plan).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
                ),
        )
        IconButton(
            onClick = onAlternarFavorito,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Espaciado.s)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                imageVector = if (esFavorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (esFavorito) R.string.home_favorite_remove else R.string.home_favorite_add,
                ),
                tint = if (esFavorito) Color(0xFFFF5C8A) else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
