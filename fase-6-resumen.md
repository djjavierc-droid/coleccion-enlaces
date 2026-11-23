# Fase 6 — Pantalla principal (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45138685325** — `success` (~6m34s, commit `5eb9ae1`).
  - `testDebugUnitTest`: BUILD SUCCESSFUL (100 pruebas, 0 fallos).
  - `assembleDebug`: BUILD SUCCESSFUL.
- Artefacto: **coleccion-debug** (APK de depuración, 20.507.144 bytes),
  en el run 45138685325 del repo `djjavierc-droid/coleccion-enlaces`.
  El APK creció respecto a la Fase 5 (13.656.716 bytes) por la
  dependencia `material-icons-extended`, necesaria para los íconos
  de las 17 categorías (ver abajo).
- Commits en `main`: 82a152b (pantalla principal), d0b0d2f
  (corrección de compilación), 5eb9ae1 (corrección de una carrera
  en una prueba de la Fase 5, ver abajo).

## ADVERTENCIA EXPLÍCITA — la pantalla NO se vio visualmente

La pantalla quedó verificada por compilación (`assembleDebug` en
verde) y por conexión con el estado real (`HomeViewModel.estado`,
con sus `secciones`, contadores, filtro y búsqueda), NO vista
visualmente: en CI no hay emulador ni capturas de pantalla, y en
esta VM está prohibido correr Gradle en local. Nadie ha visto la
pantalla principal renderizada. La revisión visual es de las
Fases 9/10 y del teléfono de Javier. No se afirma que la pantalla
se vea bien, solo que compila y que está conectada al estado real.

## Archivos creados/modificados

- `app/src/main/java/com/coleccionenlaces/app/ui/home/HomeScreen.kt` —
  reescrita completa: consume `viewModel.estado` (`collectAsState`)
  y dibuja encabezado grande ("Colección" + contador total sin
  filtrar + subtítulo), barra de búsqueda redondeada, fila
  horizontal de chips y `LazyVerticalGrid` agrupada por categoría.
- `app/src/main/java/com/coleccionenlaces/app/ui/home/IconosCategoria.kt` —
  nuevo: resuelve la clave `icono` de `Categoria` al Material Icon
  real (las 17 claves; una clave desconocida cae en el ícono
  genérico, nunca rompe la pantalla), `colorCategoria` (ARGB Long
  a `Color` de Compose) y `colorSobreCategoria` (negro sobre
  colores claros, blanco sobre oscuros, para que ningún chip
  seleccionado quede con contraste insuficiente).
- `app/src/main/java/com/coleccionenlaces/app/ui/home/TarjetaEnlace.kt` —
  nuevo: tarjeta con imagen Coil `AsyncImage` arriba (degradado
  inferior, `ContentScale.Crop`), título máx. 2 líneas,
  descripción máx. 2 líneas (si está vacía muestra la URL),
  etiqueta de categoría con su color e ícono, e ícono de favorito
  que alterna con `ViewModel.alternarFavorito`. Sin imagen (sitio
  sin vista previa) muestra un respaldo con el ícono de enlace
  sobre el color de la categoría; mientras carga, shimmer. La
  tarjeta NO abre nada al tocarla (Fase 8). Animación de entrada
  (fade + leve desplazamiento) una vez por enlace.
- `app/src/main/java/com/coleccionenlaces/app/ui/home/Shimmer.kt` —
  nuevo: `rememberShimmerBrush` (degradado que se desplaza con
  `infiniteTransition`, sin spinner genérico) y `TarjetaShimmer`
  con la misma forma que la tarjeta real.
- `app/src/main/java/com/coleccionenlaces/app/ui/home/EstadoVacio.kt` —
  nuevo: ilustración hecha solo con Compose (círculos con
  degradado + ícono de enlace, sin imágenes externas) y texto
  invitando a pegar el primer enlace; con filtro o búsqueda
  activos y sin resultados, el texto cambia a "Sin resultados".
- `app/src/main/res/values/strings.xml` — textos nuevos en español
  (búsqueda, chips, contador, favorito, vacío, sin resultados);
  se eliminan `home_empty` y `home_phase_marker` de la Fase 1,
  ya sin uso.
- `gradle/libs.versions.toml` y `app/build.gradle.kts` —
  `androidx.compose.material:material-icons-extended` (versión
  gestionada por el BOM de Compose): los íconos de categoría
  (restaurant, car_repair, construction, newspaper,
  sports_esports, handyman, local_florist, forest, checkroom…)
  NO están en el núcleo de íconos de Material 3; sin esta
  dependencia del propio Compose no se pueden resolver.
- `app/src/test/java/com/coleccionenlaces/app/ui/home/HomeViewModelTest.kt` —
  corrección de una carrera en 2 pruebas de la Fase 5 (ver abajo);
  sin cambios en el código de producción.

## Qué quedó funcionando

- Encabezado: título "Colección", subtítulo y contador total
  ("Sin enlaces todavía" / "1 enlace" / "N enlaces").
- Búsqueda redondeada con borrado rápido, filtrada en vivo por el
  ViewModel (título, descripción y nombre de categoría).
- Chips horizontales: "Todos" (con el total), "Favoritos" y uno
  por categoría con su color, su ícono y su contador
  (`contadores[id] ?: 0`); el seleccionado va relleno con el color
  de su categoría y texto legible sobre él.
- Cuadrícula `LazyVerticalGrid` con `GridCells.Adaptive(minSize =
  160.dp)`: 2 columnas en teléfono y 3 o más en tablet. Agrupada
  por categoría con encabezados de sección (ícono en círculo de
  color + nombre + cantidad) usando las `secciones` derivadas del
  ViewModel, en el orden de los chips. `animateItem` en tarjetas
  y encabezados; `AnimatedVisibility` al aparecer cada sección
  (fade + deslizamiento) y animación de entrada por tarjeta nueva.
- Favorito: el ícono de la tarjeta alterna con
  `ViewModel.alternarFavorito` y SÍ funciona en esta fase.
- Carga: shimmer (6 tarjetas) mientras `cargando` es true; una
  tarjeta shimmer al principio de la cuadrícula mientras
  `guardandoEnlace` es true (se agrega un enlace y se obtiene su
  vista previa).
- Estado vacío ilustrado (con y sin resultados de filtro).
- Insets: `Scaffold` con su `padding` (edge-to-edge ya activado
  en `MainActivity`) y `contentPadding` inferior de 96 dp para
  que nada quede detrás del botón flotante ni de la barra de
  navegación.
- Botón flotante `ExtendedFloatingActionButton` con "+" y texto
  "Agregar enlace": abre el estado `mostrarDialogoAgregar`, ya
  preparado para la Fase 7.
- `testDebugUnitTest` y `assembleDebug` en verde en CI
  (run 45138685325).

## Qué falta (fases siguientes)

- Fase 7: diálogo de agregar — el botón flotante solo deja el
  estado `mostrarDialogoAgregar` en true; el CONTENIDO del
  diálogo (campo, validación, vista previa en curso y guardado)
  NO existe en esta fase y no se inventó. Usará
  `HomeViewModel.agregarEnlace` y su estado `guardandoEnlace` /
  `errorAgregar`. Luego el intent-filter ACTION_SEND.
- Fase 8: tocar la tarjeta NO abre nada en esta fase (a propósito);
  abrir el enlace y el menú de tarjeta (bottom sheet: cambiar
  categoría, favorito, compartir, borrar con confirmación) son la
  Fase 8.
- Fases 9–10 según el plan. La revisión visual de esta pantalla
  es de las Fases 9/10 y del teléfono de Javier (ver advertencia
  arriba).

## Correcciones durante la fase (2 fallos de CI, resueltos)

1. Run 45137509248, `compileDebugKotlin` FAILED (3 errores en
   `HomeScreen.kt`):
   - `FilterChipDefaults.filterChipColors` no tiene el parámetro
     `leadingIconColor` en esta versión de Material 3: el color
     del ícono sin seleccionar se llama `iconColor` (el de
     seleccionado sí es `selectedLeadingIconColor`). Corregido
     (commit d0b0d2f).
   - Falta el import de `androidx.compose.foundation.background`
     en `HomeScreen.kt` (lo usaba `EncabezadoSeccion`). Corregido
     (commit d0b0d2f).
2. Run 45138042781, `testDebugUnitTest` FAILED (100 pruebas,
   1 fallo): `HomeViewModelTest > agregarEnlace actualiza la
   lista con su categoria resuelta`. La compilación ya pasaba.
   Causa: carrera preexistente en la prueba de la Fase 5, no una
   regresión de la Fase 6 (esta fase no toca ViewModel,
   repositorio ni base de datos): la prueba esperaba solo
   `totalEnlaces == 1` y luego afirmaba `contadores`,
   `guardandoEnlace` y `secciones`, pero los contadores son otra
   consulta de Room que puede llegar después que la lista, y
   `guardandoEnlace` se apaga en una actualización posterior a
   la inserción; el primer estado con total 1 puede traer los
   contadores aún vacíos o `guardandoEnlace` aún en true. Es la
   misma clase de carrera que la Fase 5 ya corrigió en otra
   prueba (commit bc90371). Corregido esperando el estado ya
   asentado en el predicado (`totalEnlaces == 1` +
   `contadores[cocinaId] == 1` + `!guardandoEnlace` +
   `errorAgregar == null` + `secciones.size == 1`) y aplicando
   la misma corrección a la prueba de URL inválida (el error se
   pone en una actualización y `guardandoEnlace` se apaga en la
   siguiente). Solo cambió la prueba; el código de producción
   no se tocó (commit 5eb9ae1). El siguiente run quedó en verde.

## Notas

- Verificación real solo en CI: Gradle local está prohibido en
  esta VM (el daemon necesita TCP local) y no se pidió ningún
  permiso de puerto.
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función fuera del plan.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.
- Sin palabras clave sensibles en ningún archivo, reporte,
  commit ni log.

## Ideas futuras

- Contador de favoritos en el chip "Favoritos": el estado actual
  no expone el total de favoritos (solo el total general y los
  contadores por categoría); habría que añadirlo a `HomeUiState`.
- Usar `collectAsStateWithLifecycle` en lugar de `collectAsState`
  (requeriría añadir `lifecycle-runtime-compose`).
