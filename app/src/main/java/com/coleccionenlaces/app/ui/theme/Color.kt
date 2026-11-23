package com.coleccionenlaces.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta propia. Acento principal vibrante: violeta-índigo.
// Modo oscuro predeterminado; modo claro disponible (Fase 9: interruptor).

// Oscuro
val VioletaPrimarioOscuro = Color(0xFF9D8CFF)
val VioletaContenedorOscuro = Color(0xFF3B2A86)
val CoralSecundarioOscuro = Color(0xFFFF8FA3)
val CianTerciarioOscuro = Color(0xFF4DD0E1)
val FondoOscuro = Color(0xFF0E0F1A)
val SuperficieOscura = Color(0xFF171829)
val SuperficieVarianteOscura = Color(0xFF262840)
val TextoOscuro = Color(0xFFF2F0FF)
val TextoSecundarioOscuro = Color(0xFFB9B6D6)
val BordeOscuro = Color(0xFF6E7191)

// Claro
val VioletaPrimarioClaro = Color(0xFF5B3DF5)
val VioletaContenedorClaro = Color(0xFFDCD2FF)
val CoralSecundarioClaro = Color(0xFFD6336C)
val CianTerciarioClaro = Color(0xFF0E7490)
val FondoClaro = Color(0xFFF6F5FF)
val SuperficieClara = Color(0xFFFFFFFF)
val SuperficieVarianteClara = Color(0xFFE9E7F8)
val TextoClaro = Color(0xFF191A2E)
val TextoSecundarioClaro = Color(0xFF55576E)
val BordeClaro = Color(0xFFC9C6E8)

// Tono superior del degradado en modo claro (corrección 1.0.1,
// punto 1): lavanda muy claro y OPACO. El degradado anterior usaba
// el primario con alfa 0.14, que se compone sobre el fondo de la
// ventana (oscuro) y en el teléfono se medía #5C596E detrás del
// título: el subtítulo quedaba en 1.05:1 y el título en 2.53:1.
// Con este tono opaco el título primario queda en 5.02:1 y el
// subtítulo en 5.79:1 sobre la zona superior. El modo oscuro no
// cambia: sigue con el primario translúcido.
val DegradadoSuperiorClaro = Color(0xFFE9E6FF)
