package com.coleccionenlaces.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba de la migración 1 → 2 (corrección 1.0.1, punto 3).
 *
 * Se construye una base con el esquema de la versión 1 (sin
 * `htmlVideo` ni `imagenLocal`), con un enlace guardado, y se corre
 * [AppDatabase.MIGRACION_1_2] encima: las columnas nuevas deben
 * aparecer y el enlace debe sobrevivir tal cual.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migracion12Test {

    @Test
    fun `migracion 1 a 2 anade htmlVideo e imagenLocal y conserva el enlace`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuracion = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `categorias` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`nombre` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                            "`icono` TEXT NOT NULL, `esPredefinida` INTEGER NOT NULL, " +
                            "`orden` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `enlaces` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`url` TEXT NOT NULL, `titulo` TEXT NOT NULL, " +
                            "`descripcion` TEXT NOT NULL, `urlImagen` TEXT, " +
                            "`categoriaId` INTEGER, `esFavorito` INTEGER NOT NULL, " +
                            "`categoriaManual` INTEGER NOT NULL, " +
                            "`fechaCreacion` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "INSERT INTO categorias (nombre, color, icono, esPredefinida, orden) " +
                            "VALUES ('Cocina', 123, 'restaurant', 1, 0)",
                    )
                    db.execSQL(
                        "INSERT INTO enlaces (url, titulo, descripcion, urlImagen, " +
                            "categoriaId, esFavorito, categoriaManual, fechaCreacion) " +
                            "VALUES ('https://ejemplo.com/receta', 'Receta', " +
                            "'La receta tradicional.', NULL, 1, 0, 0, 123456789)",
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuracion)
        val db = helper.writableDatabase

        assertFalse(columnas(db).contains("htmlVideo"))
        assertFalse(columnas(db).contains("imagenLocal"))

        AppDatabase.MIGRACION_1_2.migrate(db)

        val columnas = columnas(db)
        assertTrue(columnas.contains("htmlVideo"))
        assertTrue(columnas.contains("imagenLocal"))
        db.query(
            "SELECT titulo, descripcion, htmlVideo, imagenLocal " +
                "FROM enlaces WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Receta", cursor.getString(0))
            assertEquals("La receta tradicional.", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
        db.close()
    }

    /** Nombres de columna de la tabla `enlaces` (PRAGMA table_info). */
    private fun columnas(db: SupportSQLiteDatabase): Set<String> {
        val nombres = mutableSetOf<String>()
        db.query("PRAGMA table_info(`enlaces`)").use { cursor ->
            while (cursor.moveToNext()) {
                nombres.add(cursor.getString(1))
            }
        }
        return nombres
    }
}
