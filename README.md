# Colección de enlaces

Aplicación Android para guardar enlaces en una colección personal, con vista
previa automática (imagen, título y descripción), clasificación automática por
categoría y carpetas propias protegidas con huella dactilar.

- **Idioma de la interfaz:** español.
- **Paquete:** `com.coleccionenlaces.app` — versión 1.0.0.
- **Android mínimo:** API 26. **Objetivo:** API 36.
- **Sin servidor, sin cuenta, sin registro, sin nube, sin analítica ni
  publicidad.** Todos los datos se guardan únicamente en el teléfono
  (base de datos Room). La app descarga la página del enlace directamente
  desde el teléfono para obtener la vista previa; eso requiere internet,
  pero no requiere ningún backend.
- **Solo fuentes del sistema:** la app no empaqueta ninguna fuente
  (sin archivos `.ttf` ni `.otf` en el APK).

## Qué hace

- **Agregar enlace:** pegando la URL con el botón «+ Agregar enlace», o
  recibiéndola desde el menú de compartir de Android (WhatsApp, Chrome,
  YouTube, etc., texto plano). El enlace compartido nunca se guarda en
  silencio: se abre el mismo diálogo prellenado para confirmarlo.
- **Vista previa automática:** imagen, título y descripción de la página
  (`og:title`, `og:description`, `og:image`, con respaldo a `<title>`,
  `meta description` y primera imagen). Si el sitio no tiene vista previa,
  el enlace se guarda igual, con el dominio como título y un ícono de
  respaldo. Si no hay internet o la página tarda más de 10 segundos, se
  guarda igual y no se rompe el flujo. Solo una URL inválida impide guardar.
- **Clasificación automática:** cada enlace se clasifica solo en una
  categoría (ver «Clasificación» más abajo). El usuario puede cambiar la
  categoría desde el menú de la tarjeta; esa elección manual tiene
  prioridad y no se sobrescribe jamás.
- **Cuadrícula agrupada por categoría:** dos columnas en teléfono, tres o
  más en tablet, con encabezados de sección y contador por categoría.
- **Filtros:** fila de chips por categoría (con su color, ícono y
  contador), chip «Todos» y chip «Favoritos» (solo favoritos).
- **Búsqueda:** por texto en título, descripción y nombre de categoría.
- **Abrir enlace:** al tocar una tarjeta se abre en el navegador o en la
  app correspondiente.
- **Favoritos:** desde el ícono de la tarjeta o desde el menú.
- **Menú de tarjeta (pulsación larga):** cambiar categoría, favorito,
  compartir el enlace y borrar con confirmación.
- **Contador por categoría:** en cada chip y en cada encabezado de sección,
  además del contador total en el encabezado.
- **Carpetas propias:** el usuario puede crear sus propias carpetas
  (nombre, color e ícono) y mover enlaces a ellas desde el menú de la
  tarjeta. Máximo 20 categorías en total (17 predefinidas + 3 propias como
  máximo). Las carpetas propias se renombran y se borran con pulsación
  larga sobre su chip; al borrar una, sus enlaces pasan a «Otros».
- **Huella por carpeta:** cada carpeta se abre con la huella dactilar (o
  la credencial del dispositivo). Mientras una carpeta está bloqueada no
  se ve nada de su contenido — ni títulos, ni descripciones, ni imágenes,
  ni URLs — ni en la cuadrícula, ni en la búsqueda, ni en favoritos; solo
  se ve su nombre, su ícono y la cantidad de enlaces. El desbloqueo dura
  solo la sesión: al cerrar la app, todo vuelve a estar bloqueado.
- **Claro / oscuro:** interruptor en el encabezado (sol ↔ luna). El tema
  oscuro es el predeterminado y la elección se guarda en el teléfono.

## Cómo compilar

### En GitHub Actions (CI, la vía verificada)

Cada push o pull request a `main` ejecuta el workflow
`.github/workflows/build.yml`:

1. JDK 17 (Temurin).
2. Genera el archivo de palabras clave sensibles desde el secreto
   `XXX_KEYWORDS_B64` (ver «Clasificación»).
3. Instala el Android SDK (API 36, build-tools 36.0.0).
4. Corre `./gradlew testDebugUnitTest` (156 pruebas unitarias).
5. Corre `./gradlew assembleDebug` y sube el APK como artefacto
   `coleccion-debug`.

El APK de depuración queda como artefacto del run en la pestaña
*Actions* del repositorio.

### En una máquina normal

Requisitos:

- **JDK 17** (Temurin u otro JDK 17).
- **Android SDK** con la plataforma **API 36** y **build-tools 36.0.0**
  (variable `ANDROID_SDK_ROOT` o `ANDROID_HOME` apuntando al SDK).
- No hace falta instalar Gradle: el proyecto incluye el wrapper
  (`gradlew`, Gradle **8.14.3**).

```bash
./gradlew testDebugUnitTest   # pruebas unitarias
./gradlew assembleDebug       # compila el APK de depuración
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Librerías

Todas las versiones están en `gradle/libs.versions.toml` (única fuente).
Versiones estables y verificadas a 2026-11-23:

| Librería | Versión | Uso |
| --- | --- | --- |
| Android Gradle Plugin | 8.13.2 | Compilación |
| Kotlin | 2.3.21 | Lenguaje |
| KSP | 2.3.10 | Procesador de Room |
| Gradle (wrapper) | 8.14.3 | Compilación |
| Compose BOM | 2026.05.00 | UI (Compose + Material 3) |
| Navigation Compose | 2.9.8 | Navegación |
| Room | 2.8.4 | Base de datos local |
| Lifecycle (runtime, viewmodel) | 2.10.0 | ViewModel y StateFlow |
| Activity Compose | 1.13.0 | Actividad Compose |
| Core KTX | 1.17.0 | Extensiones de AndroidX |
| Coil (+ network-okhttp) | 3.5.0 | Imágenes (`AsyncImage`) |
| OkHttp | 4.12.0 | Descarga de páginas |
| Jsoup | 1.24.1 | Lectura de HTML |
| Kotlin Coroutines | 1.11.0 | Concurrencia |
| SplashScreen | 1.2.0 | Pantalla de inicio |
| Biometric | 1.1.0 | Huella por carpeta (única librería extra fuera del plan base) |
| Fragment KTX | 1.8.8 | `FragmentActivity` que exige `BiometricPrompt` |
| Material Icons Extended | (gestionada por el BOM) | Íconos de las 17 categorías |
| JUnit | 4.13.2 | Pruebas |
| Robolectric | 4.14.1 | Pruebas de DAO en la JVM |
| androidx-test core | 1.6.1 | Pruebas |

Notas de versión: las versiones más nuevas de Compose (1.12), Lifecycle
(2.11), Coil (3.6) y OkHttp (5.x) exigen `compileSdk` 37 (plataforma en
vista previa); por eso se usan las últimas estables con `compileSdk` 36.
`core-ktx` queda en 1.17.0 a propósito: en 1.18.0 la compatibilidad con
huella en API 29 e inferiores se rompió.

## Clasificación

La clasificación es una función pura y local, sin red:
`Clasificador.clasificar(titulo, descripcion, url)` devuelve el nombre de
la categoría (`app/src/main/java/com/coleccionenlaces/app/domain/Clasificador.kt`).

### Puntaje

- Cada palabra o frase clave distinta que aparece en el título o en la
  descripción suma **2 puntos** a su categoría (una misma frase cuenta
  una sola vez aunque se repita).
- El dominio de la URL suma **4 puntos**, pero solo para dominios
  claramente temáticos. Un dominio genérico (buscador, red social, portal
  de videos) no suma nada.
- Gana la categoría con más puntos. En empate gana la primera en el orden
  de las categorías predefinidas (determinista).
- **Umbral mínimo: 2 puntos.** Si ninguna categoría llega a 2, el
  resultado es «Otros». Una sola palabra clave ya clasifica.
- Normalización: minúsculas, sin acentos y puntuación convertida en
  espacios; la coincidencia exige la frase completa entre límites de
  palabra (por ejemplo, «moto» no coincide con «motocicleta»). Las listas
  usan frases específicas, no palabras genéricas, para que los casos
  ambiguos caigan en «Otros».

### Archivos de palabras clave

- `app/src/main/java/com/coleccionenlaces/app/domain/PalabrasClave.kt`:
  las listas de palabras clave y dominios temáticos de las 16 categorías
  no sensibles, en español.
  - `PalabrasClave.porCategoria`: mapa de nombre de categoría a lista de
    palabras y frases clave.
  - `PalabrasClave.dominiosPorCategoria`: mapa de nombre de categoría a
    lista de dominios temáticos.

### Cómo agregar palabras clave

1. Abre `PalabrasClave.kt`.
2. Añade la palabra o frase (en minúsculas, con acentos, tal como se
   escribe) a la lista de su categoría en `porCategoria`, o el dominio
   (sin `www.`, por ejemplo `ejemplo.com`) en `dominiosPorCategoria`.
3. Añade una prueba en `ClasificadorTest.kt` con un título o descripción
   que contenga la frase, y comprueba que clasifica en esa categoría.
4. Corre `./gradlew testDebugUnitTest` (o deja que el CI lo verifique).

Consejos: usa frases específicas («receta de pollo», no solo «pollo» si
la palabra es ambigua); una palabra dentro de otra no coincide, así que
no hace falta evitar prefijos; si una categoría se clasifica mal con
frecuencia con enlaces reales, añade sus frases típicas.

### Archivo de palabras sensibles (categoría «Contenido XXX»)

La lista de palabras clave de la categoría sensible vive en un archivo
separado, `app/src/main/java/com/coleccionenlaces/app/domain/XxxKeywords.kt`,
que **nunca entra en el repositorio**: está en `.gitignore` y solo existe
en la copia de trabajo local. En CI, el workflow lo genera antes de
compilar decodificándolo desde el secreto de GitHub `XXX_KEYWORDS_B64`
(base64 del contenido del archivo). El workflow no imprime el contenido
del secreto; si el secreto no existe, el build falla (comportamiento
correcto). Su contenido no se reproduce en ningún reporte ni documento.

## Categorías predefinidas (17, máximo 20)

1. Cocina (recetas y videos juntos)
2. Videos de mecánica
3. Videos de construcción
4. Contenido XXX
5. Entretenimiento (música, películas y series, fútbol y deportes)
6. Tecnología y celulares
7. Autos y motos
8. Salud y ejercicio
9. Dinero y finanzas
10. Noticias
11. Juegos
12. Hazlo tú mismo (DIY)
13. Jardín y plantas
14. Pesca y campo
15. Moda y ropa
16. Viajes
17. Otros (se usa cuando ninguna categoría coincide)

Cada categoría tiene su propio color de acento y su propio ícono, usados
en chips, encabezados de sección y etiquetas de tarjeta. Quedan 3 libres
hasta el máximo de 20, que el usuario llena con sus carpetas propias.
No se crean categorías automáticamente.

## Estructura del proyecto

```
app/src/main/java/com/coleccionenlaces/app/
├── MainActivity.kt            # Actividad principal, SplashScreen, edge-to-edge
├── ColeccionApplication.kt    # Application, posee el contenedor de inyección
├── di/AppContainer.kt         # Inyección manual (sin Hilt ni Koin)
├── data/                      # Room: Enlace, Categoria, DAOs, base, repositorio
├── domain/                    # Clasificador, palabras clave, vista previa, bloqueo
├── ui/home/                   # Pantalla principal, tarjetas, diálogo, menú, tema
└── util/                      # Extracción de URL, intents de abrir/compartir
```

Arquitectura: un ViewModel por pantalla (`HomeViewModel`, con un único
`StateFlow` de estado), repositorio como única fuente de datos, sin
lógica de negocio en los composables.

## Pruebas

156 pruebas unitarias (`./gradlew testDebugUnitTest`), todas en verde en
CI: DAO de enlaces y de categorías (Room en memoria con Robolectric),
parseo y servicio de vista previa (HTML local, sin internet),
clasificación (29 casos, incluidos ambiguos que deben caer en «Otros»),
repositorio, ViewModel, extracción de URL, intents, bloqueo por carpeta,
carpetas propias y preferencias de tema.

## Ideas futuras

Ideas anotadas durante la construcción, fuera del alcance de esta entrega:

- Subir a `compileSdk` 37 + Compose 1.12 / Coil 3.6 / OkHttp 5.x cuando
  Android 17 sea estable y AGP lo soporte.
- Pruebas de migración de Room (con `room-testing`, ya agregado) cuando
  la base suba a la versión 2.
- Aceptar también `og:image:secure_url` y `twitter:image` como respaldo
  de imagen cuando no hay `og:image`.
- Seguir redirecciones con límite y rechazar Content-Type no HTML antes
  de parsear.
- Ampliar las listas de palabras clave y dominios con los enlaces reales
  que el usuario guarde.
- Contar también las palabras del dominio y de la ruta de la URL (hoy
  solo el host suma puntos por dominio).
- Ofrecer `reclasificarEnlace` desde la UI cuando el usuario edite el
  título de un enlace no manual.
- Incluir las categorías con cero enlaces como entradas explícitas en el
  mapa de contadores.
- Contador de favoritos en el chip «Favoritos».
- Usar `collectAsStateWithLifecycle` en lugar de `collectAsState`.
- Al pegar desde el portapapeles, si el texto trae varias URLs, ofrecer
  elegir cuál (hoy se pega solo la primera).
- En el diálogo de agregar, mostrar una mini vista previa (título) antes
  de guardar cuando la descarga ya terminó.
- Aceptar también enlaces compartidos como `EXTRA_STREAM` o con un
  `mimeType` distinto de `text/plain` (hoy solo texto plano).
- En el menú de tarjeta, mostrar también el contador de enlaces de cada
  categoría junto a su nombre, y permitir cambiar la categoría sin cerrar
  el menú.
- Pedir huella también para borrar o renombrar una carpeta bloqueada.
- Ocultar también los contadores y el total de las carpetas bloqueadas.
- Al crear una carpeta, desbloquearla automáticamente en la sesión.
