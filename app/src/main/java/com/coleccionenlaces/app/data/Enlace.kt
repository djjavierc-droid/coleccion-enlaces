package com.coleccionenlaces.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un enlace guardado por el usuario, con su vista previa.
 *
 * Campos del plan (Fase 2, sección 8): id, url, titulo, descripcion,
 * urlImagen, categoria, esFavorito, categoriaManual, fechaCreacion.
 * La `categoria` del plan se guarda aquí como [categoriaId]: el id de
 * la fila de [Categoria] (clave foránea). Así la categoría puede
 * cambiar de nombre, color o ícono sin tocar los enlaces, y el usuario
 * puede crear carpetas propias (Fase 9) sin cambiar el modelo.
 *
 * - [urlImagen]: null cuando el sitio no ofreció imagen de vista previa.
 * - [categoriaId]: null solo si la carpeta del enlace se borró (la
 *   clave foránea usa `SET_NULL`); en ese caso el repositorio (Fase 5)
 *   lo tratará como "Otros". Las 17 predefinidas no se borran.
 * - [categoriaManual]: true cuando el usuario cambió la categoría a
 *   mano desde el menú de la tarjeta. La clasificación automática
 *   (Fase 4) NO debe sobrescribir la categoría de estos enlaces.
 *   En esta fase el flag solo se almacena.
 * - [fechaCreacion]: milisegundos desde epoch (UTC); la lista se
 *   ordena por este campo, del más reciente al más antiguo.
 */
@Entity(
    tableName = "enlaces",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["categoriaId"]),
        Index(value = ["esFavorito"]),
        Index(value = ["fechaCreacion"]),
    ],
)
data class Enlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val titulo: String,
    val descripcion: String = "",
    val urlImagen: String? = null,
    val categoriaId: Long? = null,
    val esFavorito: Boolean = false,
    val categoriaManual: Boolean = false,
    val fechaCreacion: Long = System.currentTimeMillis(),
)
