# Hilo E (ola 2) — Etiquetas de visión sobre cuadros reales

- **Fecha:** 2026-11-23 ~18:00 CST. Duración real ~5 min de cómputo.
- **Regla:** sin cambios de código, sin commits, sin escribir a Javier. Solo medición.
- **Pregunta:** la ola 1 probó OCR→clasificación (0/30 cuadros, 0/3 videos) y NUNCA probó
  etiquetas de visión (el equivalente de ML Kit Image Labeling). ¿Clasifican mejor las
  etiquetas de imagen?
- **Material:** los mismos 15 cuadros + 3 og:image de `/tmp/investigacion-1-hora/B/`,
  copiados a `frames/` en este directorio (18 JPG). Videos sustitutos con URL canónica
  conocida: Yofer 1168661422485798 (esperado **Entretenimiento**), TEC STEPX Neo
  1395074825861831 (esperado **Tecnología y celulares**), Free Fire 914666554561073
  (esperado **Juegos**).
- **Casos exactos de Javier:** EterniCodIA, Soylorainegodoy y el video de juego siguen SIN
  ID canónico en el repo (verificado por grep: los nombres solo aparecen en tests y
  resúmenes, sin URL `/reel/<id>/` descargable; el único `/share/r/` en el repo es
  `1JqVPz7de`, otro enlace). Se trabajó con los 3 sustitutos, como en la ola 1.

## Métodos probados (2, ambos locales, sin cuenta, sin servidor)

1. **MobileNetV2 ImageNet (Keras, TF 2.21, venv de entrenamiento)** — el equivalente
   más cercano a ML Kit Image Labeling: etiquetas genéricas de objetos (1000 clases
   ImageNet), top-5 por cuadro, mapeadas a mano a las 16 categorías de la app
   (`mobilenet.py`). Pesos descargados de Googleapis (14 MB). Corrección aplicada en el
   análisis: el mapeo original fallaba por guiones bajos (`cellular_telephone` vs
   `cellular telephone`); el re-mapeo corregido está en el análisis, no en el JSON
   crudo (el JSON guarda las etiquetas crudas, así que es reproducible).
2. **CLIP zero-shot ViT-B/32 (Xenova `clip-vit-base-patch32`, ONNX cuantizado,
   onnxruntime)** — clasificación directa contra las 16 categorías: el cuadro se compara
   con 16 descripciones de texto. Dos pasadas de prompts: **EN** (idioma nativo de
   CLIP) y **ES** (idioma de la app). Modelos: `clip-onnx/vision_model_quantized.onnx`
   (89 MB) + `text_model_quantized.onnx` (64 MB) + tokenizer. Determinista: la
   segunda pasada reprodujo los valores idénticos. Velocidad: 18 imágenes en ~13 s
   (~0,7 s/cuadro en CPU de esta VM, 4 hilos).

## Veredicto por video (voto por mayoría de los 5 cuadros)

| Video (esperado) | MobileNet (ImageNet) | CLIP zero-shot EN | CLIP zero-shot ES | og:title ola 1 (referencia) |
|---|---|---|---|---|
| **Yofer** (Entretenimiento) | ❌ Otros (4/5 Otros; 1/5 Entretenimiento vía `accordion` 0,046) | ❌ **Contenido XXX 4/5** (0,36–0,62) | ❌ Juegos 2/5, Dinero 2/5 (0/5 aciertos) | 0,532 ✓ (hilo B) / Otros (hilo D, inestable) |
| **TEC STEPX Neo** (Tecnología) | ❌ Otros (1/5 Tecnología vía `cellular_telephone` 0,39) | ❌ **Juegos 4/5** (0,37–0,76) | ✅ **Tecnología 2/5** (pluralidad; prob. media 0,174) | 0,984 ✓ |
| **Free Fire** (Juegos) | ❌ Tecnología 3/5 (`monitor`/`screen`) | ✅ **Juegos 4/5** (0,88–0,97) | ✅ **Juegos 3/5** (0,31–0,61) | 0,468 → Otros ~ / 0,630 ✓ (inestable) |
| **TOTAL (mayoría)** | **0/3** | **1/3** | **2/3** | 2/3 (inestable) |

**Por cuadro (15 cuadros):** MobileNet 2/15 correctos. CLIP-EN 4/15. CLIP-ES 5/15.
**og:image sola (3 imágenes):** MobileNet 0/3. CLIP-EN 1/3 (solo Yofer → Entretenimiento
0,610). CLIP-ES 2/3 (Yofer 0,406 ✓, TEC 0,591 ✓, Free Fire → Autos 0,160 ✗).

## ¿Ayuda votar entre varios cuadros?

**Sí, un poco; no rescata los casos perdidos.** La mayoría y la probabilidad media
coinciden en los 6 casos (3 videos × 2 idiomas CLIP): TEC-ES se salva por pluralidad
con solo 2/5 cuadros correctos (los otros 3 se dispersan en Noticias/Salud/XXX), y
Free Fire es estable en ambas. Pero Yofer es 0/5 en los dos idiomas de CLIP y 1/5 en
MobileNet: ningún voto lo rescata. Un cuadro negro (Free Fire t12.72) vota Juegos
0,438 en EN: los cuadros vacíos también votan, con confianza no despreciable.

## Los 3 modos de fallo, con causa concreta

1. **Contenido XXX es un imán de falsos positivos (CLIP-EN).** Yofer 4/5 cuadros →
   Contenido XXX (0,356–0,622); la og:image de Free Fire → XXX 0,535. El cuadro de
   Yofer verificado visualmente es un póster oscuro estilo terror (cara ensangrentada
   con ojos rojos, mano con sangre sobre la cara, disco de vinilo dorado, texto
   "50K"): sin instrumento visible, oscuro, con piel/cara. CLIP asocia oscuridad +
   persona al prompt adulto. **Peligro activo:** si la app usara este prompt, un video
   musical se archivaría como pornografía con confianza 0,62.
2. **El prompt "phone screen" se vuelve contra TEC (CLIP-EN).** El prompt de Juegos era
   "video game gameplay on a phone screen, gaming" y el video de TEC ES una grabación
   de pantalla de teléfono: 4/5 cuadros → Juegos (hasta 0,762). La redacción del
   prompt, no el contenido, decide. En ES el prompt ("gameplay de videojuego en la
   pantalla de un celular") pesó menos y TEC se salvó.
3. **ImageNet no tiene el concepto "videojuego" ni "video musical".** MobileNet ve
   `book_jacket` 0,753 en el meme de Free Fire, `comic_book` 0,387 en Yofer,
   `parachute`/`projectile` 0,763 en TEC (la única vez que acierta TEC es el cuadro
   t48.20 con el teléfono visible: `cellular_telephone` 0,392). Las etiquetas de
   objetos genéricos no expresan el tema de un video; el mapeo manual a categorías
   es en su mayor parte adivinanza sobre ruido (confianzas top-1 de 0,04–0,19 en
   la mayoría de cuadros).

## Comparación con OCR (ola 1) y con og:title

| Señal | Videos (mayoría) | Cuadros |
|---|---|---|
| OCR → clasificador (ola 1) | 0/3 | 0/30 |
| **MobileNet ImageNet (este hilo)** | **0/3** | **2/15** |
| **CLIP zero-shot EN (este hilo)** | **1/3** | **4/15** |
| **CLIP zero-shot ES (este hilo)** | **2/3** | **5/15** |
| og:title → clasificador (ola 1) | 2/3, inestable entre hilos | — |

**Conclusión: las etiquetas de visión clasifican MEJOR que el OCR (0/3 → 2/3 con
CLIP-ES por mayoría), pero NO sirven como clasificador fiable.** El mejor método de
visión (CLIP-ES) falla el video musical —el caso más común— en los 5 cuadros, y
produce un falso positivo grave (XXX 0,62). Ningún método de visión probado llega
al nivel del og:title (2/3) con estabilidad, y el og:title sigue siendo la mejor
señal disponible cuando existe. ML Kit Image Labeling, la opción real on-device de
la app, es del tipo ImageNet (400+ etiquetas de objetos genéricos): este hilo
mide su techo en **0/3 videos**; no es la vía. CLIP zero-shot no está en ML Kit:
requeriría empaquetar un ViT-B/32 (~350 MB, ~0,7 s/cuadro en CPU) en la app, y
aun así falla 1/3 con un falso XXX.

## Recomendación (medida, no opinión)

- No usar etiquetas ImageNet/ML Kit como señal de clasificación: techo medido 0/3.
- No usar CLIP zero-shot con el prompt XXX tal como está: falso positivo 0,62 en un
  video musical. Si alguna vez se probara CLIP, habría que (a) quitar o blindar la
  categoría XXX (p. ej. exigir una segunda señal), (b) reescribir los prompts sin
  "phone screen" para Juegos, y (c) validar con más videos antes de confiar.
- El orden de señales que mejor sale de ola 1 + ola 2 sigue siendo: og:title
  primero; visión (CLIP) solo como respaldo débil con voto por mayoría, nunca sola;
  OCR solo como respaldo aún más débil.

## Archivos de evidencia (este directorio)

- `frames/` — 18 JPG clasificados (15 cuadros + 3 og:image, copias de
  `/tmp/investigacion-1-hora/B/`).
- `etiquetas-por-cuadro.csv` — etiqueta+confianza por cuadro (CLIP EN/ES top-1 y
  top-3, MobileNet top-5 crudo).
- `clip-resultados.json` — CLIP completo: top-1/top-3 y vector de probabilidad de
  las 16 categorías por cuadro e idioma (reproducible, 2 pasadas idénticas).
- `mobilenet-resultados.json` — MobileNet top-5 ImageNet crudo por cuadro.
- `resumen-votos.json` — votos por mayoría / probabilidad media por video.
- `clip.py`, `mobilenet.py` — scripts de clasificación.
- `clip-onnx/` — modelos CLIP ONNX + tokenizer usados (visión 89 MB, texto 64 MB).
- `clip-log.txt`, `clip-log2.txt`, `mobilenet-log.txt` — salidas de ejecución.

## Tablas por cuadro

### Yofer 1168661422485798 — esperado Entretenimiento

| Cuadro | CLIP-EN top-1 (prob) | CLIP-ES top-1 (prob) | MobileNet top-1 |
|---|---|---|---|
| t0.10 (oscuro) | Contenido XXX 0,379 | Juegos 0,432 | beer_glass 0,045 |
| t3.66 (nítido, póster terror + "50K") | Contenido XXX 0,622 | Dinero 0,479 | comic_book 0,155 |
| t7.32 (desenfoque) | Juegos 0,532 | Juegos 0,330 | comic_book 0,387 |
| t10.97 | Contenido XXX 0,449 | Contenido XXX 0,190 | lipstick 0,142 |
| t14.48 | Contenido XXX 0,356 | Dinero 0,441 | loudspeaker 0,190 |
| og:image | Entretenimiento 0,610 ✓ | Entretenimiento 0,406 ✓ | book_jacket 0,236 |

### TEC STEPX Neo 1395074825861831 — esperado Tecnología y celulares

| Cuadro | CLIP-EN top-1 (prob) | CLIP-ES top-1 (prob) | MobileNet top-1 |
|---|---|---|---|
| t0.10 | Juegos 0,371 | Tecnología 0,193 ✓ | parachute 0,122 |
| t24.10 | Juegos 0,385 | Noticias 0,210 | bullet_train 0,086 |
| t48.20 (teléfono visible) | Juegos 0,762 | Tecnología 0,453 ✓ | cellular_telephone 0,392 ✓ |
| t72.30 | Entretenimiento 0,682 | Salud 0,202 | feather_boa 0,055 |
| t96.25 | Juegos 0,549 | Contenido XXX 0,179 | projectile 0,763 |
| og:image | DIY 0,315 | Tecnología 0,591 ✓ | airship 0,563 |

### Free Fire 914666554561073 — esperado Juegos

| Cuadro | CLIP-EN top-1 (prob) | CLIP-ES top-1 (prob) | MobileNet top-1 |
|---|---|---|---|
| t0.10 (meme, texto) | Contenido XXX 0,489 | Autos 0,157 | book_jacket 0,753 |
| t3.22 (gameplay) | Juegos 0,881 ✓ | Juegos 0,310 ✓ | stretcher 0,140 |
| t6.43 (gameplay) | Juegos 0,969 ✓ | Juegos 0,615 ✓ | stretcher 0,112 |
| t9.65 (gameplay) | Juegos 0,926 ✓ | Juegos 0,354 ✓ | stretcher 0,150 |
| t12.72 (negro) | Juegos 0,438 ✓ | Entretenimiento 0,323 | monitor 0,192 |
| og:image | Contenido XXX 0,535 | Autos 0,160 | book_jacket 0,701 |
