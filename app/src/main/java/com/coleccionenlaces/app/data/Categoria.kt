package com.coleccionenlaces.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una categoría (carpeta) de enlaces.
 *
 * Decisión de diseño (Fase 2): las categorías NO son un enum cerrado.
 * Son filas de esta tabla, por lo que la app soporta las 17 predefinidas
 * de la especificación MÁS las carpetas que el usuario cree a mano
 * (Fase 9), hasta el máximo de 20. [Enlace] referencia la categoría
 * por [id] (clave foránea), nunca por nombre.
 *
 * - [color]: color de acento ARGB (Long) de la categoría, usado en
 *   chips, encabezados de sección y etiquetas de tarjeta.
 * - [icono]: clave del ícono (nombre de Material Icon, p. ej.
 *   "restaurant"); la UI de las Fases 6 y 9 lo resuelve al ícono real.
 *   Para la categoría de contenido adulto se usa a propósito un ícono
 *   discreto ("lock"), sin ninguna imagen explícita.
 * - [esPredefinida]: true para las 17 sembradas en la primera creación
 *   de la base; false para las carpetas creadas por el usuario.
 * - [orden]: posición en la lista de chips y secciones.
 */
@Entity(
    tableName = "categorias",
    indices = [
        Index(value = ["nombre"], unique = true),
        Index(value = ["orden"]),
    ],
)
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val color: Long,
    val icono: String,
    val esPredefinida: Boolean = false,
    val orden: Int = 0,
)
