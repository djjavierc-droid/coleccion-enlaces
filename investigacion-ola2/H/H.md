# HILO H — WebView con sesión a fondo (ola 2, Colección de enlaces)

- **Fecha:** 2026-11-23 ~17:58–18:30 CST
- **Alcance:** documentar los pasos EXACTOS de la vía WebView con sesión, verificando cada API en la documentación oficial de Android. Sin cambios de código, sin commits, sin pruebas en el teléfono de Javier.
- **Predecesores:** `~/workspace/coleccion-enlaces/investigacion-1-hora.md` (hilo C, §4: diseño a nivel de pasos, riesgos de Términos, por qué Chrome Custom Tabs no sirve) y `~/workspace/coleccion-enlaces/fix-5-diagnostico.md` (por qué Fix 4 no puede funcionar: `innerText` siempre `""` por el iframe; Facebook sirve el error del lado del servidor a peticiones anónimas).
- **Páginas oficiales archivadas:** `fuentes/` en este mismo directorio (HTML completos de `PixelCopy`, `CookieManager`, `WebViewClient`, `WebView`, descargados 2026-11-23 23:59 UTC, HTTP 200). El PDF espejo de los Términos de Meta no se pudo archivar por curl (HTTP 000 en esta VM); su texto se leyó íntegro vía `browser.open` y se cita abajo con su URL.

## Veredicto ejecutivo

1. **La vía es CONSTRUIBLE en código**: todas las APIs necesarias existen, están documentadas y son compatibles con el teléfono de Javier (Android 16, POCO X6 Pro 5G; PixelCopy exige API 24+, el resto API 21 o menos). Ningún permiso nuevo salvo el `INTERNET` que la app ya tiene; sin root, sin servidor.
2. **Pero NO está verificada como funcional, y hay DOS premisas que pueden matarla antes de escribir una línea de captura:**
   - **(a) Facebook puede rechazar el login dentro del WebView.** Meta deprecó el login en navegadores embebidos de Android el 5 de octubre de 2021; el mensaje de error documentado es *"For your account security, logging into Facebook from an embedded browser is disabled"*. Esa deprecación apunta formalmente a **Facebook Login (flujo OAuth del SDK)**, no al formulario de `facebook.com` en sí — pero el bloqueo se dispara por detectar un WebView, y **no hay fuente oficial que confirme que el login del sitio web funcione dentro de un WebView genérico en 2026**. Es la primera cosa que hay que probar en el teléfono, antes que nada.
   - **(b) Aunque haya sesión, nadie ha probado que la redirección revele la URL canónica** ni que el cuadro de video sea capturable (el video puede vivir en una superficie separada y salir en negro con `draw()`). Ambas son hipótesis.
3. **El riesgo de Términos de Meta es REAL y está citado textualmente abajo**: acceso automatizado prohibido sin permiso previo, "regardless of whether such automated access or collection is undertaken while logged in", y suspensión o desactivación permanente "with or without notice" según el resumen de la norma. La cuenta de Javier ya tiene un aviso de desbloqueo pendiente (antes del 23-nov-2026 18:31, según MEMORY.md). La decisión es de Javier.
4. **Matiz de seguridad honesto:** "la app no ve la contraseña" es una propiedad **de diseño, no técnica**. Un WebView da a la app anfitriona capacidad total de inyección de JavaScript (`evaluateJavascript`); si la app quisiera, podría leer los campos del formulario de login. La garantía solo vale si el código nunca inyecta JS en la página de login. Esto debe quedar como regla explícita del código, no como suposición.

---

## 1. Login una vez dentro del WebView y persistencia de la sesión

### 1.1 Configuración mínima del WebView (todo verificado en la doc oficial)

| Ajuste | API | Fuente — qué dice |
|---|---|---|
| `WebSettings.setJavaScriptEnabled(true)` | API 1 (método clásico; la doc oficial indica que JavaScript está desactivado por defecto y debe activarse explícitamente) | `WebView` — developer.android.com: "By default, a WebView provides no browser-like widgets, does not enable JavaScript"; guías oficiales de WebView. Sin JS, la página de login de Facebook no funciona. |
| `WebSettings.setDomStorageEnabled(true)` | Método clásico de `WebSettings` (nivel exacto no re-verificado en esta sesión; es anterior a API 19) | Necesario: el login de Facebook usa `localStorage`/almacenamiento DOM. Sin él, el flujo de login puede fallar o no recordar la sesión. |
| `WebViewClient` propio + `WebChromeClient` | `WebViewClient` API 1; `WebChromeClient` necesario para diálogos JS/alertas del login | `WebViewClient.onPageFinished(view, url)` — verificado en la página oficial de `WebViewClient` (líneas 86–87 del HTML archivado). |
| `CookieManager.getInstance().setAcceptCookie(true)` | API 1; por defecto ya es `true` | Doc oficial `CookieManager`: "By default this is set to `true` and the WebView accepts cookies." |
| `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)` | **API 21** | Doc oficial `CookieManager`: las apps con `targetSdk` Lollipop o superior **por defecto NO aceptan cookies de terceros**. Facebook carga recursos de `fbcdn.net` y otros dominios; si la sesión depende de cookies de terceros, hay que activarlo explícitamente por WebView. |

### 1.2 Persistencia: `CookieManager.flush()` — verificado

- **Firma (doc oficial):** `public abstract void flush()` — *"Ensures all cookies currently accessible through the getCookie API are written to persistent storage. This call will block the caller until it is done and may perform I/O."*
- **Nivel de API:** la página de `CookieManager` no estampa el nivel en el extracto, pero la página oficial de `CookieSyncManager` lo fija: *"This class was deprecated in API level 21. The WebView now automatically syncs cookies as necessary. […] To manually force a sync you can use the CookieManager method `CookieManager.flush` which is a synchronous replacement for `sync()`."* → **`flush()` existe desde API 21**.
- **Consecuencias prácticas:**
  - El WebView ya sincroniza cookies automáticamente "as necessary"; `flush()` es solo el forzado manual. Llamarlo en `onPageFinished` tras un login correcto es suficiente; **no llamarlo en el hilo UI** porque bloquea y hace I/O (la propia doc lo advierte).
  - Las cookies viven en el directorio privado de datos del WebView de la app (`/data/data/<paquete>/app_webview/`); sobreviven a cierres de la app. No se comparten con Chrome ni con la app de Facebook: **cada app tiene su propio `CookieManager`** (es un singleton por proceso/app). La sesión de Facebook de Javier en su app de Facebook **no** aparece mágicamente en el WebView: hay que iniciar sesión dentro del WebView.
  - `CookieManager.getInstance().getCookie("https://www.facebook.com")` devuelve las cookies como cabecera HTTP (`"key=value; key2=value2"`). **Esto expone las cookies de sesión (`c_user`, `xs`, `sb`) a la app: son secreto de facto** (quien las tenga, tiene la sesión). La app solo debe leerlas para comprobar si hay sesión, nunca guardarlas en texto plano ni enviarlas a ningún sitio. Hilo C ya lo advirtió; se reafirma.
  - `hasCookies()` (API 1) dice si hay alguna cookie guardada; `removeAllCookies(ValueCallback)` (API 21) borra todo — útil para un "cerrar sesión" explícito.

### 1.3 EL BLOQUEO: Facebook Login deprecado en WebViews (hallazgo crítico, verificado)

- **Anuncio oficial de Meta (28-jun-2021):** *"Deprecating Facebook Login support on Android WebViews"* — `https://developers.facebook.com/blog/post/2021/06/28/deprecating-support-fb-login-authentication-android-embedded-browsers/`. **La lectura directa de esa URL devolvió HTTP 403 en esta sesión** (no se pudo verificar el texto original en vivo); el contenido está citado textualmente en fuentes secundarias que reproducen el mensaje de Facebook:
  - Mensaje de Facebook a desarrolladores (citado en `groups.google.com/g/keycloak-dev/c/3VRwOPFmvWU`): *"We've been monitoring an increase in phishing attempts on Android embedded browsers, also known as WebViews. Because of this, we will no longer support this method of Facebook Login and **your users will not be able to log in using Android embedded browsers beginning October 5, 2021**."* La recomendación oficial es usar Custom Tabs o el SDK ≥ 8.2.0, nunca `LoginBehavior=WEB_VIEW_ONLY`.
  - Mensaje de error que ve el usuario (múltiples fuentes, p. ej. `techzillo.com/logging-into-facebook-from-an-embedded-browser-is-disabled/` y `cloud4wi.zendesk.com/hc/en-us/articles/4410611114381`): *"For your account security, logging into Facebook from an embedded browser is disabled. To continue, download and log in to the Facebook app on your device and try again."*
- **Qué cubre exactamente y qué NO está establecido:**
  - Lo deprecado es **Facebook Login** = el flujo OAuth (`facebook.com/dialog/oauth`) que usan apps y juegos para "Entrar con Facebook". Eso está confirmado.
  - **NO está establecido por ninguna fuente consultada si el formulario de login del propio sitio `facebook.com` / `m.facebook.com` cargado directamente en un `WebView` genérico funciona o también es bloqueado.** El bloqueo se dispara por detección del User-Agent embebido, y los reportes de usuarios hablan de "logging into Facebook from an embedded browser is disabled" en general. Es perfectamente posible que el login del sitio también falle.
  - **Consecuencia para este proyecto: el paso 1 de toda la vía ("login una vez en el WebView") es una hipótesis sin probar y el punto de fallo más probable.** Debe ser lo PRIMERO que se pruebe en el teléfono (cargar `m.facebook.com` en el WebView de la app y ver si el login funciona), antes de escribir código de captura, de interceptación o de clasificación. Si sale el error de navegador embebido, la vía entera muere ahí.
  - **Riesgo adicional aunque el login funcione:** un login desde un WebView (nueva huella: UA de WebView, sin historial) es exactamente el tipo de actividad que Facebook marca como sospechosa y puede derivar en checkpoint / verificación de identidad / 2FA. La cuenta de Javier ya tiene un aviso de desbloqueo pendiente. El re-login puede exigir pasos que solo él puede hacer en ese momento.
- **Por qué Chrome Custom Tabs NO es alternativa para el login con captura** (hilo C §4, no re-verificado en esta sesión pero registrado): el Custom Tab comparte la sesión de Chrome y ahí el login sí funcionaría, pero corre en el proceso de Chrome: la app no tiene acceso al DOM, ni a inyección de JS, ni al bitmap. Solo sirve para abrir el video y verlo.

### 1.4 Secuencia de login (diseño, a probar en teléfono)

1. WebView dedicado (pantalla propia "Conectar con Facebook"), con la configuración de §1.1.
2. `loadUrl("https://m.facebook.com/login/")` o `https://www.facebook.com/` — el usuario escribe su correo y contraseña **dentro del WebView**; la app no recibe las credenciales (regla de diseño §0, punto 4).
3. Detectar login correcto: en `onPageFinished`, comprobar que la URL ya no contiene `/login` y que `CookieManager.getInstance().getCookie("https://www.facebook.com")` contiene la cookie `c_user` (heurística; el nombre de la cookie no está verificado en doc oficial, es conocimiento de campo — **a confirmar en teléfono**).
4. `CookieManager.getInstance().flush()` en hilo de fondo.
5. Cerrar el WebView de login; la sesión queda persistida para los WebViews posteriores de captura.

---

## 2. Cargar la página del video con sesión: ¿revela la canónica?

### 2.1 Lo que la API permite observar (verificado)

- `WebViewClient.onPageFinished(WebView view, String url)`: la doc oficial dice *"Notify the host application that a page has finished loading"* y el parámetro `url` es la URL de la página cargada. Si Facebook redirige `/share/r/<token>` → `/reel/<id>/` (o `/watch/?v=<id>`), esa URL final llega aquí y a `WebView.getUrl()`.
- `WebViewClient.shouldOverrideUrlLoading(WebView, WebResourceRequest)`: **API 24** (la sobrecarga con `String` está deprecada en API 24; verificado en la página oficial). Permite ver cada navegación antes de que ocurra.
- `WebViewClient.doUpdateVisitedHistory(WebView, String url, boolean isReload)`: notifica cada cambio de URL del historial, incluidas las navegaciones SPA (`history.pushState`) que no recargan la página.
- `WebView.evaluateJavascript(String, ValueCallback<String>)`: **API 19** (verificado en la página oficial de `WebView`, que lista el método; el nivel 19 consta en las guías oficiales y secundarias). Permite leer `document.querySelector('meta[property="og:url"]').content`, `document.querySelector('meta[property="og:image"]').content` y `document.querySelector('video')?.src` directamente del DOM con sesión.

### 2.2 Estado: HIPÓTESIS SIN PROBAR (no se probó en teléfono, por prohibición expresa)

- **Hipótesis H-1:** con sesión, `GET /share/r/<token>` en el WebView redirige a la URL canónica (`/reel/<id>/`) y esa URL es observable en `onPageFinished` / `getUrl()`. **Sin verificar.** Contra-evidencia posible: Facebook es una SPA; la navegación puede hacerse con `history.pushState` sin que la URL visible cambie de `/share/r/`, o la página del video puede servirse directamente bajo la URL `/share/r/` sin redirigir jamás. En ese caso la canónica seguiría sin conocerse, pero el DOM con sesión sí contendría el `og:image` / el `<video>` (vía `evaluateJavascript` o `shouldInterceptRequest`, §3.4).
- **Hipótesis H-2:** con sesión, la página del plugin `plugins/video.php?href=<url>` deja de devolver "Video Unavailable" y entrega reproductor (1 `<video>`, .mp4). **Sin verificar.** Es la extensión natural del Fix 5 §2.2 (sin sesión: 0 `<video>`, 0 `.mp4`; reel bueno anónimo: 1 `<video>`, 14 `.mp4`), pero con sesión nadie lo ha medido.
- **Hipótesis H-3:** con sesión, `og:image`/`og:title` de la página canónica son los reales. **Sin verificar** para `/share/r/`; para canónica conocida sin sesión ya está verificado (hilo B: 3/3 videos).
- **Qué observar en la prueba de teléfono (cuando Javier decida):** cargar el enlace 1JqVPz7de (prueba) y el 1FKC67bv (caso real) en el WebView con sesión y registrar: URL final en `onPageFinished`, `og:url`/`og:image` por `evaluateJavascript`, presencia de `<video>` y de `.mp4` por `shouldInterceptRequest`. Eso decide H-1/H-2/H-3 de una vez.

---

## 3. Capturar el cuadro real

### 3.1 El método documentado para un WebView: `onDraw(Canvas)` (verificado)

- **Doc oficial `WebView`:** `capturePicture()` — *"This method was deprecated in API level 19. Use `onDraw(Canvas)` to obtain a bitmap snapshot of the WebView, or `saveWebArchive(String)` to save the content to a file."*
- Procedimiento: crear `Bitmap` + `Canvas(bitmap)` y llamar `webView.draw(canvas)` (o sobreescribir `onDraw`). Es el método que **la app ya usa hoy** (`MiniaturaWebView.capturarCuadro`, Fix 5 §4) y que capturó la página de error: prueba que `draw()` captura contenido DOM.
- **Limitación conocida (fuente secundaria, no en la doc oficial):** el video con aceleración por hardware se dibuja en una **ventana/superficie separada** detrás de la jerarquía de vistas; al dibujar el WebView en un `Canvas`, el rectángulo del video sale **en negro** (se oye el audio pero no hay imagen). Fuente: respuesta aceptada en `exchangetuts.com/android-draw-youtube-video-on-surfacetexture-1640162404044967` — *"the video is being drawn to a separate window that is behind the view hierarchy's window and is not readily accessible to be drawn on your Canvas […] the video is being rendered in native code […] Put simply, I don't see a clean way to do this […] with the public API."* El mismo patrón está documentado para iOS (`WKWebView` + video acelerado por hardware → rectángulo negro en la captura).
- **Matiz importante:** en el WebView moderno (Chromium), el video *inline* normalmente se compone dentro de la propia superficie del WebView (ubercompositor) y `draw()` **puede** capturarlo; la superficie separada aparece sobre todo en **pantalla completa** o con ciertas rutas de decodificación por hardware. **La doc oficial no garantiza ninguno de los dos comportamientos.** Por tanto: si `draw()` captura el cuadro de video o sale en negro es **hipótesis H-4, solo verificable en el teléfono** (cargar un reel con sesión, esperar a que el video reproduzca, capturar y mirar el bitmap).

### 3.2 `PixelCopy` — verificado en la doc oficial, con un matiz decisivo

Página oficial archivada: `fuentes/PixelCopy.html` — `https://developer.android.com/reference/android/view/PixelCopy`.

- **Clase:** `public final class PixelCopy` — *"Provides a mechanisms to issue pixel copy requests to allow for copy operations from `Surface` to `Bitmap`"*.
- **Nivel de API: 24 (Android 7.0 Nougat).** La página oficial no estampa el nivel en el extracto leído, pero dos fuentes independientes lo fijan: la referencia .NET de Microsoft (`ApiSince=24` en todas las sobrecargas clásicas) y el código de CameraX (`SurfaceViewImplementation.java`: *"Getting a Bitmap from a Surface is achieved using the `PixelCopy#request()` API, which would introduced in API level 24"*). El POCO (Android 16) lo cumple de sobra.
- **Sobrecargas existentes (todas verificadas en la página oficial):**
  - `request(Surface source, Bitmap dest, OnPixelCopyFinishedListener listener, Handler listenerThread)` y su variante con `Rect`.
  - `request(SurfaceView source, Bitmap dest, …)` y su variante con `Rect` — *"Requests for the display content of a `SurfaceView` to be copied into a provided `Bitmap`. […] The most recently queued buffer in the SurfaceView's Surface will be used as the source of the copy."*
  - `request(Window source, Bitmap dest, …)` y su variante con `Rect` — *"Requests a copy of the pixels from a `Window` to be copied into a provided `Bitmap`. […] The most recently queued buffer in the Window's Surface will be used as the source of the copy."*
  - Sobrecarga con `PixelCopy.Request` + `Executor` — **API 34**.
- **NO existe `PixelCopy.request(WebView, …)`.** Un `WebView` es una `View`, no un `SurfaceView` ni un `Surface`. Las únicas sobrecargas aplicables a un WebView son:
  - **`request(Window, …)`** — copiar la superficie de la ventana de la Activity que contiene el WebView (toda la ventana, luego recortar el rectángulo del WebView). Es el procedimiento correcto según la doc para contenido acelerado por hardware.
  - **`request(Surface, …)`** — inaplicable: el WebView no expone su `Surface` por API pública.
- **Requisitos y errores documentados (página oficial):**
  - La ventana debe tener `DecorView` no nulo; si `Window.peekDecorView()` es nulo o el DecorView aún no tiene superficie, lanza `IllegalArgumentException`. La doc recomienda usar `OnDrawListener` para asegurarse de que al menos un dibujado ha ocurrido antes de copiar.
  - `ERROR_SOURCE_NO_DATA` (3): *"The source has nothing to copy from. When the source is a `Surface` this means that no buffers have been queued yet. Wait for the source to produce a frame and try again."* → hay que esperar a que el video haya producido al menos un cuadro y reintentar.
  - `ERROR_SOURCE_INVALID` (4): *"It is not possible to copy from the source. This can happen if the source is hardware-protected or destroyed."* → **si Facebook marca el video como contenido protegido por hardware (DRM / superficie segura), PixelCopy falla por diseño.** Es un riesgo documentado, no especulado.
  - `ERROR_TIMEOUT` (2), `ERROR_DESTINATION_INVALID` (5, p. ej. bitmap demasiado grande para el hardware), `ERROR_UNKNOWN` (1).
  - El callback `OnPixelCopyFinishedListener` recibe el código de resultado; el `Handler` decide en qué hilo se invoca.
  - El contenido del rectángulo de origen se escala para encajar exactamente en el bitmap; el formato de píxel se convierte al `Bitmap.Config` del destino (usar `ARGB_8888`).
- **Hipótesis H-5 (sin verificar):** que `PixelCopy.request(window)` incluya el cuadro de video del WebView. La doc dice que copia "los píxeles de la ventana", pero **no especifica si el video compuesto por el GPU del WebView — y mucho menos un video en superficie separada — aparece en esa copia**. Es el punto que hay que probar en el teléfono, comparando `draw()` vs `PixelCopy(window)` sobre el mismo reel con sesión. Si el video está en superficie separada, ambas vías pueden salir en negro, y la única salida sería la vía B (§3.4), que no captura píxeles.

### 3.3 Procedimiento de captura recomendado (diseño, a validar en teléfono)

1. WebView con sesión carga el enlace (página del video o plugin). Esperar a `onPageFinished` + tiempo de reproducción (el video debe haber empezado: el póster o el primer cuadro).
2. **Intento 1 — `draw(Canvas)`:** `Bitmap.createBitmap(w, h, ARGB_8888)` + `webView.draw(Canvas(bitmap))`. Barato, síncrono, ya implementado en la app. Comprobar con el análisis de píxeles (M3 del Fix 5: colores distintos + luminancia) que no es negro ni la página de error.
3. **Intento 2 — `PixelCopy.request(window, bitmap, listener, handler)`** (API 24+): solo si el intento 1 sale en negro en la zona del video. Esperar a que el DecorView haya dibujado (`OnDrawListener`), pedir la copia con el `Rect` del WebView dentro de la ventana, y tratar `ERROR_SOURCE_NO_DATA` reintentando tras ~500 ms–1 s.
4. **Regla honesta (heredada del Fix 5 §7.4):** nunca guardar una miniatura sin señal positiva de cuadro real (video presente + bitmap con riqueza de imagen). Sin señal positiva → marcador propio, nunca la foto del error.

### 3.4 Vía B (hilo C): no capturar píxeles — interceptar la red con sesión

Esta vía evita por completo el problema de la superficie de video, y usa APIs también verificadas:

- **`WebViewClient.shouldInterceptRequest(WebView, WebResourceRequest)`** — verificado en la página oficial: *"Notify the host application of a resource request and allow the application to return the data."* La sobrecarga con `WebResourceRequest` es la vigente; **la sobrecarga con `String` está deprecada en API 21** (página oficial, líneas 130–135 del HTML archivado). El `WebResourceRequest` da la URL de cada subrecurso que pide la página.
- Procedimiento: con sesión, el WebView pide el `og:image` (póster) y los `.mp4`; `shouldInterceptRequest` ve esas URLs; la app las descarga con OkHttp **reutilizando las cookies de sesión** (las cookies del `CookieManager` se pueden leer con `getCookie(url)` y reenviar como cabecera `Cookie`, o usar un `CookieJar` propio — diseño a detallar en implementación). El póster descargado es la miniatura real sin capturar un solo píxel.
- **Ventaja:** no depende de si el video sale en negro; reutiliza el pipeline que ya funciona (hilo B: `og:image` real 157.257 B, `.mp4` descargables).
- **Hipótesis H-6 (sin verificar):** que con sesión el WebView pida efectivamente el `og:image` real y los `.mp4` (y no solo la página de error). Es la misma H-2 vista desde la red.
- Esta vía es probablemente **más robusta que PixelCopy** para este caso concreto, porque el objetivo es la miniatura (el póster), no un cuadro arbitrario del video.

---

## 4. Guardar la imagen en almacenamiento interno

- **Ya implementado en la app** (Fix 5 §4: `TarjetaEnlace` da prioridad a la imagen local; la app ya descarga y guarda miniaturas firmadas en almacenamiento interno). No hay nada nuevo que verificar: el bitmap capturado (`draw()` o PixelCopy) o el póster descargado (vía B) se guarda con el mismo mecanismo existente (PNG/JPEG en `filesDir`, con el nombre/registro que ya usa la app).
- **Advertencias que se mantienen:** las URLs `fbcdn` llevan firma temporal (`oe=…`): descargar a local al guardar (hilo B §2), nunca guardar solo la URL. Y las 2 tarjetas viejas + el enlace 1FKC67bv seguirán con la miniatura de error: hay que borrarlos y re-compartirlos (Fix 5 §7.6; hilo C §4).

---

## 5. Detección de sesión caducada y re-login

- **Detección (diseño, heurísticas a validar en teléfono):**
  1. `CookieManager.getInstance().getCookie("https://www.facebook.com")` vacío o sin la cookie de sesión (`c_user`) → sesión caducada o cerrada. **El nombre exacto de la cookie de sesión es conocimiento de campo, no de la doc oficial: a confirmar en teléfono.**
  2. Al cargar un enlace con el WebView de captura, `onPageFinished` llega con URL que contiene `/login` o la página muestra el formulario de login → sesión caducada.
  3. La página del plugin vuelve a devolver "Video Unavailable" con 0 `<video>` (M5) **estando en sesión**: ambiguo — puede ser caducidad de sesión o bloqueo por copyright del video (hilo C §5: el segundo error no contiene "Video Unavailable"). No usarlo como único detector.
- **Re-login:** reabrir el WebView de login (§1.4). Si Facebook pide verificación/checkpoint/2FA, solo Javier puede completarlo en ese momento; la app debe mostrarle la pantalla y no reintentarlo en bucle (los reintentos automáticos de login son justo lo que los sistemas anti-abuso detectan).
- **Caducidad típica:** las cookies de sesión de Facebook (`c_user`/`xs`) duran meses si hay actividad, pero Facebook las invalida ante cambios de contraseña, actividad sospechosa o revisión de seguridad. No hay cifra oficial pública que citar: no prometer duración.
- **Limpieza:** `CookieManager.getInstance().removeAllCookies(null)` (API 21, asíncrono) para un "desconectar" explícito. No borrar cookies automáticamente ante un fallo transitorio de red: un error de red no es caducidad de sesión.

---

## 6. Riesgo de Términos de Meta — citas textuales

### 6.1 Términos de Servicio de Meta (vigentes desde el 1 de enero de 2025)

Texto leído íntegro en `https://privacytracker.co.za/tos/facebook-tos/version/facebook-tos-e74221/pdf` (espejo del texto oficial; la página oficial `facebook.com/terms.php` no se abrió en esta sesión). Citas literales:

- **§3.2.3 — acceso automatizado prohibido:**
  > *"You may not access or collect data from our Products using automated means (without our prior permission) or attempt to access data you do not have permission to access, **regardless of whether such automated access or collection is undertaken while logged in to a Facebook account**."*
  - La frase final es decisiva para este proyecto: **estar logueado no lo hace permitido**. La vía WebView con sesión es acceso con sesión a datos (título, imagen, video) mediante un mecanismo automatizado (la app carga la página, lee el DOM/red y guarda la imagen sin que el usuario esté mirando esa página).
- **§4.2 — suspensión o desactivación:**
  > *"If we determine, in our discretion, that you have clearly, seriously or repeatedly breached our Terms or Policies, including in particular the Community Standards, **we may suspend or permanently disable your access to Meta Company Products, and we may permanently disable or delete your account**."*
  - La decisión es "in our discretion" (a criterio de Meta) y la sanción llega hasta la eliminación permanente de la cuenta.
- **§3.1 — no compartir acceso:**
  > *"Not share your password, give access to your Facebook account to others, or transfer your account to anyone else (without our permission)."*
  - Matiz: aquí el que accede es el propio usuario con su sesión dentro de su app; no se comparte la contraseña con nadie. Pero la app sí queda en posesión técnica de las cookies de sesión (§1.2), que es acceso a la cuenta.
- **§1.5 — detección de uso indebido:**
  > *"We employ […] advanced technical systems to detect potential misuse of our Products […] If we learn of content or conduct like this, we may take appropriate action […] including […] disabling an account […] We share data across Meta Companies when we detect misuse."*

### 6.2 Automated Data Collection Terms (vigentes desde el 7 de octubre de 2024)

- Resumen verificado en fuentes secundarias que citan el documento (`vorplabs.com/agent-tools/instagram-api`; `facebookscraperapi.com/blog/is-scraping-facebook-legal`): prohíben *"web scrapers, bots, robots, spiders, crawlers, user-agents, and other automated or programmatic mechanisms"* **sin el permiso expreso por escrito de Meta** ("Meta's express written permission"), y establecen que **aceptar los términos no constituye ese permiso**. El documento oficial no se abrió directamente en esta sesión (no se disponía de su URL en los resultados de búsqueda); la cita es de segundo nivel y así se marca.
- **Nuance legal (no asesoría):** en *Meta v. Bright Data* (enero 2024) un juez falló que los términos de entonces no prohibían el scraping de datos públicos **sin sesión**. Meta reescribió los términos el 1-ene-2025 precisamente para cerrar ese hueco ("regardless of whether … while logged in"). La vía WebView con sesión cae del lado que los términos nuevos prohíben expresamente.

### 6.3 Evaluación del riesgo para la cuenta de Javier

- **Probabilidad:** no cuantificable con fuentes públicas (Meta no publica cifras de baneos por este motivo). Factores que la suben: (a) la cuenta ya tiene un **aviso de desbloqueo pendiente** (antes del 23-nov-2026 18:31, según MEMORY.md) — es decir, Meta ya la tiene marcada; (b) el login se haría desde un WebView, huella que Facebook asocia a phishing (por eso lo deprecó en 2021); (c) si la app reintenta cargas o re-logins en bucle, el patrón es indistinguible de un bot.
- **Impacto:** de "confirmar identidad" (leve) a **suspensión o eliminación permanente de la cuenta** (§4.2). Javier usa esa cuenta para su comunidad (Comunidad Muse) y para ver los videos: perderla no es un daño menor.
- **Atenuantes (no eliminan el riesgo):** el uso es personal, de bajo volumen (unos pocos enlaces al día, no scraping masivo), con su propia sesión y sin compartir credenciales; nada de esto es un permiso. La única forma de eliminar el riesgo sería el permiso expreso por escrito de Meta, que no existe.
- **Veredicto:** la vía es técnicamente construible, pero **asume un riesgo contractual real sobre la cuenta del usuario, con sanción máxima documentada**. La decisión es de Javier, con esta información por delante. Si se construye, que sea con volumen mínimo, sin reintentos automáticos, sin cargas en segundo plano en bucle, y con re-login solo manual.

---

## 7. Tabla de verificación: qué está verificado y qué queda sin verificar

| # | Afirmación | Estado | Fuente |
|---|---|---|---|
| V1 | `CookieManager.getInstance()` + `flush()` persisten la sesión; `flush()` bloquea y hace I/O | ✅ Verificado | developer.android.com/reference/android/webkit/CookieManager (+ CookieSyncManager: `flush()` es el reemplazo síncrono de `sync()`, API 21) |
| V2 | Apps con target Lollipop+ no aceptan cookies de terceros por defecto; hay que activarlas por WebView | ✅ Verificado | Misma página oficial |
| V3 | `WebView.capturePicture()` deprecado en API 19; usar `onDraw(Canvas)` para el snapshot | ✅ Verificado | developer.android.com/reference/android/webkit/WebView |
| V4 | `WebView.evaluateJavascript()` existe (API 19) para leer el DOM con sesión | ✅ Verificado (método en la doc oficial; nivel 19 en guías oficiales) | developer.android.com/reference/android/webkit/WebView |
| V5 | `PixelCopy` existe desde API 24; sobrecargas `Surface`, `SurfaceView`, `Window` (+ `Request` en API 34); **no hay sobrecarga para `WebView`** | ✅ Verificado | developer.android.com/reference/android/view/PixelCopy (HTML archivado) + ApiSince=24 (Microsoft Learn) + CameraX |
| V6 | PixelCopy `Window` exige DecorView con superficie; errores `ERROR_SOURCE_NO_DATA` (reintentar), `ERROR_SOURCE_INVALID` (fuente protegida por hardware) | ✅ Verificado | Misma página oficial |
| V7 | `shouldInterceptRequest(WebView, WebResourceRequest)` vigente; sobrecarga `String` deprecada en API 21 | ✅ Verificado | developer.android.com/reference/android/webkit/WebViewClient (HTML archivado) |
| V8 | `shouldOverrideUrlLoading(WebResourceRequest)` API 24; `onPageFinished(view, url)` notifica la URL final | ✅ Verificado | Misma página oficial |
| V9 | Meta deprecó Facebook Login en WebViews Android el 5-oct-2021; mensaje de error documentado | ✅ Verificado (fuentes secundarias que citan el anuncio oficial; la página oficial devolvió 403 al abrirla) | groups.google.com/g/keycloak-dev/c/3VRwOPFmvWU; techzillo.com; cloud4wi.zendesk.com |
| V10 | ToS §3.2.3 prohíbe acceso automatizado sin permiso previo, también con sesión; §4.2 permite suspender o eliminar la cuenta | ✅ Verificado (texto íntegro leído) | Espejo del texto oficial vigente 1-ene-2025 (privacytracker.co.za) |
| H-1 | Con sesión, `/share/r/` redirige a la canónica observable en `onPageFinished`/`getUrl()` | ❌ Hipótesis — solo teléfono | — |
| H-2 | Con sesión, el plugin deja de dar "Video Unavailable" y entrega `<video>`/`.mp4` | ❌ Hipótesis — solo teléfono | — |
| H-3 | Con sesión, `og:image`/`og:title` de `/share/r/` son los reales | ❌ Hipótesis — solo teléfono | — |
| H-4 | `draw(Canvas)` captura el cuadro de video (o sale en negro por superficie separada) | ❌ Hipótesis — solo teléfono | Indicio en contra: exchangetuts (video en ventana separada → negro) |
| H-5 | `PixelCopy.request(window)` incluye el cuadro de video del WebView | ❌ Hipótesis — solo teléfono | La doc no lo especifica |
| H-6 | Con sesión, `shouldInterceptRequest` ve el `og:image` real y los `.mp4` | ❌ Hipótesis — solo teléfono | — |
| H-7 | El formulario de login de `facebook.com` funciona dentro de un WebView genérico en 2026 | ❌ **Hipótesis crítica** — solo teléfono | Lo deprecado es Facebook Login (OAuth); el login del sitio no está confirmado |
| H-8 | Nombre de la cookie de sesión (`c_user`) y su presencia como detector de sesión | ❌ Conocimiento de campo — confirmar en teléfono | — |

**Conclusión de construibilidad:** la vía es construible tal como está documentada **en lo que toca al código** (APIs, niveles, permisos, guardado). Lo que **no** está construido ni verificado es si funciona: depende de H-7 (login), H-1/H-2 (canónica/reproductor con sesión) y H-4/H-5 (captura del cuadro). **El orden de prueba en teléfono, si Javier lo autoriza, es: H-7 primero (login en WebView), luego H-1/H-2 (cargar un /share/r/ con sesión y registrar URL final + DOM + red), y solo después H-4/H-5 (captura).** Escribir el código de captura antes de probar H-7 sería trabajar a ciegas.

---

## 8. Fuentes consultadas (URLs verbatim de los resultados de búsqueda / aperturas)

**Documentación oficial de Android (leída y archivada en `fuentes/`):**
1. `https://developer.android.com/reference/android/view/PixelCopy` — clase PixelCopy, sobrecargas, errores, nota del DecorView. (HTML archivado, 2.266.421 bytes, HTTP 200.)
2. `https://developer.android.com/reference/android/webkit/CookieManager` — `flush()`, `getCookie()`, `setAcceptCookie()`, `setAcceptThirdPartyCookies()`. (HTML archivado, 2.270.656 bytes, HTTP 200.)
3. `https://developer.android.com/reference/android/webkit/CookieSyncManager` — deprecado en API 21; `flush()` como reemplazo síncrono de `sync()`.
4. `https://developer.android.com/reference/android/webkit/WebViewClient` — `shouldInterceptRequest`, `shouldOverrideUrlLoading`, `onPageFinished`, `onPageStarted`, `doUpdateVisitedHistory`. (HTML archivado, 2.328.152 bytes, HTTP 200.)
5. `https://developer.android.com/reference/android/webkit/WebView` — `capturePicture()` deprecado en API 19, usar `onDraw(Canvas)`; `evaluateJavascript()`. (HTML archivado, 3.412.575 bytes, HTTP 200.)

**Meta / Facebook — Términos y login en WebView:**
6. `https://privacytracker.co.za/tos/facebook-tos/version/facebook-tos-e74221/pdf` — texto íntegro de los Términos de Servicio de Meta, vigentes desde el 1 de enero de 2025 (§3.2.3, §4.2, §3.1, §1.5 citados). Espejo; no se pudo archivar por curl (HTTP 000).
7. `https://developers.facebook.com/blog/post/2021/06/28/deprecating-support-fb-login-authentication-android-embedded-browsers/` — anuncio oficial de la deprecación (5-oct-2021). **Devolvió HTTP 403 al abrirlo en esta sesión**; su contenido se cita vía las fuentes 8–10.
8. `https://groups.google.com/g/keycloak-dev/c/3VRwOPFmvWU` — reproduce el mensaje de Facebook a desarrolladores ("your users will not be able to log in using Android embedded browsers beginning October 5, 2021").
9. `https://techzillo.com/logging-into-facebook-from-an-embedded-browser-is-disabled/` — mensaje de error al usuario y fecha de la deprecación.
10. `https://cloud4wi.zendesk.com/hc/en-us/articles/4410611114381-Facebook-Login-no-longer-working-on-Android-devices` — confirmación de la fecha y el mensaje.
11. `https://vorplabs.com/agent-tools/instagram-api` — Automated Data Collection Terms (7-oct-2024): permiso expreso por escrito; aceptar los términos no es el permiso.
12. `https://www.facebookscraperapi.com/blog/is-scraping-facebook-legal` — Automated Data Collection Terms, cierre del hueco de *Meta v. Bright Data* con los términos del 1-ene-2025, y reserva del derecho de Meta a suspender cuentas.
13. `https://siftmarketplace.com/guides/facebook-marketplace-bots` — resumen de los tres documentos de Meta sobre bots y la suspensión "with or without notice".

**Captura de video en WebView (fuentes secundarias):**
14. `https://exchangetuts.com/android-draw-youtube-video-on-surfacetexture-1640162404044967` — el video se dibuja en una ventana separada; al dibujar el WebView en un Canvas sale en negro; "no clean way […] with the public API".
15. `https://learn.microsoft.com/en-us/dotnet/api/android.views.pixelcopy.request?view=net-android-34.0` — `ApiSince=24` en las sobrecargas de PixelCopy.
16. `https://android.googlesource.com/platform/frameworks/support/+/3fb47eb224e238f9d7252965f0e2a1c14a19cb6d/camera/camera-viewfinder/src/main/java/androidx/camera/viewfinder/SurfaceViewImplementation.java` — CameraX: PixelCopy introducido en API 24.
17. `https://geekchamp.com/how-to-take-screenshots-programmatically-on-android/` — PixelCopy para ventana o SurfaceView propios; errores posibles (`ERROR_SOURCE_NO_DATA` = esperar a que la fuente produzca un cuadro).

**Contexto del proyecto (archivos locales):**
18. `~/workspace/coleccion-enlaces/investigacion-1-hora.md` — hilo C §4 (diseño WebView, CCT, riesgos) y §5 (segundo error de copyright, corrección M5).
19. `~/workspace/coleccion-enlaces/fix-5-diagnostico.md` — causa raíz Fix 4, M5, código leído (`MiniaturaWebView.kt`, `RepositorioEnlaces.kt`, etc.).

## Archivos de evidencia

- Este informe: `~/workspace/coleccion-enlaces/investigacion-ola2/H/H.md`
- Páginas oficiales archivadas: `~/workspace/coleccion-enlaces/investigacion-ola2/H/fuentes/{PixelCopy,CookieManager,WebViewClient,WebView}.html` (HTTP 200, 2026-11-23 23:59 UTC)
- No archivado: PDF espejo de los Términos de Meta (curl HTTP 000 en esta VM); texto íntegro leído vía `browser.open` y citado en §6.1.
