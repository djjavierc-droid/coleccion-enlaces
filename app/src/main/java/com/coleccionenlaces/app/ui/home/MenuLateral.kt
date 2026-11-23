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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
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
import com.coleccionenlaces.app.ui.legal.TipoLegal
import com.coleccionenlaces.app.ui.legal.tituloLegalRes
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
 * Una fila del menú lateral, en el orden en que se dibuja. Función
 * pura: el composable [MenuLateral] solo la dibuja con un
 * [LazyColumn], así que TODAS las filas son alcanzables desplazando
 * (Fix 4, bug 1: antes el contenido era un [Column] sin scroll y las
 * categorías a partir de "Hazlo tú mismo (DIY)" quedaban cortadas en
 * el borde inferior, con "Acerca de" inalcanzable).
 */
sealed interface FilaMenuLateral {
    /** Encabezado: título de la app y total de enlaces. */
    data object Encabezado : FilaMenuLateral

    /** Fila "Todos · N". */
    data object Todos : FilaMenuLateral

    /** Fila "Favoritos". */
    data object Favoritos : FilaMenuLateral

    /** Separador horizontal. */
    data object Separador : FilaMenuLateral

    /** Una categoría (predefinida o propia). */
    data class Categoria(val item: ItemMenuCategoria) : FilaMenuLateral

    /** Fila "Nueva carpeta". */
    data object NuevaCarpeta : FilaMenuLateral

    /** Título de sección ("Acerca de"). */
    data object TituloAcercaDe : FilaMenuLateral

    /** Una entrada de "Acerca de" (legal). */
    data class Legal(val tipo: TipoLegal) : FilaMenuLateral
}

/**
 * Construye las filas del menú lateral en orden: encabezado, "Todos",
 * "Favoritos", las categorías (predefinidas + propias, en su orden),
 * "Nueva carpeta" y la sección "Acerca de" con sus tres entradas.
 * Función pura: las pruebas comprueban que ninguna categoría ni
 * entrada legal se pierde, sea cual sea el número de carpetas.
 */
fun filasMenuLateral(
    categorias: List<Categoria>,
    contadores: Map<Long, Int>,
    desbloqueadas: Set<Long>,
): List<FilaMenuLateral> {
    val filas = mutableListOf<FilaMenuLateral>()
    filas += FilaMenuLateral.Encabezado
    filas += FilaMenuLateral.Todos
    filas += FilaMenuLateral.Favoritos
    filas += FilaMenuLateral.Separador
    for (item in itemsMenuLateral(categorias, contadores, desbloqueadas)) {
        filas += FilaMenuLateral.Categoria(item)
    }
    filas += FilaMenuLateral.Separador
    filas += FilaMenuLateral.NuevaCarpeta
    filas += FilaMenuLateral.Separador
    filas += FilaMenuLateral.TituloAcercaDe
    for (tipo in TipoLegal.entries) {
        filas += FilaMenuLateral.Legal(tipo)
    }
    return filas
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
 * Al final va la sección "Acerca de" (modelo del cajón de Reddit):
 * Política de privacidad, Acuerdo del usuario y Eliminación de
 * datos. Cada una abre su pantalla dentro de la aplicación con el
 * texto completo ([onAbrirLegal]); el cajón se cierra al tocarla.
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
    onAbrirLegal: (TipoLegal) -> Unit,
) {
    val porId = categorias.associateBy { it.id }
    val filas = filasMenuLateral(categorias, contadores, categoriasDesbloqueadas)
    ModalDrawerSheet {
        // Fix 4, bug 1: LazyColumn en vez de Column. El cajón tiene
        // 17 categorías + carpetas propias + "Acerca de": en un
        // Column sin scroll las últimas filas quedaban cortadas en
        // el borde inferior y no se podía bajar. Con LazyColumn
        // todo el contenido es alcanzable desplazando.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Espaciado.s, vertical = Espaciado.m),
        ) {
            itemsIndexed(filas, key = { indice, _ -> indice }) { _, fila ->
                when (fila) {
                    FilaMenuLateral.Encabezado -> EncabezadoMenu(totalEnlaces)
                    FilaMenuLateral.Todos -> FilaMenu(
                        nombre = "${stringResource(R.string.home_chip_todos)} · $totalEnlaces",
                        icono = Icons.Filled.Link,
                        colorCirculo = MaterialTheme.colorScheme.primary,
                        colorIcono = MaterialTheme.colorScheme.onPrimary,
                        seleccionado = categoriaSeleccionadaId == null && !soloFavoritos,
                        bloqueada = false,
                        onClick = onSeleccionarTodos,
                        onLongClick = null,
                    )
                    FilaMenuLateral.Favoritos -> FilaMenu(
                        nombre = stringResource(R.string.home_chip_favoritos),
                        icono = Icons.Filled.Favorite,
                        colorCirculo = MaterialTheme.colorScheme.secondary,
                        colorIcono = MaterialTheme.colorScheme.onSecondary,
                        seleccionado = soloFavoritos,
                        bloqueada = false,
                        onClick = onAlternarSoloFavoritos,
                        onLongClick = null,
                    )
                    FilaMenuLateral.Separador -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = Espaciado.s),
                    )
                    is FilaMenuLateral.Categoria -> {
                        val item = fila.item
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
                    FilaMenuLateral.NuevaCarpeta -> FilaMenu(
                        nombre = stringResource(R.string.carpeta_add_chip),
                        icono = Icons.Filled.Add,
                        colorCirculo = MaterialTheme.colorScheme.surfaceVariant,
                        colorIcono = MaterialTheme.colorScheme.onSurfaceVariant,
                        seleccionado = false,
                        bloqueada = false,
                        onClick = onCrearCarpeta,
                        onLongClick = null,
                    )
                    FilaMenuLateral.TituloAcercaDe ->
                        TituloSeccionMenu(stringResource(R.string.menu_about))
                    is FilaMenuLateral.Legal -> FilaMenu(
                        nombre = stringResource(tituloLegalRes(fila.tipo)),
                        icono = iconoLegal(fila.tipo),
                        colorCirculo = MaterialTheme.colorScheme.surfaceVariant,
                        colorIcono = MaterialTheme.colorScheme.onSurfaceVariant,
                        seleccionado = false,
                        bloqueada = false,
                        onClick = { onAbrirLegal(fila.tipo) },
                        onLongClick = null,
                    )
                }
            }
        }
    }
}

/** Encabezado del menú: título de la app y total de enlaces. */
@Composable
private fun EncabezadoMenu(totalEnlaces: Int) {
    Column {
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
    }
}

/** El ícono de cada elemento de "Acerca de". */
private fun iconoLegal(tipo: TipoLegal): ImageVector = when (tipo) {
    TipoLegal.PRIVACIDAD -> Icons.Filled.PrivacyTip
    TipoLegal.TERMINOS -> Icons.Filled.Description
    TipoLegal.ELIMINACION -> Icons.Filled.DeleteForever
}

/** Título pequeño de sección dentro del menú, como "Acerca de". */
@Composable
private fun TituloSeccionMenu(texto: String) {
    Text(
        text = texto.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Espaciado.m,
            top = Espaciado.xs,
            bottom = Espaciado.xs,
        ),
    )
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
