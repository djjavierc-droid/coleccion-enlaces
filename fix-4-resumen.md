# Fix 4 — Ronda de corrección v1.0.3

Fecha: 2026-11-23. Commit: `162f7ff` ("Fix 4 (v1.0.3)..."). Versión: versionCode **4** / versionName **1.0.3** (solo `app/build.gradle.kts`).
Agente: subagente Fix 4. Origen: Javier probó la v1.0.2 en su POCO y reportó 4 cosas (3 capturas leídas por el padre).

> La modificación local sin commitear de `fix-3-resumen.md` (sección 6, v1.0.2, run 45193562687) NO se perdió: se incluyó en este mismo commit `162f7ff`.

## 0. Censo por OmniRoute (obligatorio)

Preguntas: (a) por qué falla la vista previa de Facebook en `/share/r/` y cómo capturar el cuadro real; (b) por qué el clasificador confunde un video de juego con tecnología cuando el único texto es el dominio.
Solo OmniRoute (`~/workspace/omni-client.mjs`, `http://localhost:20128/v1`); ningún proveedor directo.
Respuestas crudas en `censo-fix4.json`. Resultado: **5 de 8 llamadas respondieron**.

| Modelo | Resultado |
|---|---|
| `ddgw/claude-haiku-4-5` | OK (2.267 caracteres) |
| `ddgw/gpt-5.4-mini` | OK (1.720) |
| `groq/qwen/qwen3.8-27b` | OK (5.303) |
| `nvidia/nvidia/nemotron-3.5-lightning-30b-a3b` | OK (578) |
| `auto/best-fast` (reintento) | OK (5.647) |
| `auto/best-free` | FALLO timeout 120 s |
| `cohere/command-a-03-2025` | FALLO HTTP 429 (límite de tasa) |
| `cohere/command-r-08-2024` (reintento) | FALLO HTTP 429 (límite de tasa) |

**Consenso (5 modelos):**
- (A) `/share/r/` es una URL corta de redirección/intersticial; oEmbed solo devuelve el HTML del reproductor, sin título ni miniatura; el WebView pinta la página de error y el temporizador fijo de 2 s captura lo que haya pintado; `innerText` no tiene título porque la página de error no lo trae. Arreglo acordado: resolver la URL canónica, verificar que el reproductor cargó de verdad antes de capturar, detectar el texto de error y no guardar esa captura, y mostrar un marcador propio ("Video no disponible") si el video es privado o eliminado.
- (B) Un dominio solo ("facebook.com") es señal casi vacía: el modelo decide por priors/frecuencia de clase y cae en Tecnología; Juegos estaba infrarrepresentada. Arreglo acordado: más ejemplos reales de juegos (+ contrapesos) y NO alimentar al modelo con un dominio solo: devolver "Otros" o dejar decidir a la corrección manual.

**Dos afirmaciones del censo que la medición NO confirma (se siguió la evidencia medida, no el censo):**
1. Groq y `auto/best-fast` dijeron que oEmbed con la URL corta `/share/r/` devuelve error o un reproductor vacío. Medido con curl 2026-11-23: `graph.facebook.com/v27.0/oembed_video?url=<corta>` responde **200** con el HTML del reproductor (`div fb-video data-href=<corta>` + SDK). oEmbed NO es el punto de fallo.
2. Groq y `auto/best-fast` propusieron verificar `document.querySelector('video').readyState` en el documento principal. El reproductor de Facebook vive dentro de un iframe (otro origen): el documento principal no ve su `<video>`. La señal que SÍ se puede leer es `document.body.innerText` (la que ya usaba la app), y sobre ella se hace la detección de error.

## 1. Bug 1 — Cajón no desplaza

**Diagnóstico (código, no medición de red):** `MenuLateral.kt` ponía TODO el contenido en un `Column` sin scroll dentro de `ModalDrawerSheet`. El cajón tiene 17 categorías + carpetas propias + "Nueva carpeta" + "Acerca de" (3 entradas): en la pantalla del POCO las filas a partir de "Hazlo tú mismo (DIY)" quedaban cortadas en el borde inferior y "Acerca de" era inalcanzable. Exactamente lo que muestran las capturas 1 y 2.

**Arreglo (`ui/home/MenuLateral.kt`):**
- Contenido como lista pura de filas: `filasMenuLateral(categorias, contadores, desbloqueadas): List<FilaMenuLateral>` (sealed interface: Encabezado, Todos, Favoritos, Separador, Categoria, NuevaCarpeta, TituloAcercaDe, Legal).
- El composable dibuja esa lista con `LazyColumn` + `itemsIndexed` (clave = índice; los separadores repetidos no admiten clave por contenido). Nada más cambió: mismo orden, mismos colores/íconos/contadores/candados y misma gestión por pulsación larga.

**Prueba (`MenuLateralTest`, nueva):** `las filas del menu incluyen las 17 categorias las propias y Acerca de` — con 17 predefinidas + 2 propias: 19 filas de categoría en orden, 3 entradas legales al final tras el título "Acerca de", "Nueva carpeta" entre categorías y "Acerca de", y Todos/Favoritos presentes.

## 2. Bug 2 — Clasificador confunde juego con tecnología

### 2.1 Diagnóstico con el modelo real (reproducido)

Entrada EXACTA que la app le dio al modelo para el enlace del juego: título `"facebook.com"` (respaldo por dominio: Facebook no dio título), descripción `""` → texto `"facebook.com"`. Pasado por `entrenamiento/modelo_clasificador.tflite` (el mismo .tflite de la v1.0.2), con la misma tokenización:

| Texto (modelo v1.0.2) | Resultado |
|---|---|
| `facebook.com` | **Tecnología y celulares 0,624** (Juegos 0,222) — el bug, reproducido |
| `youtube.com` | Juegos 0,674 (también espurio: debería ser "Otros") |
| `tiktok.com` | DIY 0,270 (confianza baja, caería en "Otros" por el umbral) |
| `Nuevo juego para celular gratis` | **Tecnología 0,931** (Juegos 0,032) — el mecanismo real: "celular" solo aparecía con Tecnología en entrenamiento, nunca con Juegos |
| `La IA juega Minecraft mejor que yo` | Juegos 0,538 (Tecnología 0,384) — justo |
| `Gameplay de Minecraft...` | Juegos 0,975 |
| EterniCodIA (título+desc) | Tecnología 0,873 |

Causa raíz, en dos capas:
1. **El modelo nunca vio dominios sueltos en entrenamiento** ("Otros" no es una clase: es el respaldo por baja confianza). Un dominio solo es texto fuera de distribución y el modelo responde con su prior: Tecnología.
2. **"celular/móvil/Android/IA" solo co-ocurrían con Tecnología.** Un video de juego real suele mencionar el celular donde se juega; el modelo aprendió celular→Tecnología y eso aplasta la señal de "juego".

Además, el camino de aprendizaje tenía DOS defectos que impedían que el movimiento manual de Javier sirviera:
- (a) `ClasificadorInteligente` consultaba el modelo PRIMERO: con 0,624 ≥ umbral 0,5 devolvía Tecnología sin consultar jamás a la cabeza aprendida. La corrección manual nunca llegaba a hablar para este texto.
- (b) `AprendizajeDao.guardarPesos` era `@Insert` sin estrategia de conflicto (ABORT por defecto) pese a que su propio comentario decía "(una sola fila; REPLACE)": la PRIMERA corrección persistía bien, pero la SEGUNDA chocaba con la PK id=1 y lanzaba `SQLiteConstraintException`, tragada por el `catch` de `registrarCorreccion`. La cabeza en memoria aprendía, pero al reiniciar la app se perdía todo lo aprendido después de la primera corrección.

### 2.2 Arreglo (lado app)

- `domain/MotorClasificacion.kt`: `esTextoSoloDominio(texto)` — un único token con al menos un punto, sin espacios ni ruta (`facebook.com`, `www.youtube.com`). Si el texto es solo un dominio, el modelo NO decide: solo la cabeza aprendida (la corrección manual de Javier) y, si no ha aprendido nada sobre ese dominio, "Otros". Un título real con palabras sigue el camino normal (modelo → cabeza → "Otros").
- `data/Aprendizaje.kt`: `guardarPesos` ahora `@Insert(onConflict = REPLACE)`.

Simulación exacta del algoritmo de la cabeza (cubos de "facebook"=710 y "com"=385, sin colisión): un paso en línea deja "Juegos" como argmax con confianza 0,23 (por debajo del umbral 0,6 del motor); tras el reentrenamiento en reposo (10 épocas) sube a ~0,64 y supera el umbral. Es decir: la corrección manual se nota en el acto en la cabeza y se aplica al clasificar tras el reentrenamiento en reposo. Límite honesto: una sola corrección de un texto de 2 palabras no supera el umbral 0,6 por sí sola; hacen falta el reentrenamiento o más correcciones.

### 2.3 Arreglo (reentrenamiento)

`entrenamiento/generar_datos.py` (mismo pipeline `entrenar.py`, base DistilBERT local, 100% offline):
- Juegos: +30 títulos realistas que mencionan celular/móvil/Android/iPhone/IA ("Nuevo juego para celular gratis", "Gameplay en el móvil: el juego del momento", "La IA juega Minecraft mejor que yo", ...).
- Tecnología: +10 contrapesos con celular/móvil SIN juego ("El celular con mejor cámara de 2026", "Trucos de Android que no conocías", ...).
- Datos nuevos: entrenamiento 2.885 (antes 2.825), validación 328 (antes 320). Juegos 225 ejemplos (antes 165); Tecnología 315 (antes 270). El texto EXACTO de EterniCodIA sigue excluido del entrenamiento (filtro `TEXTO_PRUEBA` verificado).

**Resultados del modelo nuevo (rango dinámico, NO INT8 completo):**
- Entrenamiento TF: validación **0,9665** (antes 0,9531); pérdida 0,2037. Épocas 1→3: val_accuracy 0,4604 → 0,8476 → 0,9665.
- Conversión `convertir.py`: VERIFICACIÓN PASA. EterniCodIA → **Tecnología y celulares 0,936**. Validación con umbral 0,5: 308/328 = **0,939**. Genéricos en "Otros": 10/20.
- 12/12 casos de verificación con el .tflite nuevo (script `/tmp/verificar-fix4.py`, no commiteado):

| Caso | Resultado nuevo (antes) |
|---|---|
| EterniCodIA título+desc | Tecnología 0,936 ✓ |
| EterniCodIA solo título | Tecnología 0,984 ✓ |
| "Nuevo juego para celular gratis" | **Juegos 0,975** (antes Tecnología 0,931) |
| "Gameplay en el móvil: el juego del momento" | Juegos 0,991 |
| "La IA juega Minecraft mejor que yo" | Juegos 0,925 (antes 0,538) |
| "Juego gratis para Android..." | Juegos 0,979 |
| "Gameplay de Minecraft..." | Juegos 0,988 |
| "El juego de fútbol para móvil..." | Juegos 0,978 |
| "El celular con mejor cámara de 2026" | Tecnología 0,968 ✓ (contrapeso) |
| "Trucos de Android que no conocías" | Tecnología 0,989 ✓ |
| "Contratamos a una IA para vender croquetas en Marte" (variación) | Tecnología 0,946 ✓ |
| "Mi celular nuevo: primera impresión" | Tecnología 0,953 ✓ |

- Los dominios solos SIGUEN sin poder decidirlos el modelo (`facebook.com` → Tecnología 0,570; `youtube.com` → Juegos 0,822): por eso existe el atajo en la app (2.2), que es lo que realmente protege este caso. No se intentó "enseñar" dominios al modelo: "Otros" no es una clase entrenable y etiquetar "facebook.com"→"Juegos" contaminaría el modelo (ese dominio no ES un juego).

Nuevo `.tflite` en `app/src/main/assets/modelo_clasificador.tflite`: 67.823.088 bytes, SHA-256 `c4053c8a5409023116f3aa6aad54fe772664494eb05109080badeba8bca4efc5` (= el verificado en Python). Rango dinámico, como en Fix 3.

**Pruebas:**
- `ClasificadorInteligenteTest` (4 nuevas): `esTextoSoloDominio` (8 casos), título solo-dominio → "Otros" sin cabeza, → "Juegos" con cabeza entrenada (el movimiento de Javier), y título real sigue el camino normal.
- `RepositorioEnlacesTest` (1 nueva): `mover un enlace a mano guarda la correccion y los pesos en Room` — el enlace `facebook.com` movido de Tecnología a Juegos: queda movido + manual, 1 corrección guardada, pesos en Room no nulos, argmax de la cabeza = "Juegos"; un SEGUNDO movimiento también persiste (cubre el bug REPLACE); tras `reentrenarEnReposo()` la confianza es ≥ 0,6; y una cabeza nueva cargada SOLO desde los pesos de Room predice "Juegos".

## 3. Bug 3 — Vistas previas siguen sin mostrarse

### 3.1 Diagnóstico con enlaces públicos reales (curl, 2026-11-23)

Enlace de prueba real usado en los tests: `https://www.facebook.com/share/r/1JqVPz7de`. (Los IDs exactos de las capturas de Javier venían truncados en el reporte — `1DbnJLtb...` y `1Bo58NQ...` — así que el diagnóstico se hizo con el `/share/r/` real del repo; el comportamiento es el mismo para cualquier `/share/r/`.)

1. **Redirección:** `GET /share/r/1JqVPz7de` → **302** a `https://m.facebook.com/share/r/1JqVPz7de?wtsid=...` → **400 Bad Request** (muro de login, sin og tags, título vacío). La URL "canónica" pública NO es un `/watch/` ni un `/reel/`: sigue siendo un enlace de compartir. La resolución por sí sola NO arregla la vista previa.
2. **oEmbed con la URL corta: FUNCIONA.** `graph.facebook.com/v27.0/oembed_video?url=<corta>` → **200** con el HTML del reproductor (`div fb-video data-href=<corta>` + SDK JS), sin título ni miniatura. El fallo NO está en oEmbed.
3. **El fallo está en el WebView:** el SDK carga el reproductor y Facebook sirve dentro la página "Video no disponible". `MiniaturaWebView` capturaba a los 2 s fijos cualquier cuadro NO en blanco (`esBitmapEnBlanco` era el único filtro) y lo guardaba como miniatura local PNG. La página de error no es en blanco → se guardó como si fuera la miniatura. Además `innerText` solo enriquecía el título; nada lo consultaba antes de guardar.
4. El título "facebook.com" es el respaldo por dominio: og:title no existe (muro 400) y oEmbed no da título.

### 3.2 Arreglo

- `data/ServicioVistaPrevia.kt`: `resolverUrlCanonica(url)` — un GET (OkHttp sigue redirects) que devuelve la URL final; nunca lanza (con fallo devuelve la URL tal cual).
- `data/RepositorioEnlaces.kt`: `urlParaOEmbed()` — solo para enlaces cortos de compartir de Facebook (`domain/DeteccionVideo.kt: esUrlCortaFacebookCompartida`), resuelve a la canónica antes de pedir el oEmbed, en `prepararEnlace` y en `agregarEnlace`.
- `domain/DeteccionVideo.kt` (nuevo): `detectarVideoNoDisponible(texto)` — normalizado sin acentos, frases en español e inglés ("video no disponible", "video unavailable", "este video ya no existe", "no tienes permiso para verlo", "contenido no disponible", ...).
- `ui/home/MiniaturaWebView.kt`: `programarCaptura` ahora lee `document.body.innerText` **ANTES** de capturar. Si es la página de error, NO captura nada y avisa con `onVideoNoDisponible()` (una sola vez). Si el texto no se puede leer (iframe de otro origen), la captura procede como antes.
- `ui/home/TarjetaEnlace.kt` + `strings.xml`: si el reproductor avisa de video no disponible, el WebView se esconde y la tarjeta muestra su **marcador propio "Video no disponible"** (nuevo string `card_video_no_disponible`), nunca la página de error como imagen.

**Pruebas:**
- `DeteccionVideoTest` (nuevo, 5 tests): texto exacto de la captura de Javier, variantes en inglés/sin acentos, no-falsos-positivos ("Mira este video de Minecraft", "Video" solo, EterniCodIA), `/share/r/` y `fb.watch` como cortos, y no-cortos (perfil, watch, YouTube).
- `ServicioVistaPreviaTest` (4 nuevos): `resolverUrlCanonica` devuelve la URL final tras la redirección, sin redirección devuelve la misma, con fallo de red devuelve la URL tal cual, con URL inválida no toca la red. **Límite del test (medido)**: un 302 literal NO se puede simular con interceptores — el de aplicación lo devuelve antes de que `RetryAndFollowUpInterceptor` lo vea (OkHttp no lo sigue) y el de red debe llamar a `proceed()` exactamente una vez (si no, `IllegalStateException`). El test simula la respuesta DESPUÉS de que OkHttp siga el redirect (200 con petición = URL final). Que OkHttp siga el 302 real está medido con curl contra Facebook (ver 3.1): es comportamiento del framework, no de este código. El primer intento de este test (302 literal con interceptor de aplicación, y luego de red) falló en local por esto mismo y se corrigió; ver sección 5.
- `RepositorioEnlacesTest` (1 nuevo): `prepararEnlace pide el oEmbed con la URL canonica...` — con la respuesta post-redirect simulada, el oEmbed se pide con `m.facebook.com`, no con la corta.

## 4. Requisito nuevo — Bloqueo automático al cerrar

**Arreglo:**
- `gradle/libs.versions.toml` + `app/build.gradle.kts`: `androidx.lifecycle:lifecycle-process` 2.10.0 (misma versión que el resto de lifecycle).
- `domain/ObservadorBloqueoApp.kt` (nuevo): `DefaultLifecycleObserver` cuyo `onStop` llama `bloqueo.bloquearTodas()`.
- `di/AppContainer.kt`: UNA sola instancia `bloqueoCarpetas`, compartida entre el ViewModel y el observador (antes el ViewModel creaba la suya y nadie más podía bloquear desde fuera).
- `ui/home/HomeViewModel.kt`: `bloqueo` pasa a ser parámetro del constructor (con valor por defecto; la factoría lo recibe del contenedor).
- `ColeccionApplication.kt`: `ProcessLifecycleOwner.get().lifecycle.addObserver(contenedor.observadorBloqueoApp)` en `onCreate`. `ON_STOP` se dispara cuando la app entera deja de estar visible (segundo plano o cierre; con ~700 ms de retardo propio de ProcessLifecycleOwner). Al volver, cada carpeta pide huella de nuevo. El desbloqueo sigue solo en memoria, como antes.

**Pruebas (`ObservadorBloqueoAppTest`, nuevo, 3 tests):** `onStop` bloquea las 3 carpetas desbloqueadas; con todo bloqueado no hace nada malo; tras `onStop` se puede desbloquear de nuevo (simula la huella al volver).

## 5. CI y APK

- Commits en `main`: `162f7ff` (Fix 4) + `16632e6` (corrección de tests). Push verificado con `git ls-remote` antes; `XxxKeywords.kt` sigue ignorado por git y NO entró en ningún commit ni en este resumen.
- CI run **45205765343: FAILURE** en el paso "Pruebas unitarias" (commit `162f7ff`). Causa: error de compilación de tests — `ClasificadorInteligenteTest.kt` usaba `assertTrue`/`assertFalse` sin importarlos (8 errores `unresolved reference`). El log de CI no se pudo descargar (la API responde 403 "Must have admin rights to Repository" con el token disponible; tampoco hay artefacto de informes). Se reprodujo en local con kotlinc 2.1.0 + JUnit (compilación kotlinc + tests puros sin red, sin Gradle ni puertos, como ordena la regla): los 8 errores eran solo esos imports. Corregido en `16632e6`; verificación local tras la corrección: 87 tests puros OK + 58 tests de red simulada OK (145 en total). El test de `resolverUrlCanonica` con 302 literal también fallaba en local y se corrigió (ver 3.2): no era fallo de CI, era el test.
- CI run **45208123244: SUCCESS** (commit `16632e6`, completado ~15:47 UTC). Artefacto `coleccion-debug` (80.610.698 bytes, id 14834214056). Descarga por API directa: 403 "You must have the actions scope to download artifacts" con el token de `~/.git-credentials`; descargado con `gh run download` (mismo token) sin problema.

**APK v1.0.3 VERIFICADO POR MÍ (descargado del artefacto):** `Coleccion-v1.0.3-debug.apk`, **105.111.783 bytes**, SHA-256 `70da98e6a1199d0473c15a9d54907dfe4872b100bb5c02f269e5e07ad9797281`, package `com.coleccionenlaces.app`, versionCode **4**, versionName **1.0.3**, minSdk **26**, targetSdk **36**, MainActivity lanzable (`com.coleccionenlaces.app.MainActivity`), apksigner **Verifies** (esquema v2 true, v1/v3/v3.1/v4 false; firma debug, como las anteriores), **0 fuentes empaquetadas**.
`assets/modelo_clasificador.tflite` dentro del APK: 67.823.088 bytes, SHA-256 `c4053c8a5409023116f3aa6aad54fe772664494eb05109080badeba8bca4efc5` = **el mismo .tflite nuevo verificado en Python** (12/12 casos, EterniCodIA 0,936, validación 0,9665 TF / 0,939 con umbral). También dentro: `assets/vocab.txt` (231.508 B) y `assets/etiquetas.txt` (244 B).
Archivo guardado en `~/workspace/coleccion-enlaces/Coleccion-v1.0.3-debug.apk`. NO entregado a Javier: la entrega es del padre.

## 6. Límites honestos (lo que solo se puede probar de verdad en el teléfono de Javier)

1. **El pintado real del reproductor de Facebook** (que el cuadro capturado sea el video y no otra pantalla) solo se verifica en un teléfono real: en la JVM no hay WebView. Lo probado en JVM es la decisión (detección de error, resolución, oEmbed con canónica).
2. **La detección de error depende de que `innerText` sea legible.** El texto del reproductor vive en un iframe; si Facebook lo sirve de otro origen, el WebView no lo puede leer y la captura procede como antes (sin la protección). En el caso medido (baseUrl del propio enlace) es legible.
3. **Las 2 tarjetas ya guardadas con la página de error como miniatura local NO se corrigen solas**: su `imagenLocal` ya es la imagen de error y la tarjeta ya no monta el WebView para ese enlace. Hay que borrar y volver a compartir esos 2 enlaces (o borrar la tarjeta); con el flujo nuevo, si el video sigue sin estar disponible, saldrá el marcador "Video no disponible".
4. **El clasificador con el `.tflite` real en el teléfono** (Kotlin + LiteRT) no se prueba en la JVM de CI: las 4 pruebas del intérprete se saltan allí y solo corren de verdad en teléfono (límite ya conocido del Fix 3). La verificación Python con el mismo motor es la evidencia disponible.
5. **El bloqueo automático con `ProcessLifecycleOwner`** se prueba en JVM llamando a `onStop` directamente; que Android dispare `ON_STOP` al pasar a segundo plano en el POCO solo se confirma usándolo.
6. **El scroll del cajón** se prueba en JVM sobre la lista de filas; el desplazamiento táctil real solo se confirma en el teléfono.
7. **Una sola corrección manual de un texto de 2 palabras no supera el umbral 0,6 por sí sola** (0,23 tras el paso en línea; ~0,64 tras el reentrenamiento en reposo). Si Javier mueve un enlace y vuelve a guardar otro igual antes del reentrenamiento, irá a "Otros" (no a la categoría incorrecta de antes, pero tampoco aún a la correcta).
8. El `.tflite` (67,8 MB) supera el máximo recomendado de GitHub (50 MB): el push lo acepta con aviso (límite duro 100 MB), igual que en Fix 3.
