package com.coleccionenlaces.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Número de enlaces guardados en una categoría (para los contadores de los chips). */
data class ConteoPorCategoria(
    val categoriaId: Long?,
    val total: Int,
)

@Dao
interface EnlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(enlace: Enlace): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(enlaces: List<Enlace>): List<Long>

    @Update
    suspend fun actualizar(enlace: Enlace)

    @Delete
    suspend fun borrar(enlace: Enlace)

    @Query("DELETE FROM enlaces WHERE id = :id")
    suspend fun borrarPorId(id: Long)

    @Query("SELECT * FROM enlaces WHERE id = :id")
    suspend fun obtenerPorId(id: Long): Enlace?

    /** Todos los enlaces, del más reciente al más antiguo. */
    @Query("SELECT * FROM enlaces ORDER BY fechaCreacion DESC, id DESC")
    fun obtenerTodos(): Flow<List<Enlace>>

    /** Enlaces de una categoría (carpeta), del más reciente al más antiguo. */
    @Query(
        "SELECT * FROM enlaces WHERE categoriaId = :categoriaId " +
            "ORDER BY fechaCreacion DESC, id DESC",
    )
    fun obtenerPorCategoria(categoriaId: Long): Flow<List<Enlace>>

    /** Solo los enlaces marcados como favoritos. */
    @Query(
        "SELECT * FROM enlaces WHERE esFavorito = 1 " +
            "ORDER BY fechaCreacion DESC, id DESC",
    )
    fun obtenerFavoritos(): Flow<List<Enlace>>

    /**
     * Búsqueda por texto en título, descripción y nombre de categoría.
     * La búsqueda no distingue mayúsculas de minúsculas (LIKE de SQLite
     * es insensible a mayúsculas en ASCII). Con texto vacío devuelve todo.
     */
    @Query(
        "SELECT e.* FROM enlaces AS e " +
            "LEFT JOIN categorias AS c ON e.categoriaId = c.id " +
            "WHERE e.titulo LIKE '%' || :texto || '%' " +
            "OR e.descripcion LIKE '%' || :texto || '%' " +
            "OR c.nombre LIKE '%' || :texto || '%' " +
            "ORDER BY e.fechaCreacion DESC, e.id DESC",
    )
    fun buscar(texto: String): Flow<List<Enlace>>

    /** Contador de enlaces por categoría (para los chips de la pantalla principal). */
    @Query("SELECT categoriaId, COUNT(*) AS total FROM enlaces GROUP BY categoriaId")
    fun contarPorCategoria(): Flow<List<ConteoPorCategoria>>
}
