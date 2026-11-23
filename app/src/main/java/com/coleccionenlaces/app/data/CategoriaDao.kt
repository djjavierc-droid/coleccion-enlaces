package com.coleccionenlaces.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(categoria: Categoria): Long

    @Update
    suspend fun actualizar(categoria: Categoria)

    @Delete
    suspend fun borrar(categoria: Categoria)

    @Query("SELECT * FROM categorias WHERE id = :id")
    suspend fun obtenerPorId(id: Long): Categoria?

    @Query("SELECT * FROM categorias WHERE nombre = :nombre LIMIT 1")
    suspend fun obtenerPorNombre(nombre: String): Categoria?

    @Query("SELECT * FROM categorias WHERE nombre = :nombre COLLATE NOCASE LIMIT 1")
    suspend fun obtenerPorNombreSinDistinguirMayusculas(nombre: String): Categoria?

    /** El mayor valor de `orden` guardado, o null si no hay categorías. */
    @Query("SELECT MAX(orden) FROM categorias")
    suspend fun ordenMaximo(): Int?

    /** Todas las categorías, en el orden de los chips y secciones. */
    @Query("SELECT * FROM categorias ORDER BY orden ASC, id ASC")
    fun obtenerTodas(): Flow<List<Categoria>>

    /** Solo las 17 predefinidas sembradas en la primera creación. */
    @Query("SELECT * FROM categorias WHERE esPredefinida = 1 ORDER BY orden ASC, id ASC")
    fun obtenerPredefinidas(): Flow<List<Categoria>>

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contar(): Int
}
