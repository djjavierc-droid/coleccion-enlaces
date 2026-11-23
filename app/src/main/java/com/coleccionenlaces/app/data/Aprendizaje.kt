package com.coleccionenlaces.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Una corrección de Javier para el aprendizaje (Fix 3): movió un
 * enlace a su carpeta correcta. El texto es el título + descripción
 * del enlace en ese momento y la categoría es la que él eligió.
 *
 * Estos ejemplos son el conjunto de entrenamiento de la cabeza
 * ([com.coleccionenlaces.app.domain.CabezaAprendizaje]): el paso en
 * línea se hace al registrar la corrección y el reentrenamiento por
 * lotes pasa sobre todos ellos. Se guardan todos (sin borrar): la
 * app es definitiva y el aprendizaje vive en el teléfono.
 */
@Entity(tableName = "correcciones_aprendizaje")
data class CorreccionAprendizaje(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val texto: String,
    val categoria: String,
    val creadoEn: Long = System.currentTimeMillis(),
)

/**
 * Los pesos de la cabeza de aprendizaje en una sola fila (id = 1):
 * el BLOB es la serialización de
 * [com.coleccionenlaces.app.domain.CabezaAprendizaje.aBytes].
 * Si no hay fila, la cabeza empieza en cero (sin aprender nada).
 */
@Entity(tableName = "pesos_cabeza")
data class PesosCabeza(
    @PrimaryKey val id: Int = 1,
    val pesos: ByteArray,
    val actualizadoEn: Long = System.currentTimeMillis(),
)

@Dao
interface AprendizajeDao {

    @Insert
    suspend fun insertarCorreccion(correccion: CorreccionAprendizaje): Long

    /** Todas las correcciones, de la más antigua a la más reciente. */
    @Query("SELECT * FROM correcciones_aprendizaje ORDER BY creadoEn ASC, id ASC")
    suspend fun obtenerCorrecciones(): List<CorreccionAprendizaje>

    /** Número de correcciones guardadas. */
    @Query("SELECT COUNT(*) FROM correcciones_aprendizaje")
    suspend fun contarCorrecciones(): Int

    /** Guarda los pesos (una sola fila; REPLACE). */
    @Insert
    suspend fun guardarPesos(pesos: PesosCabeza)

    /** Los pesos guardados, o null si aún no hay. */
    @Query("SELECT * FROM pesos_cabeza WHERE id = 1")
    suspend fun obtenerPesos(): PesosCabeza?
}
