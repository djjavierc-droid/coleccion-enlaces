# Investigación de 1 hora — Colección de enlaces: miniatura real + clasificación exacta

- **Fecha:** 2026-11-23, ~17:37–18:05 CST (los 4 hilos terminaron antes de la hora; no quedaba nada más que probar sin nueva dirección)
- **Orden de Javier:** usar una hora completa buscando opciones para que TODO funcione: (1) al pegar un enlace de Facebook, la tarjeta sale CON su miniatura real, automáticamente; (2) el bot detecta EXACTAMENTE el contenido del video y lo clasifica bien. Sin esquivar: probar todos los hilos.
- **Método:** 4 hilos en paralelo con subagentes (A: resolver /share/r/ → canónica; B: pipeline completo con descargas reales; C: alternativas si A falla; D: cadena visión+OCR → clasificación exacta). Todo medido; cada afirmación con su salida guardada.
- **Regla:** sin cambios de código, sin commits, sin escribir a Javier.

## Resumen ejecutivo — el veredicto

1. **Resolver `/share/r/` → URL canónica sin sesión: NO SE PUDO.** El hilo A probó ~40 peticiones nuevas (más las 30 de la sesión previa): oEmbed, pared de login, cadena 302 con cookies, wtsid, l.php, fb.me, base62 del token → ID, 8 servicios de terceros, Graph sin token, Wayback, archive.ph, proxies. Nada revela la canónica ni el ID. El enlace `/share/r/` es opaco por diseño.
2. **El pipeline completo SÍ funciona — pero solo con la URL canónica conocida** (`/reel/<id>/` + UA Googlebot): og:title + og:image + .mp4 directos sin sesión, cuadros reales con ffmpeg, OCR local real y clasificación con el .tflite real. Verificado de punta a punta en 3 videos reales (hilo B).
3. **La clasificación por OCR de cuadros NO funciona: 0 de 3 videos, 0 de 30 cuadros** (hilo D). El clasificador está sano (controles de texto limpio 4/4, validación 318/335 = 0,949), pero el texto quemado en los cuadros no habla del tema y la basura OCR da confianza ALTA y equivocada (12/30 cuadros > 0,5 con categoría errónea). **La vía que sí clasifica es el og:title: 2 de 3** — pero inestable entre hilos (ver §7).
4. **Única vía restante para la foto real de un `/share/r/`: WebView con la sesión de Facebook del usuario** (login una vez dentro del WebView de la app; la app no ve la contraseña). Consenso 15/19 del censo previo + hilo C. Conlleva riesgo de Términos de Meta (baneo de la cuenta) y NO está verificada en teléfono. La decisión es de Javier.
5. **Corrección crítica a M5 (hilo C):** la detección de "video no disponible" debe ser por SEÑAL POSITIVA (`<video>` o `.mp4` presente), nunca por texto de error. Hay un segundo error nuevo (copyright) que no contiene "Video Unavailable", y la página buena de Yofer contiene el texto "Video unavailable" como respaldo (falso positivo inverso).
6. **Peligro activo (hilo D):** el texto "Video no disponible" clasifica como **Tecnología 0,737**. Si ese texto llega al clasificador, clasifica mal con confianza alta. Debe filtrarse antes. Y "facebook.com" está a 0,004 del umbral (0,496): el atajo solo-dominio NO debe quitarse.

## Hilo A — Resolver /share/r/ → canónica / ID: NINGUNA vía funciona

Evidencia original en `/tmp/investigacion-1-hora/A/` (52 archivos) — **PERDIDA en el borrado de /tmp (ver §9)**; el informe sobrevive en el reporte del subagente. ~40 peticiones nuevas con curl.

| Vía probada | Resultado |
|---|---|
| oEmbed `data-href` | Solo repite la URL de entrada. Cero IDs, cero canónica. |
| Pared de login (fbext y Googlebot, descargas frescas) | Únicos números largos: IDs de sesión (`brsid`, cambia en cada petición), telemetría y páginas de ayuda. Cero `video_id`/`post_id`/`story_fbid`, cero `canonical`/`og:url`/`fb://`, cero `og:image`. |
| Cadena 302 con jar de cookies | Un solo salto `www → m.facebook.com/share/r/…?wtsid=rdr_…`, única cookie `datr` anónima. Nada revela la canónica. |
| wtsid | Aleatorio: quitarlo o falsificarlo da la misma pared. |
| l.php | Exige hash `h` no falsificable (400). |
| fb.me | Va a la home. `fb.watch/1JqVPz7de` → 400. |
| **base62 del token → ID** (la idea más prometedora) | Probada a fondo y muerta: los 4 órdenes de alfabeto dan 4 IDs (`288233179446782`, `378342550523208`, `11606125210167752`, `11696234581244178`) que como `/reel/<id>/` devuelven ~477 KB con 0 og:image/og:title, indistinguibles del control negativo con ID inventado; `/watch/?v=` da título genérico y `/story.php?story_fbid=` da 404. |
| Microlink (en vivo) | 200, pero título "Log in or sign up to view" y su captura PNG (verificada visualmente) es la pared de login. |
| jsonlink, getlinkinfo | Bloqueados por Cloudflare (403). |
| checkshorturl | 503. wheregoes: 404 (endpoint muerto). expandurl: no procesa. unshorten.me: exige clave (401). urlscan: 0 resultados. linkpreview.net: no se probó (exige clave de pago). |
| Graph sin token | 400 OAuthException. |
| `?_fb_noscript=1`, `?__a=1`, `share_button.php` | Mismo shell sin datos. `dialog/share`, `sharer.php`: redirigen a login. mbasic: 400. |
| Wayback Machine | 0 capturas (`archived_snapshots: {}`). archive.ph: CAPTCHA. Memento y fdown: conexión fallida. r.jina.ai: devuelve el login en Markdown. Translate proxy: 400. getfvid: solo su formulario. UA FBAN/FB4A: igual. Token no indexado en buscadores. |

**Conclusión A:** ningún método resuelve el ID/canónica; no hubo verificación de punta a punta que hacer. La única ruta que da la foto real sigue siendo la conocida (canónica `/reel/<id>/` + UA fbext/Googlebot → `og:image` real), pero el hueco —obtener la canónica desde `/share/r/`— sigue sin solución sin sesión. Vías no probadas por falta de medios: WebView con sesión (hilos B/C), pasar el CAPTCHA de archive.ph en navegador real (pero devolvería la misma pared de login, como demuestran Microlink y r.jina.ai), y servicios de pago con clave.

## Hilo B — Pipeline completo con canónica conocida: SÍ funciona de punta a punta

Informe durable: `~/workspace/coleccion-enlaces/investigacion-hilo-B.md` + scripts/salidas en `~/workspace/coleccion-enlaces/investigacion-hilo-B/`. Evidencia de trabajo regenerada en `/tmp/investigacion-1-hora/B/` (33 MB: 3 videos, 15 cuadros, 3 HTML, OCR, salidas del clasificador).

### Por video (todo medido con descargas reales)

| Video | og:title → clasificador | OCR cuadros → clasificador | og:image como OCR |
|---|---|---|---|
| **Yofer** 1168661422485798, 14,63 s, video 720×866 2.262.571 B (también rendition baja 360×434, 616.716 B) | **Entretenimiento 0,532** ✓ | Otros 0,458 ✗ | Solo "TUBE"/"ATION" → Otros (el póster es un recorte con texto cortado) |
| **TEC STEPX Neo** 1395074825861831 (sustituto tecnología), 96,4 s, video 720×1280 19.498.730 B | **Tecnología y celulares 0,984** ✓ | **Autos y motos 0,736** ✗ (basura OCR clasificada con confianza alta) | 0 líneas |
| **Free Fire** 914666554561073 (sustituto juegos), 12,87 s, video 720×1280 1.508.500 B | Juegos 0,468 → **Otros** ~ | **Entretenimiento 0,534** ✗ (el meme domina; Juegos solo 0,140) | Entretenimiento 0,617 ✗ |

- **og:title de Yofer** (316 caracteres): "🦇🔥 ¡¡¡50.000 VISITAS!!! … «LÁGRIMAS DE MI SOLEDAD» … YOFER – LÁGRIMAS DE MI SOLEDAD … #Yofer #LágrimasDeMiSoledad #50k".
- **OCR RapidOCR** (motor de referencia): Yofer nítido → "50.000+VISTAS", "GRACIASPORELAPOYO!", "MILESTONEACHIEVED" (0,98–0,99); Free Fire → "YO CUANDO SEA PAPA Y ESTE EN PLENA PARTIDA DE FREE FIRE", "INGENIERO", "#DaniDlop", "FF"; TEC → casi nada útil. **Tesseract 5.3.4** (instalado a mano por .deb) es mucho más ruidoso: pierde el meme de Free Fire por completo.
- **Casos exactos del repo**: EterniCodIA sin ID canónico en el repo → por texto **Tecnología 0,971** ✓; video de juego sin ID (solo texto "facebook.com" → **Otros** 0,496); Soylorainegodoy sin URL descargable (1FKC67bv… truncada) → por texto OCR exacto **Entretenimiento 0,979** ✓. "Nuevo juego para celular gratis" → **Juegos 0,983** ✓ (bug Fix 4 corregido).
- **og:image como cuadro para OCR: mala** (recorte arbitrario, rendition variable por petición: Yofer 157.257 B 1312×1576 en la sesión previa vs 78.491 B 999×1200 ahora). Como miniatura para guardar: sí, es el póster real. Los cuadros del .mp4 son mejores para OCR.
- **Lección de diseño**: el og:title manda (título+OCR da idéntico 0,532: el OCR queda fuera de los 64 tokens); el OCR solo es respaldo débil y a veces engañoso — **nunca clasificar solo con él**; y el título debe pasarse completo en una sola entrada (párrafos sueltos dan Viajes 0,920 / Dinero 0,589 / Tecnología 0,927 según el fragmento).

### Hallazgos adicionales (hilo B)

1. **La URL .mp4 de la sesión previa era solo audio** (60.394 bps, 110.435 B, stream AAC único). Hay que elegir rendition por bitrate/`vencodetag`, no la primera `.mp4`.
2. **403 "Bad URL hash" = URL truncada**, no bloqueo: el primer extractor cortaba antes de `oh`/`oe` por no decodificar `\u0025`/`\u0026`. Con URLs completas, las 3 renditions de los 3 videos dan 200.
3. **Tesseract**: `apt-get update` se quedó >25 min descargando ~418 MB de listas sin terminar; se instaló por .deb directos (versiones vía API de Launchpad: tesseract-ocr 5.3.4-1build5, libtesseract5, liblept5 1.82.0-3build4, spa/eng 4.1.0-2, libgif7 5.2.2-1ubuntu1.2). RapidOCR por pip (`~/.ocr-venv`) funcionó antes y mejor.

## Hilo C — Alternativas si A falla: solo queda la WebView con sesión

Informe original en `/tmp/investigacion-1-hora/C/INFORME-HILO-C.md` — **PERDIDO en el borrado de /tmp (ver §9)**; el contenido sobrevive en el reporte del subagente. Todo medido en vivo, 2026-11-23 ~17:45–18:30 CST.

### 1. Extras del intent de Facebook — NO aporta nada
Confirmado: Facebook manda solo la URL en `EXTRA_TEXT` (`text/plain`). Sin `EXTRA_STREAM`, sin `EXTRA_SUBJECT`, sin texto del post. La prueba empírica es el propio repo (Fix 5 §6.2: título guardado `facebook.com`, descripción vacía). Además se refutó el comentario de `ExtraerUrl.kt` que afirma que "el texto suele traer el texto de la publicación": para videos de Facebook es falso. Fuentes citadas (handler `ImplicitShareIntentHandler` de `com.facebook.katana`, política anti-pre-rellenado desde 2012). Defensa barata sugerida: leer también `EXTRA_SUBJECT`/`EXTRA_TITLE`/`EXTRA_STREAM` por si acaso, pero no resuelve nada hoy.

### 2. Chrome Custom Tabs — NO sirve para capturar nada, por diseño
Verificado con docs oficiales: SÍ comparte la sesión de Chrome (cookies, logins), pero la app no tiene acceso al DOM ni inyección de JS ni al bitmap — el tab corre en el proceso de Chrome. Matiz: la sesión compartida es la de Chrome, no la de la app de Facebook; si Javier solo tiene sesión en la app, el Custom Tab también vería login. Uso legítimo: solo abrir el video para verlo.

### 3. Servicios de terceros — NINGUNO da la foto real de un /share/r/
Probados en vivo con curl: Microlink (200, título "Log in or sign up to view", imagen 20×20), thum.io (200, PNG verificado visualmente = pared de login), jsonlink (conexión falla, HTTP 000), linkpreview/iframely/Embedly (403 sin clave), opengraph.io (401), noembed (sin proveedor Facebook), ScreenshotMachine (GIF "Invalid key" verificado visualmente), Urlbox (404), ApiFlash/Screenshotlayer (401). Pero **Microlink SÍ da título + og:image real (157.257 B, idéntica a la de la sesión previa) con URL canónica**, y sus screenshots del reel bueno muestran el cuadro real verificado visualmente — confirmación independiente de que el hueco es solo la resolución /share/r/ → canónica. Plan gratis Microlink: 25 peticiones/día, insuficiente, y además dependencia externa de pago.

### 4. WebView con sesión — ÚNICA vía que puede funcionar
Pasos exactos a nivel de diseño (login una vez en el WebView, `CookieManager` + `flush()`, vías A og:image / B `shouldInterceptRequest` / C PixelCopy, detección de sesión caducada, permisos: solo `INTERNET`, sin root/servidor) y riesgos concretos con fuentes: Términos de Meta (acceso automatizado prohibido sin permiso expreso, suspensión "with or without notice"), riesgo real para la cuenta de Javier (que ya tiene un aviso de desbloqueo pendiente), detección de automatización, fragilidad del DOM, sesión que caduca, RAM/batería, y que la app podría leer las cookies de sesión (tratarlas como secreto). **No verificada en teléfono** — falta probar si la redirección con sesión revela la canónica. La decisión de asumir el riesgo de Términos es de Javier.

### 5. Extracción .mp4 del plugin — 2 de 5 videos (40%)
7 peticiones, 5 videos distintos: Yofer y reel 1327563215460977 dan reproductor (1 `<video>`, 14–16 .mp4); reels 1608175297086240 y 27913913738298103 dan un **segundo error nuevo, no visto en Fix 5**: "Facebook Unavailable — This video can't be embedded because it may contain content owned by someone else" (bloqueo por copyright, HTML 99,05% idéntico al del error A); /share/r/ da "Video Unavailable". **Corrección crítica a M5:** la detección debe ser por señal positiva (`<video>` o `.mp4` presente), nunca por texto de error — los 2 casos de copyright no contienen "Video Unavailable" y pasarían el filtro como buenos, y la página buena de Yofer contiene texto de respaldo "Video unavailable / Sorry, this video could not be played" (falso positivo inverso). Control: las 4 páginas canónicas con UA `facebookexternalhit` devuelven og:image ×2 y og:title real, **incluidos los 2 bloqueados en el plugin** — la ruta del rastreador es más permisiva (4/4).

### 6. Otras — todas cerradas
Caché de la app de Facebook sin root (imposible por scoped storage Android 11+, docs oficiales); content provider de Facebook con miniaturas (inexistente; el único provider documentado es el del SDK en la propia app); Graph API `/{id}/picture` sin token (HTTP 400, código 104, medido en vivo; con app token expone el secreto en el APK); MediaProjection/Accessibility (exige diálogo de consentimiento cada vez, no automático; accesibilidad no expone píxeles de video); deep links `fb://` (abren la app, no devuelven datos).

**Recomendación del hilo C:** solo queda la WebView con sesión, con el riesgo de Términos explícito para que Javier decida. Mientras tanto, aplicar M5 corregido (señal positiva, nunca texto de error) + marcador propio si no hay cuadro. Combinación ganadora si la sesión resuelve la canónica: sesión → canónica → og:image (4/4 en la muestra).

## Hilo D — La cadena OCR→clasificación NO funciona sobre cuadros reales

Informe: `/tmp/investigacion-1-hora/D/INFORME-D.md` (copia de seguridad persistente en `/home/hatch/tmp/investigacion-1-hora/D/`). Evidencia: `/tmp/investigacion-1-hora/D/` — `bateria.json` + `bateria-resultados.json` (22 textos), `bateria-cuadros.json` + `cuadros-resultados.json` (23 cuadros regenerados; 32 en la primera pasada), `ocr-yofer-mio.json`, `ocr-b.json`, `ocr.py`, `clasificar.py`, `cuadros-yofer/` (15), `cuadros-b/` (5 TEC), `yofer-v2.mp4`, `tec-v2.mp4`, `reel-*.html`, og:images.

### Tabla de resultados (modelo .tflite real, umbral app 0,5 → si no, "Otros")

| Video (ID / esperado) | Entrada | Predicción (conf) | Final app | ¿Acierto? |
|---|---|---|---|---|
| **Yofer** 1168661422485798 / Entretenimiento | OCR 1 cuadro: "50.000 VISTAS GRACIAS POR EL APOYO MILESTONE ACHIEVED" | Dinero y finanzas (0,492) | Otros | ❌ |
| Yofer | OCR agregado 15 cuadros (limpio) | Dinero y finanzas (0,221) | Otros | ❌ |
| Yofer | OCR agregado 5 cuadros (crudo) | Tecnología (0,562) | **Tecnología** | ❌ fallo > umbral |
| Yofer | og:title completo | Viajes (0,430) | Otros | ❌ |
| Yofer | 1 cuadro con "YOUTUBE SENSATION" (limpio a mano) | Entretenimiento (0,749) | Entretenimiento | ✅ solo con limpieza manual |
| **TEC STEPX Neo** 1395074825861831 / Tecnología | OCR agregado 5 cuadros: "1329 ROOO TEC" | Cocina (0,541) | **Cocina** | ❌ fallo > umbral |
| TEC | og:title ("StepFun presentó el STEPX Neo, primer smartphone para IA con agentes…") | **Tecnología (0,986)** | Tecnología | ✅ |
| TEC | og:image OCR: "" (vacío) | DIY (0,140) | Otros | ❌ (vacío→Otros, fallback correcto) |
| **FreeFire** 914666554561073 / Juegos | OCR 1 cuadro meme: "YO CUANDO SEA PAPA Y ESTE EN PLENA PARTIDA DE FREE FRE" | Entretenimiento (0,847) | **Entretenimiento** | ❌ fallo con conf. alta |
| FreeFire | OCR agregado 5 cuadros | Entretenimiento (0,779) | **Entretenimiento** | ❌ |
| FreeFire | og:image OCR (sin espacios) | Moda y ropa (0,270) | Otros | ❌ |
| FreeFire | Solo marca de agua "INGENIERO FF" | Dinero y finanzas (0,715) | **Dinero** | ❌ fallo con conf. alta |
| FreeFire | og:title ("…#freefire #freefirelatam… El ingeniero FF") | **Juegos (0,630)** | Juegos | ✅ |

**Por cuadro individual: Yofer 0/15** (conf. media 0,451; finales Otros ×9, Tecnología ×6), **FreeFire 0/5** (0,484), **TEC 0/5** (0,393). **Total 0/30.** Controles de texto limpio, todos ✅: EterniCodIA → Tecnología 0,978 (`verificar.py`: PASA, validación 318/335 = 0,949); reflexión → Entretenimiento 0,979; "Nuevo juego para celular gratis" → Juegos 0,983; "Gameplay de Minecraft" → Juegos 0,984.

### Límites medidos (hilo D)

- **Sin texto → Otros (correcto):** vacío → DIY 0,140 → Otros. El fallback funciona cuando la confianza es baja.
- **Pero la basura OCR da confianza ALTA y equivocada:** 12/30 cuadros superan 0,5 con categoría errónea (Tecnología 0,822 en Yofer; Autos 0,864 por el texto "TEC"; Dinero 0,715 por "INGENIERO FF"). El umbral 0,5 no protege contra OCR ruidoso.
- **Ningún número de cuadros lo arregla:** Yofer 1 cuadro → Otros; 2 → Otros; 5 → Tecnología (peor); 15 → Otros. El texto quemado no habla del tema y el modelo se entrenó con títulos en español natural, no con fragmentos OCR en inglés ("MILESTONE ACHIEVED") ni basura.
- **Peligro activo: "Video no disponible" → Tecnología 0,737.** Si ese texto llegara al clasificador, clasificaría mal con confianza alta. Debe filtrarse antes ("Video no disponible"/"Video Unavailable"). "facebook.com" está a 0,004 del umbral (0,496) — el atajo solo-dominio no debe quitarse.

### OCR y ML Kit (hilo D)

- tesseract no instalable (apt sin listas/bloqueado); se usó **RapidOCR local (PaddleOCR/ONNX)** instalado en el venv — OCR real, no metadatos. Es proxy razonable de ML Kit v2 (ambos CRNN), no ML Kit mismo.
- **ML Kit Text Recognition v2 verificado en la doc oficial** (developers.google.com, consultada 2026-11-23 23:46 UTC): on-device, gratis, imágenes **y vídeos**, API 21+; latín bundled `com.google.mlkit:text-recognition:16.0.1` (~4 MB/script/arq., listo al instalar) o unbundled Play Services 19.0.2 (~260 KB, descarga en primer uso); **español (es) y es-419 están en "Supported"**. Recomendación: variante bundled latina. **La precisión de ML Kit sobre estos cuadros queda pendiente de prueba en el teléfono.**

### Qué falta por probar (hilo D)

1. En el teléfono: ML Kit v2 sobre estos mismos cuadros (¿lee mejor el título estilizado? ¿"STEPX Neo"?).
2. El texto OCR solo no basta: hace falta visión (etiquetas de imagen on-device) o reentrenar con texto tipo OCR; y si se usa OCR, filtrar el texto de error y exigir texto legible en español antes de confiar en el modelo (o subir el umbral para entradas OCR).
3. Validar og:title como vía principal con más reels (y probar si quitar emojis/hashtags arregla el caso Yofer, 0,430 → Otros).

## El pipeline que SÍ funciona (con URL canónica conocida) — pasos exactos

1. `GET https://www.facebook.com/reel/<id>/` con UA `Googlebot/2.1` (o `facebookexternalhit/1.1`). Sin sesión. HTTP 200.
2. Del HTML, extraer: `og:title` (título real), `og:image` (póster real, URL fbcdn con firma temporal `oe=…` — **descargar a local al guardar**, como ya hace la app con las imágenes firmadas) y `og:url` (canónica). Con Googlebot, además, hasta 3 URLs `.mp4` directas.
3. **Extraer las .mp4 con unescape JSON completo** (`\u0025` → `%`, `\u0026` → `&`): una URL truncada da 403 "Bad URL hash". **Elegir la rendition de video por bitrate/`vencodetag`, no la primera `.mp4`** (la primera puede ser solo audio AAC).
4. Descargar el .mp4 por rangos (HEAD 200 `video/mp4`; rango 0–99.999 → 206; verificar `ftypmp41`). Extraer cuadros con ffmpeg (equivalente en el teléfono: `MediaMetadataRetriever`).
5. **Clasificar con el og:title como señal principal** (pasarlo completo en una sola entrada, sin fragmentarlo). El OCR de los cuadros es solo respaldo débil: nunca clasificar solo con él.
6. **Filtrar antes del clasificador** los textos "Video no disponible" / "Video Unavailable" (clasifican como Tecnología 0,737) y mantener el atajo solo-dominio ("facebook.com" → 0,496, a 0,004 del umbral).
7. **Detección de error M5 corregida:** señal positiva (`<video>` o `.mp4` presente), nunca texto de error. Sin señal positiva → marcador propio, nunca la foto del error.

## Discrepancias entre hilos — anotadas, no tapadas

El og:title del mismo video dio resultados distintos según el hilo, porque el texto exacto que se le pasa al modelo cambia el resultado:

| Video | Hilo B (og:title) | Hilo D (og:title) |
|---|---|---|
| Yofer 1168661422485798 | Entretenimiento **0,532** ✓ | Viajes **0,430** → Otros ❌ |
| Free Fire 914666554561073 | Juegos **0,468** → Otros ~ | Juegos **0,630** → Juegos ✅ |

Causa probable: distinta extracción/limpieza del título (emojis, hashtags, truncado a 64 tokens). El propio hilo B lo midió: párrafos sueltos del mismo título dan Viajes 0,920 / Dinero 0,589 / Tecnología 0,927 según el fragmento. **Lección: la clasificación es sensible al texto exacto de entrada; el og:title es la mejor señal disponible pero no es estable.** Queda por probar si quitar emojis/hashtags estabiliza el caso Yofer.

## Qué queda bloqueado (con evidencia)

1. **Resolución /share/r/ → canónica/ID sin sesión: BLOQUEADO.** ~70 peticiones medidas en total (30 sesión previa + ~40 hilo A) y ningún método funciona. Sin la canónica, el pipeline del § anterior no se puede usar para los enlaces que produce el botón de compartir de Facebook — que son los enlaces de Javier.
2. **Foto real de un /share/r/: solo queda la WebView con sesión.** No verificada en teléfono; falta probar si la redirección con sesión revela la canónica. Riesgo de Términos de Meta (baneo de la cuenta) — decisión de Javier.
3. **Clasificación exacta del contenido del video: BLOQUEADA en la vía OCR.** 0/30 cuadros, 0/3 videos. El og:title clasifica 2/3 pero inestable (ver §7). Falta: visión (etiquetas de imagen on-device) o reentrenar con texto tipo OCR; y probar ML Kit v2 en el teléfono.
4. **Casos exactos de Javier no descargables:** Soylorainegodoy (URL 1FKC67bv… truncada) y el video de juego (URL truncada) no tienen ID canónico recuperable; se usaron sustitutos reales y se anotó así. EterniCodIA tampoco tiene ID canónico en el repo, pero su texto clasifica Tecnología 0,971–0,978.

## Incidente: el servicio de ejecución se reinició y borró /tmp

A mitad del trabajo (~23:48 UTC) el servicio se reinició y **borró `/tmp` por completo**, incluida toda la evidencia de la sesión previa (`/tmp/miniatura-real/`, 30 respuestas) y la primera pasada de los hilos A, B, C y D. Los hilos B y D **rehicieron el trabajo desde cero y reprodujeron los valores clave byte a byte** (mismos tamaños de video y cuadros; Yofer por cuadro 0/15, Otros ×9 / Tecnología ×6). Pérdidas no recuperables: los informes de los hilos A y C en `/tmp` (su contenido sobrevive en los reportes de los subagentes, resumidos arriba) y los 5 cuadros del video FreeFire (sus URLs `.mp4` ahora dan 403 "Bad URL hash" incluso recién pedidas, 3 intentos; sus valores medidos quedan en los informes y su og:image + og:title se re-verificaron idénticos). **Lección: la evidencia de aquí en adelante va al repo, no a /tmp.**

## Archivos de evidencia

- Este informe: `~/workspace/coleccion-enlaces/investigacion-1-hora.md`
- Hilo B (durable): `~/workspace/coleccion-enlaces/investigacion-hilo-B.md` + `~/workspace/coleccion-enlaces/investigacion-hilo-B/` (scripts y salidas)
- Hilo D: `/tmp/investigacion-1-hora/D/` (INFORME-D.md, baterías JSON, scripts, cuadros) + copia de seguridad en `/home/hatch/tmp/investigacion-1-hora/D/`
- Hilo B (trabajo): `/tmp/investigacion-1-hora/B/` (INFORME.md, 3 videos, 15 cuadros, 3 HTML, OCR, salidas del clasificador)
- Hilos A y C: informes perdidos en el borrado de /tmp; contenido resumido arriba desde los reportes de los subagentes
- Sesión previa: `~/workspace/coleccion-enlaces/miniatura-real-curl.md` (el informe sobrevive; las 30 respuestas en `/tmp/miniatura-real/` se perdieron en el borrado)
- Referencias: `~/workspace/coleccion-enlaces/fix-5-diagnostico.md`, `~/workspace/coleccion-enlaces/censo-miniatura-real.json`, `~/workspace/coleccion-enlaces/censo-sin-texto.json`
