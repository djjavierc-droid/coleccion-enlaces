package com.coleccionenlaces.app.data

/**
 * Las 17 categorías predefinidas de la especificación, en orden.
 *
 * Única fuente de los datos de siembra: [AppDatabase] inserta esta
 * lista en la primera creación de la base de datos. El color es el
 * acento ARGB de cada categoría y el ícono es la clave de Material
 * Icon que la UI resuelve (ver [Categoria]).
 *
 * Quedan 3 huecos libres hasta el máximo de 20 categorías: el usuario
 * puede crear sus propias carpetas (Fase 9) y estas se guardan como
 * filas nuevas de la tabla `categorias` con `esPredefinida = false`.
 */
data class CategoriaSemilla(
    val nombre: String,
    val color: Long,
    val icono: String,
)

object CategoriasPredefinidas {

    const val MAX_CATEGORIAS = 20

    val lista: List<CategoriaSemilla> = listOf(
        CategoriaSemilla("Cocina", 0xFFFF7043, "restaurant"),
        CategoriaSemilla("Videos de mecánica", 0xFF78909C, "car_repair"),
        CategoriaSemilla("Videos de construcción", 0xFFFFB300, "construction"),
        CategoriaSemilla("Contenido XXX", 0xFFEC407A, "lock"),
        CategoriaSemilla("Entretenimiento", 0xFFAB47BC, "movie"),
        CategoriaSemilla("Tecnología y celulares", 0xFF29B6F6, "smartphone"),
        CategoriaSemilla("Autos y motos", 0xFFEF5350, "directions_car"),
        CategoriaSemilla("Salud y ejercicio", 0xFF66BB6A, "fitness_center"),
        CategoriaSemilla("Dinero y finanzas", 0xFF7CB342, "attach_money"),
        CategoriaSemilla("Noticias", 0xFF5C6BC0, "newspaper"),
        CategoriaSemilla("Juegos", 0xFF26A69A, "sports_esports"),
        CategoriaSemilla("Hazlo tú mismo (DIY)", 0xFF8D6E63, "handyman"),
        CategoriaSemilla("Jardín y plantas", 0xFF558B2F, "local_florist"),
        CategoriaSemilla("Pesca y campo", 0xFF039BE5, "forest"),
        CategoriaSemilla("Moda y ropa", 0xFF8E24AA, "checkroom"),
        CategoriaSemilla("Viajes", 0xFF00ACC1, "flight"),
        CategoriaSemilla("Otros", 0xFF90A4AE, "category"),
    )
}
