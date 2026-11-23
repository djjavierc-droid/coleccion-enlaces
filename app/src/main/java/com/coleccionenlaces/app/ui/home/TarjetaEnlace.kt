package com.coleccionenlaces.app.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.EnlaceConCategoria
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado
import java.io.File

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
 * respaldo con el ícono de enlace sobre el color de la categoría y
 * el aviso "Sin vista previa"; el enlace se guardó igual, con el
 * título automático (corrección 1.0.1).
 *
 * Miniatura de vídeo (corrección 1.0.1, punto 3): si el enlace trae
 * el HTML del reproductor oEmbed ([Enlace.htmlVideo]) y aún no tiene
 * miniatura local, la tarjeta pinta el reproductor en un WebView
 * sin autoplay y captura su cuadro con [MiniaturaWebView]: el
 * cuadro se guarda en local con [onMiniaturaCapturada] y el texto
 * visible del reproductor se entrega con [onTextoDetectado] para
 * enriquecer el título. La prioridad de imagen es: miniatura local
 * > reproductor oEmbed (WebView) > imagen de la vista previa >
 * respaldo con aviso.
 *
 * Fix 4, bug 3: si el reproductor muestra la página de error de
 * Facebook ("Video no disponible"), el WebView NO captura el cuadro
 * y la tarjeta muestra su marcador propio ("Video no disponible")
 * en vez de la página de error como imagen.
 *
 * La animación de entrada (fade y leve desplazamiento) se ejecuta
 * una vez por enlace: el estado vive en `remember` y la cuadrícula
 * usa la clave del id, así que al filtrar no se vuelve a animar.
 *
 * Fase 8: tocar la tarjeta abre el enlace ([onAbrir]) y la
 * pulsación larga abre el menú de tarjeta ([onAbrirMenu]). El
 * ícono de favorito sigue siendo su propio botón: su toque lo
 * consume él y no abre el enlace.
 */
@Composable
fun TarjetaEnlace(
    item: EnlaceConCategoria,
    onAlternarFavorito: (Long) -> Unit,
    onAbrir: () -> Unit,
    onAbrirMenu: () -> Unit,
    onMiniaturaCapturada: (Long, Bitmap) -> Unit = { _, _ -> },
    onTextoDetectado: (Long, String) -> Unit = { _, _ -> },
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
            modifier = Modifier.combinedClickable(
                onClick = onAbrir,
                onLongClick = onAbrirMenu,
            ),
        ) {
            Column {
                ImagenTarjeta(
                    imagenLocal = enlace.imagenLocal,
                    htmlVideo = enlace.htmlVideo,
                    urlImagen = enlace.urlImagen,
                    url = enlace.url,
                    titulo = enlace.titulo,
                    colorCategoria = colorCategoria,
                    esFavorito = enlace.esFavorito,
                    onAlternarFavorito = { onAlternarFavorito(enlace.id) },
                    onMiniaturaCapturada = { bitmap ->
                        onMiniaturaCapturada(enlace.id, bitmap)
                    },
                    onTextoDetectado = { texto ->
                        onTextoDetectado(enlace.id, texto)
                    },
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
    imagenLocal: String?,
    htmlVideo: String?,
    urlImagen: String?,
    url: String,
    titulo: String,
    colorCategoria: Color,
    esFavorito: Boolean,
    onAlternarFavorito: () -> Unit,
    onMiniaturaCapturada: (Bitmap) -> Unit,
    onTextoDetectado: (String) -> Unit,
) {
    // Estado de carga por fuente: si la local falla se cae al
    // reproductor o a la remota, y si la remota falla al respaldo.
    var estadoLocal by remember(imagenLocal) { mutableStateOf(EstadoImagen.CARGANDO) }
    var estadoRemota by remember(urlImagen) { mutableStateOf(EstadoImagen.CARGANDO) }
    // Fix 4, bug 3: el reproductor mostró la página de error de
    // Facebook ("Video no disponible"): se esconde el WebView y se
    // muestra el marcador propio, nunca la página de error.
    var videoNoDisponible by remember(htmlVideo) { mutableStateOf(false) }
    val mostrarLocal = imagenLocal != null && estadoLocal != EstadoImagen.ERROR
    val mostrarWebView = !mostrarLocal && htmlVideo != null && !videoNoDisponible
    val mostrarRemota = !mostrarLocal && !mostrarWebView &&
        urlImagen != null && estadoRemota != EstadoImagen.ERROR
    val mostrarRespaldo = !mostrarLocal && !mostrarWebView && !mostrarRemota
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (mostrarLocal) {
            AsyncImage(
                model = File(imagenLocal!!),
                contentDescription = titulo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { estadoLocal = EstadoImagen.CARGANDO },
                onSuccess = { estadoLocal = EstadoImagen.OK },
                onError = { estadoLocal = EstadoImagen.ERROR },
            )
        } else if (mostrarWebView) {
            MiniaturaWebView(
                html = htmlVideo!!,
                baseUrl = url,
                onMiniaturaCapturada = onMiniaturaCapturada,
                onTextoDetectado = onTextoDetectado,
                onVideoNoDisponible = { videoNoDisponible = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (mostrarRemota) {
            AsyncImage(
                model = urlImagen,
                contentDescription = titulo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { estadoRemota = EstadoImagen.CARGANDO },
                onSuccess = { estadoRemota = EstadoImagen.OK },
                onError = { estadoRemota = EstadoImagen.ERROR },
            )
        }
        if (mostrarRespaldo) {
            // Respaldo sin imagen: ícono de enlace sobre el color de
            // la categoría, con el aviso "Sin vista previa" (la
            // tarjeta lo muestra sin pedirle nada al usuario).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorCategoria.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = colorCategoria,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(Espaciado.xs))
                    Text(
                        text = stringResource(
                            if (videoNoDisponible) {
                                R.string.card_video_no_disponible
                            } else {
                                R.string.card_no_preview
                            },
                        ),
                        style = ColeccionTipografia.etiqueta,
                        color = colorCategoria,
                    )
                }
            }
        }
        val cargandoImagen = (mostrarLocal && estadoLocal == EstadoImagen.CARGANDO) ||
            (mostrarRemota && estadoRemota == EstadoImagen.CARGANDO)
        if (cargandoImagen) {
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
            onClick = {
                // Feedback táctil al marcar/quitar favorito (Fase 9).
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAlternarFavorito()
            },
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
