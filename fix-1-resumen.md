# Resumen Fix 1 — Colección de enlaces v1.0.1

Versión 1.0.1 (versionCode 2, instala encima de la 1.0.0). Estado: **código
completo y verificado en local; pendiente de CI por un bloqueo de
facturación de GitHub (ver "Estado de CI" al final)**. Nada entregado a Javier.

## El defecto: sin vista previa en enlaces de Facebook

Javier guardó un enlace de Facebook a un vídeo de EterniCodIA
(`https://www.facebook.com/share/r/1JqVPz7de...`, título "Contratamos a una
IA... y el primer día ya vendía croquetas en Marte...") y la tarjeta quedó
sin miniatura ni título: solo el dominio.

### Causa probada (llamadas reales con curl, 2026-11-23)

Facebook devuelve un muro de login a cualquier petición sin sesión:

| Intento | Respuesta |
|---|---|
| URL /share/r/ con UA de la app (Chrome/126 Mobile), sin seguir redirects | HTTP 302 a `m.facebook.com/share/r/1JqVPz7de?wtsid=...` |
| Siguiendo redirects, UA móvil | Final `m.facebook.com/share/r/...`, HTTP 400, 3676 bytes, `<title>Error Facebook</title>`, **cero og tags** |
| Siguiendo redirects, UA de escritorio | Final `www.facebook.com/share/r/1JqVPz7de`, HTTP 400, 1542 bytes, `<title>Error</title>`, cero og tags |
| Página pública `facebook.com/EterniCodIA` | Redirect a `m.facebook.com/login/?next=...`, HTTP 400, título "Error Facebook" |
| `mbasic.facebook.com/share/r/...` | HTTP 400, título "Error Facebook" |

WhatsApp sí muestra la vista previa porque Meta deja entrar a su propio
rastreador; esta app no es Meta. **Prohibido** fingir ser el rastreador de
Meta (nunca se usa el UA `facebookexternalhit` ni ningún engaño de
identidad).

### Qué se probó y no funcionó

- **Microlink** (`api.microlink.io`, gratis sin clave, ~50/día): tampoco puede
  con Facebook. Devuelve título "Facebook", descripción "Explore the things
  you love", imagen null y URL final `facebook.com/login`: el mismo muro.
  No se añade ninguna dependencia de servicio de vistas previas.
- **Facebook Sharing Debugger**: solo sirve al dueño del sitio web que se
  comparte, no a una app que lee facebook.com.
- **yt-dlp** (probado por el padre, instalado 2026.10.26): contra
  `facebook.com/EterniCodIA` dio ERROR "Unable to download webpage: The read
  operation timed out". Además exigiría backend o embeber un runtime en el
  APK, contra la regla sin servidor. **No se usa.**

### La solución: oEmbed oficial sin token (idea de Javier, verificada)

Desde el 2026-06-15 Meta levantó el token para oEmbed: **sin token, sin
cuenta, sin clave**. Límite sin token: 1.000 peticiones por endpoint y hora.
Verificado con llamadas reales (2026-11-23). El mecanismo es **uno genérico
para todas las plataformas**: oEmbed oficial sin token → cargar el HTML del
reproductor en un WebView sin autoplay → capturar el cuadro del vídeo como
miniatura y guardarla en local.

| Proveedor | Endpoint oEmbed (sin token) | Devuelve |
|---|---|---|
| Facebook | `graph.facebook.com/v27.0/oembed_video` (respaldo `oembed_post`) | Solo `html` (reproductor fb-video + sdk.js v27.0), `width` 500. Sin título ni miniatura (los quitaron el 2025-11-03). `oembed_post` con URL de página da error 100 "Invalid parameter" (manejado: cae al respaldo). |
| YouTube | `www.youtube.com/oembed` | Título, `thumbnail_url` (`i.ytimg.com/.../hqdefault.jpg`) y HTML. Shorts funciona igual. |
| TikTok | `www.tiktok.com/oembed` | **Solo HTML** (iframe con `autoplay=1&muted=1`): sin título ni `thumbnail_url`, ni para vídeos existentes. Corrección a una afirmación anterior: TikTok NO devuelve título. Se le quita el autoplay (`autoplay=1` → `autoplay=0`) y se captura el cuadro. |
| Instagram | `graph.facebook.com/v27.0/instagram_oembed` | Solo HTML del incrustado (blockquote instagram-media + embed.js), `width` 658. Sin título ni miniatura. Probado con un reel público real. |
| X | `publish.twitter.com/oembed` | Solo HTML del tuit (blockquote twitter-tweet + widgets.js) y `author_name`. Sin título ni miniatura. Probado con un tuit público real (hace un 301 previo; OkHttp lo sigue). |
| Vimeo | `vimeo.com/api/oembed.json` | Título, `thumbnail_url` (`i.vimeocdn.com/...`) y HTML. |
| Dailymotion | `www.dailymotion.com/services/oembed` | Título, `thumbnail_url` (`s1.dmcdn.net/...`) y HTML. |

Solo funciona con contenido **público**; Javier aclaró que la mayoría de
enlaces guardados son Shorts, Reels y vídeos de TikTok públicos, así que
oEmbed cubre la mayoría de casos. Si oEmbed falla (contenido no público),
el enlace se guarda igual con el título automático (abajo) y el aviso
"Sin vista previa", sin pedirle nada al usuario.

### Miniaturas: captura del reproductor + guardado local

- YouTube, Vimeo y Dailymotion dan `thumbnail_url` directo: se usa directo
  (y se descarga a local al guardar).
- Facebook, TikTok, Instagram y X: el HTML del reproductor se carga en un
  WebView **sin autoplay** (`mediaPlaybackRequiresUserGesture = true`, y en
  TikTok el `autoplay=1` ya viene cambiado a `autoplay=0`): el vídeo NO se
  reproduce solo, solo se pinta su primer cuadro. Se espera 2 s a que se
  pinte; si el cuadro sale en blanco se reintenta una vez más a los 2 s; si
  sigue en blanco, el WebView queda visible como reproductor de respaldo.
- **La miniatura capturada se guarda en el almacenamiento local del teléfono
  en el momento de guardar el enlace** (`miniatura-<id>.png`), no se
  referencia la URL: las imágenes de Facebook (`fbcdn.net`) llevan firma
  temporal (`oe=...`) y se rompen a los días. Lo mismo para cualquier URL de
  imagen con firma temporal: se descarga a local al guardar.
- La captura del WebView y la migración de base de datos v1→v2 **solo se
  pueden verificar en un teléfono real** (ver "Qué queda por probar").

### Título 100% automático: la edición manual queda ELIMINADA

Por orden de Javier **no hay título ni descripción editables en ningún
lado**. El proceso es automático; nadie escribe títulos. El título efectivo
sale de la primera fuente que lo tenga, en este orden:

1. Título oEmbed (YouTube, Vimeo, Dailymotion).
2. Título de la vista previa (og:title), cuando el sitio deja leerla.
3. Primera línea del texto que acompañaba al enlace al compartirlo (el
   intent de compartir de Facebook trae el texto de la publicación; al
   pegar, el texto pegado junto a la URL).
4. Texto visible del reproductor oEmbed, leído automáticamente del WebView
   (autor y texto de la publicación; best-effort: vive dentro de un iframe
   de otro origen y el WebView no siempre lo puede leer; solo rellena lo que
   falta — título solo si aún es el dominio, descripción solo si está vacía—
   y reclasifica si cambia).
5. Último recurso: el dominio, como hacía antes.

La descripción es la de la vista previa o, si no hay, el texto compartido.
El diálogo de agregar es de un solo paso, sin campos de título/descripción
y sin aviso de "Sin vista previa" (la tarjeta muestra ese aviso cuando no
hay imagen). Para otros sitios, además: timeout 10 s → 15 s, reintento de
descarga hasta 2 veces (3 intentos, 500 ms de pausa) solo en fallos
transitorios (timeout, HTTP 5xx, IOException genérica; NO en 4xx, vista
previa vacía o sin internet), y filtro de títulos genéricos ("Error",
"Error Facebook", "Facebook", "Log into Facebook", "Inicia sesión en
Facebook", etc.: nunca se guardan como título).

## Punto 1 — Modo claro: el degradado se queda, mucho más claro

Diagnóstico corregido por Javier (captura con círculo rojo): toda la zona
superior en tema claro se veía apagada y oscura porque el fondo degradado
era un gris demasiado oscuro. La causa era el degradado, no los textos
sueltos. **El degradado NO se quita** (corrección posterior de Javier: nada
de fondo plano casi blanco); se aclara bastante y el título va con color
vivo. El modo oscuro no se toca.

| Elemento | Antes | Después |
|---|---|---|
| Degradado superior claro | `primary.copy(alpha = 0.14f)` translúcido sobre el fondo de ventana (oscuro): medido `#5C596E` detrás del título | `DegradadoSuperiorClaro = Color(0xFFE9E6FF)` opaco (lavanda muy claro) cuando `background.luminance() > 0.5f`; oscuro igual que antes |
| Título "Colección" | `#191A2E` sobre `#5C596E`: 2.53:1 (ilegible) | `colorScheme.primary` vivo: 5.02:1 sobre el degradado, 5.66:1 sobre el fondo; oscuro `#9D8CFF` 6.92:1 |
| Subtítulo | `#55576E` sobre `#5C596E`: 1.05:1 (lo que vio Javier) | `#55576E` sin cambiar: 5.79:1 sobre el degradado, 6.53:1 sobre el fondo |
| Barra de búsqueda | `surfaceVariant #E9E7F8` indistinguible del degradado claro | Blanca (`surface`) en claro, igual en oscuro |

Contrastes medidos (WCAG AA para texto normal: 4.5:1): subtítulo 5.79:1 /
6.53:1, título claro 5.02:1 / 5.66:1, título oscuro 6.92:1, texto de
búsqueda 14.04:1, placeholder 7.06:1. **Verificación (b)**: el subtítulo
tenía contraste real suficiente sobre el fondo previsto (`#F6F5FF`,
6.53:1); lo que fallaba era el fondo degradado, ya corregido. No se cambió
el color del subtítulo.

## Punto 2 — Categorías en menú lateral deslizante (modelo Muse)

Sustituye a la fila horizontal de chips, que se cortaba en el borde. El
contador total y la búsqueda se quedan en la pantalla principal.

- **Botón del cajón** (decisión delegada por Javier, con su condición de
  que la gente entienda que se despliega de izquierda a derecha):
  (1) ícono de hamburguesa Material 3 estándar arriba a la izquierda;
  (2) la palabra visible "Categorías" junto al ícono; (3) cajón modal
  estándar que desliza de izquierda a derecha, con gesto desde el borde
  izquierdo; (4) la primera vez que se abre la app, el cajón se abre solo
  brevemente una vez como pista (400 ms de espera, 1800 ms visible, marca
  guardada en SharedPreferences `coleccion_preferencias`).
- **Contenido**: "Todos · N", "Favoritos", las 17 categorías + carpetas
  propias en vertical (círculo de color + ícono, nombre, contador, candado
  si bloqueada), "Nueva carpeta". Todos / Favoritos / categoría son vistas
  excluyentes. Pulsación larga sobre carpeta propia abre su gestión.
- **Bloqueo por huella**: se mantiene por categoría y el menú lo respeta:
  las filas solo llevan nombre, ícono, color y contador (nunca títulos ni
  contenido de enlaces); tocar una bloqueada pide la huella antes de
  seleccionarla.
- **Sección "Acerca de" al final** (lo único tomado del cajón de Reddit;
  Javier corrigió después que la estructura completa de Reddit quedaba
  descartada: NADA de "Visto recientemente" ni otras secciones): título
  de sección + tres elementos, cada uno abre su pantalla dentro de la app
  (ruta `legal/{tipo}` en el NavHost) con el texto completo y real:
  - **Política de privacidad** (2012 caracteres): sin cuentas, no recoge
    datos personales, sin analítica, sin anuncios ni rastreadores; solo
    guarda en el teléfono lo que el usuario guarda; conexiones solo al
    guardar un enlace (vista previa y oEmbed, solo viaja la URL); huella
    solo local; menores; sin cambios (versión única).
  - **Acuerdo del usuario** (1180 caracteres): tus enlaces son tuyos, sin
    copia de seguridad en la nube (si desinstalas se pierde todo), vistas
    previas según cada sitio, uso aceptable, sin cambios.
  - **Eliminación de datos** (869 caracteres): borrar un enlace (pulsación
    larga → Borrar), borrar una carpeta (sus enlaces pasan a "Otros"),
    "Borrar todos los datos" en Ajustes → Aplicaciones → Colección de
    enlaces → Almacenamiento, desinstalar (se borra todo, no hay copia
    fuera del teléfono), sin vuelta atrás.

## Verificación (a) — "Sin resultados" solo con filtro activo

Revisado en código (`HomeScreen` + `HomeViewModel`): la lógica era
correcta. En la primera captura el "Sin resultados" aparecía con el chip
"Cocina · 0" seleccionado (claro) y "Favoritos" seleccionado (oscuro): en
ambos casos había filtro activo, así que era correcto. La condición pasó de
estar repetida en el composable a ser la propiedad derivada
`HomeUiState.hayFiltroActivo` (búsqueda no vacía, categoría seleccionada o
solo favoritos), que `EstadoVacio` usa para elegir entre "Sin resultados" y
"Tu colección está vacía". Pruebas nuevas: 1 enlace sin filtro → lista no
vacía y sin filtro activo; búsqueda sin coincidencias → vacía con filtro
activo; categoría sin enlaces → vacía con filtro activo; solo favoritos sin
favoritos → vacía con filtro activo; sin filtro y sin enlaces → sin filtro
activo.

## Archivos (Fix 1 + puntos 1/2/3)

Producción: `data/RepositorioEnlaces.kt`, `data/ServicioOEmbed.kt`,
`data/ServicioVistaPrevia.kt`, `data/Enlace.kt`, `data/AppDatabase.kt`
(migración v1→v2), `domain/OEmbed.kt`, `util/ExtraerUrl.kt`,
`ui/home/HomeViewModel.kt`, `ui/home/DialogoAgregarEnlace.kt`,
`ui/home/MiniaturaWebView.kt` (nuevo), `ui/home/TarjetaEnlace.kt`,
`ui/home/HomeScreen.kt`, `ui/home/MenuLateral.kt` (nuevo),
`ui/home/PreferenciasMenu.kt` (nuevo), `ui/legal/TextosLegales.kt`
(nuevo), `ui/legal/PantallaLegal.kt` (nuevo), `MainActivity.kt`,
`di/AppContainer.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`,
`res/values/strings.xml`, `app/build.gradle.kts` (1.0.1, versionCode 2).
Pruebas: `OEmbedTest`, `ServicioOEmbedTest`, `RepositorioEnlacesTest`,
`HomeViewModelTest`, `Migracion12Test`, `ExtraerUrlTest`,
`MenuLateralTest`, `PreferenciasMenuTest`, `TextosLegalesTest`,
`ParseadorVistaPreviaTest`, `ServicioVistaPreviaTest`.

## Estado de CI

| Run | Commit | Resultado |
|---|---|---|
| 45158546985 | lote 1 | SUCCESS (5m35s; primer intento falló por 429 de Maven Central en `org.robolectric:nativeruntime-dist-compat:1.0.16.jar`, se arregló con `gh run rerun --failed`) |
| 45160107827 | d702b98 | FAILURE: 1 test (`ExtraerUrlTest` token de puntuación) |
| 45160938020 | 70991b1 | SUCCESS |
| 45162308451 | 35d6bc1 (C') | FAILURE: compilación — `HomeScreen` llamaba al diálogo viejo de dos pasos |
| 45162885547 | 33681ad (D') | FAILURE: 4 tests (2× `actualizarTextoDesdeWebView` por falta de la palabra "ia", aún no añadida; `crearCarpeta hasta el maximo de 20`, flaky conocido; `abrirDialogoAgregar`, cascada del anterior) |
| 45164285011 | 85304f5 (Fix 2 núcleo) | **SUCCESS** (248 tests; los 4 fallos anteriores desaparecieron) |
| 45165320874 | 5e65928 ("Acerca de") | **FAILURE sin compilar: el job no arrancó** — "recent account payments have failed or your spending limit needs to be increased" (límite de gasto de GitHub). Reintentado una vez: mismo fallo. **Bloqueo de facturación, no de código.** |

**Pendiente por el bloqueo**: los commits locales `d79a65b` (diccionario) y
`a230ac7` (verificación a) están commiteados en local y **no se han subido**
para no encadenar runs que no pueden arrancar; el commit `5e65928`
("Acerca de") está subido pero sin verificación de CI. En cuanto se
resuelva la facturación hay que: subir `d79a65b` y `a230ac7`, esperar el CI
en verde, descargar el artefacto `coleccion-debug` y verificarlo con
`aapt2` + `apksigner verify` + `sha256sum`. **El APK 1.0.1 aún no está
verificado: no hay artefacto.**

## Qué queda por probar en el teléfono real de Javier

- Captura del reproductor en WebView para un enlace de cada plataforma
  (Facebook, YouTube, TikTok, Instagram, X, Vimeo, Dailymotion): que la
  miniatura aparezca en la tarjeta y quede guardada en local.
- Lectura del texto visible del reproductor (título automático desde el
  WebView) con el enlace de EterniCodIA.
- Migración v1→v2 sobre la base instalada (v1.0.0): que sus enlaces y
  carpetas sobrevivan.
- Cajón: apertura con el botón "Categorías", gesto desde el borde, pista en
  el primer inicio (solo una vez), bloqueo por huella desde el menú,
  pantallas de "Acerca de".
- Tema claro: degradado luminoso, título vivo, subtítulo legible.
