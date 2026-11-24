# HILO F — Ola 2: segundo censo resolver /share/r/ → canónica

- **Fecha:** 2026-11-23 ~23:57–00:10 CST
- **Pregunta exacta** (copiada tal cual a cada modelo): "Un enlace facebook.com/share/r/XXXX es opaco: no revela su URL canónica ni el ID del video sin sesión. ¿Cómo se resuelve a su URL canónica o ID de video sin sesión, sin servidor y sin pagar? Responde en 3 líneas: método, por qué funciona sin servidor, y su punto débil."
- **Vía:** solo `~/workspace/omni-client.mjs` contra `http://localhost:20128/v1`. Sin llamadas directas.
- **Respuestas crudas:** `~/workspace/coleccion-enlaces/censo-resolver-share.json`
- **Enlace de prueba con curl:** `https://www.facebook.com/share/r/1JqVPz7de` (token real, el más citado en el repo)

## Participación

- Intentados: 31. Respondieron: **21**. Fallaron: 10.
- ddgw 6/6. groq 5/7 (qwen3.6-27b y compound: HTTP 404, modelo no disponible; qwen3.8-27b y gpt-oss-safeguard-20b entraron en reintento).
- auto 10/12 en primera pasada; thrifty y coding por timeout, respondieron en reintento único.
- **cohere 0/6: todos HTTP 429** (rate limit de OmniRoute/proveedor). Es stop duro: NO se reintentaron.
- 2 respuestas vacías (groq/openai/gpt-oss-20b, groq/openai/gpt-oss-safeguard-20b).

## Métodos distintos propuestos (21 respuestas con contenido: 19)

| # | Método | Modelos | Estado |
|---|---|---|---|
| 1 | Seguir la redirección 302 / leer `Location` (`curl -L`, `curl -I -L`, fetch `response.url`) | 11: mistral-small, gpt-oss-120b(groq), best-fast, cheap, fast, reasoning, coding, thrifty, qwen3.8, claude-haiku (parcial), gpt-5.4-mini (parcial) | **Ya probado, muerto** (ola 1: un solo salto `www → m.facebook.com/share/r/…?wtsid=rdr_…`, nada revela la canónica) |
| 2 | Extraer `og:url` / `canonical` / `video_id` del HTML final | 5: gemma4-31b, gpt-5.4-mini, claude-haiku, auto/chat, qwen3.8 | **Ya probado, muerto** (ola 1: pared de login; cero `og:url`/`canonical`/`video_id`) |
| 3 | Facebook Sharing Debugger (`facebook.com/debugger/`) | 1: tinfoil/gpt-oss-120b | **Nuevo, probado, muerto** (ver abajo) |
| 4 | urldecoder.org | 1: auto/best-chat | **Nuevo, probado, muerto** (ver abajo) |
| 5 | decodefacebook.com | 1: auto/best-free | **Nuevo, probado, muerto** (ver abajo) |
| 6 | Graph API con JSONP (`graph.facebook.com/XXXX?fields=permalink_url,video`) | 1: auto/smart | **Ya probado, muerto** (ola 1: Graph sin token → 400 OAuthException; además el token no es un ID) |
| 7 | "Herramienta de reconocimiento de URL" genérica (patrón + extensión/sitio) | 1: groq/allam-2-7b | Vago, sin concreción; equivale a los 8 servicios de terceros ya probados y muertos en ola 1 |
| 8 | "No se puede" (sin vía fiable anónima solo-cliente) | 1: ddgw/gpt-5.6-luna | Coincide con el veredicto de la ola 1 |

## Pruebas con curl de las 3 propuestas nuevas (medidas, no solo propuestas)

Enlace de prueba: `https://www.facebook.com/share/r/1JqVPz7de`. Salidas en `pruebas/`.

1. **Sharing Debugger** — `GET https://www.facebook.com/debugger/?q=<url-encoded share>` con UA Chrome Android:
   `HTTP 400`, final `https://m.facebook.com/login/?next=.../debugger/...`, 3.676 B, cuerpo "Error Facebook / something went wrong".
   **MUERTO: exige login.** El debugger no es público sin sesión.
2. **decodefacebook.com** — `GET https://decodefacebook.com/` y `http://decodefacebook.com/`:
   `HTTP 000`, 0 B en ambos. No hay conexión (el dominio no responde; parece inventado por el modelo).
   **MUERTO: el sitio no existe/no conecta.**
3. **urldecoder.org** — `GET https://urldecoder.org/` → `HTTP 000`; `http://` → `HTTP 403` Cloudflare "Just a moment..." (5.330 B, challenge anti-bot).
   Además, búsqueda web confirma qué es: una herramienta de **decodificar percent-encoding** (`%3A` → `:`), no un resolvedor de Facebook. Un token `/share/r/` no lleva percent-encoding: decodificarlo devuelve la misma URL.
   **MUERTO por partida doble: bloqueado por Cloudflare desde curl y conceptualmente inútil para este problema.**

## Consenso

- **11 de 19 respuestas con contenido proponen la redirección 302** — el método más votado, y el que la ola 1 ya midió como muerto con ~70 peticiones. Los modelos alucinan una redirección a la canónica que Facebook no emite sin sesión.
- El segundo bloque (5 modelos) propone leer `og:url`/`canonical` del HTML, también muerto en ola 1 (pared de login).
- Solo **1 modelo (gpt-5.6-luna) respondió correctamente que no hay vía fiable**; los demás asumen un comportamiento de Facebook que no existe.
- **Ningún método nuevo sobrevivió a la medición con curl: 0 de 3.**

## Veredicto

**Todos murieron.** El segundo censo no aporta ninguna vía nueva que funcione: las 2 ideas realmente nuevas (debugger, decodefacebook.com, urldecoder.org) se probaron con curl contra un enlace `/share/r/` real y las 3 murieron medidas. El consenso de los modelos (seguir el 302) es un espejismo: la ola 1 ya demostró que esa redirección no revela la canónica. El veredicto de la ola 1 queda confirmado por segunda vía independiente: **resolver /share/r/ → canónica sin sesión, sin servidor y sin pagar no tiene método conocido; solo queda la WebView con sesión (decisión de Javier, con riesgo de Términos).**

## Archivos

- `~/workspace/coleccion-enlaces/censo-resolver-share.json` (respuestas crudas, 31 intentos)
- `~/workspace/coleccion-enlaces/investigacion-ola2/F/censo.mjs`, `censo-retry.mjs` (scripts)
- `~/workspace/coleccion-enlaces/investigacion-ola2/F/pruebas/` (debugger.html, urldecoder-http.html)
- Este informe: `~/workspace/coleccion-enlaces/investigacion-ola2/F/INFORME-HILO-F.md`
