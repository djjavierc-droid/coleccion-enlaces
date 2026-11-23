package com.coleccionenlaces.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos Room de la app (versión 2).
 *
 * Tablas: `enlaces` y `categorias`. En la primera creación se siembran
 * las 17 categorías predefinidas ([CategoriasPredefinidas.lista]); las
 * carpetas que el usuario cree a mano (Fase 9) se añaden como filas
 * nuevas de `categorias` con `esPredefinida = false`.
 *
 * Migración 1 → 2 (corrección 1.0.1, punto 3): la tabla `enlaces`
 * gana `htmlVideo` (HTML del reproductor oEmbed) e `imagenLocal`
 * (ruta de la miniatura guardada en local). Las dos son nullables,
 * así que los enlaces ya guardados sobreviven tal cual.
 *
 * Singleton de la app: [obtenerInstancia]. Las pruebas usan
 * [crearEnMemoria], que construye una base en memoria (también
 * sembrada) y permite consultas en el hilo principal del test.
 */
@Database(
    entities = [Enlace::class, Categoria::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun enlaceDao(): EnlaceDao

    abstract fun categoriaDao(): CategoriaDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        /**
         * Migración 1 → 2: añade las columnas del vídeo oEmbed y la
         * miniatura local a `enlaces`, sin tocar ningún dato.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE enlaces ADD COLUMN htmlVideo TEXT")
                db.execSQL("ALTER TABLE enlaces ADD COLUMN imagenLocal TEXT")
            }
        }

        fun obtenerInstancia(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coleccion-enlaces.db",
                )
                    .addCallback(CallbackSiembra)
                    .addMigrations(MIGRACION_1_2)
                    .build()
                    .also { instancia = it }
            }

        /** Base en memoria para pruebas unitarias del DAO (también sembrada). */
        fun crearEnMemoria(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
            )
                .addCallback(CallbackSiembra)
                .allowMainThreadQueries()
                .build()

        /**
         * Siembra las 17 categorías predefinidas en la primera creación
         * de la base. Se hace con SQL directo (sin DAO) porque el
         * callback corre antes de que la base esté lista para usarse.
         */
        private object CallbackSiembra : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CategoriasPredefinidas.lista.forEachIndexed { indice, semilla ->
                    db.execSQL(
                        "INSERT INTO categorias " +
                            "(nombre, color, icono, esPredefinida, orden) " +
                            "VALUES (?, ?, ?, 1, ?)",
                        arrayOf<Any?>(semilla.nombre, semilla.color, semilla.icono, indice),
                    )
                }
            }
        }
    }
}
