package com.coleccionenlaces.app.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.coleccionenlaces.app.data.Categoria

/**
 * Resuelve la clave de ícono guardada en [Categoria.icono] al
 * Material Icon real (Fase 6, sección 3 del plan: cada categoría
 * tiene su propio color y su propio ícono, usados en chips,
 * encabezados de sección y etiquetas de tarjeta).
 *
 * Las claves son las de [CategoriasPredefinidas][com.coleccionenlaces.app.data.CategoriasPredefinidas];
 * una clave desconocida (p. ej. de una carpeta creada a mano en la
 * Fase 9 con una clave no prevista) cae en el ícono genérico, nunca
 * rompe la pantalla.
 */
fun iconoCategoria(clave: String): ImageVector = when (clave) {
    "restaurant" -> Icons.Filled.Restaurant
    "car_repair" -> Icons.Filled.CarRepair
    "construction" -> Icons.Filled.Construction
    "lock" -> Icons.Filled.Lock
    "movie" -> Icons.Filled.Movie
    "smartphone" -> Icons.Filled.Smartphone
    "directions_car" -> Icons.Filled.DirectionsCar
    "fitness_center" -> Icons.Filled.FitnessCenter
    "attach_money" -> Icons.Filled.AttachMoney
    "newspaper" -> Icons.Filled.Newspaper
    "sports_esports" -> Icons.Filled.SportsEsports
    "handyman" -> Icons.Filled.Handyman
    "local_florist" -> Icons.Filled.LocalFlorist
    "forest" -> Icons.Filled.Forest
    "checkroom" -> Icons.Filled.Checkroom
    "flight" -> Icons.Filled.Flight
    "category" -> Icons.Filled.Category
    else -> Icons.Filled.Category
}

/** Color ARGB guardado en [Categoria.color] como [Color] de Compose. */
fun colorCategoria(categoria: Categoria): Color = Color(categoria.color)

/**
 * Color de texto/ícono legible sobre el color de una categoría:
 * negro sobre colores claros (p. ej. el ámbar de Construcción) y
 * blanco sobre colores oscuros. Así ningún chip seleccionado ni
 * etiqueta queda con contraste insuficiente.
 */
fun colorSobreCategoria(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White
