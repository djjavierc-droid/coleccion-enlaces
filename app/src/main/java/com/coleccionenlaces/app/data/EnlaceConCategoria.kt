package com.coleccionenlaces.app.data

/**
 * Un enlace guardado junto con su categoría ya resuelta, lista para
 * mostrar en la UI.
 *
 * ## Decisión de diseño (Fase 5): la categoría nunca es null aquí
 * [Enlace.categoriaId] puede ser null (la carpeta del enlace se borró
 * y la clave foránea usó `SET_NULL`), pero la UI de la Fase 6 muestra
 * secciones por categoría y necesita siempre un nombre, un color y un
 * ícono. Por eso el repositorio resuelve cada enlace a su [Categoria]
 * y, cuando no hay categoría guardada o la fila ya no existe, usa la
 * categoría "Otros" (la fila real sembrada; solo si ni siquiera esa
 * existiera, una sintética con sus mismos datos). La UI nunca tiene
 * que tratar el caso null: todo enlace tiene categoría visible.
 */
data class EnlaceConCategoria(
    val enlace: Enlace,
    val categoria: Categoria,
)
