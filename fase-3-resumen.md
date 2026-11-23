# Fase 3 — Vista previa (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45129549881** — `success` (commit `eb73a76`).
  - `testDebugUnitTest`: BUILD SUCCESSFUL (2m50s).
  - `assembleDebug`: BUILD SUCCESSFUL (1m38s).
- Artefacto: **coleccion-debug** (APK de depuración, 13.618.447 bytes),
  en el run 45129549881 del repo `djjavierc-droid/coleccion-enlaces`.
- Commits en `main`: 58d32cd (servicio), df0b845 (pruebas),
  eb73a76 (corrección de 2 fallos de CI, ver abajo).

## Archivos creados/modificados

- `app/src/main/java/com/coleccionenlaces/app/domain/VistaPrevia.kt` —
  modelo `VistaPrevia` (titulo, descripcion, urlImagen, todos
  nullables, con `estaVacia`), `TipoErrorVistaPrevia`
  (SIN_INTERNET, URL_INVALIDA, TIEMPO_AGOTADO, SIN_VISTA_PREVIA) y
  `ResultadoVistaPrevia` (sealed: `Exito` / `Error`, ambas con la
  vista previa obtenida, vacía cuando no hubo ninguna).
- `app/src/main/java/com/coleccionenlaces/app/domain/ParseadorVistaPrevia.kt` —
  función pura `parsearHtml(html, urlBase): VistaPrevia` (Jsoup, sin
  red). Extrae `og:title` / `og:description` / `og:image`, con
  respaldo a `<title>`, `meta name=description` y primera `<img>`.
  Las URLs relativas de imagen se resuelven a absolutas contra la
  URL base; los valores en blanco se tratan como ausentes.
- `app/src/main/java/com/coleccionenlaces/app/data/ServicioVistaPrevia.kt` —
  `ServicioVistaPrevia` (OkHttp): timeout de 10 segundos en
  conexión, lectura y escritura; User-Agent de navegador; descarga
  en `Dispatchers.IO` y delega el parseo en `parsearHtml`. Valida la
  URL antes de tocar la red (solo http(s) con host). Nunca lanza
  excepciones fuera: timeout → TIEMPO_AGOTADO, host desconocido o
  fallo de conexión → SIN_INTERNET, HTTP no exitoso o página sin
  datos → SIN_VISTA_PREVIA con la vista previa vacía. El cliente
  OkHttp y la función de parseo son inyectables para las pruebas.
- `app/src/test/resources/{og-completo, og-imagen-relativa, sin-og,
  sin-imagen, vacio, malformado}.html` — HTML de ejemplo para las
  pruebas, sin depender de internet.
- `app/src/test/java/com/coleccionenlaces/app/domain/ParseadorVistaPreviaTest.kt` —
  10 pruebas del parseo puro.
- `app/src/test/java/com/coleccionenlaces/app/data/ServicioVistaPreviaTest.kt` —
  10 pruebas del servicio, con interceptores de OkHttp que simulan
  respuestas y fallos (sin red).
- Eliminado `app/src/main/java/com/coleccionenlaces/app/domain/.gitkeep`
  (el paquete ya tiene archivos reales).

## Pruebas: 44 unitarias, todas en verde en CI

- `ParseadorVistaPreviaTest` (10): OG completas ganan a los
  respaldos; imagen OG relativa resuelta a absoluta; sin OG usa
  title, meta description y primera imagen (relativa resuelta);
  sin imagen (urlImagen null, resto presente); HTML vacío (todo
  null); HTML malformado (recupera lo que puede); og:title en
  blanco usa `<title>`; sin og:description usa meta description;
  imagen OG absoluta se conserva; sin metadatos (todo null).
- `ServicioVistaPreviaTest` (10): URL no válida, vacía, con esquema
  ftp y sin host → URL_INVALIDA sin tocar la red; timeout simulado
  → TIEMPO_AGOTADO; host desconocido → SIN_INTERNET; 404 →
  SIN_VISTA_PREVIA con vista vacía; 200 con OG completa → Exito;
  200 con HTML vacío → SIN_VISTA_PREVIA; 200 sin OG → Exito con
  los respaldos del parseo.
- `EnlaceDaoTest` (16), `CategoriaDaoTest` (6) y `TemaTest` (2) de
  las Fases 1–2: siguen pasando. Total: 44 pruebas, 0 fallos.

## Qué quedó funcionando

- Descarga de la página con OkHttp (10 s, User-Agent de navegador)
  y extracción de vista previa con Jsoup, con respaldos y URLs
  relativas resueltas.
- Errores distinguidos sin romper el flujo: el servicio nunca lanza
  excepciones; sin vista previa devuelve la vista previa vacía, de
  modo que el enlace se podrá guardar igual en la Fase 7 (con el
  dominio como título).
- `testDebugUnitTest` y `assembleDebug` en verde en CI (run 45129549881).

## Qué falta (fases siguientes)

- Fase 4: clasificación (función pura + archivo separado y secreto CI).
- Fase 5: repositorio y ViewModels (aquí se usará
  `ServicioVistaPrevia.obtenerVistaPrevia` y se resolverá la vista
  previa vacía como dominio de título).
- Fases 6–10 según el plan. No hay UI en esta fase: sin lógica de
  negocio en composables, como pide el plan.

## Correcciones durante la fase (1 fallo de CI, resuelto)

1. Run 45129074193, `testDebugUnitTest` FAILED (44 pruebas, 2 fallos):
   - `sin OG usa title...`: la primera `<img src="">` no se saltaba —
     `absUrl("")` de Jsoup resuelve a la propia URL base, no a vacío.
     Corregido en el parseador: se filtra por el `src` crudo no
     vacío antes de resolver (commit eb73a76).
   - `HTML malformado...`: el `<title>` sin cerrar se tragaba los
     meta como texto (el parser HTML trata `<title>` como texto crudo
     hasta `</title>`), así que `og:title` no existía como elemento.
     Corregido el HTML de prueba: título cerrado y el resto de
     etiquetas sin cerrar (commit eb73a76). El siguiente run quedó
     en verde.

## Notas

- Verificación real solo en CI: Gradle local está prohibido en esta VM
  (el daemon necesita TCP local) y no se pidió ningún permiso de puerto.
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función fuera del plan.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.

## Ideas futuras

- Aceptar también `og:image:secure_url` y `twitter:image` como
  respaldo de imagen cuando no hay `og:image` (fuera del plan:
  el plan pide solo og:image y primera imagen).
- Seguir redirecciones con límite y rechazar Content-Type no HTML
  antes de parsear (hoy OkHttp sigue redirecciones por defecto y
  se parsea cualquier cuerpo 200).
