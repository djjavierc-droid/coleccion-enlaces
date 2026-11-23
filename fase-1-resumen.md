# Fase 1 — Proyecto base (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45125750501** — `success` (commit `07b3bf8`).
- Artefacto: **coleccion-debug** (APK de depuración, 13.584.242 bytes),
  en el run 45125750501 del repo `djjavierc-droid/coleccion-enlaces`.
- Commits en `main`: 0e11c19 (docs), f57c5a2 (Gradle), f9a9d95 (Manifest/recursos),
  57737ce (código), 224426f (CI/.gitignore), 430b85c, 2555dc5, 07b3bf8 (3 correcciones CI).

## Archivos creados/modificados

- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- `gradle/libs.versions.toml` — catálogo de versiones (única fuente)
- `gradle/wrapper/` + `gradlew` / `gradlew.bat` — wrapper Gradle 8.14.3
- `app/build.gradle.kts` — `compileSdk 36`, `targetSdk 36`, `minSdk 26`,
  `applicationId com.coleccionenlaces.app`, `versionName 1.0.0`
- `app/src/main/AndroidManifest.xml` — permiso INTERNET, MainActivity
- `app/src/main/res/values/{strings,colors,themes}.xml` — textos en español,
  tema splash, icono adaptativo propio (`mipmap-anydpi-v26`, vector dibujado a mano)
- `app/src/main/java/com/coleccionenlaces/app/MainActivity.kt` — SplashScreen
  instalada, `enableEdgeToEdge()`, NavHost con destino `inicio`
- `app/src/main/java/com/coleccionenlaces/app/ui/home/HomeScreen.kt` — pantalla
  mínima real: título «Colección», subtítulo y texto de marcador (no en blanco)
- `app/src/main/java/com/coleccionenlaces/app/ui/theme/{Color,Type,Theme}.kt` —
  ColorScheme propio (oscuro predeterminado, claro disponible), acento
  violeta-índigo + coral, escala tipográfica propia, espaciado en múltiplos
  de 4/8 dp, fondo con degradado sutil. Solo fuentes del sistema.
- `app/src/main/java/com/coleccionenlaces/app/{data,domain,util}/.gitkeep` —
  estructura de paquetes (data, domain, ui, util)
- `app/src/test/java/com/coleccionenlaces/app/TemaTest.kt` — 2 pruebas unitarias
- `.github/workflows/build.yml` — push/PR a main: JDK 17 Temurin,
  `./gradlew testDebugUnitTest assembleDebug`, sube el APK debug
- `.gitignore` — estándar Android + `domain/XxxKeywords.kt` (nunca se commitea)

## Librerías (todas estables, en `gradle/libs.versions.toml`)

AGP 8.13.2, Kotlin 2.3.21, KSP 2.3.10, Gradle 8.14.3, Compose BOM 2026.05.00,
Navigation Compose 2.9.8, Room 2.8.4, Lifecycle 2.10.0, Activity Compose 1.13.0,
Core KTX 1.17.0, Coil 3.5.0 (+ network-okhttp), OkHttp 4.12.0, Jsoup 1.24.1,
Coroutines 1.11.0, SplashScreen 1.2.0, Biometric 1.1.0 (única extra fuera del plan).

## Qué quedó funcionando

- `testDebugUnitTest` y `assembleDebug` en verde en CI (run 45125750501).
- La app arranca con SplashScreen y muestra la pantalla inicial real.
- Tema oscuro predeterminado con degradado; modo claro definido en código.

## Qué falta (fases siguientes)

- Fase 2: entidad Enlace, DAO, base Room y pruebas DAO.
- Fase 3: vista previa con OkHttp + Jsoup. Fase 4: clasificación (+ secreto
  `XXX_KEYWORDS_B64` en CI y generación del archivo `XxxKeywords.kt` en el
  workflow; la Fase 1 no lo incluye porque aún no existe código que lo use).
- Fases 5–10 según el plan. El interruptor claro/oscuro en UI llega en Fase 9.

## Correcciones durante la fase (3 fallos de CI, todos resueltos)

1. Kotlin 2.3 eliminó `kotlinOptions.jvmTarget` → DSL `compilerOptions`.
2. Compose 1.12, Lifecycle 2.11, Coil 3.6.3 y OkHttp 5.5.1 exigen compileSdk 37
   (plataforma en vista previa) → se bajaron a las últimas estables con
   compileSdk 36 (BOM 2026.05.00, Lifecycle 2.10.0, Coil 3.5.0, OkHttp 4.12.0).
   Cuando Android 17 sea estable, se podrá subir a compileSdk 37 y a esas versiones.
3. `Theme.Material3.DayNight.NoActionBar` no existe como recurso XML (M3 Compose
   no aporta temas XML) → padre `android:Theme.Material.NoActionBar`; Compose
   dibuja el resto.

## Notas

- `core-ktx` quedó en 1.17.0 a propósito: en 1.18.0 la compatibilidad con
  huella en API 29 e inferiores se rompió (restaurada solo en la alfa 1.20.0);
  la huella por carpeta es Fase 9.
- Verificación real solo en CI: Gradle local está prohibido en esta VM
  (el daemon necesita TCP local) y no se pidió ningún permiso de puerto.

## Ideas futuras

- Subir a compileSdk 37 + Compose 1.12 / Coil 3.6 / OkHttp 5.x cuando
  Android 17 sea estable y AGP lo soporte.
- Migrar `actions/setup-java@v4` a `@v5` (aviso de deprecación en CI, no bloquea).
