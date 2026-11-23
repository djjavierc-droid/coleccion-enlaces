# Fase 2 — Modelo de datos (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45127373122** — `success` (5m00s, commit `0833fcd`).
  - `testDebugUnitTest`: BUILD SUCCESSFUL (3m04s).
  - `assembleDebug`: BUILD SUCCESSFUL (1m34s).
- Artefacto: **coleccion-debug** (APK de depuración, 13.609.449 bytes),
  en el run 45127373122 del repo `djjavierc-droid/coleccion-enlaces`.
- Commits en `main`: 754c16b (modelo + DAOs + base), b9783f5 (pruebas DAO),
  0833fcd (corrección de compilación, ver abajo).

## Archivos creados/modificados

- `app/src/main/java/com/coleccionenlaces/app/data/Enlace.kt` — entidad
  `enlaces` con los campos del plan: id, url, titulo, descripcion,
  urlImagen (nullable), categoriaId, esFavorito, categoriaManual,
  fechaCreacion (milisegundos desde epoch).
- `app/src/main/java/com/coleccionenlaces/app/data/Categoria.kt` — entidad
  `categorias`: id, nombre (único), color (ARGB), icono (clave de
  Material Icon), esPredefinida, orden.
- `app/src/main/java/com/coleccionenlaces/app/data/CategoriasPredefinidas.kt` —
  las 17 predefinidas en orden, con su color de acento y su ícono, y
  `MAX_CATEGORIAS = 20`. Única fuente de los datos de siembra.
- `app/src/main/java/com/coleccionenlaces/app/data/EnlaceDao.kt` — insertar,
  insertar varios, actualizar, borrar, borrar por id, leer por id, listar
  todo (fecha descendente), por categoría, solo favoritos, buscar por texto
  (título, descripción y nombre de categoría, sin distinguir mayúsculas) y
  contador por categoría. Todo lo observable devuelve `Flow`.
- `app/src/main/java/com/coleccionenlaces/app/data/CategoriaDao.kt` —
  insertar, actualizar, borrar, leer por id y por nombre, listar todas,
  solo predefinidas y contar.
- `app/src/main/java/com/coleccionenlaces/app/data/AppDatabase.kt` — base
  Room versión 1 (exportSchema = false), singleton `obtenerInstancia` y
  `crearEnMemoria` para pruebas. Siembra las 17 predefinidas en la
  primera creación con SQL directo en el callback `onCreate`.
- `app/src/test/java/com/coleccionenlaces/app/data/EnlaceDaoTest.kt` — 16
  pruebas del DAO de enlaces.
- `app/src/test/java/com/coleccionenlaces/app/data/CategoriaDaoTest.kt` — 6
  pruebas del DAO de categorías y de la siembra.
- `gradle/libs.versions.toml` y `app/build.gradle.kts` — `testImplementation`
  de `androidx.room:room-testing` (2.8.4), `androidx.test:core` (1.6.1) y
  `org.robolectric:robolectric` (4.14.1); `testOptions.unitTests.isIncludeAndroidResources = true`.
- Eliminado `app/src/main/java/com/coleccionenlaces/app/data/.gitkeep` (el
  paquete ya tiene archivos reales).

## Decisión de diseño de categorías (importante para la Fase 9)

**NO es un enum cerrado.** Las categorías son filas de la tabla
`categorias`, por lo que la app soporta las 17 predefinidas MÁS las
carpetas que el usuario cree a mano (Fase 9), hasta el máximo de 20.
`Enlace` referencia la categoría por id (`categoriaId`, clave foránea a
`categorias.id` con `onDelete = SET_NULL`): si se borra una carpeta
propia, sus enlaces sobreviven sin categoría (el repositorio de la
Fase 5 los tratará como "Otros"); las 17 predefinidas no se borran.

- La `categoria` del plan se guarda como `categoriaId: Long?` — así la
  categoría puede cambiar de nombre, color o ícono sin tocar los enlaces.
- Color e ícono por categoría (sección 3 del plan, adaptada a 17):
  Cocina naranja `0xFFFF7043` / restaurant; Mecánica gris azulado
  `0xFF78909C` / car_repair; Construcción ámbar `0xFFFFB300` /
  construction; Contenido XXX rosa `0xFFEC407A` / lock (ícono discreto a
  propósito, sin imagen explícita); Entretenimiento violeta `0xFFAB47BC` /
  movie; Tecnología azul claro `0xFF29B6F6` / smartphone; Autos y motos
  rojo `0xFFEF5350` / directions_car; Salud verde `0xFF66BB6A` /
  fitness_center; Dinero verde lima `0xFF7CB342` / attach_money; Noticias
  índigo `0xFF5C6BC0` / newspaper; Juegos turquesa `0xFF26A69A` /
  sports_esports; DIY marrón `0xFF8D6E63` / handyman; Jardín verde oliva
  `0xFF558B2F` / local_florist; Pesca y campo azul `0xFF039BE5` / forest;
  Moda magenta `0xFF8E24AA` / checkroom; Viajes cian `0xFF00ACC1` /
  flight; Otros gris `0xFF90A4AE` / category. Los 17 colores son distintos.
  La UI (Fases 6 y 9) resuelve cada clave `icono` al Material Icon real.
- `categoriaManual` solo se almacena en esta fase: cuando sea true, la
  clasificación automática (Fase 4) no debe sobrescribir la categoría.
- No se crean categorías automáticamente: solo las 17 sembradas y las
  que el usuario cree a mano.

## Pruebas: 24 unitarias, todas en verde en CI

- `EnlaceDaoTest` (16): insertar y leer por id; valores por defecto;
  actualizar (título, descripción, favorito); borrar y borrar por id;
  listar ordenado por fecha descendente; filtrar por categoría; solo
  favoritos; buscar por título, por descripción y por nombre de categoría;
  búsqueda sin distinguir mayúsculas; sin coincidencias (vacío); texto
  vacío (devuelve todo); `categoriaManual` se conserva al actualizar;
  contador por categoría.
- `CategoriaDaoTest` (6): la siembra crea las 17 predefinidas en orden con
  los nombres exactos; colores distintos e íconos no vacíos; insertar una
  carpeta propia (pasa a 18); renombrar una carpeta propia; borrar una
  carpeta propia deja sus enlaces con categoría nula (SET_NULL);
  `obtenerPorNombre` devuelve null si no existe.
- `TemaTest` (2, de la Fase 1): siguen pasando.
- Las pruebas del DAO usan Room en memoria con Robolectric
  (`@Config(sdk = [34])`, `ApplicationProvider`): Room necesita un Context
  de Android y SQLite real, que Robolectric aporta en la JVM sin emulador,
  por lo que corren en `testDebugUnitTest`. `room-testing` queda agregado
  como artefacto de pruebas de Room (para estas pruebas y para futuras
  pruebas de migración).

## Qué quedó funcionando

- Entidad Enlace, tabla Categoria sembrada con las 17, DAOs con
  insertar/actualizar/borrar/listar/buscar/filtrar y base Room singleton
  con Flow para observar cambios.
- `testDebugUnitTest` y `assembleDebug` en verde en CI (run 45127373122).

## Qué falta (fases siguientes)

- Fase 3: vista previa con OkHttp + Jsoup.
- Fase 4: clasificación (función pura + archivo separado y secreto CI).
- Fase 5: repositorio y ViewModels (aquí se usará `AppDatabase.obtenerInstancia`
  y se resolverá `categoriaId = null` como "Otros").
- Fases 6–10 según el plan. La creación de carpetas por el usuario y la
  huella por carpeta son UI de la Fase 9; el soporte en datos ya existe.

## Corrección durante la fase (1 fallo de CI, resuelto)

1. Run 45127024850, `compileDebugKotlin` FAILED: Kotlin 2.3 rechaza
   `arrayOf(...)` con tipos mezclados (String, Long, Int) en la siembra de
   `AppDatabase.kt:71` ("Type argument for reified type parameter 'T' was
   inferred to the intersection..."). Corregido con tipo explícito
   `arrayOf<Any?>(...)` (commit 0833fcd); el siguiente run quedó en verde.

## Notas

- Verificación real solo en CI: Gradle local está prohibido en esta VM
  (el daemon necesita TCP local) y no se pidió ningún permiso de puerto.
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función fuera del plan.

## Ideas futuras

- Pruebas de migración de Room (con `room-testing`, ya agregado) cuando
  la base suba a la versión 2 en alguna fase posterior.
- Migrar `actions/setup-java@v4` a `@v5` (aviso de deprecación en CI, no
  bloquea; ya anotado en la Fase 1).
