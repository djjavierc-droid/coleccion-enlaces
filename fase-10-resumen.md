# Fase 10 — Verificación final y entrega (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI de verificación: **45149024103** — `success` (commit `e8ff88d`,
  el commit del README). Pasos en verde: `testDebugUnitTest` (156 pruebas,
  0 fallos), `assembleDebug` y subida del artefacto `coleccion-debug`.
- El primer intento de ese run falló con 1 prueba (`HomeViewModelTest >
  crearCarpeta hasta el maximo de 20 rechaza la siguiente`,
  `UncompletedCoroutinesError`): una carrera en una prueba de la Fase 9,
  sin relación con esta fase (el commit solo añadía el README, sin tocar
  código). La misma prueba pasó en los dos runs anteriores sobre el mismo
  código (45146741106 y 45147758730). Se reintentó el job fallido con
  `gh run rerun --failed` y quedó en verde sin cambiar nada.
- Único aviso del run (no bloquea, ya anotado en la Fase 9):
  `actions/upload-artifact@v5` todavía apunta a Node 20 y GitHub lo
  ejecuta forzado en Node 24; no hay versión más nueva que lo quite.
- Commits en `main` en esta fase: `e8ff88d` (README) y el commit de este
  resumen. No se agregó ninguna función nueva.

## Archivos creados/modificados

- `README.md` (nuevo, en la raíz del repo): cómo compilar (CI y máquina
  normal), librerías con versiones, clasificación (puntaje, umbral,
  archivos de palabras clave y cómo agregar palabras), las 17 categorías
  predefinidas, carpetas propias (máximo 20), huella por carpeta
  (desbloqueo solo en sesión), interruptor claro/oscuro, estructura del
  proyecto, pruebas e ideas futuras. Sobre el archivo de palabras
  sensibles explica solo que vive fuera del repo y se genera en CI desde
  el secreto `XXX_KEYWORDS_B64`, sin reproducir su contenido.
- `fase-10-resumen.md` (este archivo): informe final completo.

## Verificación del APK de depuración (run 45149024103, artefacto coleccion-debug)

Descargado el artefacto y verificado en local con `aapt2` y `apksigner`
(solo lectura; no se compiló nada en local):

- **Tamaño:** 21.506.115 bytes.
- **SHA-256:** `548f1ed0ec753c8b63c940022fd3cc80eb2e9c9bfc09d2522c799bdb74d76329`.
- **Paquete:** `com.coleccionenlaces.app`, `versionCode 1`,
  `versionName 1.0.0`, etiqueta `Colección`.
- **minSdkVersion 26**, **targetSdkVersion 36**, compileSdk 36.
- **Permisos:** `INTERNET`, `USE_BIOMETRIC`, `USE_FINGERPRINT` (los dos
  últimos los aporta la librería Biometric de la huella por carpeta).
- **Fuentes empaquetadas:** ninguna — el APK no contiene archivos
  `.ttf` ni `.otf` (solo fuentes del sistema).
- **Firma (debug):** `apksigner verify` → `Verifies`; esquema v2: true;
  1 firmante. Es el APK de depuración firmado con la clave de depuración,
  como corresponde.

**La instalación en un dispositivo real NO se probó.** Eso queda para el
teléfono de Javier: nadie ha instalado este APK en un teléfono.

## Lista de verificación final — sección 7 del plan

Cada punto con su evidencia. «Cumple» significa: implementado, con
pruebas unitarias en verde en CI y conectado al estado real; lo que no se
pudo ver en un dispositivo se marca aparte en «Lo NO verificado».

| Función (sec. 7) | Estado | Evidencia |
| --- | --- | --- |
| Agregar enlace (pegado manual) | Cumple | `ui/home/DialogoAgregarEnlace.kt` (campo, pegar del portapapeles, validación en tiempo real, carga); `HomeViewModel.agregarEnlace` (línea 244); pruebas `HomeViewModelTest` (guardar con éxito cierra el diálogo; URL inválida lo mantiene abierto con error) |
| Agregar enlace (desde compartir) | Cumple en código; no probado en dispositivo | `AndroidManifest.xml` (intent-filter ACTION_SEND, `text/plain`, `singleTop`); `MainActivity.onNewIntent` (línea 76) + `onCreate`; `util/ExtraerUrl.kt` + 14 pruebas `ExtraerUrlTest`; el enlace compartido abre el mismo diálogo prellenado, nunca se guarda en silencio |
| Vista previa automática | Cumple | `data/ServicioVistaPrevia.kt` (OkHttp, 10 s, User-Agent de navegador); `domain/ParseadorVistaPrevia.kt` (og: tags con respaldos); 20 pruebas (`ParseadorVistaPreviaTest` 10 + `ServicioVistaPreviaTest` 10, HTML local, sin internet) |
| Clasificación automática con cambio manual | Cumple | `domain/Clasificador.kt` (2 puntos por palabra clave, 4 por dominio temático, umbral 2, «Otros» si no llega); 29 pruebas `ClasificadorTest`; cambio manual en `ui/home/MenuTarjeta.kt` → `HomeViewModel.cambiarCategoriaManual` (línea 308, `categoriaManual = true`, no se sobrescribe) |
| Cuadrícula agrupada por categoría con filtro por chips | Cumple en código; no vista visualmente | `ui/home/HomeScreen.kt` (`LazyVerticalGrid`, línea 244; secciones agrupadas; chips con color, ícono y contador; `ChipCategoria` propio); `HomeViewModel.secciones` derivadas del estado |
| Búsqueda por texto | Cumple | `HomeViewModel.cambiarBusqueda` (línea 224); filtrado en título, descripción y nombre de categoría; pruebas `HomeViewModelTest` y `EnlaceDaoTest` (búsqueda sin distinguir mayúsculas) |
| Abrir enlace al tocar la tarjeta | Cumple en código; no probado en dispositivo | `ui/home/TarjetaEnlace.kt` (`combinedClickable`, línea 101); `HomeScreen.kt:164` (`startActivity(crearIntentAbrirEnlace(url))`); `util/IntentsEnlace.kt` + 6 pruebas `IntentsEnlaceTest`; `Snackbar` amigable si ninguna app puede abrirlo |
| Favoritos (y chip de solo favoritos) | Cumple | Chip «Favoritos» en `HomeScreen.kt:568` (`soloFavoritos`, `alternarSoloFavoritos` línea 219); ícono de favorito en la tarjeta y fila en el menú (`alternarFavorito`, línea 294); pruebas de repositorio y ViewModel |
| Borrar con confirmación | Cumple en código; no visto visualmente | `ui/home/MenuTarjeta.kt:204` (`AlertDialog` de confirmación, solo al confirmar llama a `borrarEnlace`); `HomeViewModel.borrarEnlace` (línea 301); pruebas de borrado en repositorio y ViewModel |
| Compartir el enlace | Cumple en código; no probado en dispositivo | `util/IntentsEnlace.kt` (`crearIntentCompartirEnlace`, ACTION_SEND con chooser); fila de compartir en `MenuTarjeta.kt`; 6 pruebas `IntentsEnlaceTest`; `Snackbar` amigable si ninguna app puede compartir |
| Contador por categoría | Cumple | `EnlaceDao` (contador por categoría); `HomeViewModel.contadores` (línea 69, combinado en el estado, línea 167); chips y encabezados de sección muestran el contador (`contadores[id] ?: 0`); pruebas de contadores en DAO, repositorio y ViewModel |

## Checklist final de calidad visual — sección 9 del plan

Marcado con honestidad: lo verificado es por código y compilación
(`assembleDebug` en verde) y por pruebas unitarias; **nadie ha visto
ninguna pantalla renderizada** (en CI no hay emulador ni capturas, y en
esta VM está prohibido correr Gradle en local). Nada de lo visual se
marca como cumplido por haber sido visto.

| Punto (sec. 9) | Marca | Evidencia / honestidad |
| --- | --- | --- |
| Material 3 en todas las pantallas, sin Material 2 | Verificado por código | Todos los componentes son `androidx.compose.material3` (`AlertDialog`, `ModalBottomSheet`, `FilterChip`, `Scaffold`, `ExtendedFloatingActionButton`); los únicos imports `androidx.compose.material.*` son íconos, no componentes. No visto visualmente |
| Tema personalizado en modo oscuro y claro, sin colores por defecto | Verificado por código | `ui/theme/Theme.kt` (`darkColorScheme` línea 25, `lightColorScheme` línea 43, acento violeta-índigo + coral, degradado de fondo); interruptor en el encabezado con persistencia (`PreferenciasTema.kt`, oscuro por defecto). No visto visualmente |
| Tarjetas con esquinas redondeadas, imagen con degradado y texto legible | Verificado por código | `TarjetaEnlace.kt:96` (`RoundedCornerShape(20.dp)`), `:219` (`Brush.verticalGradient` inferior sobre la imagen), título y descripción con máx. 2 líneas. No visto visualmente |
| Chips de categoría con colores propios y estado seleccionado claro | Verificado por código | `HomeScreen.kt` (`ChipCategoria` con color e ícono propios por categoría; seleccionado relleno con el color y texto legible sobre él, `colorSobreCategoria`). No visto visualmente |
| Shimmer durante la carga, estado vacío ilustrado y estados de error amigables | Verificado por código | `Shimmer.kt` (degradado que se desplaza, sin spinner), `EstadoVacio.kt` (ilustración solo con Compose), errores amigables en diálogo y `Snackbar`. No visto visualmente |
| Animaciones de entrada, filtro y tarjeta nueva | Verificado por código | `animateItem` en tarjetas, encabezados y shimmer; `AnimatedVisibility` al aparecer cada sección; animación de entrada por tarjeta nueva (`TarjetaEnlace.kt:90`). No visto en movimiento |
| Edge-to-edge y predictive back funcionando | Verificado por código, NO probado con el dedo | `MainActivity.kt:45` (`enableEdgeToEdge()`), `installSplashScreen()` (línea 43), `padding` del `Scaffold` + hueco inferior de 96 dp; `AndroidManifest.xml:11` (`enableOnBackInvokedCallback="true"`). Nadie lo ha probado con el dedo en un teléfono |
| Ninguna pantalla tiene texto con contraste insuficiente | NO verificado | No hay forma de verificar el contraste sin ver las pantallas renderizadas. El código calcula `colorSobreCategoria` (negro sobre colores claros, blanco sobre oscuros) para los chips, pero el resto de textos no se ha comprobado visualmente |

## Ideas futuras (recogidas de las fases 1–9)

- Subir a `compileSdk` 37 + Compose 1.12 / Coil 3.6 / OkHttp 5.x cuando
  Android 17 sea estable y AGP lo soporte (Fase 1).
- Pruebas de migración de Room (con `room-testing`, ya agregado) cuando
  la base suba a la versión 2 (Fase 2).
- Aceptar también `og:image:secure_url` y `twitter:image` como respaldo
  de imagen cuando no hay `og:image` (Fase 3).
- Seguir redirecciones con límite y rechazar Content-Type no HTML antes
  de parsear (Fase 3).
- Ampliar las listas de palabras clave y dominios con los enlaces reales
  que el usuario guarde (Fase 4).
- Contar también las palabras del dominio y de la ruta de la URL (hoy
  solo el host suma puntos por dominio) (Fase 4).
- Ofrecer `reclasificarEnlace` desde la UI cuando el usuario edite el
  título de un enlace no manual (Fase 5).
- Incluir las categorías con cero enlaces como entradas explícitas en el
  mapa de contadores (Fase 5).
- Contador de favoritos en el chip «Favoritos» (Fases 6 y 9).
- Usar `collectAsStateWithLifecycle` en lugar de `collectAsState`
  (Fase 6).
- Al pegar desde el portapapeles, si el texto trae varias URLs, ofrecer
  elegir cuál (hoy se pega solo la primera) (Fase 7).
- En el diálogo de agregar, mostrar una mini vista previa (título) antes
  de guardar cuando la descarga ya terminó (Fase 7).
- Aceptar también enlaces compartidos como `EXTRA_STREAM` o con un
  `mimeType` distinto de `text/plain` (hoy solo texto plano) (Fase 7).
- En el menú de tarjeta, mostrar también el contador de enlaces de cada
  categoría junto a su nombre, y permitir cambiar la categoría sin cerrar
  el menú (Fase 8).
- Pedir huella también para borrar o renombrar una carpeta bloqueada
  (Fase 9).
- Ocultar también los contadores y el total de las carpetas bloqueadas
  (Fase 9).
- Al crear una carpeta, desbloquearla automáticamente en la sesión
  (Fase 9).
- (Ya hecho en la Fase 9, no futuro: migrar `actions/setup-java` de v4
  a v5.)

## Lo NO verificado (explícito)

- **Ninguna pantalla se vio visualmente**: ni la principal, ni el
  diálogo de agregar, ni el menú de tarjeta, ni el diálogo de carpeta,
  ni el interruptor de tema, ni los marcadores de carpeta bloqueada.
- **La biometría no se probó en un dispositivo real**: el
  `BiometricPrompt` no se puede probar en la JVM ni en CI; que el prompt
  se abra y desbloquee de verdad en un teléfono con huella configurada
  está sin probar, igual que el caso sin ningún método de bloqueo y el
  comportamiento en API 26–29.
- **La instalación en un dispositivo real NO se probó**: el APK de
  depuración compila y se verifica con `aapt2`/`apksigner`, pero nadie
  lo ha instalado en un teléfono.
- **Haptics, predictive back y edge-to-edge no se probaron con el
  dedo**: el gesto atrás y la vibración no existen en CI.
- **El contraste de los textos no se comprobó visualmente** (ver el
  último punto del checklist de la sección 9).
- **Compartir un enlace real** desde WhatsApp, Chrome o YouTube a la
  app no se probó en un dispositivo; solo la extracción de URL y el
  intent-filter están verificados por código y pruebas.
- **Abrir un enlace real** tocando una tarjeta no se probó en un
  dispositivo; solo la construcción del intent está probada.

## Notas de la fase

- Verificación real solo en CI: Gradle local está prohibido en esta VM
  (el daemon necesita TCP local) y no se pidió ningún permiso de puerto.
  La verificación del APK se hizo con `aapt2` y `apksigner` de solo
  lectura sobre el artefacto descargado (build-tools 36.0.0 descargados
  de Google, SHA-1 verificado contra el manifiesto del repositorio).
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función nueva en esta fase.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.
- Sin palabras clave sensibles en ningún archivo, reporte, commit ni log.
- El archivo sensible `XxxKeywords.kt` sigue ignorado por git
  (`.gitignore:22`, verificado con `git check-ignore`) y solo existe en
  la copia de trabajo local; en CI se genera desde el secreto
  `XXX_KEYWORDS_B64`.
