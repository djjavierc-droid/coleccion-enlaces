# Fase 9 — Pulido visual + huella por carpeta + carpetas propias (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45146741106** — `success` (5m42s, commit `42bfaf4`).
  - `testDebugUnitTest`: BUILD SUCCESSFUL (156 pruebas, 0 fallos).
  - `assembleDebug`: BUILD SUCCESSFUL.
- Artefacto: **coleccion-debug** (APK de depuración, 20.632.527 bytes),
  en el run 45146741106 del repo `djjavierc-droid/coleccion-enlaces`.
  El APK creció levemente respecto a la Fase 8 (20.545.894 bytes) por
  la dependencia `fragment-ktx` (necesaria para BiometricPrompt).
- Commits en `main`: 9cc3713 (bloqueo + carpetas: repositorio,
  ViewModel y pruebas), 778500b (huella, diálogo de carpeta, tema,
  haptics y predictive back), 42bfaf4 (corrección de una prueba).
- El primer run de la fase (45146119010) falló con 1 prueba: la
  prueba de reclasificar esperaba `!cargando`, que devuelve el primer
  estado sin carga (anterior al desbloqueo). Corregido esperando el
  estado ya asentado (enlace visible en Entretenimiento); el
  siguiente run quedó en verde.

## ADVERTENCIAS EXPLÍCITAS — lo que NO se vio ni se probó en un dispositivo real

- **La pantalla NO se vio visualmente.** Todo quedó verificado por
  compilación (`assembleDebug` en verde) y por pruebas unitarias de
  la lógica y del estado. En CI no hay emulador ni capturas de
  pantalla, y en esta VM está prohibido correr Gradle en local.
  Nadie ha visto renderizados el marcador de carpeta bloqueada, el
  diálogo de crear carpeta, el interruptor de tema claro ni los
  chips con candado. La revisión visual es de la Fase 10 y del
  teléfono de Javier.
- **La biometría NO se probó en un dispositivo real.** El
  `BiometricPrompt` no se puede probar en la JVM ni en CI: no hay
  huella, rostro ni credencial de dispositivo en el runner. Solo
  está probada la lógica que lo rodea (qué está desbloqueado, qué
  se filtra). Que el prompt se abra y desbloquee de verdad en un
  teléfono con huella configurada está sin probar; también sin
  probar el caso sin ningún método de bloqueo (el mensaje amigable)
  y el comportamiento en API 26–29 (rama con credencial del
  dispositivo). La primera prueba real es la de Javier.
- **Los haptics, el predictive back y el edge-to-edge tampoco se
  probaron en un dispositivo real**: el gesto atrás y la vibración
  no existen en CI. El Manifest lleva `enableOnBackInvokedCallback`
  y los diálogos y el bottom sheet usan los componentes de Material 3
  que responden al gesto atrás por defecto, pero nadie lo ha hecho
  con el dedo.

## Archivos creados/modificados

- `app/src/main/java/com/coleccionenlaces/app/domain/BloqueoCarpetas.kt` —
  nuevo: `BloqueoCarpetas` (lógica pura, testeable sin BiometricPrompt)
  con el conjunto de categorías desbloqueadas en la sesión y
  `filtrarEnlacesVisibles` (la única regla de visibilidad: solo
  enlaces de categorías desbloqueadas).
- `app/src/main/java/com/coleccionenlaces/app/ui/home/Biometria.kt` —
  nuevo: capa fina de `BiometricPrompt` (autenticación fuerte o
  credencial del dispositivo; en API 26–29, credencial con
  `setDeviceCredentialAllowed`) y `puedeIntentarAutenticacion`
  (false si no hay ningún método de bloqueo configurado).
- `app/src/main/java/com/coleccionenlaces/app/ui/home/DialogoCarpeta.kt` —
  nuevo: diálogo de crear carpeta (nombre obligatorio, paleta de 8
  colores, 12 íconos Material) y de renombrar (solo el nombre),
  diálogo de gestionar carpeta propia (renombrar / borrar) y
  confirmación de borrado (avisa que los enlaces pasan a "Otros").
  Ancho máximo 560 dp para tablet.
- `app/src/main/java/com/coleccionenlaces/app/ui/theme/PreferenciasTema.kt` —
  nuevo: tema oscuro/claro persistido con SharedPreferences del
  framework (sin librería nueva); oscuro es el predeterminado.
- `app/src/main/java/com/coleccionenlaces/app/data/RepositorioEnlaces.kt` —
  `crearCarpeta`, `renombrarCarpeta` y `borrarCarpeta` con
  `ResultadoCarpeta` / `TipoErrorCarpeta` (nombre vacío, duplicado
  sin distinguir mayúsculas, límite de 20, no es propia, no existe).
- `app/src/main/java/com/coleccionenlaces/app/data/CategoriaDao.kt` —
  `obtenerPorNombreSinDistinguirMayusculas` (COLLATE NOCASE) y
  `ordenMaximo` (la carpeta nueva va al final).
- `app/src/main/java/com/coleccionenlaces/app/ui/home/HomeViewModel.kt` —
  `HomeUiState` con `categoriasDesbloqueadas` y `seccionesBloqueadas`
  (categorías con enlaces que siguen bloqueadas); el estado aplica
  el filtro de bloqueo al final (cuadrícula, búsqueda y favoritos);
  `desbloquearCategoria` / `estaDesbloqueada`, diálogo de carpeta y
  `crearCarpeta` / `renombrarCarpeta` / `borrarCarpeta` (borrar la
  categoría del filtro activo vuelve el filtro a "Todos").
- `app/src/main/java/com/coleccionenlaces/app/ui/home/HomeScreen.kt` —
  marcador de sección bloqueada (candado, nombre e ícono de la
  categoría, "Toca para abrir con huella"; sin ningún contenido del
  enlace); chip de categoría propio con candado cuando está
  bloqueada, toque (pide huella si está bloqueada) y pulsación larga
  (gestionar, solo carpetas propias); chip "+" para crear carpeta;
  interruptor de tema en el encabezado; la cuadrícula recorre las
  categorías en orden e intercala secciones desbloqueadas y
  marcadores; los marcadores se ocultan con búsqueda activa o en
  favoritos.
- `app/src/main/java/com/coleccionenlaces/app/ui/home/IconosCategoria.kt` —
  10 claves nuevas para las carpetas propias (favorite, bookmark,
  star, home, work, school, music_note, photo_camera, pets,
  sports_soccer).
- `app/src/main/java/com/coleccionenlaces/app/ui/home/TarjetaEnlace.kt` y
  `MenuTarjeta.kt` — feedback táctil al marcar/quitar favorito y al
  borrar (también al desbloquear con huella, en `HomeScreen`).
- `app/src/main/java/com/coleccionenlaces/app/MainActivity.kt` —
  extiende `FragmentActivity` (el prompt de huella lo exige;
  `ComponentActivity` no basta) y guarda el tema en estado de
  Compose, leído de SharedPreferences al arrancar.
- `app/src/main/AndroidManifest.xml` —
  `android:enableOnBackInvokedCallback="true"` (predictive back,
  API 33+).
- `gradle/libs.versions.toml` y `app/build.gradle.kts` —
  `androidx.fragment:fragment-ktx` 1.8.8 (solo para la
  FragmentActivity del prompt de huella).
- `.github/workflows/build.yml` — `actions/checkout`, `setup-java`
  y `upload-artifact` migrados de v4 a v5. Queda un aviso en el run
  (no bloquea): `upload-artifact@v5` todavía apunta a Node 20 y
  GitHub lo ejecuta forzado en Node 24; no hay versión más nueva
  que lo quite.
- `app/src/main/res/values/strings.xml` — 25 textos nuevos en
  español (huella, tema, carpetas).
- Pruebas nuevas: `BloqueoCarpetasTest` (7), `CarpetasUsuarioTest`
  (13), `PreferenciasTemaTest` (3) y 8 pruebas nuevas en
  `HomeViewModelTest`. Las pruebas existentes del ViewModel que
  agregan enlaces ahora desbloquean la carpeta antes de afirmar
  que el enlace se ve (antes no había bloqueo).

## Decisiones de diseño

- **Bloqueo por carpeta, solo en sesión.** El desbloqueo vive solo
  en memoria (en el ViewModel, vía `BloqueoCarpetas`): al cerrar la
  app se pierde y todo vuelve a estar bloqueado. No se guarda nada
  en la base ni en preferencias. La huella no se pide para abrir la
  app, solo por carpeta.
- **Bloqueo real, sin contenido visible.** De una carpeta bloqueada
  no se ve nada en ningún sitio: ni títulos, ni descripciones, ni
  imágenes, ni URLs (el ViewModel filtra la lista final, así que la
  búsqueda y los favoritos también la excluyen). El marcador muestra
  solo el nombre, el ícono y la cantidad (un número, no contenido),
  para que se pueda tocar y abrir con huella. El total del
  encabezado y los contadores de los chips también son solo números
  y se siguen mostrando; si se quisieran ocultar también, es un
  cambio de una línea en el ViewModel.
- **Lógica separada del prompt.** `BloqueoCarpetas` es lógica pura
  (sin Android) y se prueba en la JVM; `Biometria` es solo el
  llamado a `BiometricPrompt` y no se puede probar sin dispositivo.
- **Gestionar carpetas: pulsación larga sobre el chip.** Solo las
  carpetas propias se pueden renombrar y borrar (las predefinidas
  no: su pulsación larga no hace nada). Borrar pide confirmación y
  sus enlaces pasan a "Otros" (SET_NULL, decisión de la Fase 2).
  Borrar o renombrar una carpeta bloqueada no pide huella: no
  muestra contenido, solo destruye o renombra la carpeta (anotado
  como posible refuerzo en "Ideas futuras").
- **Chips de categoría propios.** `FilterChip` no expone pulsación
  larga, así que los chips de categoría son un composable propio
  (`ChipCategoria`) con el mismo aspecto (fondo, borde, forma de
  8 dp); "Todos", "Favoritos" y "+" siguen siendo `FilterChip`.
- **Tema.** Interruptor en el encabezado (sol ↔ luna), persistido
  con SharedPreferences y aplicado al arrancar. Oscuro sigue siendo
  el predeterminado.
- **SplashScreen y edge-to-edge verificados en código, no en
  pantalla.** `installSplashScreen()` sigue en `MainActivity` y
  `enableEdgeToEdge()` con el `padding` del `Scaffold` y el hueco
  inferior de 96 dp siguen como en la Fase 6; nada nuevo queda
  detrás de las barras del sistema en el código. Sin emulador, no
  hay captura que lo demuestre.
- **Animaciones (sección 6) completas.** Ya estaban: transiciones al
  filtrar (`animateItem` en tarjetas, encabezados y shimmer),
  `AnimatedVisibility` al aparecer cada sección y animación de
  entrada por tarjeta nueva. Esta fase añade la misma entrada
  animada a los marcadores de bloqueado. No faltaba ninguna de la
  lista.
- **Tablet.** La cuadrícula adaptativa (`GridCells.Adaptive`,
  minSize 160 dp) ya existía; los diálogos nuevos limitan su ancho
  a 560 dp para no estirarse en pantallas anchas.

## Pruebas: 156 unitarias, todas en verde en CI

- `BloqueoCarpetasTest` (7, nuevas): por defecto todo bloqueado;
  desbloquear abre solo esa carpeta; desbloquear dos veces no
  duplica; bloquear todo; filtrar sin desbloqueo no deja ver nada;
  filtrar deja ver solo las desbloqueadas; con todo desbloqueado
  se ve todo.
- `CarpetasUsuarioTest` (13, nuevas): crear pasa a 18 y no es
  predefinida; nombre vacío rechazado; duplicado exacto y sin
  distinguir mayúsculas rechazados; nombre de predefinida
  rechazado; crear hasta 20 rechaza la 21 con límite; renombrar
  propia cambia el nombre; renombrar predefinida rechazado sin
  tocarla; renombrar a duplicado rechazado; renombrar inexistente
  rechazado; borrar propia mueve sus enlaces a "Otros"; borrar
  predefinida e inexistente rechazados.
- `PreferenciasTemaTest` (3, nuevas): sin nada guardado es oscuro;
  guardar claro y oscuro persisten.
- `HomeViewModelTest` (8 nuevas): por defecto las carpetas están
  bloqueadas hasta desbloquear (lista y secciones vacías, marcador
  con el nombre); la búsqueda excluye carpetas bloqueadas; los
  favoritos excluyen carpetas bloqueadas; crear carpeta crea y
  cierra el diálogo; duplicada pone el error y no crea; hasta 20
  rechaza la siguiente; renombrar predefinida pone el error;
  borrar propia mueve sus enlaces a "Otros".
- Las 125 pruebas de las Fases 1–8 siguen pasando. Total: 156,
  0 fallos.

## Qué quedó funcionando

- Huella por carpeta: marcador de bloqueado en "Todos", prompt de
  huella o credencial al tocar el marcador o el chip, desbloqueo
  solo en sesión, búsqueda y favoritos sin contenido bloqueado,
  mensaje amigable si no hay método de bloqueo configurado.
- Carpetas propias: crear (nombre, color, ícono; máximo 20 en
  total), renombrar y borrar solo las propias (pulsación larga en
  el chip), con confirmación al borrar y enlaces a "Otros". La
  lista de "cambiar categoría" del bottom sheet ya las incluía
  (Fase 8) y no necesitó cambios.
- Pulido visual: haptics en favorito, borrado y desbloqueo;
  predictive back activado; SplashScreen y edge-to-edge verificados
  en código; interruptor claro/oscuro persistido (oscuro por
  defecto); diálogos con ancho limitado en tablet; animaciones de
  la sección 6 completas; workflow migrado a acciones v5.
- `testDebugUnitTest` y `assembleDebug` en verde en CI
  (run 45146741106).

## Qué falta (fases siguientes)

- Fase 10 según el plan (verificación final y entrega). La revisión
  visual de esta fase y la prueba real de la huella son de la
  Fase 10 y del teléfono de Javier (ver advertencias arriba).
- Sin avisos de deprecación de Node 20 que se puedan quitar: el
  aviso restante viene de `upload-artifact@v5` y no hay versión
  más nueva.

## Notas

- Verificación real solo en CI: Gradle local está prohibido en
  esta VM (el daemon necesita TCP local) y no se pidió ningún
  permiso de puerto. El CI pasó a la segunda (1 fallo de prueba,
  corregido; ver arriba).
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función fuera del plan ni de las órdenes
  de Javier.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.
- Sin palabras clave sensibles en ningún archivo, reporte, commit ni log.

## Ideas futuras

- Pedir huella también para borrar o renombrar una carpeta
  bloqueada (hoy no se pide: no muestra contenido, pero destruye
  o cambia la carpeta sin autenticarse).
- Ocultar también los contadores y el total de las carpetas
  bloqueadas (hoy son solo números visibles; ver "Decisiones").
- Al crear una carpeta, desbloquearla automáticamente en la sesión
  (hoy nace bloqueada como las demás; no tiene enlaces hasta que
  se mueve uno, así que no se nota).
- Contador de favoritos en el chip "Favoritos" (idea de la Fase 6,
  sigue pendiente).
