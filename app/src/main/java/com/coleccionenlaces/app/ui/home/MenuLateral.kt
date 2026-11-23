package com.coleccionenlaces.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coleccionenlaces.app.R
import com.coleccionenlaces.app.data.Categoria
import com.coleccionenlaces.app.ui.theme.ColeccionTipografia
import com.coleccionenlaces.app.ui.theme.Espaciado

/**
 * Una fila del menú lateral para una categoría. Solo lleva lo que el
 * menú muestra: nombre, color, ícono, contador y si sigue bloqueada.
 * NO lleva títulos, descripciones, imágenes ni URLs de enlaces: de
 * una carpeta bloqueada el menú no revela ningún contenido (solo su
 * nombre, su ícono y su contador, que es solo un número, como ya
 * hacían los chips). El bloqueo por huella se respeta: tocar una
 * fila bloqueada pide la huella antes de seleccionarla, y sus
 * enlaces no aparecen en ningún sitio hasta desbloquearla.
 */
data class ItemMenuCategoria(
    val categoriaId: Long,
    val nombre: String,
    val color: Long,
    val icono: String,
    val cantidad: Int,
    val bloqueada: Boolean,
    val esPredefinida: Boolean,
)

/**
 * Construye las filas del menú lateral desde las categorías, sus
 * contadores y las desbloqueadas de la sesión. Función pura: el
 * bloqueo por huella se refleja en [ItemMenuCategoria.bloqueada] y
 * el contenido de las bloqueadas nunca llega a estas filas, porque
 * no forma parte de ellas.
 */
fun itemsMenuLateral(
    categorias: List<Categoria>,
    contadores: Map<Long, Int>,
    desbloqueadas: Set<Long>,
): List<ItemMenuCategoria> = categorias.map { categoria ->
    ItemMenuCategoria(
        categoriaId = categoria.id,
        nombre = categoria.nombre,
        color = categoria.color,
        icono = categoria.icono,
        cantidad = contadores[categoria.id] ?: 0,
        bloqueada = categoria.id !in desbloqueadas,
        esPredefinida = categoria.esPredefinida,
    )
}

/**
 * Contenido del menú lateral deslizante (corrección 1.0.1, punto 2):
 * sustituye a la fila horizontal de chips, que se cortaba en el
 * borde. Las categorías van en vertical, cada una con su ícono, su
 * color y su contador; arriba van "Todos" (con el total) y
 * "Favoritos", y al final "Nueva carpeta" para crear una carpeta
 * propia. La pulsación larga sobre una carpeta PROPIA abre su
 * gestión (renombrar o borrar), como hacía la pulsación larga sobre
 * su chip; sobre una predefinida no hace nada.
 *
 * El contador total y la búsqueda se quedan en la pantalla
 * principal: este menú solo lista categorías.
 */
@Composable
fun MenuLateral(
    categorias: List<Categoria>,
    contadores: Map<Long, Int>,
    totalEnlaces: Int,
    categoriaSeleccionadaId: Long?,
    soloFavoritos: Boolean,
    categoriasDesbloqueadas: Set<Long>,
    onSeleccionarTodos: () -> Unit,
    onAlternarSoloFavoritos: () -> Unit,
    onSeleccionarCategoria: (Long) -> Unit,
    onCrearCarpeta: () -> Unit,
    onGestionarCarpeta: (Categoria) -> Unit,
) {
    val items = itemsMenuLateral(categorias, contadores, categoriasDesbloqueadas)
    val porId = categorias.associateBy { it.id }
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Espaciado.s, vertical = Espaciado.m),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = ColeccionTipografia.tituloGrande,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = Espaciado.m,
                    bottom = Espaciado.xs,
                ),
            )
            Text(
                text = when (totalEnlaces) {
                    0 -> stringResource(R.string.home_total_none)
                    1 -> stringResource(R.string.home_total_one)
                    else -> stringResource(R.string.home_total_many, totalEnlaces)
                },
                style = ColeccionTipografia.etiqueta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Espaciado.m,
                    bottom = Espaciado.m,
                ),
            )
            FilaMenu(
                nombre = "${stringResource(R.string.home_chip_todos)} · $totalEnlaces",
                icono = Icons.Filled.Link,
                colorCirculo = MaterialTheme.colorScheme.primary,
                colorIcono = MaterialTheme.colorScheme.onPrimary,
                seleccionado = categoriaSeleccionadaId == null && !soloFavoritos,
                bloqueada = false,
                onClick = onSeleccionarTodos,
                onLongClick = null,
            )
            FilaMenu(
                nombre = stringResource(R.string.home_chip_favoritos),
                icono = Icons.Filled.Favorite,
                colorCirculo = MaterialTheme.colorScheme.secondary,
                colorIcono = MaterialTheme.colorScheme.onSecondary,
                seleccionado = soloFavoritos,
                bloqueada = false,
                onClick = onAlternarSoloFavoritos,
                onLongClick = null,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Espaciado.s),
            )
            for (item in items) {
                val categoria = porId[item.categoriaId]
                val color = colorCategoria(
                    categoria ?: Categoria(
                        id = item.categoriaId,
                        nombre = item.nombre,
                        color = item.color,
                        icono = item.icono,
                        esPredefinida = item.esPredefinida,
                        orden = 0,
                    ),
                )
                FilaMenu(
                    nombre = "${item.nombre} · ${item.cantidad}",
                    icono = iconoCategoria(item.icono),
                    colorCirculo = color,
                    colorIcono = colorSobreCategoria(color),
                    seleccionado = categoriaSeleccionadaId == item.categoriaId,
                    bloqueada = item.bloqueada,
                    onClick = { onSeleccionarCategoria(item.categoriaId) },
                    onLongClick = if (item.esPredefinida || categoria == null) {
                        null
                    } else {
                        { onGestionarCarpeta(categoria) }
                    },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Espaciado.s),
            )
            FilaMenu(
                nombre = stringResource(R.string.carpeta_add_chip),
                icono = Icons.Filled.Add,
                colorCirculo = MaterialTheme.colorScheme.surfaceVariant,
                colorIcono = MaterialTheme.colorScheme.onSurfaceVariant,
                seleccionado = false,
                bloqueada = false,
                onClick = onCrearCarpeta,
                onLongClick = null,
            )
        }
    }
}

/** Una fila del menú: círculo de color con ícono, nombre, contador y candado si está bloqueada. */
@Composable
private fun FilaMenu(
    nombre: String,
    icono: ImageVector,
    colorCirculo: Color,
    colorIcono: Color,
    seleccionado: Boolean,
    bloqueada: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (seleccionado) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = Espaciado.s + Espaciado.xs,
                vertical = Espaciado.s,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colorCirculo, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = colorIcono,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(Espaciado.s + Espaciado.xs))
        Text(
            text = nombre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (bloqueada) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
