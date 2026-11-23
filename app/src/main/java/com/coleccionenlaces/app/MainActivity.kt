package com.coleccionenlaces.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coleccionenlaces.app.ui.home.HomeScreen
import com.coleccionenlaces.app.ui.home.HomeViewModel
import com.coleccionenlaces.app.ui.theme.ColeccionTheme
import com.coleccionenlaces.app.util.extraerUrl

class MainActivity : ComponentActivity() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ViewModel principal, creado con la factoría del contenedor
        // de inyección manual (Fase 5). La UI lo observa en la Fase 6.
        val contenedor = (application as ColeccionApplication).contenedor
        homeViewModel = ViewModelProvider(
            this,
            contenedor.homeViewModelFactory,
        )[HomeViewModel::class.java]
        setContent {
            ColeccionTheme(darkTheme = true) {
                ColeccionNavHost(homeViewModel)
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
     */
    private fun manejarIntentCompartir(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val texto = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = extraerUrl(texto) ?: return
        homeViewModel.abrirDialogoAgregar(url)
    }
}

@Composable
private fun ColeccionNavHost(homeViewModel: HomeViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "inicio") {
        composable("inicio") { HomeScreen(homeViewModel) }
    }
}
