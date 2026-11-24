# Fix 5 — Resumen de construcción (v1.0.4)

Fecha: 2026-11-23 (noche) / 2026-11-24 00:16 UTC (APK verificado).
Versión: **versionCode 5 / versionName 1.0.4**.
Commit final: `70cb5b7` (correcciones de la investigación de 1 hora: `279dd1b`).
CI: run **45294930856 SUCCESS** (`testDebugUnitTest` + `assembleDebug`).
APK: `Coleccion-v1.0.4-debug.apk`, **193.525.977 bytes**,
SHA-256 `cd4c03a1e3882db2e957c3ca10f50890ab7df2e714d53ac26c01660b0fcdd1b6`,
package `com.coleccionenlaces.app`, minSdk 26, targetSdk 36,
apksigner Verifies (v2 true, 1 firmante), **0 fuentes empaquetadas**,
11 dex, `.tflite` dentro 67.818.584 bytes SHA
`ea5d39bcf19c3e888cc7f7f38f943d10f796bd0ba01d47ef0ba97cae74b38f13`
(= el modelo reentrenado, verificado byte a byte).

Diagnóstico que se integra: `fix-5-diagnostico.md` (no se pisa).
Investigación de 1 hora: `investigacion-1-hora.md` (+ `investigacion-hilo-B/`,
`censo-resolver-share.json`).

## 1. Qué se construyó

Fix 5 ataca el bug que Javier reportó en la 1.0.3: un enlace de Facebook
sin título ni descripción quedaba en "Otros" y su tarjeta guardaba la
página de error "Video no disponible" como miniatura. Cuatro piezas:

### 1.1 Clasificación por visión + OCR (ML Kit, en el teléfono, sin servidor)

- **Señales**: `domain/SenalesVisuales.kt` + `data/AnalizadorVisualMlKit.kt`.
  ML Kit Image Labeling (etiquetas de lo que se ve) + ML Kit Text
  Recognition v2 (OCR del texto quemado en el vídeo, latín, español
  cubierto). Los dos **empaquetados** (bundled) en el APK:
  `com.google.mlkit:image-labeling:17.0.9` y
  `com.google.mlkit:text-recognition:16.0.1`. Decisión verificada en la
  documentación oficial: la app se instala por sideload (no desde Play
  Store), así que la descarga en instalación por manifiesto no se
  dispararía; empaquetado garantiza que funciona offline desde el
  primer arranque, sin Google Play Services. La API es idéntica en las
  dos variantes: cambiar después es solo cambiar la dependencia.
- **Mapeo de etiquetas** (`domain/MapeoEtiquetas.kt`): ~50 etiquetas
  en inglés de ML Kit mapeadas a mano a las 16 categorías. Nada mapea
  a Noticias ni a Contenido XXX; "Person" no vota. El voto suma
  confianzas; el ganador necesita total ≥ 0,8 y ventaja ≥ 0,3 sobre
  el segundo; etiquetas < 0,5 no votan.
- **Decisión** (`domain/ClasificadorVision.kt`): pantalla de error
  (OCR) → "Otros" y nunca clasifica; texto OCR → mismo motor de texto
  que ya existe, **solo si la confianza ≥ 0,85**; si no, voto de
  etiquetas; si no hay señal → "Otros". El motor nunca adivina con
  palabras clave en ejecución.
- **Gancho**: `RepositorioEnlaces.guardarMiniaturaVideo` — cuando llega
  el cuadro/miniatura y el enlace sigue en "Otros" y no es manual, se
  analiza la foto y se reclasifica; el texto OCR se guarda como
  descripción cuando está vacía, para que la clasificación sea estable.

### 1.2 M5 + M2 + M3 (la foto del error no se guarda nunca)

Del diagnóstico (`fix-5-diagnostico.md`): el mecanismo de Fix 4 no podía
funcionar nunca — `document.body.innerText` del documento principal es
siempre `""` porque el reproductor vive en un iframe.

- **M5** (`data/ServicioComprobacionVideo.kt`): pre-comprobar la página
  del plugin con OkHttp ANTES de montar el WebView
  (`plugins/video.php?href=<URL>&show_text=false&width=500`). **Solo
  por señal positiva** (`<video>` o `.mp4` presente → DISPONIBLE; si
  no → NO_DISPONIBLE; fallo de red / no-2xx → DESCONOCIDO). Corrección
  crítica de la investigación de 1 hora: NUNCA por texto de error —
  la página buena de Yofer contiene "Video unavailable" como respaldo
  (falso positivo) y el error nuevo de copyright no lo contiene
  (falso negativo).
- **M2** (`ui/home/MiniaturaWebView.kt`): sondeo cada 1 s hasta ~8 s
  leyendo `document.body.innerText` **más** el `contentDocument` de
  cada iframe (try/catch por iframe). El texto ya NO aborta por error:
  solo enriquece (`onTextoDetectado`), y el texto de error nunca se
  entrega (el clasificador lo tomaría por Tecnología 0,737).
  El mecanismo innerText de Fix 4 queda retirado.
- **M3**: riqueza del bitmap (rejilla 100×100, hasta 10.000 muestras):
  < 500 colores distintos Y luminancia media < 60 → no es un cuadro
  real. Red de seguridad tras M5. Umbrales calibrados con las muestras
  JPEG del diagnóstico (error 810–1.030 colores, luminancia 42,7–48,2;
  cuadro real 4.088–62.542): conservadores (pueden dejar pasar algún
  error, no rechazan cuadros reales). **Necesitan recalibración con
  PNG reales del teléfono.** Un cuadro real casi negro da falso
  positivo (límite documentado).
- Regla: nunca se guarda miniatura sin señal positiva de cuadro real.

### 1.3 La mina del aprendizaje (dominio solo → Juegos)

Del diagnóstico §6.3: `cambiarCategoriaManual` registraba la corrección
con "titulo descripcion" — para el vídeo de juego que Javier movió a
mano eso era "facebook.com" → Juegos; tras el reentrenamiento en reposo
subiría a ~0,64 y TODOS los facebook.com sin texto irían a Juegos.

- `RepositorioEnlaces.cambiarCategoriaManual` y
  `ServicioAprendizaje.registrarCorreccion` ya NO registran correcciones
  cuando el texto es solo el dominio (`esTextoSoloDominio`).
- `ServicioAprendizaje.purgarCorreccionesSoloDominio()` (nuevo):
  borra los ejemplos solo-dominio guardados, reinicia la cabeza
  (`CabezaAprendizaje.reiniciar()`) y la reconstruye con los ejemplos
  restantes; idempotente. Se ejecuta al arrancar, DESPUÉS de
  `cargarPesos()` (`AppContainer.iniciarAprendizaje`).

### 1.4 Fuente de cuadros resuelta (og:image / .mp4)

Con la URL canónica conocida, pedir `/reel/<id>/` con UA
`facebookexternalhit` o `Googlebot` devuelve og:image real y 3 .mp4
directas sin sesión (`miniatura-real-curl.md`). El hueco — un `/share/r/`
nunca revela su canónica — sigue abierto (ver §5).

- `domain/PosterResuelto.kt`: detección de URL canónica de vídeo de
  Facebook (`/reel/<id>/`, `/watch/?v=`, `/<pagina>/videos/<id>/`,
  `/video.php?v=`; NO `/share/r/` ni `fb.watch`),
  `resolverUrlCanonicaFacebook` (punto de conexión: hoy devuelve la
  URL si ya es canónica, si no null), `extraerOgImage` (solo meta
  og:image, nunca `<img>` — el muro de login trae un icono de aviso y
  un píxel de seguimiento), `extraerUrlsVideoMp4` con **unescape JSON
  completo** (`\/`, `\u0025` PRIMERO — es el escape del carácter de
  escape — `\u0026`, `\u002F`, `\u003D`, `\u003F`, `\u0023`, `\u002B`).
  Una URL truncada daba 403 "Bad URL hash".
- `data/ServicioPosterResuelto.kt`: resuelve el póster (crawler UA,
  respaldo Googlebot; null si no hay og:image ni .mp4 — el muro de
  login queda excluido), `elegirVideoPorBitrate` (HEAD por URL, solo
  2xx con Content-Type `video/*`, la de mayor Content-Length; si
  ningún HEAD responde, la primera; lista vacía → null) y
  `extraerCuadroDeVideo` (descarga por rangos + MediaMetadataRetriever,
  solo en el teléfono, nunca lanza).
- `RepositorioEnlaces`: el póster resuelto entra en la cadena de
  título/descripción (después del título de vista previa, antes del
  texto compartido), la imagen del póster se descarga a local (prioridad:
  miniatura oEmbed → imagen del póster → imagen de vista previa — el
  póster gana porque cuando existe, la vista previa era el muro de
  login), sin og:image se extrae un cuadro del primer .mp4, y el
  gancho de visión/OCR corre sobre la foto real guardada cuando el
  enlace sigue en "Otros".

### 1.5 Modelo reentrenado (DistilBERT, Fix 5)

El modelo de la 1.0.3 NO podía con el caso de Javier: el texto OCR de
Soylorainegodoy daba "Hazlo tú mismo (DIY)" 0,445 → "Otros", y la
variación reflexiva daba Tecnología 0,674 (equivocada con confianza).
Reentrenamiento (agente 56a20f0d, primer intento, sin iteraciones):

- +25–30 textos realistas de prosa reflexiva/viral en Entretenimiento
  + ~10 contrapesos (Tecnología IA/aprender, Salud, Dinero).
  El texto exacto de Soylorainegodoy y el de EterniCodIA siguen
  EXCLUIDOS del entrenamiento (se enseña el patrón, no la respuesta).
- Datos: 2.949 entrenamiento / 335 validación. Validación TF **0,9821**
  (antes 0,9665); con el .tflite y umbral 0,5: **318/335 = 0,949**.
- Casos obligatorios con el .tflite final, **15/15**: Soylorainegodoy
  OCR completo → Entretenimiento **0,963**; primera frase → 0,970;
  EterniCodIA título solo → Tecnología 0,988; título+desc → 0,971;
  "Nuevo juego para celular gratis" → Juegos 0,983; gameplay Minecraft
  → 0,982; receta de pasta → Cocina 0,963; 12 casos de Fix 4: 12/12
  (0,957–0,991). Extras: variación reflexiva → Entretenimiento 0,910;
  vídeo gracioso viral → 0,978.
- Cuantización: **dynamic range (67,8 MB)** — el INT8 completo
  destruye DistilBERT (medido en Fix 3: 0,0906). Commit `311d566`.
- **Bug encontrado y corregido por el agente**: `convertir.py` con
  `from_keras_model` (TF 2.20) emitía el .tflite con las entradas
  INVERTIDAS (índice 0 = máscara, 1 = input_ids) mientras la app
  alimenta por posición (0 = ids). La primera verificación posicional
  dio 15/15 fallos. Corregido con `from_concrete_functions` y firma
  explícita `[input_ids, input_word_mask]` + un assert que falla si el
  orden se invierte de nuevo. El commit incluye `convertir.py`.

## 2. El umbral OCR: 0,85 (medido, no adivinado)

La investigación de 1 hora (hilo D) midió que la clasificación por OCR
de cuadros NO funciona sola: **0/3 vídeos, 0/30 cuadros**. La basura OCR
da confianza ALTA y equivocada: 12/30 cuadros > 0,5 con categoría
errónea; el peor, 0,847 (meme de Free Fire → Entretenimiento). El caso
obligatorio (Soylorainegodoy, OCR exacto) mide 0,963. El umbral 0,85
queda entre ambos: bloquea todo el OCR erróneo medido y deja pasar el
caso que debe funcionar. Por eso el OCR es solo un respaldo débil:

- **Señal principal: el og:title completo en una sola entrada**
  (el título se guarda entero y se clasifica como "titulo descripcion"
  en una entrada, título primero para llenar la ventana de 64 tokens).
  Clasifica 2/3, pero es inestable entre variantes de extracción
  (Yofer: 0,532 ✓ en un hilo, 0,430 → Otros en otro; el mismo título
  fragmentado da Viajes 0,920 / Dinero 0,589 / Tecnología 0,927 según
  el fragmento).
- El OCR nunca clasifica solo: por debajo de 0,85 caen las etiquetas
  de imagen; sin etiquetas → "Otros".
- **Peligro activo**: "Video no disponible" clasifica como Tecnología
  0,737. Se filtra antes del clasificador en tres sitios
  (`ClasificadorVision`, `MiniaturaWebView`, `RepositorioEnlaces`).
  "facebook.com" está a 0,004 del umbral (0,496): el atajo de
  solo-dominio NO se quita.

## 3. Crecimiento del APK: 105 MB → 193,5 MB

La 1.0.3 pesaba 105.111.783 bytes; la 1.0.4, 193.525.977 (+88,4 MB).
Desglose medido dentro del APK:

- ML Kit OCR nativo: `libmlkit_google_ocr_pipeline.so` +
  `libmlkitcommonpipeline.so` × 4 ABIs ≈ 83 MB sin comprimir.
- ML Kit Image Labeling: modelo `mobile_ica_8bit` 3.042.638 bytes.
- Modelos OCR en assets ≈ 1 MB (detector + reconocimiento + layout).
- Código ML Kit: `classes9.dex` 15,7 MB + `classes10.dex` 12,5 MB.
- El clasificador DistilBERT sigue igual: 67.818.584 bytes.

Es el precio de la variante empaquetada (funciona offline desde el
primer arranque, sin Play Services). La variante no empaquetada sería
~200 KB en el APK + descarga del modelo por Play Services en el primer
uso (con las llamadas fallando hasta que termina). Para una app
sideloaded sin Play Store, la empaquetada es la correcta; si algún día
la app se publica en Play, el cambio es solo la dependencia. **Decisión
para el padre**: el tamaño casi se duplica; no hay alternativa gratuita
más ligera que mantenga el offline inmediato.

## 4. Pruebas

**400 pruebas unitarias en el repo** (`testDebugUnitTest` en CI:
BUILD SUCCESSFUL, 0 fallos). Las del intérprete
(`ClasificadorModeloTest`, 7) se saltan en la JVM de CI con JUnit
Assume — el .so de Android no carga en Linux — y corren de verdad en
el teléfono/emulador; el comportamiento del modelo se verificó en
Python con los mismos bytes y el tokenizador Kotlin en CI contra los
ids exactos de Python.

Nuevas en Fix 5 (y en las correcciones de la investigación):

- `MapeoEtiquetasTest`: mapa, nada mapea a Noticias/XXX, "Person" no
  vota, el voto suma, un solo voto fuerte gana, total bajo / empate /
  margen pequeño / confianza baja / vacío → null, insensible a
  mayúsculas.
- `ClasificadorVisionTest`: la pantalla de error nunca clasifica
  aunque las etiquetas digan Juegos; el OCR vence a las etiquetas;
  las etiquetas deciden cuando el OCR no da nada o el motor dice
  "Otros"; sin señal → "Otros"; texto solo-dominio no va al motor;
  fallo del motor → etiquetas. **Umbral OCR**: 0,84 → no clasifica
  (caen las etiquetas); 0,5 → no clasifica; 0,85 → sí clasifica.
- `ServicioComprobacionVideoTest`: error → NO_DISPONIBLE; página
  buena con "Video unavailable" de respaldo → DISPONIBLE; error nuevo
  de copyright → NO_DISPONIBLE; neutro sin vídeo → NO_DISPONIBLE;
  no-Facebook → DISPONIBLE sin peticiones; sin red / HTTP 500 →
  DESCONOCIDO.
- `ServicioAprendizajeTest`: corrección solo-dominio rechazada
  (contador 0, la cabeza no aprende nada); la purga borra el ejemplo
  malo, guarda el bueno y la cabeza sigue prediciendo el texto bueno
  > 0,6 tras reconstruir; purga sin nada malo = no-op; `reiniciar()`
  pone la cabeza a cero.
- `MiniaturaWebViewTest`: caja de error oscura → sin cuadro real;
  cuadro real con muchos colores → real; blanco uniforme → real;
  casi negro con pocos colores → se trata como sin cuadro (falso
  positivo documentado); el JS lee dentro de los iframes.
- `ServicioPosterResueltoTest`: canónica + crawler → póster con
  título/imagen/3 vídeos; muro de login → null; `/share/r/` → null
  sin peticiones; no-Facebook → null; respaldo a Googlebot.
  **Elegir por bitrate**: audio 110.435 B vs baja 616.716 B vs alta
  2.262.571 B → elige la alta (el orden de la lista no importa);
  respuesta no-vídeo se salta; sin HEAD → primera URL; lista vacía →
  null.
- `PosterResueltoTest`: detección de canónica, `/share/r/` → null,
  og:image solo meta, .mp4 con unescape `\/` y `\u0026`, **unescape
  `\u0025` antes que los demás** (`\u00252F` → `%2F`).
- `DeteccionVideoTest`: frases de copyright detectadas.
- `RepositorioEnlacesTest`: OCR clasifica un enlace en "Otros" a
  Cocina + guarda el OCR como descripción + conserva `imagenLocal`;
  OCR de pantalla de error no reclasifica; analizador null no
  reclasifica; enlace ya clasificado no se toca; póster con título
  que clasifica → título real + imagen local + Cocina; póster con
  título que no clasifica + OCR → Cocina por visión; `/share/r/` →
  sin póster, título = dominio, "Otros", sin imagen local; solo-.mp4
  + extractor falso → local.jpg + Cocina por visión. **La prueba de
  Fix 4 que documentaba la mina como comportamiento esperado
  ("facebook.com" → Juegos, contador 1→2, confianza ≥ 0,6) se reescribió**
  con títulos de contenido real; hay una prueba nueva: mover un
  enlace solo-dominio cambia la categoría y marca manual, pero el
  contador queda en 0 y `predecir("facebook.com")` es null.
  **Filtro de texto de error**: `actualizarTextoDesdeWebView` con
  "Video no disponible" o con el texto de copyright devuelve false y
  deja el enlace como estaba (título de dominio, "Otros").
- `ClasificadorModeloTest`: 2 pruebas nuevas con el modelo real
  (patrón Assume): Soylorainegodoy OCR completo → "Entretenimiento"
  ≥ 0,5; "Nuevo juego para celular gratis" → Juegos ≥ 0,5.

Historial CI de Fix 5 (todo por API, sin navegador):

- `45289594168` (311d566): FALLO — `RepositorioEnlaces.kt:445`:
  `Enlace.categoriaId` es `Long?` y `obtenerPorId` pide `Long`.
  Corregido en `257e55c`.
- `45290416374` (257e55c): SUCCESS.
- `45291639504` (813226e): FALLO — falta el import de `PosterResuelto`.
  Corregido en `d3879a6`.
- `45292198618` (d3879a6): FALLO — import duplicado de `VistaPrevia`.
  Corregido en `4cf8e4e`.
- `45292781948` (4cf8e4e): SUCCESS.
- `45294096734` (279dd1b): FALLO — `RepositorioEnlacesTest.kt:220`:
  falta el import de `Clasificador` (nuevo falso con confianza).
  Corregido en `70cb5b7`.
- **`45294930856` (70cb5b7): SUCCESS.**

## 5. Límites honestos (lo que Fix 5 NO resuelve)

1. **Resolver `/share/r/` → canónica sin sesión: BLOQUEADO.**
   ~70 peticiones medidas en total (30 de la sesión anterior + ~40
   del hilo A de la investigación): oEmbed, muro de login, cadena 302
   con cookies, wtsid, l.php, fb.me, token base62 → ID (4 órdenes de
   alfabeto dieron 4 IDs indistinguibles de un ID inventado de
   control), 8 servicios de terceros, Graph sin token, Wayback,
   archive.ph, proxies. Nada revela la canónica ni el ID. El enlace
   `/share/r/` es opaco por diseño. Sin la canónica, el pipeline
   completo (og:title + og:image + .mp4) no se puede usar para los
   enlaces que produce el botón de compartir de Facebook — que son
   los enlaces de Javier.
2. **Foto real de un `/share/r/`: solo queda el WebView con la sesión
   de Facebook del usuario** (login una vez dentro del WebView de la
   app; la app no ve la contraseña; el WebView no comparte cookies
   con la app de Facebook). Consenso 15/19 del censo de miniatura
   real. **No verificado en un teléfono** (falta probar si la
   redirección con sesión revela la canónica). **Riesgo de Términos
   de Meta (suspensión de la cuenta) — decisión de Javier.** 6/15
   modelos del censo lo advierten. La cuenta de Javier ya tiene un
   aviso de desbloqueo pendiente. No se construyó en Fix 5: el alcance
   era M5 + mina + visión/OCR + póster resuelto.
3. **Clasificación exacta del contenido del vídeo: BLOQUEADA por OCR**
   (0/30 cuadros, 0/3 vídeos). El texto quemado no habla del tema y
   la basura OCR clasifica mal con confianza alta. El og:title
   clasifica 2/3 pero es inestable entre variantes de extracción.
   Falta: visión (etiquetas de imagen, ya construida en Fix 5 pero
   sin probar en el teléfono con cuadros reales) o reentrenar con
   texto estilo OCR; y probar ML Kit v2 en el teléfono con estos
   mismos cuadros.
4. **Las 3 tarjetas con la miniatura de error guardada NO se
   auto-reparan**: las 2 viejas + el enlace nuevo 1FKC67bv seguirán
   con la foto del error aunque el código esté arreglado. Javier tiene
   que borrarlos y re-compartirlos (ya se le dijo en la entrega de la
   1.0.3).
5. **M3 necesita recalibración** con PNG reales capturados por la app
   en el teléfono (los umbrales se calibraron con JPEG de capturas de
   pantalla de Javier).
6. **Los 4 tests del intérprete se saltan en la JVM de CI** y solo
   corren de verdad en el teléfono/emulador. La extracción de cuadros
   de .mp4 (`MediaMetadataRetriever`) también es solo de teléfono; en
   CI se prueba el cableado con un falso.
7. **El APK creció a 193,5 MB** (ver §3). ML Kit empaquetado es la
   causa. Si el tamaño es un problema, la alternativa es la variante
   no empaquetada por Play Services (~200 KB + descarga en primer
   uso), que exige Play Services y conexión en el primer uso.
8. **Decisión pendiente del padre**: el UA de crawler
   (facebookexternalhit/Googlebot) en producción — es lo que exige la
   medición para que Facebook sirva og:image y .mp4 sin sesión; la
   app presenta la petición como el crawler de Meta/Google. Está
   marcado en `ServicioPosterResuelto` como punto de decisión.

## 6. Archivos y commits

Commits en `main` (repo público `djjavierc-droid/coleccion-enlaces`):

- `311d566` — reentrenar DistilBERT con prosa reflexiva →
  Entretenimiento (modelo + `generar_datos.py` + `convertir.py`).
- `c9114a0` — visión + OCR, M5/M2/M3, mina del aprendizaje, versión
  1.0.4 (25 archivos, +2058/−109).
- `257e55c` — fix `categoriaId` nullable (CI).
- `813226e` — fuente de póster resuelto (og:image / .mp4).
- `d3879a6`, `4cf8e4e` — fixes de compilación en CI.
- `279dd1b` — correcciones críticas de la investigación de 1 hora
  (M5 señal positiva, filtro de texto de error, unescape `\u0025`,
  elegir por bitrate, umbral OCR 0,85, + documentación de la
  investigación).
- `70cb5b7` — fix de compilación en CI (import `Clasificador`).

Archivos nuevos principales: `domain/SenalesVisuales.kt`,
`domain/MapeoEtiquetas.kt`, `domain/ClasificadorVision.kt`,
`data/AnalizadorVisualMlKit.kt`, `data/ServicioComprobacionVideo.kt`,
`domain/PosterResuelto.kt`, `data/ServicioPosterResuelto.kt`,
`domain/DeteccionVideo.kt` (frases de copyright),
`domain/MotorClasificacion.kt` (`clasificarTextoConConfianza`).
Modificados: `MiniaturaWebView.kt`, `TarjetaEnlace.kt`,
`HomeScreen.kt`, `HomeViewModel.kt`, `RepositorioEnlaces.kt`,
`ServicioAprendizaje.kt`, `Aprendizaje.kt`, `CabezaAprendizaje.kt`,
`AppContainer.kt`, `gradle/libs.versions.toml`,
`app/build.gradle.kts` (ML Kit + versión 1.0.4).

APK entregable (NO commiteado, nunca se commitean APKs):
`~/workspace/coleccion-enlaces/Coleccion-v1.0.4-debug.apk`
(193.525.977 bytes, SHA-256
`cd4c03a1e3882db2e957c3ca10f50890ab7df2e714d53ac26c01660b0fcdd1b6`).
