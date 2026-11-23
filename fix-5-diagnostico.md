# Fix 5 — Diagnóstico: la vista previa sigue guardando el error "Video no disponible"

- **Fecha:** 2026-11-23 ~17:10 CST
- **Versión probada:** v1.0.3 (versionCode 4) — **confirmada instalada por Javier** (hipótesis de versión vieja descartada)
- **Enlace que falla:** `https://www.facebook.com/share/r/1FKC67bv...` (URL completa desconocida: truncada en la captura; autora Soylorainegodoy; video real que SÍ se ve en la app de Facebook)
- **Síntomas:** la tarjeta muestra como foto la pantalla de error "Video no disponible" y el enlace cae en "Otros"
- **Capturas:** `~/workspace/user/files/1000572309_1_qm47.jpg` (tarjeta con el error) y `1000572310_2_mipj.jpg` (video real en Facebook)
- **Regla del trabajo:** solo diagnóstico. Sin cambios de código, sin commits, sin subir versión.

## 1. Resumen ejecutivo — la causa exacta

El mecanismo de Fix 4 **no puede funcionar nunca** en el flujo de embed de Facebook, porque lee el texto del documento equivocado:

> `MiniaturaWebView` lee `document.body.innerText` del **documento principal** y busca "Video no disponible". Medido: ese innerText es **siempre `""` (longitud 0)** — a los 2 s, 5 s y 10 s, con baseUrl `www.facebook.com` y con `m.facebook.com`. innerText **nunca incluye el contenido de un iframe**, y el reproductor de Facebook vive entero dentro de un iframe. La detección, por tanto, **nunca se dispara** y la captura procede como antes.

El error además no es un fallo de renderizado ni de tiempo de espera: **Facebook lo sirve del lado del servidor para peticiones anónimas**. La página del plugin (`plugins/video.php`) descargada con curl contiene literalmente `Video Unavailable` y **cero** etiquetas `<video>`, cero `.mp4`, cero `video_url`, cero póster y cero `<img>`. Para estos enlaces **no existe ningún cuadro real que capturar** — el WebView no tiene sesión de Facebook y Facebook no entrega el video. El contraste lo prueba: otro video público (reel de Yofer) sí entrega reproductor anónimo (1 `<video>`, 14 `.mp4`). El fallo es por video (privacidad / ajustes de incrustación / requisito de sesión), no universal.

**Método de detección que sí funciona (medido): M5 — pre-comprobar la página del plugin con OkHttp antes de montar el WebView.** La URL del plugin es determinista y una sola petición HTTP distingue el error del caso bueno con separación limpia en las muestras medidas (ver §5). Recomendación: M5 como detector primario + sondeo del iframe (M2) como confirmación + análisis de píxeles (M3) como red de seguridad, y la regla honesta: **nunca guardar una miniatura sin una señal positiva de cuadro real**; si no hay cuadro, la tarjeta muestra su propio marcador.

**Clasificación en "Otros": confirmado.** Título guardado = `facebook.com`, descripción vacía → el atajo solo-dominio del motor manda al clasificador… no, manda a la cabeza aprendida, y si esta no llega a 0.6 → "Otros" (ver §6). No hay ninguna señal de texto real disponible que la app esté dejando de usar para este enlace: el intent de compartir de Facebook trae solo la URL, y el texto del reproductor nunca llega porque es el mismo fallo del innerText. **Mina detectada:** la corrección manual de Javier (mover el video de juego a Juegos) se registró con el texto `facebook.com` → tras el reentrenamiento en reposo, **todos** los enlaces `facebook.com` sin texto irían a Juegos (ver §6.3).

## 2. El fallo, reproducido con pruebas reales

Enlace de reproducción: `https://www.facebook.com/share/r/1JqVPz7de` (enlace de prueba de Fix 4; mismo tipo `/share/r/` que el de Javier; la URL 1FKC67bv completa no se conoce porque la captura la trunca).

Todas las mediciones con el UA exacto de la app (Chrome/126 Mobile, POCO 2311DRK48G).

### 2.1 curl — la pared de login y oEmbed

| Petición | Resultado |
|---|---|
| `GET /share/r/1JqVPz7de` | 302 → `m.facebook.com/share/r/1JqVPz7de?wtsid=…` → **HTTP 400** (pared de login, cero og tags) |
| oEmbed con la URL corta | HTTP 200, 425 bytes: html = `<div id="fb-root">` + `sdk.js` + `<div class="fb-video" data-href="…share/r/1JqVPz7de">`; **sin título, sin miniatura** |
| oEmbed con la URL canónica `m.facebook.com` | HTTP 200, 423 bytes, misma estructura |
| oEmbed página EterniCodIA | HTTP 200, 419 bytes, misma estructura |
| oEmbed YouTube (control) | HTTP 200, 868 bytes: **sí** trae title, author_name, thumbnail_url |

### 2.2 curl — la página del plugin (el dato decisivo)

| Página del plugin | "Video Unavailable" | `<video>` | `.mp4` | `video_url` | `<img>` |
|---|---|---|---|---|---|
| Error, href corta `www…/share/r/1JqVPz7de` | **1** | 0 | 0 | 0 | 0 |
| Error, href canónica `m.facebook.com…` | **1** | 0 | 0 | 0 | 0 |
| Página EterniCodIA (URL de página, no de video) | 1 | 0 | 0 | 0 | 0 |
| Reel público de Yofer (`/reel/1168661422485798/`) | **0** | **1** | **14** | **3** | — |

Texto exacto del error en el HTML: `<div class="_3i0o _50f7">Video Unavailable</div><div class="_3i0p">This video may no longer exist, or you don't have permission to view it.</div>`. Tamaño: error 49.587 bytes; caso bueno 229.778 bytes.

**Lectura:** el error se decide en el servidor de Facebook antes de que exista ningún reproductor. No hay póster, no hay cuadro, no hay nada que capturar. Y no es universal: algunos videos sí se incrustan anónimos.

### 2.3 Playwright — el flujo exacto de la app

Entorno: Chromium 1254, viewport móvil 390×844, UA tipo WebView. Limitación del entorno: Chromium en esta VM **no llega directo a facebook.com** (`ERR_TUNNEL_CONNECTION_FAILED` en el proxy) mientras curl sí llega; por eso el SDK se sustituyó por el iframe real con el HTML del plugin descargado por curl, servido por intercepción de rutas. Fiel para la pregunta del DOM/renderizado. Scripts: `/tmp/fix5/repro.cjs`, `repro2.cjs`.

**CASO A — flujo exacto de la app** (baseUrl = `https://www.facebook.com/share/r/1JqVPz7de`, mismo origen que el iframe):

| Momento | innerText documento principal | iframe existe | contentDocument accesible | innerText del iframe | `<video>` en el documento principal | respuestas de red con video |
|---|---|---|---|---|---|---|
| t = 2 s (momento de captura de la app) | `""` (longitud 0) | sí | sí | `""` (vacío) | 0 | 0 |
| t = 10 s | `""` (longitud 0) | sí | sí | "Video Unavailable\nThis video may no longer exist, or you don't have permission to view it.\nLearn more" | 0 | 0 |

**CASO B — baseUrl = `https://m.facebook.com/share/r/1JqVPz7de` (iframe de otro origen):** a 2 s y a 10 s el innerText principal es `""` y `contentDocument` es **null** (inaccesible). 0 respuestas de video.

**CASO C — la página del plugin cargada directamente como documento principal:** innerText = "Video Unavailable\nThis video may no longer exist, or you don't have permission to view it.\nLearn more" (longitud 101).

Capturas: `/tmp/fix5/fb-www-2s.png` (página en blanco — el iframe aún no se había renderizado a los 2 s en este entorno, sin CSS/JS del plugin), `fb-www-10s.png` (texto del error sin estilos arriba). En el teléfono se ve la versión estilizada (caja negra); los hechos del DOM son lo que importa.

**Conclusión de la reproducción:** el innerText del documento principal es vacío **siempre**, en ambos casos de baseUrl y a cualquier tiempo medido. La detección de Fix 4 no puede dispararse jamás en este flujo.

## 3. Causa raíz exacta

1. **Mecanismo de Fix 4 (el que falla):** `MiniaturaWebView.kt` carga el HTML de oEmbed con `loadDataWithBaseURL(baseUrl = enlace.url, …)`; en `onPageFinished` espera 2000 ms y evalúa `document.body.innerText`; si `detectarVideoNoDisponible(texto)` → `onVideoNoDisponible()` una vez, sin captura; si no, captura el bitmap (si no está en blanco) o reintenta una vez. El propio comentario del código admite el límite: el texto vive en un iframe y el WebView no siempre puede leerlo (otro origen); si vuelve vacío, no se detecta nada y la captura procede como antes.
2. **Medido:** `document.body.innerText` del documento principal es `""` a 2 s, 5 s y 10 s, con baseUrl www y con baseUrl m. innerText **nunca** incluye contenido de iframes. La condición de detección es inalcanzable.
3. **El error es del servidor, no del renderizado:** la página del plugin ya trae "Video Unavailable" y cero datos de video para peticiones anónimas. Esperar más o reintentar no ayuda (estable a 10 s; jamás llegan bytes de video).
4. **No es falta de sesión del usuario en el sentido de "la app no inició sesión":** el WebView es anónimo por diseño (no hay ni debe haber sesión de Facebook dentro de la app). El video se ve en la app de Facebook porque ahí Javier tiene sesión; el WebView anónimo recibe el error del servidor. El contraste con el reel de Yofer prueba que algunos videos sí se sirven anónimos: depende del video (privacidad / ajustes de incrustación), no de la app.

## 4. Código leído (para que el arreglo no se base en suposiciones)

- `MiniaturaWebView.kt` — flujo oEmbed → `onPageFinished` + 2000 ms → `document.body.innerText` → `detectarVideoNoDisponible` → capturar o no. baseUrl = la URL del enlace (no la canónica).
- `DeteccionVideo.kt` — frases normalizadas en español e inglés ("video no disponible", "video unavailable", "este video ya no existe", "no tienes permiso para verlo", "contenido no disponible", etc.).
- `TarjetaEnlace.kt` — la imagen local tiene prioridad (`mostrarLocal`); el WebView solo se monta si NO hay imagen local; si el reproductor reporta no disponible, el WebView se oculta y la tarjeta muestra su propio marcador "Video no disponible".
- `ServicioVistaPrevia.kt` — `resolverUrlCanonica` (OkHttp sigue redirecciones; nunca lanza; si falla devuelve la URL tal cual); reintentos ×2 solo para fallos transitorios.
- `ServicioOEmbed.kt` — oEmbed sin token; Facebook: `oembed_video`, reserva `oembed_post` en error 100.
- `RepositorioEnlaces.kt` — `prepararEnlace` (vista previa; si vacía, oEmbed con URL canónica para enlaces cortos de Facebook vía `urlParaOEmbed`); `agregarEnlace`: título = oEmbed title → og:title → primera línea del texto compartido → host (dominio); descripción = og:description → texto compartido → "". `actualizarTextoDesdeWebView` enriquece el título (solo si sigue siendo el dominio) y la descripción (solo si está en blanco) desde el texto del reproductor y RECLASIFICA si cambian — pero `onTextoDetectado` solo se dispara con texto no vacío, así que para embeds de Facebook nunca se dispara.
- `MotorClasificacion.kt` (`ClasificadorInteligente`) — texto solo-dominio ("facebook.com": un solo token con punto, sin espacios ni barras) → el modelo NUNCA decide; solo la cabeza aprendida, y si no llega a 0.6 → "Otros". En otro caso: modelo (≥0.5) → cabeza (≥0.6) → "Otros".
- `MainActivity.kt` — intent de compartir: `EXTRA_TEXT` → `extraerUrl` (primera URL) + `extraerTextoAcompanante` (texto menos la URL).

## 5. Métodos de detección evaluados (con evidencia)

### M1 — innerText del documento principal (Fix 4, actual): NO FUNCIONA
Medido `""` a 2 s, 5 s y 10 s, baseUrl www y m. La detección no puede dispararse jamás. **Causa raíz confirmada.**

### M2 — `iframe.contentDocument.body.innerText` (mismo origen, con sondeo): FUNCIONA PARCIALMENTE
Accesible cuando el host de baseUrl == host del iframe (www vs www — la configuración real de la app, porque baseUrl = enlace.url = URL www original). Texto del error legible a los 10 s; **vacío en el momento de captura de la app (2 s)** en esta emulación (renderizado incompleto; el CSS/JS del plugin estaba bloqueado en esta VM, así que el tiempo de 2 s en el teléfono no está probado). Completamente inaccesible si baseUrl fuera `m.facebook.com` (contentDocument null). **Necesita sondeo (leer cada ~1 s hasta ~8 s), no una sola lectura a los 2 s.** Sirve como confirmación, no como detector único.

### M3 — análisis de píxeles del bitmap capturado: POSIBLE, como red de seguridad (no primario)
La app ya tiene el bitmap en `capturarCuadro` antes de guardarlo; el coste es el mismo bucle de muestreo que `esBitmapEnBlanco`. Medido sobre las capturas JPEG de Javier (indicativo, no calibrado — el bitmap real es PNG sin pérdidas):

| Región (recorte correcto, verificado con rejilla) | luminancia media | desv. típica | colores distintos | lum < 16 |
|---|---|---|---|---|
| Caja de error (x 80–480, y 780–1000) | 42,7 | 52,0 | 1.030 | 5,9 % |
| Interior de la caja de error | 48,2 | 57,2 | 810 | 5,2 % |
| Fondo de la tarjeta (comparación) | 21,8 | 2,9 | 12 | 0 % |
| Cuadro real, centro (foto) | 64,5 | 57,2 | 62.542 | 4,8 % |
| Cuadro real, mitad inferior (parte oscura) | 36,7 | 45,4 | 4.088 | 4,2 % |

Hallazgos:
- **La luminancia media NO separa:** error 42,7 vs mitad oscura del cuadro real 36,7 (se solapan). La caja de error no es negro puro: su interior es gris muy oscuro (RGB ~24, no 0,0,0) con texto blanco.
- **El conteo de colores distintos SÍ separa en estas dos muestras** (810–1.030 vs 4.088–62.542), pero: (a) son recortes JPEG de una captura de pantalla, no el bitmap PNG que analizaría la app (el JPEG infla el conteo del error y el degradado de la tarjeta contamina el recorte); (b) un cuadro de video muy oscuro o fundido a negro podría tener pocos colores y dar falso positivo.
- **Veredicto:** útil como red de seguridad (p. ej. "si el bitmap tiene < 500 colores distintos Y luminancia media < 60, tratar como sin cuadro real"), pero **debe calibrarse con los bitmaps PNG reales que captura la app**, no con estos recortes JPEG. No es detector primario.

### M4 — nivel de red: cero bytes de video (`shouldInterceptRequest`): POSIBLE, como señal de apoyo
Medido: 0 respuestas con video en el caso de error. El caso bueno (Yofer) tiene referencias `.mp4`/`video_url` en el HTML del plugin. **No verificado en navegador para el caso bueno** (los subrecursos están bloqueados en esta VM), así que es señal de apoyo, no detector primario.

### M5 — pre-comprobar la página del plugin con OkHttp antes de montar el WebView: **FUNCIONA (recomendado como primario)**
La URL del plugin es determinista: `https://www.facebook.com/plugins/video.php?href=<URL codificada>&show_text=false&width=500` (la misma forma que construye el SDK). Una petición HTTP barata en el momento de guardar, antes de montar el WebView:

| Muestra | "Video Unavailable" | `<video>` | `.mp4` |
|---|---|---|---|
| Error, href corta (www) | 1 | 0 | 0 |
| Error, href canónica (m.facebook.com) | 1 | 0 | 0 |
| Caso bueno (reel Yofer) | 0 | 1 | 14 |

Separación limpia en las muestras medidas, con ambas formas de URL que la app podría usar. **Advertencias honestas:** solo 2 muestras limpias contrastadas (1 error + 1 bueno) + 1 muestra débil (URL de página, no de video); conviene ampliar las muestras por tipo de video (reel, video de página, video de grupo, live) antes de confiar ciegamente. Comprobar las frases en inglés y en español (el servidor devuelve en_US por defecto con curl; el WebView del teléfono puede recibir español). No requiere WebView, no hay carrera de tiempos, no depende del origen del iframe.

### M6 — presencia de `<video>` en el documento principal: NO FUNCIONA
El `<video>` vive dentro del iframe; el documento principal tiene 0 (medido en ambos casos). Es el hermano de M2 dentro del iframe.

### M7 — esperar más / reintentar la captura: NO AYUDA
El error es del servidor y estable a los 10 s; jamás llegan bytes de video.

## 6. Clasificación en "Otros" — confirmado, y una mina

### 6.1 Lo que pasó con este enlace
La tarjeta muestra título `facebook.com` y como descripción la propia URL (la descripción guardada está vacía). Con entrada `facebook.com` + descripción vacía, el atajo solo-dominio de `MotorClasificacion` aplica: el modelo nunca decide; solo se consulta la cabeza aprendida, y si no llega a 0.6 → **"Otros"**. Es lo que el diseño produce hoy para esta entrada, y coincide con la captura.

### 6.2 ¿Hay alguna señal real que la app no esté usando?
- **Texto del enlace compartido:** no existe para compartir desde Facebook — el intent trae solo la URL (lo prueba el título guardado `facebook.com` + descripción vacía; si hubiera texto acompañante, `extraerTextoAcompanante` lo habría usado). No hay señal de texto compartido sin usar.
- **Texto del reproductor (WebView):** la vía existe en el código (`actualizarTextoDesdeWebView` + reclasificación), pero **está rota por la misma causa raíz**: el innerText del documento principal siempre es `""`, así que `onTextoDetectado` nunca se dispara para embeds de Facebook. Arreglar la lectura del texto (M2/M5) repara también esta vía.
- **Contenido del video (cuadros / OCR):** vía práctica identificada por investigación aparte (censo `censo-sin-texto.json`: consenso 3 de 5 — clasificar por el contenido del video en el teléfono; fuentes oficiales: ML Kit Image Labeling + Text Recognition v2, gratis, sin internet, español cubierto). **NO probada en esta app. No se promete.**

### 6.3 Mina: la corrección manual aprende el dominio, no el video
`cambiarCategoriaManual` registra la corrección con el texto `"${enlace.titulo} ${enlace.descripcion}".trim()` (`RepositorioEnlaces.kt`, línea ~521). Para el video de juego que Javier movió a mano, el título era `facebook.com` y la descripción vacía → la cabeza aprendió **`facebook.com` → Juegos**. Fix 4 midió que un solo paso en línea da confianza 0.23 (< 0.6, por eso este enlace cayó en "Otros" y no en Juegos); tras el reentrenamiento en reposo subiría a ~0.64 (≥ 0.6). **Consecuencia: después de ese reentrenamiento, TODOS los enlaces futuros `facebook.com` sin texto — incluido un video de IA que debería ser Tecnología, y este video emotivo — irían a Juegos.** La corrección debe registrarse con el contenido real del enlace (o no registrarse cuando el texto es solo el dominio), no con el dominio. Esto no estaba en ningún informe anterior.

## 7. Recomendación de arreglo (para decisión del padre)

1. **M5 como detector primario:** antes de montar el WebView, pedir por OkHttp la página del plugin con la URL canónica; si contiene "Video Unavailable" / "Video no disponible" (o no contiene `<video>` ni referencias de video), **no montar el WebView y no guardar miniatura**: la tarjeta muestra su propio marcador. Ampliar primero las muestras por tipo de video.
2. **M2 con sondeo como confirmación** en el WebView (solo cuando baseUrl www == iframe www): leer `iframe.contentDocument.body.innerText` cada ~1 s hasta ~8 s; si aparece el texto del error, descartar la captura. Nunca depender de una sola lectura a los 2 s.
3. **M3 como red de seguridad** sobre el bitmap antes de guardarlo (colores distintos + luminancia), calibrado con bitmaps PNG reales, no con recortes JPEG.
4. **Regla honesta (respaldo):** nunca guardar una miniatura sin una señal positiva de cuadro real (elemento `<video>` presente, o bytes de video observados, o bitmap con riqueza de imagen). Sin señal positiva → marcador propio, nunca la foto del error.
5. **Arreglar la mina de §6.3:** no registrar correcciones cuando el texto es solo el dominio; y al reparar la lectura de texto (M2), el título real del post alimentará la clasificación como estaba diseñado.
6. **No olvidar:** las 2 tarjetas ya guardadas con la página de error como miniatura NO se corrigen solas — Javier tiene que borrar y volver a compartir esos 2 enlaces (ya se le dijo; sigue vigente). Este enlace nuevo (1FKC67bv) también deberá borrarse y re-compartirse tras el arreglo, porque su miniatura ya quedó guardada.

## 8. Límites honestos de este diagnóstico

- La URL completa del enlace de Javier (1FKC67bv…) es desconocida (truncada en la captura); la reproducción usó el enlace de prueba 1JqVPz7de, del mismo tipo `/share/r/`. El comportamiento medido (error servido por el servidor para anónimos) es consistente con lo que muestra la captura de Javier.
- M5 se apoya en 2 muestras limpias contrastadas; necesita más muestras por tipo de video.
- M2 a los 2 s se midió en una emulación donde el CSS/JS del plugin no cargaba (proxy de esta VM); el tiempo exacto de renderizado en el teléfono puede diferir, pero el innerText principal vacío a 2 s, 5 s y 10 s es independiente de eso.
- M3 se midió sobre recortes JPEG de la captura de pantalla de Javier, no sobre el bitmap PNG que captura la app; los umbrales propuestos son orientativos y deben recalibrarse.
- No se probó ningún método de clasificación por contenido de video (ML Kit / OCR) dentro de esta app.

## 9. Archivos de evidencia

- Diagnóstico: `~/workspace/coleccion-enlaces/fix-5-diagnostico.md` (este archivo)
- `/tmp/fix5/oembed-corta.json`, `oembed-canonica.json`, `oembed-yt.json`, `oembed-eterni.json` — respuestas oEmbed medidas
- `/tmp/fix5/plugin.html` (error, href corta, 49.587 bytes), `plugin-canon.html` (error, href canónica, 49.664 bytes), `plugin-reel.html` (caso bueno Yofer, 229.778 bytes), `plugin-eterni.html`
- `/tmp/fix5/repro.cjs`, `repro2.cjs`, `repro.log`, `repro2.log` — reproducción Playwright
- `/tmp/fix5/fb-www-2s.png`, `fb-www-10s.png`, `fb-m-2s.png`, `fb-m-10s.png`, `fb-plugin-directo.png`, `grid-error.jpg` — capturas de la reproducción y rejilla de medición
- Código leído: `MiniaturaWebView.kt`, `DeteccionVideo.kt`, `TarjetaEnlace.kt`, `ServicioVistaPrevia.kt`, `ServicioOEmbed.kt`, `RepositorioEnlaces.kt` (líneas 495–545, `cambiarCategoriaManual`), `MotorClasificacion.kt`, `MainActivity.kt`
