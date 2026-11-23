# Resumen Fix 2 — Colección de enlaces v1.0.1

Versión 1.0.1 (versionCode 2, instala encima de la 1.0.0). Estado: **código
completo y verificado en local; pendiente de CI por un bloqueo de
facturación de GitHub (ver "Estado de CI" al final)**. Nada entregado a Javier.

## El defecto: los vídeos de IA caían en "Otros"

El título "Contratamos a una IA... y el primer día ya vendía croquetas en
Marte..." (vídeo de EterniCodIA) contiene "IA" pero no "inteligencia
artificial" escrita. La lista de "Tecnología y celulares" tenía
"inteligencia artificial" pero no "ia" sola, así que puntuaba 0 y el enlace
caía en "Otros".

## Núcleo del Fix 2 (commit 85304f5, CI en verde)

Palabras añadidas a "Tecnología y celulares": **"ia", "chatgpt", "gemini",
"claude", "copilot", "robot", "automatizacion"**. La coincidencia es por
palabra completa, como ya hacía el clasificador: "ia" no coincide dentro de
otras palabras.

Pruebas nuevas en `ClasificadorTest` (10):

- Título y descripción **exactos** del vídeo de EterniCodIA clasifican
  "Tecnología y celulares" (título: "Contratamos a una IA... y el primer día
  ya vendía croquetas en Marte..."; descripción: "Hoy hemos contratado a
  una"; URL del /share/r/ de Facebook).
- Una prueba por palabra nueva ("La nueva IA", "ChatGPT responde preguntas",
  "Probando Gemini", "Probando Claude", "Probando Copilot", "Mi nuevo
  robot", "Todo sobre automatizacion").
- Dos negativas: "La historia de mi familia" y "Energía solar en casa" **no**
  clasifican como Tecnología por la "ia" interior.

Este núcleo desbloqueó además las pruebas de `actualizarTextoDesdeWebView`
(repositorio y ViewModel), que reclasifican a Tecnología con el texto del
reproductor (ver fix-1-resumen.md, run 45162885547 → 45164285011).

## Punto 4 — Diccionario completo (commit local d79a65b, pendiente de CI)

Javier pidió agregar **todas** las palabras posibles para que el clasificador
detecte cada cosa. Se ampliaron las 15 listas de `PalabrasClave.kt` con **15
palabras o frases nuevas por categoría: 225 palabras nuevas en total** (las
listas también sirven como datos de entrenamiento del clasificador LiteRT,
ver abajo). Sin categorías nuevas: todo dentro de las 17 predefinidas.
"Otros" no tiene lista. La lista sensible sigue solo en local
(`XxxKeywords.kt`, en `.gitignore`, nunca commiteada).

### Ejemplos de Javier

- **bola, cometa, risueño → Entretenimiento.** "bola" (juego con pelota),
  "cometa" (cometa de jugar / cometa del cielo, sin categoría de ciencia),
  "risueño" (alegre, divertido).
- **paleta → Juegos** (mando de juego: ping pong, pádel). "paleta" también
  es helado (Cocina) y paleta de colores (DIY/Moda); se deja en Juegos, la
  más específica para el uso como mando, y se documenta.
- **sueño, cama → Salud y ejercicio** (decisión de Javier, aunque "cama"
  también sea un mueble).

### Reglas aplicadas

- Coincidencia por palabra completa (la del clasificador): una palabra clave
  suma 2 puntos, el umbral es 2 (una sola palabra ya clasifica).
- Frases específicas antes que palabras sueltas que contaminen.
- **"medicina" NO se añade**: el test "Medicina moderna" debe seguir en
  "Otros" ("medicina" contiene "cine").
- Sin duplicados entre listas. Sin solape con la lista sensible
  (verificado en local con la lista real: **0 coincidencias**).
- No se añadieron palabras que romperían pruebas existentes: ni "video",
  "semana", "mira" ni "esto" (el test "El mejor video de la semana" debe
  seguir en "Otros"); ni "short" (confunde los Shorts de vídeo con la ropa);
  ni "medias" ("a medias"); ni "bateria" sola ("batería del auto" y
  "batería del celular" son frases de dos categorías distintas); ni "skin"
  ("skin care" es belleza, no juegos); ni "obra" sola ("obra de arte").

### Decisiones ambiguas (palabra en dos categorías → la más específica)

| Palabra | Queda en | La otra lectura |
|---|---|---|
| cama | Salud y ejercicio | Mueble (DIY) — decisión de Javier |
| paleta | Juegos | Helado (Cocina) / paleta de colores (DIY, Moda) |
| cometa | Entretenimiento | Astro (no hay categoría de ciencia) |
| tacos | Cocina | Calzado deportivo (en empate gana Cocina por orden) |
| pasta | Cocina | Pasta dental (Salud) |
| frenos | Mecánica | "frenos ABS" (Autos); en empate gana Mecánica por orden |
| jugador | — | No añadida: el deporte es Entretenimiento, no Juegos |
| entrevista | Noticias | Entrevista a famosos (Entretenimiento); en empate gana Entretenimiento por orden |
| visa | Viajes | Tarjeta Visa (Dinero) |
| pension | Dinero | Pensión como alojamiento (Viajes) |
| volcan | Viajes | Erupción como noticia (Noticias) |
| catarata | Viajes | Catarata del ojo (Salud) |
| rio / lago | Pesca y campo | Lugares de viaje (Viajes) |
| pasto | Jardín y plantas | Potrero (Pesca y campo) |
| madera | DIY | Vigas de construcción (Construcción) |
| pantalla | Tecnología | Pantalla de cine (Entretenimiento) |
| remodelacion | Construcción | Remodelar una habitación (DIY) |
| cocina | Cocina | La cocina como habitación (Construcción); en empate gana Cocina por orden |

Orden de las predefinidas (para los empates): Cocina, Mecánica,
Construcción, Contenido XXX, Entretenimiento, Tecnología, Autos, Salud,
Dinero, Noticias, Juegos, DIY, Jardín, Pesca, Moda, Viajes, Otros.

### Pruebas

Un test en bucle por categoría (15 tests), una aserción por palabra nueva
con el título "Mira esto: \<palabra\>" ("Mira esto" es neutro, probado que
cae en "Otros"): **225 aserciones**. Verificado en local con kotlinc:
**225/225 palabras clasifican en su categoría, 0 solapes con la lista
sensible y todas las regresiones existentes en verde** (incluidas
"Medicina moderna" → Otros, "El mejor video de la semana" → Otros y el
empate "Receta de pastel y partido de futbol" → Cocina).

## El clasificador LiteRT — BLOQUEADO (falta el documento del consenso)

El alcance incluye el clasificador del consenso de 16 modelos por
OmniRoute, pero **el documento del consenso no se encontró**: ni en
`~/workspace` ni en memoria hay ningún archivo que mencione
MobileBERT/DistilBERT/LiteRT/tflite (las únicas menciones de "MobileBERT"
son documentos de LLMLingua-2 en `omniroute/pkg/r5-run/package/README.md`,
sin relación). **No se inventó el diseño: no hay código LiteRT, ni .tflite,
ni reentrenamiento en esta entrega. Falta que el padre entregue el
documento del consenso.**

Diseño registrado (del encargo, no inventado aquí) para cuando llegue el
documento:

- TensorFlow Lite (LiteRT) con MobileBERT o DistilBERT cuantizado INT8
  (~15-25 MB), entrenado con título + descripción + hashtags del enlace,
  100% en el teléfono, sin servidor, inferencia <50 ms. Se entrena una vez
  fuera del teléfono y el .tflite se empaqueta en la app. TikTok oEmbed no
  da título; ML Kit no clasifica texto (solo imágenes): el clasificador va
  directo con LiteRT, no con ML Kit.
- **Regla de respaldo (corrección de Javier, sustituye la caída a palabras
  clave)**: si el clasificador NO está seguro, NO cae a las palabras clave:
  lo pone en **"Otros" directamente**. Seguro → su categoría; no seguro →
  "Otros"; corrección manual de Javier → aprendizaje. Las palabras clave
  siguen existiendo como datos de entrenamiento, pero en ejecución la regla
  es esa. Las pruebas unitarias deben reflejarlo: baja confianza va a
  "Otros", nunca a una categoría adivinada.
- **La app es DEFINITIVA, una sola versión, SIN actualizaciones**: todo el
  aprendizaje ocurre DENTRO del teléfono, en esa única versión:
  1. El modelo base se entrena una vez antes de entregar, con los datos más
     completos posibles (las listas ampliadas + ejemplos de títulos por
     categoría); su calidad inicial es fija.
  2. Aprendizaje en el teléfono = reentrenamiento en el dispositivo de solo
     la última capa (transfer learning / TFLite Model Personalization): las
     correcciones de Javier se guardan en Room y periódicamente (tras
     acumular varias, con el teléfono en reposo) se reentrena solo la cabeza
     del modelo, sin servidor y sin actualización.
  3. Capa local inmediata: cada corrección actualiza al instante los pesos
     palabra→categoría en Room, así el aprendizaje se nota desde la primera
     corrección sin esperar al reentrenamiento.
- **Límites honestos**: el reentrenamiento completo del modelo NO es posible
  en el teléfono, solo la cabeza. Sin el documento del consenso no se puede
  verificar ni el tamaño, ni la latencia, ni el umbral de confianza exactos.

## Archivos (Fix 2 + punto 4)

Producción: `domain/PalabrasClave.kt` (núcleo + 225 palabras).
Pruebas: `domain/ClasificadorTest.kt` (10 tests del núcleo + 15 tests en
bucle del punto 4, 225 aserciones).

## Estado de CI

| Run | Commit | Resultado |
|---|---|---|
| 45164285011 | 85304f5 (Fix 2 núcleo) | **SUCCESS** (248 tests) |
| 45165320874 | 5e65928 ("Acerca de") | FAILURE sin compilar: bloqueo de facturación de GitHub (el job no arrancó; reintentado, mismo fallo) |

**Pendiente por el bloqueo**: el commit local `d79a65b` (diccionario, 225
palabras + 15 tests) y el `a230ac7` (verificación (a), ver
fix-1-resumen.md) están commiteados en local y **no se han subido** para no
encadenar runs que no pueden arrancar. En cuanto se resuelva la facturación
hay que: subir ambos, esperar el CI en verde (273 tests esperados),
descargar el artefacto `coleccion-debug` y verificarlo con `aapt2` +
`apksigner verify` + `sha256sum`. **El APK 1.0.1 aún no está verificado: no
hay artefacto.**

## Qué queda por probar en el teléfono real de Javier

- Guardar el enlace de EterniCodIA y comprobar que cae en "Tecnología y
  celulares" (con su título automático y su miniatura).
- Mover un enlace a otra carpeta y comprobar que la siguiente vez clasifica
  mejor (aprendizaje; solo cuando el clasificador LiteRT esté integrado).
- Probar títulos con las palabras nuevas del diccionario (bola, cometa,
  paleta, sueño, cama, risueño) y comprobar su categoría.
