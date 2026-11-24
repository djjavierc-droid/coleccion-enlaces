# Investigación de 1 hora, OLA 2 — Colección de enlaces: miniatura real + clasificación exacta

- **Fecha:** 2026-11-23, ~17:57–18:21 CST (los 5 hilos terminaron antes de la hora; no quedaba nada más que probar sin nueva dirección)
- **Orden de Javier:** usar la hora COMPLETA buscando opciones para que TODO funcione: (1) al pegar un enlace, la tarjeta sale CON su miniatura real, automáticamente; (2) el bot detecta EXACTAMENTE el contenido del video y lo clasifica bien. Sin esquivar.
- **Método:** 5 hilos en paralelo con subagentes (E: etiquetas de visión sobre cuadros reales; F: segundo censo resolver /share/r/; G: más servicios de terceros; H: WebView con sesión a fondo; I: estabilidad del og:title). Todo medido; cada afirmación con su salida guardada **en el repo** (`investigacion-ola2/`), nunca solo en /tmp (la ola 1 perdió evidencia por un reinicio que borró /tmp).
- **Regla:** sin cambios de código, sin commits, sin escribir a Javier.
- **Informe ola 1:** `investigacion-1-hora.md` + `investigacion-hilo-B.md`.

## Resumen ejecutivo — el veredicto

1. **Resolver `/share/r/` → canónica/ID sin sesión: CONFIRMADO IMPOSIBLE por segunda vía independiente.** Ola 1: ~70 peticiones, nada. Ola 2: censo de 21 modelos (hilo F) + 9 servicios nuevos (hilo G), 0 de 12 vías nuevas funcionaron. El enlace `/share/r/` es opaco por diseño de Facebook.
2. **La foto real de un `/share/r/` solo queda por WebView con sesión — y la ola 2 encontró que su premisa más básica puede estar muerta:** Facebook puede rechazar el login dentro de un WebView (Meta lo deprecó en 2021; error documentado). Además PixelCopy NO tiene `request(WebView)`; la vía robusta sería `shouldInterceptRequest`. Sin verificar en teléfono. Riesgo de Términos documentado con el texto íntegro vigente desde 2025: el acceso automatizado está prohibido incluso con sesión, y Meta puede eliminar la cuenta. La decisión es de Javier.
3. **La clasificación SÍ mejoró con evidencia: la limpieza "estilo D" del og:title da 3/3** (Yofer 0,643, TEC 0,986, Free Fire 0,468→Juegos 0,670). Y el hallazgo crítico: **quitar los hashtags es lo peor que se puede hacer** — en Free Fire llevan la señal de Juegos. El modelo es 100% determinista; la inestabilidad entre hilos era sensibilidad al texto exacto, no aleatoriedad.
4. **Las etiquetas de visión NO sirven como clasificador fiable:** MobileNet (equivalente a ML Kit Image Labeling) 0/3 videos; CLIP zero-shot en español 2/3, en inglés 1/3. Peor: CLIP en inglés manda el video musical de Yofer a Contenido XXX con 0,62. Orden de señales medido: og:title (limpio) > visión (respaldo débil) > OCR (aún más débil).
5. **Todo lo anterior depende de la URL canónica.** Sin ella no hay og:title, ni og:image, ni .mp4, ni cuadros. El hueco sigue siendo el mismo de la ola 1.

## Hilo E — Etiquetas de visión sobre cuadros reales: mejor que OCR, no fiable

Informe: `investigacion-ola2/E/E.md` + `etiquetas-por-cuadro.csv` + `frames/` (18 JPG) + scripts y modelos ONNX.

| Video (esperado) | MobileNet ImageNet (≈ ML Kit Image Labeling) | CLIP zero-shot EN | CLIP zero-shot ES |
|---|---|---|---|
| **Yofer** (Entretenimiento) | ❌ Otros (4/5; 1/5 Entretenimiento vía `accordion` 0,046) | ❌ **Contenido XXX 4/5** (0,36–0,62) | ❌ Juegos 2/5, Dinero 2/5 (0/5) |
| **TEC STEPX Neo** (Tecnología) | ❌ Otros (1/5 vía `cellular_telephone` 0,39) | ❌ **Juegos 4/5** (0,37–0,76) | ✅ **Tecnología 2/5** (pluralidad; prob. media 0,174) |
| **Free Fire** (Juegos) | ❌ Tecnología 3/5 (`monitor`/`screen`) | ✅ **Juegos 4/5** (0,88–0,97) | ✅ **Juegos 3/5** (0,31–0,61) |
| **Total** | **0/3** | **1/3** | **2/3** |

Por cuadro (15): MobileNet 2/15, CLIP-EN 4/15, CLIP-ES 5/15. og:image sola: MobileNet 0/3, CLIP-EN 1/3, CLIP-ES 2/3. Referencia ola 1: OCR 0/30 cuadros, 0/3 videos; og:title 2/3 pero inestable.

- **Votar entre cuadros ayuda un poco, no rescata los perdidos.** Mayoría y probabilidad media coinciden en los 6 casos. Yofer es 0/5 en ambos idiomas de CLIP y 1/5 en MobileNet: ningún voto lo rescata. Un cuadro negro (Free Fire t12.72) también vota Juegos 0,438.
- **Tres modos de fallo con causa concreta:**
  1. **XXX es un imán de falsos positivos (CLIP-EN):** Yofer 4/5 → Contenido XXX (hasta 0,622); og:image de Free Fire → XXX 0,535. Cuadro verificado visualmente: póster oscuro estilo terror (cara ensangrentada, ojos rojos, disco de vinilo, "50K"), sin instrumento visible. Si la app usara este prompt, un video musical se archivaría como pornografía con confianza 0,62.
  2. **El prompt "phone screen" se vuelve contra TEC (CLIP-EN):** el prompt de Juegos decía "gameplay on a phone screen" y el video de TEC *es* una grabación de pantalla de teléfono: 4/5 → Juegos. La redacción del prompt, no el contenido, decide.
  3. **ImageNet no tiene "videojuego" ni "video musical":** MobileNet ve `book_jacket` 0,753 en el meme de Free Fire, `comic_book` 0,387 en Yofer, `projectile` 0,763 en TEC. ML Kit Image Labeling es de este tipo (etiquetas genéricas de objetos): su techo medido es **0/3 videos**. CLIP no está en ML Kit (requeriría empaquetar un ViT-B/32 de ~350 MB, ~0,7 s/cuadro en CPU) y aun así falla 1/3 con un falso XXX.
- **Casos exactos de Javier:** confirmado por grep, EterniCodIA, Soylorainegodoy y el video de juego siguen sin ID canónico en el repo; se trabajó con los 3 sustitutos, como en la ola 1.
- **Recomendación medida del hilo:** no usar etiquetas ImageNet/ML Kit como señal (techo 0/3); no usar CLIP con el prompt XXX tal como está; el orden de señales sigue siendo og:title primero, visión solo como respaldo débil con voto por mayoría, OCR aún más débil.

## Hilo F — Segundo censo resolver /share/r/: todos murieron

Informe: `investigacion-ola2/F/INFORME-HILO-F.md` + `censo-resolver-share.json` (31 intentos, respuestas crudas) + scripts `censo.mjs`, `censo-retry.mjs`.

- **Participación (solo OmniRoute, vía `~/workspace/omni-client.mjs`):** intentados 31 modelos, respondieron **21**, fallaron 10. ddgw 6/6. groq 5/7 (qwen3.6-27b y compound: HTTP 404, modelo no disponible). auto 10/12. **cohere 0/6: todos HTTP 429** — stop duro por rate limit, no se reintentaron. 2 respuestas vacías (groq gpt-oss-20b, gpt-oss-safeguard-20b).
- **Métodos propuestos (19 respuestas con contenido) y su estado medido:**
  1. **Seguir la redirección 302 / leer `Location` — 11 modelos, el consenso.** Ya probado, muerto en ola 1 (un solo salto `www → m.facebook.com/share/r/…?wtsid=rdr_…`, sin canónica). Los modelos alucinan una redirección a la canónica que Facebook no emite sin sesión.
  2. **Extraer `og:url` / `canonical` / `video_id` del HTML — 5 modelos.** Ya probado, muerto (pared de login, cero metadatos).
  3. **Facebook Sharing Debugger — 1 modelo (tinfoil/gpt-oss-120b). NUEVO → probado con curl, MUERTO:** `GET facebook.com/debugger/?q=<share>` → HTTP 400, final `m.facebook.com/login/…`, cuerpo "Error Facebook / something went wrong". Exige login.
  4. **urldecoder.org — 1 modelo (auto/best-chat). NUEVO → probado, MUERTO por partida doble:** HTTPS 000, HTTP 403 Cloudflare "Just a moment..."; y es solo un decodificador de percent-encoding (`%3A` → `:`), no un resolvedor de Facebook — un token `/share/r/` no lleva nada que decodificar.
  5. **decodefacebook.com — 1 modelo (auto/best-free). NUEVO → probado, MUERTO:** HTTP 000 y HTTPS 000, 0 bytes. El dominio no responde; parece inventado por el modelo.
  6. **Graph API con JSONP — 1 modelo (auto/smart).** Ya probado, muerto (Graph sin token → 400; además el token no es un ID).
  7. **"Herramienta de reconocimiento de URL" genérica — 1 modelo (allam-2-7b).** Vago; equivale a los 8 servicios de terceros ya muertos en ola 1.
  8. **"No se puede" — 1 modelo (ddgw/gpt-5.6-luna).** El único que respondió correctamente; coincide con la ola 1.
- **Pruebas con curl:** enlace real `https://www.facebook.com/share/r/1JqVPz7de`. Salidas en `investigacion-ola2/F/pruebas/` (`debugger.html`, `urldecoder-http.html`). De las 3 propuestas nuevas, **0 de 3 sobrevivieron a la medición**.

## Hilo G — Más servicios de terceros: 0 de 9

Informe: `investigacion-ola2/G/INFORME-HILO-G.md` + `run-g.sh` + salidas por servicio. Entrada: `https://www.facebook.com/share/r/1Bo58NQ` (token real del repo, de los tests de la app). Ninguno era de la lista de muertos de la ola 1.

| Servicio | HTTP | Qué devolvió | Veredicto |
|---|---|---|---|
| unshorten.it (API) | 404 | "Page Not Found" (3.712 B) | Endpoint de API muerto |
| LongURL API v2 | 000 | Conexión fallida, 0 B | Inaccesible (abandonado) |
| Redirect Detective | 000 | Home 200 (form JS a `/ld.px`, campo `word`); POST al endpoint no conecta | No verificable |
| LinkUnshorten | 301 → 403 | Forbidden anti-bot; POST 000 | Bloqueado |
| allorigins.win | 200 | 67.484 B de HTML real: título **"Log in or sign up to view"**, og:image ×0, og:url ×0, canonical ×0, scontent ×0 | Pared de login |
| codetabs proxy | 522 | `error code: 522` | No conecta con Facebook |
| PageSpeed Insights | 429 | Cuota anónima diaria agotada | Bloqueado sin clave; con clave renderizaría la misma pared de login |
| s-shot.ru | 404 | JPEG verificado visualmente: "Screenshot generation timeout / screenshot creation error" | Muerto |
| urlmeta.org | 401 | `Error: Auth Required` | Exige clave de pago |

- **Hallazgo útil:** allorigins demuestra que un fetch hecho desde el servidor de un tercero (IP de datacenter) recibe exactamente la misma pared de login — Facebook la sirve a todo fetch anónimo, lo haga quien lo haga. La vía "que lo descargue otro servidor" está cerrada por el lado de Facebook, no por el servicio elegido.
- **Límites / regla sin servidor:** ninguno llegó a fase de límite de uso. En cualquier caso, todos son servidores de terceros por definición: usar uno en el flujo de la app violaría la regla sin servidor de Javier, además de las cuotas típicas (ref. ola 1: Microlink 25/día) y claves de pago (urlmeta 401).
- **Pendientes sin probar:** dos llamadas `exec` marcaron `userConfirmationRequired: timed_out`; sus salidas sí llegaron y se usaron, pero no se reintentó la parte no aprobada: (1) POST del formulario web de unshorten.it (`/no-js` con CSRF + campo `short-url`) — solo se probó su API muerta; (2) diagnóstico del `/ld.px` de Redirect Detective. Ninguno es prometedor: aun funcionando, solo siguen redirecciones HTTP, y Facebook no redirige `/share/r/` a la canónica para anónimos.
- **Conclusión del hilo:** no hay servicio de terceros nuevo que resuelva `/share/r/`; la única vía restante sigue siendo la WebView con sesión (decisión de Javier).

## Hilo H — WebView con sesión a fondo: construible en código, NO verificada

Informe: `investigacion-ola2/H/H.md` (38.851 bytes), con las 4 páginas oficiales de Android archivadas en `investigacion-ola2/H/fuentes/` (PixelCopy, CookieManager, WebViewClient, WebView — HTML completos, HTTP 200). Nada en /tmp. No se probó nada en el teléfono.

### Hallazgo crítico (nuevo, no estaba en el hilo C)
**Facebook puede rechazar el login dentro del WebView.** Meta deprecó el login en navegadores embebidos Android el 5-oct-2021; el error documentado es *"For your account security, logging into Facebook from an embedded browser is disabled"*. Lo deprecado formalmente es Facebook Login (flujo OAuth del SDK), pero el bloqueo se dispara por detectar un WebView y **ninguna fuente confirma que el formulario de `facebook.com` funcione dentro de un WebView genérico en 2026**. La página oficial del anuncio devolvió HTTP 403 al abrirla; el contenido se verificó vía fuentes secundarias que reproducen el mensaje de Meta textualmente (Keycloak, Cloud4Wi, Techzillo). **El paso 1 de toda la vía es la hipótesis más frágil y debe probarse primero en el teléfono.**

### Matiz de seguridad honesto
"La app no ve la contraseña" es una propiedad de diseño, no técnica — el WebView da a la app capacidad total de inyección JS (`evaluateJavascript`); la garantía solo vale si el código nunca inyecta JS en la página de login.

### APIs verificadas en la doc oficial (cada una citada con URL en H.md)
- **Sesión:** `CookieManager.getInstance()` + `flush()` (API 21, reemplazo síncrono de `sync()`; bloquea y hace I/O — no en hilo UI); cookies de terceros desactivadas por defecto en target Lollipop+ (hay que activarlas por WebView); `getCookie()` expone las cookies de sesión (`c_user`, `xs`) a la app — tratarlas como secreto.
- **Carga con sesión:** `onPageFinished(view, url)` y `doUpdateVisitedHistory` observan la URL final; `shouldOverrideUrlLoading(WebResourceRequest)` API 24; `evaluateJavascript()` API 19 para leer `og:url`/`og:image`/`<video>` del DOM con sesión.
- **Captura:** `capturePicture()` deprecado en API 19 → usar `onDraw(Canvas)` (es lo que la app ya hace; captura DOM, pero el video acelerado por hardware puede salir en negro por vivir en superficie separada — fuente secundaria). **PixelCopy: NO existe `request(WebView, …)`** — solo `Surface`/`SurfaceView`/`Window`, desde **API 24**; el procedimiento correcto para un WebView es `request(Window, …)` con `Rect` del WebView, con `OnDrawListener` previo; errores documentados `ERROR_SOURCE_NO_DATA` (reintentar) y `ERROR_SOURCE_INVALID` (fuente protegida por hardware → falla por diseño).
- **Vía B (más robusta que PixelCopy, evita píxeles):** `shouldInterceptRequest(WebView, WebResourceRequest)` — sobrecarga `String` deprecada en API 21 — permite ver/descargar el `og:image` y los `.mp4` que pide la página con sesión.
- **Guardado:** ya implementado en la app (imagen local en `filesDir`); solo recordar descargar el póster `fbcdn` a local por la firma temporal.

### Qué queda sin verificar (solo se puede probar en teléfono)
Hipótesis H-1 a H-8 en la tabla §7 del informe: que el login funcione en WebView (H-7, crítica); que `/share/r/` con sesión revele la canónica (H-1) o el reproductor (H-2); que `draw()` o `PixelCopy(window)` capturen el cuadro de video y no salgan en negro (H-4/H-5); que `shouldInterceptRequest` vea el póster real (H-6); y el nombre de la cookie de sesión como detector (H-8). **Orden de prueba recomendado si Javier autoriza: H-7 primero, luego H-1/H-2, y solo después la captura.**

### Riesgo de Términos de Meta (con fuentes)
Texto íntegro de los Términos de Meta vigentes desde el 1-ene-2025 leído (espejo en `privacytracker.co.za`; el PDF no se pudo archivar por curl, HTTP 000):
- §3.2.3, literal: *"You may not access or collect data from our Products using automated means (without our prior permission)… **regardless of whether such automated access or collection is undertaken while logged in to a Facebook account**."* — estar logueado no lo hace permitido.
- §4.2, literal: Meta puede *"suspend or permanently disable your access… and… permanently disable or delete your account"* a su criterio.
- Automated Data Collection Terms (7-oct-2024): prohíben scrapers/bots sin *"Meta's express written permission"*; aceptar los términos no es ese permiso. *Meta v. Bright Data* (2024) falló a favor del scraping sin sesión bajo los términos viejos; Meta reescribió los términos en 2025 para cerrar ese hueco.
- **Evaluación del hilo:** probabilidad no cuantificable públicamente; impacto hasta eliminación permanente de la cuenta. Agravantes para Javier: su cuenta ya tiene un aviso de desbloqueo pendiente y el login desde WebView es justo la huella que Facebook asocia a phishing. Atenuante: uso personal de bajo volumen, sin compartir credenciales — no es un permiso. Decisión de Javier; si se construye, volumen mínimo, sin reintentos automáticos ni cargas en bucle, re-login solo manual.

## Hilo I — Estabilidad del og:title: la discrepancia explicada, limpieza 3/3

Informe: `investigacion-ola2/I/I.md` + `resultados-consolidado.json/.csv` + `clasificar_variantes.py` + `diagnostico_yofer.py`. Modelo .tflite verificado byte-idéntico al de la app (md5 `d1b39326…`).

### 1. ¿Quitar emojis/hashtags estabiliza el caso Yofer (0,430→Otros)?
No, y el 0,430 no era el título crudo. Reproducidos ambos hilos al milésimo: el hilo D clasificó una limpieza MANUAL del título (sin emojis, sin URL, sin `«»`, sin `!!!` tras "POSIBLE"/"60.000", y con las palabras de hashtag reducidas a solo "Youtube Spotify itunes") → Viajes 0,430. El título crudo da **Entretenimiento 0,532 ✓** (reproduce B exacto). Con el crudo, Yofer ya clasifica bien; ninguna variante lo baja de 0,526. La limpieza no "arregla" Yofer porque no estaba roto en crudo — pero tampoco lo estabiliza: otra limpieza razonable (la manual de D) lo hunde a 0,430.

### 2. ¿Cuál es la limpieza que mejor funciona?
La **"estilo D" (variante i)** — única con 3/3 aciertos y mejor margen en los 3 videos: sin emojis, `#palabra` → `palabra` (conservar la palabra), sin URLs, sin prefijo `N views · N reactions |`, sin sufijo `| Autor`, sin `«»`, `–`→espacio. Resultados: Yofer 0,532→**0,643** ✓, TEC 0,984→**0,986** ✓, Free Fire 0,468→Otros pasa a Juegos **0,670** ✓. Regla reproducible en `clasificar_variantes.py::limpieza_estilo_D`.

**Hallazgo crítico: quitar hashtags es lo peor que se puede hacer.** En Free Fire los hashtags LLEVAN la señal de Juegos; quitarlos convierte un casi-acierto (Juegos 0,468) en fallo con confianza alta (Entretenimiento 0,788; 0,840 si además se quitan emojis). Quitar el prefijo views/reactions ayuda (Free Fire 0,468→0,558); quitar solo emojis ayuda levemente (0,468→0,507, justo sobre el umbral). TEC es roca: 0,984–0,989 en todas las variantes.

### 3. ¿El modelo es determinista?
**Sí, 100 %.** 4 observaciones por variante (2 repeticiones × 2 procesos independientes): 31/31 filas idénticas hasta el 6.º decimal, 0 inconsistencias. La inestabilidad entre hilos es sensibilidad al texto exacto de entrada, no aleatoriedad. Matiz importante: es determinista pero frágil — diagnóstico Yofer: quitar los `!` de la limpieza estilo D la hunde de 0,643 a 0,477 (bajo umbral), y devolver `!!!` al texto de D no lo recupera (0,375); las palabras de hashtag de Yofer no importan (están fuera de la ventana de 64 tokens: Yofer y TEC truncan a 64 en todas las variantes). Conclusión: la limpieza sube el margen pero no hace robusto el modelo; la estabilidad real requeriría reentrenamiento, no más limpieza manual.

**Problema real sin resolver que sí arregla la variante (i):** Free Fire crudo (0,468 → Otros), que pasa a Juegos 0,670 con la limpieza estilo D.

## Qué funciona, qué no — tabla final

| Objetivo | Estado | Evidencia |
|---|---|---|
| Resolver /share/r/ → canónica sin sesión | ❌ NO, confirmado por 2 vías | Ola 1 ~70 peticiones + ola 2: censo 21 modelos (0/3 propuestas nuevas) + 9 servicios nuevos (0/9) |
| Foto real de /share/r/ sin sesión | ❌ NO | allorigins: Facebook sirve la pared de login a cualquier fetch anónimo; oEmbed sin título/miniatura |
| Foto real con WebView + sesión | ⚠️ Construible en código, NO verificada; premisa H-7 (login en WebView) puede estar muerta | H.md: APIs verificadas; Meta deprecó login embebido 2021; Términos 2025 prohíben acceso automatizado incluso con sesión |
| Clasificar por og:title (con canónica) | ✅ 3/3 con limpieza estilo D | I: Yofer 0,643, TEC 0,986, Free Fire 0,670 |
| Clasificar por etiquetas de visión | ⚠️ Respaldo débil: CLIP-ES 2/3, MobileNet/ML Kit 0/3, falso XXX en Yofer | E: tablas por cuadro |
| Clasificar por OCR de cuadros | ❌ 0/30 cuadros, 0/3 videos (ola 1) | investigacion-1-hora.md hilo D |
| Detección de error M5 | ✅ Corregida: señal positiva (`<video>` o `.mp4`), nunca texto de error | Ola 1 hilo C §5 |

## Recomendación final (con evidencia)

1. **Decisión de Javier, antes de construir nada más sobre la miniatura:** la única vía restante es el WebView con sesión, y su primer paso (que el login funcione dentro de un WebView) es la hipótesis más frágil y solo se puede probar en su teléfono, con el riesgo de Términos documentado arriba (hasta eliminación permanente de la cuenta; su cuenta ya tiene un aviso de desbloqueo pendiente). Si autoriza la prueba, el orden es H-7 (login en WebView) → H-1/H-2 (canónica/reproductor con sesión) → captura (vía B `shouldInterceptRequest` antes que PixelCopy). Si no autoriza, no hay foto real para los `/share/r/`: la app solo puede no guardar la foto del error (M5 corregido) y mostrar su marcador propio.
2. **Mejoras construibles sin esa decisión (no dependen de la sesión):** aplicar la limpieza estilo D al og:title antes de clasificar (3/3, sube el margen y arregla Free Fire 0,468→Juegos 0,670); aplicar M5 con señal positiva; mantener el atajo solo-dominio y no registrar correcciones con texto solo-dominio (mina Fix 5).
3. **No construir:** clasificador por etiquetas ImageNet/ML Kit (techo 0/3); CLIP con el prompt XXX tal como está (falso positivo de pornografía con 0,62 en un video musical); OCR solo como clasificador (0/30).
4. **Orden de señales medido para clasificar un video:** og:title limpio (estilo D) primero; visión (CLIP-ES, voto por mayoría, sin prompt XXX) solo como respaldo débil; OCR aún más débil y solo con texto legible en español y filtrando el texto de error ("Video no disponible" clasifica Tecnología 0,737).
5. **Bloqueo que sigue en pie:** sin la canónica no hay og:title, ni og:image, ni .mp4, ni cuadros — y los casos exactos de Javier (EterniCodIA, Soylorainegodoy, video de juego) siguen sin ID canónico recuperable en el repo.

## Archivos de evidencia (todo en el repo)

- Este informe: `~/workspace/coleccion-enlaces/investigacion-1-hora-ola2.md`
- Hilo E: `investigacion-ola2/E/` (E.md, etiquetas-por-cuadro.csv, clip/mobilenet-resultados.json, frames/ 18 JPG, scripts y modelos ONNX)
- Hilo F: `investigacion-ola2/F/` (INFORME-HILO-F.md, censo.mjs, censo-retry.mjs, pruebas/) + `censo-resolver-share.json`
- Hilo G: `investigacion-ola2/G/` (INFORME-HILO-G.md, run-g.sh, salidas por servicio)
- Hilo H: `investigacion-ola2/H/` (H.md, fuentes/ 4 páginas oficiales de Android)
- Hilo I: `investigacion-ola2/I/` (I.md, resultados-consolidado.json/.csv, clasificar_variantes.py, diagnostico_yofer.py)
- Ola 1: `investigacion-1-hora.md`, `investigacion-hilo-B.md`, `investigacion-hilo-B/`
