# Fase 4 — Clasificación (resumen)

## Estado: TERMINADA — CI en verde

- Run de CI: **45131533874** — `success` (5m25s, commit `eff2a4a`).
  - `testDebugUnitTest`: BUILD SUCCESSFUL.
  - `assembleDebug`: BUILD SUCCESSFUL.
- Artefacto: **coleccion-debug** (APK de depuración, 13.627.669 bytes),
  en el run 45131533874 del repo `djjavierc-droid/coleccion-enlaces`.
- Commits en `main`: a19cb90 (clasificador + listas + pruebas),
  45050fc (workflow: generación del archivo sensible desde el secreto),
  eff2a4a (corrección de 1 prueba, ver abajo).

## Archivos creados/modificados

- `app/src/main/java/com/coleccionenlaces/app/domain/Clasificador.kt` —
  `object Clasificador` con `clasificar(titulo, descripcion, url): String`,
  `puntajes(...)` (diagnóstico), `clasificarEnlace(enlace): String?`
  (prioridad manual) y las funciones internas de normalización y
  extracción de host.
- `app/src/main/java/com/coleccionenlaces/app/domain/PalabrasClave.kt` —
  listas de palabras clave y dominios temáticos de las 16 categorías
  no sensibles, en español.
- `app/src/main/java/com/coleccionenlaces/app/domain/XxxKeywords.kt` —
  lista de la categoría sensible. SOLO EN LOCAL: está en `.gitignore`
  y NUNCA se commitea (verificado con `git check-ignore` y con
  `git status`, donde no aparece).
- `app/src/test/java/com/coleccionenlaces/app/domain/ClasificadorTest.kt` —
  29 pruebas unitarias de clasificación.
- `.github/workflows/build.yml` — nuevo paso "Generar archivo de
  palabras clave sensibles" ANTES de las pruebas: decodifica el
  secreto `XXX_KEYWORDS_B64` (base64) al archivo local y falla si el
  secreto no existe. El contenido del secreto no se imprime en ningún
  log. El paso quedó en verde en el run 45131533874.

## Sistema de puntaje y umbral

- Cada palabra o frase clave distinta que aparece en el título o en
  la descripción suma **2 puntos** a su categoría (una misma frase
  cuenta una sola vez aunque se repita).
- El dominio de la URL suma **4 puntos**, pero SOLO para dominios
  claramente temáticos (los de `PalabrasClave.dominiosPorCategoria` y
  los del archivo local sensible). Un dominio genérico (buscador, red
  social, portal de videos) no suma nada.
- Gana la categoría con más puntos. En empate gana la primera en el
  orden de `CategoriasPredefinidas.lista` (determinista).
- **Umbral mínimo: 2 puntos.** Si ninguna categoría llega a 2, el
  resultado es `"Otros"`. Una sola palabra clave ya clasifica.
- Normalización: minúsculas, sin acentos y puntuación convertida en
  espacios; la coincidencia exige la frase completa entre límites de
  palabra ("moto" no coincide con "motocicleta", "cine" no coincide
  con "medicina"). Las listas usan frases específicas, no palabras
  genéricas ("video", "cómo", "tutorial" solos no clasifican), para
  que los casos ambiguos caigan en "Otros".

## Decisiones de diseño

- **Se devuelve el nombre, no el id.** `clasificar` devuelve un
  `String` con el nombre exacto de `CategoriasPredefinidas` (o
  `"Otros"`). El repositorio de la Fase 5 resolverá el nombre a
  `categoriaId` con `CategoriaDao.obtenerPorNombre`. Así el
  clasificador no depende de Room ni de los ids y sigue siendo una
  función pura comprobable sin Android. No se crean categorías
  automáticamente: el resultado es siempre una de las 17
  predefinidas, nunca un nombre nuevo.
- **Prioridad manual.** `clasificarEnlace(enlace)` devuelve `null`
  cuando `enlace.categoriaManual` es true: la elección manual tiene
  prioridad y NO se sobrescribe. Cuando es false, devuelve el nombre
  que da `clasificar` con el título, la descripción y la URL del
  enlace. El repositorio (Fase 5) solo aplica el resultado cuando no
  es `null` y lo resuelve a `categoriaId`.

## Archivo sensible y secreto (verificado)

- El secreto `XXX_KEYWORDS_B64` existe en el repo
  (`gh secret list` lo muestra, creado el 2026-11-23). Se creó ANTES
  de commitear cualquier código que referencia el archivo, con el
  contenido base64 calculado en local y sin imprimirlo; el archivo
  temporal con el base64 se destruyó después.
- El workflow genera el archivo en CI desde el secreto antes de
  compilar (paso en verde en los runs 45131109313 y 45131533874).
- Las palabras de esa lista no aparecen en ningún reporte, resumen,
  mensaje de commit, log ni archivo commiteado. Los 3 casos de prueba
  de esa categoría construyen sus entradas en tiempo de ejecución a
  partir de la lista del archivo y llevan nombres genéricos
  ("caso XXX 1", "caso XXX 2", "caso XXX 3"). Esas pruebas corren
  solo en CI (con el secreto) y en la copia local.

## Pruebas: 73 unitarias, todas en verde en CI

- `ClasificadorTest` (29, nuevas): una por categoría con palabras
  clave (16 categorías, incluida la sensible por lista en tiempo de
  ejecución); cocina solo por dominio temático; una sola palabra ya
  alcanza el umbral; texto vacío y dominio genérico caen en "Otros";
  caso ambiguo sin palabras clave cae en "Otros"; las palabras de
  otra categoría pesan más que un dominio temático; empate lo gana
  la primera categoría en el orden de las predefinidas; una palabra
  dentro de otra no coincide; `puntajes` muestra la ganadora y
  "Otros" en cero; `clasificarEnlace` devuelve null con categoría
  manual y clasifica cuando no es manual; 3 casos de la categoría
  sensible (palabra en título, palabra en descripción, dominio).
- `ParseadorVistaPreviaTest` (10), `ServicioVistaPreviaTest` (10),
  `EnlaceDaoTest` (16), `CategoriaDaoTest` (6) y `TemaTest` (2) de
  las Fases 1–3: siguen pasando. Total: 73 pruebas, 0 fallos.

## Qué quedó funcionando

- Clasificación pura y local (sin red) de título + descripción + URL
  a nombre de categoría, con puntaje documentado y umbral.
- Listas de palabras clave y dominios de las 16 categorías no
  sensibles en un archivo normal y commiteable.
- Lista sensible solo en local + secreto en GitHub + generación en
  CI antes de compilar, sin exponer su contenido.
- Lógica de prioridad manual lista para que la use el repositorio
  de la Fase 5.
- `testDebugUnitTest` y `assembleDebug` en verde en CI
  (run 45131533874).

## Qué falta (fases siguientes)

- Fase 5: repositorio y ViewModels (aquí se usará
  `Clasificador.clasificarEnlace` y se resolverá el nombre a
  `categoriaId` con `CategoriaDao.obtenerPorNombre`; `categoriaId`
  null seguirá tratándose como "Otros").
- Fases 6–10 según el plan. No hay UI en esta fase: sin lógica de
  negocio en composables, como pide el plan.

## Corrección durante la fase (1 fallo de CI, resuelto)

1. Run 45131109313, `testDebugUnitTest` FAILED (73 pruebas, 1 fallo):
   - `puntajes muestra la ganadora y Otros en cero`: la propia prueba
     tenía la expectativa equivocada (esperaba "Otros" para un título
     que clasifica como "Cocina"). El clasificador estaba bien.
     Corregido en la prueba (commit eff2a4a); el siguiente run quedó
     en verde.

## Notas

- Verificación real solo en CI: Gradle local está prohibido en esta
  VM (el daemon necesita TCP local) y no se pidió ningún permiso de
  puerto.
- No se tocaron los archivos del wrapper de Gradle.
- No se agregó ninguna función fuera del plan.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.

## Ideas futuras

- Ampliar las listas de palabras clave y dominios con los enlaces
  reales que el usuario guarde (si una categoría se clasifica mal
  con frecuencia, añadir sus frases típicas).
- Contar también las palabras del dominio y de la ruta de la URL
  (hoy solo el host suma puntos por dominio; la ruta solo se mira
  a través del título y la descripción).
