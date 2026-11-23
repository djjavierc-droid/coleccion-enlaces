package com.coleccionenlaces.app

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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ViewModel principal, creado con la factoría del contenedor
        // de inyección manual (Fase 5). La UI lo observa en la Fase 6.
        val contenedor = (application as ColeccionApplication).contenedor
        val homeViewModel = ViewModelProvider(
            this,
            contenedor.homeViewModelFactory,
        )[HomeViewModel::class.java]
        setContent {
            ColeccionTheme(darkTheme = true) {
                ColeccionNavHost(homeViewModel)
            }
        }
    }
}

@Composable
private fun ColeccionNavHost(homeViewModel: HomeViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "inicio") {
        composable("inicio") { HomeScreen(homeViewModel) }
    }
}
