# HILO G — Ola 2: más servicios de terceros para /share/r/

- **Fecha:** 2026-11-23 ~23:57–00:12 UTC (17:57–18:12 CST)
- **Entrada usada:** `https://www.facebook.com/share/r/1Bo58NQ` (token real, sacado de los tests de la propia app: `RepositorioEnlacesTest.kt:1224`, `ClasificadorInteligenteTest.kt:155`). Segundo token real del repo, no probado aquí: `1DbnJLtb`.
- **Regla:** sin tocar código, sin commits, sin escribir a Javier. Evidencia en este mismo directorio `investigacion-ola2/G/` (nunca solo en /tmp).
- **Lista de muertos de la ola 1 (NO repetidos):** Microlink, jsonlink, getlinkinfo, checkshorturl, Wayback, archive.ph, wheregoes, expandurl, unshorten.me, urlscan, linkpreview.net, thum.io, iframely, Embedly, opengraph.io, noembed, ScreenshotMachine, Urlbox, ApiFlash, Screenshotlayer, r.jina.ai, getfvid, fdown, Memento.

## Veredicto: TODOS MURIERON — 0 de 9 servicios devuelven canónica, miniatura o título

| # | Servicio | URL pedida | HTTP | Qué devolvió exactamente | Veredicto |
|---|---|---|---|---|---|
| 1 | unshorten.it (API) | `https://unshorten.it/api.php?url=…` | 404 | Página "Page Not Found" (3.712 B). El endpoint de API ya no existe | MUERTO (endpoint muerto) |
| 2 | LongURL API v2 | `https://api.longurl.org/v2/expand?url=…&format=json&title=1` | 000 | Conexión fallida, 0 bytes | MUERTO / inaccesible (servicio abandonado) |
| 3 | Redirect Detective | home 200; POST `https://www.redirectdetective.com/ld.px` campo `word` | 000 | Home 200 (form JS a `/ld.px`, campo `word`); el POST al endpoint no conecta, sin salida | MUERTO / no verificable desde aquí |
| 4 | LinkUnshorten | `https://www.linkunshorten.com/` | 301 → 403 | Home 301; HEAD posterior 403 Forbidden (protección anti-bot); POST 000 | MUERTO (bloqueado) |
| 5 | allorigins.win (proxy) | `https://api.allorigins.win/get?url=…` | 200 | 67.484 B de HTML real de Facebook: título **"Log in or sign up to view"**, og:image ×0, og:url ×0, canonical ×0, scontent ×0, "login" ×37 | MUERTO (devuelve la pared de login) |
| 6 | codetabs proxy | `https://api.codetabs.com/v1/proxy?quest=…` | 522 | `error code: 522` (el proxy no pudo conectar con Facebook) | MUERTO |
| 7 | PageSpeed Insights | `https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=…&screenshot=true` | 429 | `Quota exceeded for quota metric 'Queries' … 'Queries per day'` (project_number:583797351490). Cuota anónima diaria agotada | MUERTO sin clave; con clave seguiría renderizando la pared de login (no verificado) |
| 8 | s-shot.ru (site-shot.com) | `https://mini.s-shot.ru/1024x768/JPEG/1024/Z100/?…` | 404 | JPEG 1024×768 (65.192 B) verificado visualmente: "Screenshot generation timeout / screenshot creation error" | MUERTO (timeout de generación) |
| 9 | urlmeta.org | `https://api.urlmeta.org/?url=…` | 401 | `Error: Auth Required` | MUERTO sin clave (exige registro/pago) |

Detalle del análisis (archivos en este directorio):

- **05-allorigins** es la evidencia más útil del hilo: un fetch hecho desde el servidor de un tercero (IP de datacenter, no la nuestra) recibe exactamente la misma pared de login. Facebook sirve la pared a TODO fetch anónimo, lo haga quien lo haga. Salida completa en `05-allorigins.json` (status interno: `http_code 200`, `content_length 67527`).
- **07-pagespeed**: el 429 es de la cuota anónima compartida de Google, no de nuestra IP en particular; con API key propia se podría reintentar, pero el render resultante sería la pared de login (como Microlink/thum.io en la ola 1). Su único dato potencialmente útil sería `lighthouseResult.finalUrl`, que no se pudo obtener. No se reintentó: 429 = parada dura.
- **01-unshorten-it**: la home (200) sí existe y tiene formulario web (`POST /no-js`, campo `short-url` + `csrfmiddlewaretoken`). Solo se probó el endpoint de API (404), no el formulario.
- **03-redirectdetective**: la home carga el form (`name="f1"`, `action='JavaScript:xmlhttpPost("/ld.px")'`, campo `word`); el POST a `/ld.px` falló con conexión 000. No se pudo diagnosticar si el endpoint existe.
- **08-sshot**: primer intento 000 (conexión), reintento 404 con la imagen de error, verificada visualmente (`08b-sshot.jpg`).

## Límites y regla sin servidor

Ningún servicio llegó a la fase de "límite de uso" porque ninguno devolvió nada útil. A título informativo, de haber funcionado alguno: todos son servidores de terceros por definición — usar cualquiera como parte del flujo de la app sería depender de un servidor externo, contra la regla sin servidor de Javier (todo debe funcionar 100% sin servidor). Además los de este tipo suelen tener cuotas gratuitas bajas (referencia ola 1: Microlink 25 peticiones/día) y exigen clave de pago para uso real (urlmeta 401, PageSpeed con clave).

## Pendientes (no probados, por aviso de confirmación caducada)

Durante el trabajo, dos llamadas de ejecución marcaron `userConfirmationRequired: timed_out`; sus salidas sí llegaron y se usaron, pero por instrucción NO se reintentó la parte no aprobada. Quedan sin probar si el coordinador lo autoriza:

1. POST del formulario web de unshorten.it (`/no-js`, con `csrfmiddlewaretoken` + `short-url`) — el endpoint de API está muerto, pero el formulario web no se probó.
2. Diagnóstico del endpoint `/ld.px` de Redirect Detective (reintento con `-v` para ver si es DNS, TLS o bloqueo).

Ninguno de los dos es prometedor: aun funcionando, ambos solo siguen redirecciones HTTP, y Facebook no redirige `/share/r/` a la canónica para usuarios anónimos (ola 1: un solo salto `www → m.facebook.com/share/r/…`).

## Archivos de evidencia

- `run-g.sh` — script de la batería completa
- `00-resumen-http.log` — resumen HTTP por servicio
- `01-unshorten-it.txt` + `01-unshorten-it-home.html` — 404 API + home con form
- `02-longurl.headers.txt` — 000
- `03-redirectdetective-home.html` + `03b-redirectdetective-ldpx.headers.txt` — home 200, POST 000
- `04-linkunshorten-home.html` + `04b-linkunshorten-head.txt` — 301 → 403
- `05-allorigins.json` — pared de login completa (67 KB)
- `06-codetabs.html` — 522
- `07-pagespeed.json` — 429 cuota agotada
- `08b-sshot.jpg` — imagen de error verificada visualmente
- `09-urlmeta.json` — 401 Auth Required
- `INFORME-HILO-G.md` — este informe
