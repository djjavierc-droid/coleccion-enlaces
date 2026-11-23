# Fase 5 — Repositorio y ViewModels (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45135189175** — `success` (5m39s, commit `ecca49e`).
  - `testDebugUnitTest`: BUILD SUCCESSFUL (3m19s).
  - `assembleDebug`: BUILD SUCCESSFUL (2m1s).
- Artefacto: **coleccion-debug** (APK de depuración, 13.656.716 bytes),
  en el run 45135189175 del repo `djjavierc-droid/coleccion-enlaces`.
- Commits en `main`: 680b674 (repositorio + 18 pruebas),
  d465f0a (ViewModel + 9 pruebas), be7e4ed (inyección manual),
  11b7a34 y bd16e87 (correcciones de compilación del `combine`),
  bc90371 y ecca49e (correcciones de pruebas, ver abajo).

## Archivos creados/modificados

- `app/src/main/java/com/coleccionenlaces/app/data/RepositorioEnlaces.kt` —
  `RepositorioEnlaces`, única fuente de datos: une `EnlaceDao`,
  `CategoriaDao` y `ServicioVistaPrevia` con `Clasificador`.
  `agregarEnlace` (validar, vista previa, clasificar, resolver
  nombre a `categoriaId` con `obtenerPorNombre`, guardar),
  `enlacesConCategoria` / `buscarEnlaces` / `obtenerEnlacesPorCategoria` /
  `obtenerFavoritos` (todos con la categoría ya resuelta),
  `contadoresPorCategoria`, `actualizarEnlace`, `reclasificarEnlace`,
  `cambiarCategoriaManual`, `alternarFavorito`, `borrarEnlace`,
  `obtenerEnlacePorId`. Resultado sellado `ResultadoAgregarEnlace`
  (`Exito(id)` / `Error(TipoErrorAgregarEnlace)`).
- `app/src/main/java/com/coleccionenlaces/app/data/EnlaceConCategoria.kt` —
  modelo `EnlaceConCategoria(enlace, categoria)`: la UI recibe cada
  enlace acompañado de su categoría (nombre, color, ícono).
- `app/src/main/java/com/coleccionenlaces/app/ui/home/HomeViewModel.kt` —
  `HomeViewModel` con un único `HomeUiState` expuesto como `StateFlow`
  (`estado`), `HomeViewModelFactory` manual y `SeccionEnlaces`
  (agrupado por categoría para la cuadrícula de la Fase 6).
- `app/src/main/java/com/coleccionenlaces/app/di/AppContainer.kt` —
  contenedor de inyección manual (`lazy`): base de datos, servicio
  de vista previa, repositorio y factoría del ViewModel. Sin Hilt ni Koin.
- `app/src/main/java/com/coleccionenlaces/app/ColeccionApplication.kt` —
  `Application` que posee el contenedor (registrada en el manifiesto).
- `app/src/main/java/com/coleccionenlaces/app/MainActivity.kt` —
  crea el `HomeViewModel` con la factoría del contenedor y lo pasa
  a la navegación.
- `app/src/main/java/com/coleccionenlaces/app/ui/home/HomeScreen.kt` —
  recibe el ViewModel (la Fase 6 consumirá `viewModel.estado`).
- `app/src/main/AndroidManifest.xml` — `android:name=".ColeccionApplication"`.
- `gradle/libs.versions.toml` y `app/build.gradle.kts` —
  `androidx.lifecycle:lifecycle-viewmodel-ktx` (2.10.0, implementación)
  y `org.jetbrains.kotlinx:kotlinx-coroutines-test` (1.11.0, pruebas).
- `app/src/test/java/com/coleccionenlaces/app/data/RepositorioEnlacesTest.kt` —
  18 pruebas del repositorio (Room en memoria con Robolectric,
  vista previa falsa por interceptor OkHttp, sin red).
- `app/src/test/java/com/coleccionenlaces/app/ui/home/HomeViewModelTest.kt` —
  9 pruebas del ViewModel (`runTest` + Main de prueba `Unconfined`).

## Decisiones de diseño

- **Categoría siempre resuelta al exponer.** `EnlaceConCategoria`
  lleva la `Categoria` completa. Un `categoriaId` null (la carpeta
  del enlace se borró y la clave foránea usó `SET_NULL`) se expone
  como "Otros": la fila real sembrada, o una sintética con sus
  mismos datos si ni siquiera esa existiera. La UI nunca trata el
  caso null: todo enlace tiene nombre, color e ícono visibles.
- **Validación de URL delegada al servicio.** `agregarEnlace` no
  reimplementa la validación: llama al servicio de vista previa y,
  si este devuelve `URL_INVALIDA`, no guarda nada. El servicio ya
  valida sin tocar la red, así que hay una sola regla en un solo sitio.
- **Vista previa vacía = dominio como título.** Cualquier otro fallo
  de vista previa (sin internet, timeout, sitio sin vista previa)
  guarda el enlace igual, con el host de la URL (sin `www.`) como
  título y sin imagen. Solo `URL_INVALIDA` impide guardar.
- **Clasificación solo al agregar y al reclasificar.** El enlace nuevo
  tiene `categoriaManual = false` y se clasifica; el nombre se resuelve
  a `categoriaId` con `obtenerPorNombre` y, si no se resuelve, se usa
  "Otros". `actualizarEnlace` NO reclasifica: guarda el enlace tal
  como llega, así la categoría manual nunca se sobrescribe al editar.
  `reclasificarEnlace` es la única vía que vuelve a clasificar y
  devuelve `false` sin tocar nada si el enlace es manual.
- **Un único estado de UI.** `HomeUiState` (lista filtrada, total sin
  filtrar, categorías, contadores, filtro, búsqueda, carga, estado
  transitorio de agregar) expuesto como un solo `StateFlow`: un solo
  estado evita estados intermedios incoherentes. `secciones` es una
  propiedad derivada (agrupado por categoría, en el orden de los
  chips) para la cuadrícula de la Fase 6; el agrupado vive en el
  ViewModel, no en los composables.
- **Filtrado en memoria en el ViewModel.** Un solo Flow de todos los
  enlaces + filtro por categoría/favoritos/búsqueda en memoria: una
  sola suscripción a la base y filtro instantáneo. El filtro por
  categoría compara el id de la categoría RESUELTA (un enlace sin
  categoría se muestra como "Otros", así que al elegir ese chip
  aparece; comparar el `categoriaId` guardado, que es null, lo
  dejaría fuera). Las consultas filtradas del repositorio quedan
  como API de datos para usos directos y para las pruebas.
- **`combine` encadenado, no de 8 flujos.** El `combine` de 8 flujos
  no compila en este Kotlin: la resolución de sobrecargas elige la
  variante vararg (dos intentos fallidos, ver abajo). Los controles
  mutables (filtro + alta) viven en un solo `MutableStateFlow`
  (`ControlesUi`) y el estado se construye con `combine` de 2 flujos
  encadenados, que se resuelven sin ambigüedad.
- **Inyección manual.** `AppContainer` simple con `lazy`, sin Hilt
  ni Koin. Lo posee `ColeccionApplication`; `MainActivity` crea el
  ViewModel con su factoría.

## Pruebas: 100 unitarias, todas en verde en CI

- `RepositorioEnlacesTest` (18, nuevas): agregar con vista previa
  simulada (título, descripción, imagen, clasifica a Cocina);
  vista previa vacía → título = dominio y categoría Otros; URL
  inválida → error y no guarda nada; sin internet → guarda igual
  con el dominio; clasifica por la descripción; reclasificar no
  sobrescribe la categoría manual; reclasificar sí reclasifica
  cuando no es manual; actualizar conserva categoría y flag manual;
  cambiar categoría manual (y false con enlace/categoría
  inexistentes); alternar favorito (y null si no existe); borrar;
  buscar por título, descripción y nombre de categoría; filtrar por
  categoría y favoritos con categoría resuelta; `categoriaId` null
  se expone como Otros; contadores (sin categoría cuentan en Otros);
  las 17 predefinidas en orden; espacios alrededor de la URL.
- `HomeViewModelTest` (9, nuevas): estado inicial cargado (vacío,
  17 categorías); agregar actualiza la lista con su categoría
  resuelta (y secciones y contador); URL inválida pone el error y
  no guarda (y se limpia); búsqueda filtra por título, descripción
  y nombre de categoría; filtro por categoría; solo favoritos;
  cambiar categoría manual cambia la sección; borrar quita el
  enlace; la categoría manual no se sobrescribe al reclasificar.
- `ClasificadorTest` (29), `ParseadorVistaPreviaTest` (10),
  `ServicioVistaPreviaTest` (10), `EnlaceDaoTest` (16),
  `CategoriaDaoTest` (6) y `TemaTest` (2) de las Fases 1–4: siguen
  pasando. Total: 100 pruebas, 0 fallos.

## Qué quedó funcionando

- Repositorio como única fuente de datos, con agregar (vista previa
  + clasificación + respaldo a dominio/Otros), listar/buscar/filtrar/
  favoritos/contadores con categoría resuelta, y edición con
  prioridad manual garantizada.
- ViewModel principal con estado único, filtro, búsqueda, carga y
  alta de enlace, conectado en la app por inyección manual.
- `testDebugUnitTest` y `assembleDebug` en verde en CI
  (run 45135189175).

## Qué falta (fases siguientes)

- Fase 6: pantalla principal (aquí se consumirá
  `HomeViewModel.estado`: encabezado, búsqueda, chips, cuadrícula
  agrupada por `secciones`, tarjetas, vacío, shimmer).
- Fase 7: diálogo de agregar (usará `HomeViewModel.agregarEnlace`
  y su estado `guardandoEnlace` / `errorAgregar`).
- Fases 8–10 según el plan. No hay UI nueva en esta fase: sin
  lógica de negocio en composables, como pide el plan.

## Correcciones durante la fase (3 fallos de CI, resueltos)

1. Run 45133490555, `compileDebugKotlin` FAILED: el `combine` de
   8 flujos no infiere los tipos (la sobrecarga vararg gana).
   Intentado primero con tipos explícitos (run 45133852527: el
   compilador siguió eligiendo la variante vararg). Resuelto
   reestructurando a `combine` de 2 flujos encadenados con los
   controles en un solo `StateFlow` (commit bd16e87).
2. Run 45134305111, 100 pruebas, 1 fallo: `la busqueda filtra...`
   (`ComparisonFailure`): carrera en el propio test — tras cambiar
   la búsqueda, `first { size == 1 }` devolvía el estado viejo del
   filtro anterior (también tamaño 1). Corregido con predicados por
   contenido y aserciones sobre el estado devuelto (commit bc90371).
3. Run 45134802855, `compileDebugUnitTestKotlin` FAILED: el import
   de `ExperimentalCoroutinesApi` estaba en `kotlinx.coroutines.test`
   y vive en `kotlinx.coroutines`. Corregido el import
   (commit ecca49e); el siguiente run quedó en verde.

## Notas

- Verificación real solo en CI: Gradle local está prohibido en esta
  VM (el daemon necesita TCP local) y no se pidió ningún permiso de
  puerto.
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función fuera del plan.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.

## Ideas futuras

- `reclasificarEnlace` podría ofrecerse desde la UI cuando el
  usuario edite el título de un enlace no manual (hoy solo existe
  la operación; ningún botón la llama).
- Los contadores por categoría podrían incluir las categorías con
  cero enlaces como entradas explícitas en el mapa (hoy la UI lee
  `map[id] ?: 0`).
