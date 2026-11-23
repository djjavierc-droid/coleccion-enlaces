# Pruebas con curl: ¿alguna ruta entrega la foto real de un video de Facebook sin sesión?

- **Fecha:** 2026-11-23 ~17:36 CST
- **Enlaces probados:** error `/share/r/1JqVPz7de` (mismo tipo que el de Javier `1FKC67bv…`, cuya URL completa es desconocida porque la captura la trunca) y control bueno `/reel/1168661422485798/` (reel público de Yofer)
- **User-Agents:** app (`Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36`, el exacto de `ServicioVistaPrevia.kt`), `facebookexternalhit/1.1`, móvil Pixel 8, `Googlebot/2.1`, escritorio Chrome/126
- **Total:** 30 peticiones curl, todas con salida guardada en `/tmp/miniatura-real/` (nombres abajo). Tabla de códigos en `/tmp/miniatura-real/resultados.tsv`, conteos en `/tmp/miniatura-real/conteos.tsv`.
- **Regla:** solo medición. Sin cambios de código, sin commits.

## Veredicto

**NINGUNA ruta probada entrega la foto real para un enlace `/share/r/` sin sesión.** Para ese tipo de enlace, todas las variantes devuelven o la pared de login o `Video Unavailable` con cero datos de video.

**PERO hay una ruta que SÍ entrega foto real sin sesión — solo cuando ya se conoce la URL canónica del video** (`/reel/<id>/` o `/watch/?v=<id>`): pedir esa página con UA `facebookexternalhit/1.1` o `Googlebot` devuelve `og:title` real, `og:image` real (póster) y hasta las URLs `.mp4` directas del video, todo descargable sin sesión (ver §8). **El hueco decisivo: un enlace `/share/r/` nunca revela su URL canónica** — la redirección 302 se queda en `/share/r/…?wtsid=…` y la página que ve un rastreador es la pared de login. Sin la URL canónica, la ruta del rastreador no se puede usar.

## 1. `plugins/video.php` — variantes (UA de la app)

| Prueba | Archivo | HTTP | Bytes | `Video Unavailable` | `Video no disponible` | `<video>` | `.mp4` | `og:image` | póster |
|---|---|---|---|---|---|---|---|---|---|
| href www, `show_text=true`, w500 | `vphp-error-texto-true-w500.html` | 200 | 49.584 | 1 | 0 | 0 | 0 | 0 | 0 |
| href www, `show_text=false`, w300 | `vphp-error-texto-false-w300.html` | 200 | 49.591 | 1 | 0 | 0 | 0 | 0 | 0 |
| href www, `show_text=false`, w800 | `vphp-error-texto-false-w800.html` | 200 | 49.592 | 1 | 0 | 0 | 0 | 0 | 0 |
| href m.facebook, `show_text=true`, w500 | `vphp-error-href-m-texto-true.html` | 200 | 50.453 | 1 | 0 | 0 | 0 | 0 | 0 |
| href www, `autoplay=true&mute=true` | `vphp-error-autoplay-mute.html` | 200 | 49.643 | 1 | 0 | 0 | 0 | 0 | 0 |
| href www, `lazy=true` | `vphp-error-lazy.html` | 200 | 49.611 | 1 | 0 | 0 | 0 | 0 | 0 |
| href www, `locale=es_LA` | `vphp-error-locale-es.html` | 200 | 49.646 | 0 | **1** | 0 | 0 | 0 | 0 |
| **Control bueno** reel, `show_text=false`, w500 | `vphp-bueno-texto-false.html` | 200 | 229.805 | 0 | 0 | **1** | **14** | 0 | 0 |
| **Control bueno** reel, `show_text=true`, w800 | `vphp-bueno-texto-true-w800.html` | 200 | 235.705 | 0 | 0 | **1** | **14** | 0 | 0 |

URL base: `https://www.facebook.com/plugins/video.php?href=<URL codificada>&show_text=…&width=…`. Ninguna variante del caso de error trae cuadro, `.mp4`, `og:image` ni póster. Con `locale=es_LA` el error sale en español (`Video no disponible` ×1, `Video Unavailable` ×0): la detección M5 debe comprobar ambas frases. El control bueno sí entrega reproductor (1 `<video>`, 14 `.mp4`, 3 `video_url`).

## 2. URL canónica del post con 4 User-Agents

| Prueba | Archivo | HTTP | Bytes | `og:title` | `og:image` | `<video>` | `.mp4` | Contenido |
|---|---|---|---|---|---|---|---|---|
| `www…/share/r/1JqVPz7de` sin seguir redirección, UA app | `canon-sinL-appua.html` | **302** | 0 | — | — | — | — | `Location: https://m.facebook.com/share/r/1JqVPz7de?wtsid=rdr_0vkCsvCtHfdXkKwn2` |
| misma, con redirección, UA app | `canon-appua.html` | 400 | 3.676 | 0 | 0 | 0 | 0 | pared de login |
| misma, UA `facebookexternalhit/1.1` | `canon-fbext.html` | 200 | 67.909 | 1 | 0 | 0 | 0 | pared de login: `og:title` = `Log in or sign up to view`, `og:description` = `See posts, photos and more on Facebook`; solo 2 `<img>` (icono de aviso + píxel de seguimiento) |
| misma, UA móvil Pixel 8 | `canon-movil.html` | 400 | 3.676 | 0 | 0 | 0 | 0 | pared de login |
| misma, UA Googlebot | `canon-googlebot.html` | 200 | 59.731 | 1 | 0 | 0 | 0 | pared de login (mismo `og:title`) |
| misma, UA escritorio | `canon-escritorio.html` | 400 | 1.542 | 0 | 0 | 0 | 0 | pared de login |
| `m.facebook.com/share/r/1JqVPz7de`, UA app | `canon-m-appua.html` | 400 | 3.676 | 0 | 0 | 0 | 0 | pared de login |
| `m.facebook.com/share/r/1JqVPz7de`, UA fbext | `canon-m-fbext.html` | 200 | 4.520 | 1 | 0 | 0 | 0 | pared de login |

**La redirección 302 NO revela la URL del video**: el destino sigue siendo `/share/r/1JqVPz7de` (solo añade `?wtsid=…`). Con UA de rastreador la página responde 200, pero es la pared de login, sin `og:image` ni video. El enlace `/share/r/` es opaco por diseño.

## 3. `fb.watch` y `/watch/`

| Prueba | Archivo | HTTP | Bytes | `Video Unavailable` | `<video>` | `.mp4` | `og:image` |
|---|---|---|---|---|---|---|---|
| `https://fb.watch/1168661422485798/` (reel Yofer), UA app | `fbwatch-yofer.html` | 400 | 1.542 | 0 | 0 | 0 | 0 |
| `https://www.facebook.com/watch/?v=1168661422485798`, UA app | `watch-yofer.html` | 400 | 3.676 | 0 | 0 | 0 | 0 |
| mismo `/watch/?v=`, UA fbext | `watch-yofer-fbext.html` | 200 | 470.747 | 0 | 0 | 0 | **2** |
| `video.php` con `href=https://fb.watch/1168661422485798/` | `vphp-href-fbwatch.html` | 200 | 50.434 | **1** | 0 | 0 | 0 |

`fb.watch` no sirve como `href` de `video.php`: hasta el video bueno devuelve `Video Unavailable` con esa forma. En cambio `/watch/?v=<id>` con UA fbext sí da `og:image` ×2 (mismo contenido que la página del reel, ver §8).

## 4. `m.facebook.com`

| Prueba | Archivo | HTTP | Bytes | `og:title` | `og:image` | `<video>` | `.mp4` |
|---|---|---|---|---|---|---|---|
| `m.facebook.com/reel/1168661422485798/`, UA app | `m-reel-yofer.html` | 400 | 3.676 | 0 | 0 | 0 | 0 |
| mismo, UA fbext | `m-reel-yofer-fbext.html` | 200 | 3.762 | 1 | 0 | 0 | 0 |

La versión móvil no aporta nada: 400 con UA normal; con fbext responde 200 pero solo la pared de login (3.762 bytes, sin `og:image`).

## 5. `oEmbed` v27.0 — campos exactos que devuelve hoy

| Prueba | Archivo | HTTP | Bytes | Campos devueltos |
|---|---|---|---|---|
| `oembed_video?url=<m.share>` | `oembed-canonica-m.json` | 200 | 423 | `provider_url`, `provider_name`, `html`, `type`, `version`, `width` |
| `oembed_video?url=<reel Yofer>` | `oembed-reel.json` | 200 | 430 | los mismos 6 campos |
| `oembed_video?url=<www.share>` | `oembed-error-www.json` | 200 | 425 | los mismos 6 campos |

**Confirmado: no hay `thumbnail_url`, no hay `author_name`, no hay `author_url`.** El `html` es solo `<div id="fb-root">` + `<script src="https://connect.facebook.net/en_US/sdk.js#xfbml=1&version=v27.0">` + `<div class="fb-video" data-href="…">`. `oembed_post` con el share devuelve **400** `(#100) Invalid parameter` (`oembed-post.json`).

## 6. `sdk.js` — ¿contiene alguna URL de póster o video?

| Archivo | HTTP | Bytes | `poster` | `.mp4` | `video` | `fbcdn` |
|---|---|---|---|---|---|---|
| `https://connect.facebook.net/en_US/sdk.js` (`sdk-en_US.js`) | 200 | 12.459 | 0 | 0 | 0 | 2 |
| `https://connect.facebook.net/es_LA/sdk.js` (`sdk-es_LA.js`) | 200 | 12.459 | 0 | 0 | 0 | 2 |

Es solo un cargador (12.459 bytes, idéntico en ambos idiomas). **Cero URLs de póster, cero `.mp4`, cero video.** Nada que aprovechar.

## 7. Otras variantes

| Prueba | Archivo | HTTP | Bytes | Resultado |
|---|---|---|---|---|
| `plugins/post.php?href=<share>&show_text=true&width=500` | `postphp-error.html` | 400 | 1.542 | pared de login |
| `mbasic.facebook.com/share/r/1JqVPz7de`, UA fbext | `mbasic-fbext.html` | 404 | 4.535 | solo `og:title` ×1 (pared de login); sin imagen ni video |
| reel Yofer, UA fbext | `reel-fbext.html` | 200 | 483.847 | ver §8 |
| reel Yofer, UA Googlebot | `reel-googlebot.html` | 200 | 825.333 | ver §8 |

## 8. La ruta que SÍ funciona (control bueno, URL canónica conocida)

Pedir la página canónica del reel con UA de rastreador:

| Prueba | Archivo | HTTP | Bytes | `og:title` | `og:image` | `.mp4` |
|---|---|---|---|---|---|---|
| `/reel/1168661422485798/`, UA fbext | `reel-fbext.html` | 200 | 483.847 | 1 | **2** | 0 |
| `/reel/1168661422485798/`, UA Googlebot | `reel-googlebot.html` | 200 | 825.333 | 1 | **2** | **3** |

Contenido real obtenido:

- **`og:title`**: `🦇🔥 ¡¡¡50.000 VISITAS!!! 🖤…` (título real del video).
- **`og:url`**: `https://www.facebook.com/yofer/videos/-50000-visitas-…/1168661422485798/` (URL canónica del video).
- **`og:type`**: `video.other`. `og:locale`: `en_US`.
- **`og:image`**: URL real de fbcdn (`https://scontent-ord5-1.xx.fbcdn.net/v/t15.5256-10/896273245_…_n.jpg?…&oe=6B0AA877`).
  - **Descargada sin sesión, con UA de la app: HTTP 200, `image/jpeg`, 157.257 bytes, JPEG real 1312×1576** (`/tmp/miniatura-real/ogimage-reel.jpg`, `ogimage-reel.headers`). Es el póster real del video. La URL lleva firma temporal (`oe=…`): hay que descargarla a local al guardar, como ya hace la app con las imágenes firmadas.
- **`.mp4` directos (solo con Googlebot, ×3)**: URLs `https://video-sjc3-1.xx.fbcdn.net/o1/v/t2/f2/m412/…mp4?…&oe=6B0A93AD` (URL completa en `/tmp/miniatura-real/mp4-url.txt`, 732 caracteres).
  - **Descargable sin sesión, con UA de la app: HEAD → HTTP 200 `video/mp4`; rango 0–99.999 → HTTP 206, 100.000 bytes, MP4 válido (`ftypmp41`)** (`/tmp/miniatura-real/video-reel-parcial.mp4`). El archivo de video mismo es accesible sin sesión cuando se conoce su URL.

## Conclusión

1. **Para enlaces `/share/r/` (los que produce el botón de compartir de Facebook y el enlace de Javier): ninguna de las 30 peticiones entrega foto real sin sesión.** `video.php` en todas sus variantes, la URL canónica con 5 UAs distintos, `fb.watch`, `/watch/`, `m.facebook.com`, `mbasic`, `post.php`, `oEmbed` y `sdk.js` devuelven pared de login o `Video Unavailable` con cero `<video>`, cero `.mp4`, cero `og:image` y cero póster. El servidor decide el error antes de que exista ningún reproductor.
2. **La foto real sin sesión SÍ existe, pero solo vía la URL canónica** (`/reel/<id>/` o `/watch/?v=<id>`) pedida con UA `facebookexternalhit/1.1` o `Googlebot`: `og:title` + `og:image` (póster real, descargable: 200, JPEG 1312×1576) y, con Googlebot, hasta el `.mp4` directo (descargable: 200/206, MP4 válido).
3. **El hueco es la resolución del `/share/r/`**: la redirección 302 no revela la URL canónica y la página del rastreador es la pared de login. Si la app no conoce la URL canónica, la ruta del rastreador no se puede usar para estos enlaces. Cualquier arreglo automático y sin sesión para `/share/r/` tendría que resolver primero esa URL por otra vía (no probada aquí).
4. **Dato para la detección M5**: con `locale=es_LA` el error del plugin sale en español (`Video no disponible`), así que el detector debe comprobar ambas frases.

## Archivos de evidencia

- Informe: `~/workspace/coleccion-enlaces/miniatura-real-curl.md` (este archivo)
- Respuestas y mediciones: `/tmp/miniatura-real/` — `resultados.tsv` (URL, UA, HTTP, bytes por petición), `conteos.tsv` (conteos por archivo), `pruebas.sh` (script que reproduce todas las peticiones), `ogimage-reel.jpg` + `ogimage-reel.headers` (póster real descargado), `video-reel-parcial.mp4` + `mp4-url.txt` (video real, primeros 100 KB), `oembed-*.json`, `sdk-*.js`, `vphp-*.html`, `canon-*.html`, `reel-*.html`, `watch-*.html`, `m-reel-*.html`, `fbwatch-yofer.html`, `postphp-error.html`, `mbasic-fbext.html`, `oembed-post.json`
- Límite: el enlace exacto de Javier (`1FKC67bv…`) no se pudo probar porque su URL completa es desconocida (truncada en la captura); se probó el enlace del mismo tipo `/share/r/1JqVPz7de` usado en Fix 4, con el mismo comportamiento de error servido por el servidor.
