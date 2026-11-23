package com.coleccionenlaces.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coleccionenlaces.app.ui.home.HomeScreen
import com.coleccionenlaces.app.ui.home.HomeViewModel
import com.coleccionenlaces.app.ui.legal.PantallaLegal
import com.coleccionenlaces.app.ui.legal.TipoLegal
import com.coleccionenlaces.app.ui.theme.ColeccionTheme
import com.coleccionenlaces.app.ui.theme.PreferenciasTema
import com.coleccionenlaces.app.util.extraerTextoAcompanante
import com.coleccionenlaces.app.util.extraerUrl

/**
 * Actividad principal.
 *
 * Extiende [FragmentActivity] (y no solo ComponentActivity) porque
 * el prompt de huella por carpeta (Fase 9, [BiometricPrompt]) exige
 * una FragmentActivity o un Fragment para mostrarse.
 */
class MainActivity : FragmentActivity() {

    private lateinit var homeViewModel: HomeViewModel

    /**
     * Tema actual (Fase 9): se lee de SharedPreferences al arrancar
     * (oscuro por defecto) y el interruptor del encabezado lo cambia
     * y lo guarda. Es estado de Compose para que el tema se aplique
     * en cuanto cambia, sin recrear la actividad.
     */
    private var temaOscuro by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        temaOscuro = PreferenciasTema.leerTemaOscuro(this)
        // ViewModel principal, creado con la factoría del contenedor
        // de inyección manual (Fase 5). La UI lo observa en la Fase 6.
        val contenedor = (application as ColeccionApplication).contenedor
        homeViewModel = ViewModelProvider(
            this,
            contenedor.homeViewModelFactory,
        )[HomeViewModel::class.java]
        setContent {
            ColeccionTheme(darkTheme = temaOscuro) {
                ColeccionNavHost(
                    homeViewModel = homeViewModel,
                    temaOscuro = temaOscuro,
                    onAlternarTema = {
                        temaOscuro = !temaOscuro
                        PreferenciasTema.guardarTemaOscuro(this, temaOscuro)
                    },
                )
            }
        }
        // Arranque en frío con un enlace compartido (Fase 7).
        manejarIntentCompartir(intent)
    }

    /**
     * La actividad es `singleTop` (ver el manifiesto): con la app ya
     * abierta, un enlace compartido llega aquí en vez de crear otra
     * actividad. Se actualiza el intent vigente y se maneja igual
     * que en el arranque en frío.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        manejarIntentCompartir(intent)
    }

    /**
     * Enlace recibido desde el menú de compartir de Android (Fase 7,
     * ACTION_SEND con text/plain).
     *
     * Decisión: el enlace NO se agrega en silencio. Se extrae la
     * primera URL del texto compartido ([extraerUrl]) y se abre el
     * MISMO diálogo de agregar prellenado con ella, para que el
     * usuario la vea y la confirme con Guardar. Si el texto no trae
     * ninguna URL, no se hace nada.
     *
     * Corrección 1.0.1: el texto que acompaña a la URL (p. ej. el
     * texto de la publicación de Facebook) se extrae con
     * [extraerTextoAcompanante] y viaja al diálogo como fuente
     * automática de título/descripción: nadie escribe títulos.
     */
    private fun manejarIntentCompartir(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = extraerUrl(texto) ?: return
        homeViewModel.abrirDialogoAgregar(url, extraerTextoAcompanante(texto, url))
    }
}

@Composable
private fun ColeccionNavHost(
    homeViewModel: HomeViewModel,
    temaOscuro: Boolean,
    onAlternarTema: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "inicio") {
        composable("inicio") {
            HomeScreen(
                viewModel = homeViewModel,
                temaOscuro = temaOscuro,
                onAlternarTema = onAlternarTema,
                onAbrirLegal = { tipo ->
                    navController.navigate("legal/${tipo.ruta}")
                },
            )
        }
        // Textos legales de "Acerca de" (corrección 1.0.1, punto 2):
        // cada uno abre su pantalla dentro de la aplicación con el
        // texto completo; la flecha vuelve a la principal.
        composable("legal/{tipo}") { entrada ->
            val tipo = TipoLegal.deRuta(entrada.arguments?.getString("tipo"))
            if (tipo != null) {
                PantallaLegal(
                    tipo = tipo,
                    onVolver = { navController.popBackStack() },
                )
            }
        }
    }
}
