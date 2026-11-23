package com.coleccionenlaces.app.di

import android.content.Context
import com.coleccionenlaces.app.data.AppDatabase
import com.coleccionenlaces.app.data.RepositorioEnlaces
import com.coleccionenlaces.app.data.ServicioOEmbed
import com.coleccionenlaces.app.data.ServicioVistaPrevia
import com.coleccionenlaces.app.ui.home.HomeViewModelFactory
import java.io.File

/**
 * Inyección de dependencias MANUAL (Fase 5, sección 5 del plan).
 *
 * Un objeto contenedor simple, sin Hilt ni Koin: provee la base de
 * datos, los DAOs (a través del repositorio), el servicio de vista
 * previa, el repositorio y la factoría del ViewModel principal. Cada
 * dependencia se crea una sola vez, bajo demanda (`lazy`).
 *
 * Lo posee [com.coleccionenlaces.app.ColeccionApplication] y lo usa
 * `MainActivity` para crear el [com.coleccionenlaces.app.ui.home.HomeViewModel].
 */
class AppContainer(context: Context) {

    private val contextoApp: Context = context.applicationContext

    /** Base Room (singleton): crea y siembra las 17 categorías la primera vez. */
    val baseDatos: AppDatabase by lazy {
        AppDatabase.obtenerInstancia(contextoApp)
    }

    /** Servicio de vista previa (OkHttp + Jsoup). */
    val servicioVistaPrevia: ServicioVistaPrevia by lazy {
        ServicioVistaPrevia()
    }

    /** Servicio oEmbed sin token (YouTube, TikTok y Facebook). */
    val servicioOEmbed: ServicioOEmbed by lazy {
        ServicioOEmbed()
    }

    /** Única fuente de datos de la app. */
    val repositorio: RepositorioEnlaces by lazy {
        RepositorioEnlaces(
            enlaceDao = baseDatos.enlaceDao(),
            categoriaDao = baseDatos.categoriaDao(),
            servicioVistaPrevia = servicioVistaPrevia,
            servicioOEmbed = servicioOEmbed,
            // Miniaturas locales en el almacenamiento interno de la
            // app (corrección 1.0.1, punto 3): nada sale del teléfono.
            directorioMiniaturas = File(contextoApp.filesDir, "miniaturas"),
        )
    }

    /** Factoría del ViewModel principal. */
    val homeViewModelFactory: HomeViewModelFactory by lazy {
        HomeViewModelFactory(repositorio)
    }
}
