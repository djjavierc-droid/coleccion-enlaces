package com.coleccionenlaces.app.di

import android.content.Context
import com.coleccionenlaces.app.data.AppDatabase
import com.coleccionenlaces.app.data.RepositorioEnlaces
import com.coleccionenlaces.app.data.ServicioAprendizaje
import com.coleccionenlaces.app.data.ServicioOEmbed
import com.coleccionenlaces.app.data.ServicioVistaPrevia
import com.coleccionenlaces.app.domain.BloqueoCarpetas
import com.coleccionenlaces.app.domain.CabezaAprendizaje
import com.coleccionenlaces.app.domain.ClasificadorInteligente
import com.coleccionenlaces.app.domain.ClasificadorModelo
import com.coleccionenlaces.app.domain.ObservadorBloqueoApp
import com.coleccionenlaces.app.ui.home.HomeViewModelFactory
import com.coleccionenlaces.app.work.ReentrenamientoWorker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
            motorClasificacion = motorClasificacion,
            servicioAprendizaje = servicioAprendizaje,
        )
    }

    /** Etiquetas del modelo (las 16, en el orden de `etiquetas.txt`). */
    val etiquetasClasificador: List<String> by lazy {
        val desdeAssets = ClasificadorModelo.cargarEtiquetas(contextoApp)
        if (desdeAssets.size == ClasificadorModelo.NUM_CLASES) {
            desdeAssets
        } else {
            // Sin assets (no debería pasar): las 17 predefinidas menos "Otros".
            com.coleccionenlaces.app.data.CategoriasPredefinidas.lista
                .map { it.nombre }
                .filter { it != com.coleccionenlaces.app.domain.Clasificador.OTROS }
        }
    }

    /** Modelo DistilBERT INT8 (perezoso: carga al primer uso). */
    val clasificadorModelo: ClasificadorModelo by lazy {
        ClasificadorModelo(contextoApp)
    }

    /** Cabeza de aprendizaje (una sola instancia para motor y servicio). */
    val cabezaAprendizaje: CabezaAprendizaje by lazy {
        CabezaAprendizaje(etiquetasClasificador)
    }

    /** Motor de clasificación de producción (Fix 3). */
    val motorClasificacion: ClasificadorInteligente by lazy {
        ClasificadorInteligente(
            modelo = clasificadorModelo,
            cabeza = cabezaAprendizaje,
            etiquetas = etiquetasClasificador,
        )
    }

    /** Servicio de aprendizaje (correcciones + reentrenamiento). */
    val servicioAprendizaje: ServicioAprendizaje by lazy {
        ServicioAprendizaje(baseDatos.aprendizajeDao(), cabezaAprendizaje)
    }

    private val alcanceFondo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Arranca el aprendizaje (Fix 3): carga los pesos guardados en la
     * cabeza y programa el reentrenamiento diario en reposo. Se llama
     * una vez desde `ColeccionApplication.onCreate()`; no bloquea.
     */
    fun iniciarAprendizaje() {
        alcanceFondo.launch {
            servicioAprendizaje.cargarPesos()
        }
        ReentrenamientoWorker.programar(contextoApp)
    }

    /** Factoría del ViewModel principal. */
    val homeViewModelFactory: HomeViewModelFactory by lazy {
        HomeViewModelFactory(repositorio, bloqueoCarpetas)
    }

    /**
     * Bloqueo por carpeta (Fase 9): UNA sola instancia para toda la
     * app. La comparten el ViewModel (la UI desbloquea con huella) y
     * el observador de ciclo de vida ([ObservadorBloqueoApp], que
     * bloquea todo al pasar a segundo plano, Fix 4 requisito 4).
     * Antes el ViewModel creaba la suya propia y nadie más podía
     * bloquear desde fuera.
     */
    val bloqueoCarpetas: BloqueoCarpetas by lazy {
        BloqueoCarpetas()
    }

    /** Observador de bloqueo automático (Fix 4, requisito 4). */
    val observadorBloqueoApp: ObservadorBloqueoApp by lazy {
        ObservadorBloqueoApp(bloqueoCarpetas)
    }
}
