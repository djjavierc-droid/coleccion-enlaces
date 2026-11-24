# Hilo B — Pipeline completo miniatura + video + OCR + clasificación (verificado con descargas reales)

- **Fecha:** 2026-11-23, 17:38–18:30 CST aprox. (23:38–00:30 UTC)
- **Regla:** solo medición. Sin cambios de código de la app, sin commits, sin escribir a Javier.
- **Evidencia de trabajo:** `/tmp/investigacion-1-hora/B/` (videos, cuadros, HTML, OCR, salidas del clasificador; 33 MB).
  Copia durable de scripts y salidas de texto en `~/workspace/coleccion-enlaces/investigacion-hilo-B/`
  (este informe, `fetch_reel2.py`, `clasificar.py`, `ocr.py`, `*-og.txt`, `ocr-todos.txt`,
  `ocr-tesseract.txt`, `clasificar-final.txt`, `textos-final.txt`, `yofer-final2.txt`).
- **Incidente:** a mitad de la investigación el servicio de ejecución se reinició y **borró `/tmp` por completo**,
  incluida la evidencia del Hilo A (`/tmp/miniatura-real/` con `ogimage-reel.jpg`, `mp4-url.txt`,
  `reel-googlebot.html`, `pruebas.sh`). Todo el pipeline se rehízo desde cero con el mismo
  procedimiento y los resultados se reprodujeron byte a byte (mismos tamaños de video y de cuadros).
  La evidencia del Hilo A solo sobrevive en su informe (`miniatura-real-curl.md` en el repo).
  Lección: `/tmp` no es durable; la evidencia que importa debe copiarse al workspace.

## Veredicto

**El pipeline completo SÍ funciona de punta a punta cuando se conoce la URL canónica del reel**
(`/reel/<id>/` pedida con UA Googlebot): og:title + og:image + .mp4 directos descargables sin sesión,
cuadros reales con ffmpeg, OCR local real y clasificación con el .tflite real. **PERO con dos
limitaciones medidas que condicionan el diseño:**

1. **El OCR solo NO clasifica bien.** El texto OCR de los cuadros, alimentado solo al clasificador,
   clasifica MAL o cae en "Otros" en 2 de 3 videos reales (Free Fire → Entretenimiento 0,534 en vez
   de Juegos; Yofer → Otros 0,458; TEC → basura OCR que clasifica Autos y motos 0,736). El OCR solo
   funciona cuando el texto en pantalla es prosa temática natural (caso Soylorainegodoy → Entretenimiento
   0,979) o cuando va acompañado de un buen og:title. El og:title es la señal decisiva cuando existe.
2. **Las URLs `.mp4` de la página hay que extraerlas con unescape JSON completo.** La primera
   extracción (sin decodificar `\u0025` etc.) truncaba las URLs antes de los parámetros de firma
   (`oh`/`oe`) y el servidor devolvía **403 "Bad URL hash"**. Con el extractor corregido
   (`\/` → `/`, `\uXXXX` → carácter, `&amp;` → `&`) TODAS las renditions descargan (200).

## 1. Videos probados (todos con URL canónica conocida)

| Video | Reel ID | Duración | Video descargado (rendition alta) | Rendition baja |
|---|---|---|---|---|
| Yofer — "50.000 VISITAS" (música) | 1168661422485798 | 14,63 s | 2.262.571 B, H.264 720×866, 1.236.940 bps | 616.716 B, H.264 360×434, 337.156 bps |
| TEC — STEPX Neo, smartphone con IA (sustituto tecnología) | 1395074825861831 | 96,40 s | 19.498.730 B, H.264 720×1280, 1.618.151 bps | 4.710.949 B, H.264 360×640, 390.950 bps |
| El ingeniero FF — Free Fire (sustituto juegos) | 914666554561073 | 12,87 s | 1.508.500 B, H.264 720×1280, 937.927 bps | 208.582 bps (no descargada; la alta bastó) |

- Cada página trae **3 `.mp4`**: 2 de video + 1 de audio solo (AAC HE, ~50–65 kbps, 14 s ≈ 110 KB;
  ¡el primer intento de "video completo" del Hilo A descargó el de AUDIO, no el video!).
- Descargas con UA de la app (`ServicioVistaPrevia.kt`), HTTP 200, `ffprobe` confirma stream H.264.
- Cuadros extraídos con ffmpeg a 0 %, 25 %, 50 %, 75 % y ~100 % en cada video (5 por video, JPG).
  Yofer: t0.10/t3.66/t7.32/t10.97/t14.48. TEC: t0.10/t24.10/t48.20/t72.30/t96.25.
  Free Fire: t0.10/t3.22/t6.43/t9.65/t12.72.

## 2. og:title / og:image (página con UA Googlebot)

| Video | og:title (resumido) | og:image |
|---|---|---|
| Yofer | "🦇🔥 ¡¡¡50.000 VISITAS!!! … «LÁGRIMAS DE MI SOLEDAD» … YOFER – LÁGRIMAS DE MI SOLEDAD … #Yofer #LágrimasDeMiSoledad #50k" (316 caracteres) | 200, JPEG. Hilo A: 157.257 B, 1312×1576. Esta repetición: 78.491 B, 999×1200 (otra rendition por petición; mismo contenido: recorte ampliado del cuadro con el texto cortado en los bordes) |
| TEC | "4.7K views · 990 reactions \| 📱 La empresa StepFun presentó el STEPX Neo, el primer smartphone creado desde cero para trabajar con inteligencia artificial basada en agentes…" | 200, JPEG 9.775 B, 640×1136 (póster pequeño) |
| Free Fire | "173K views · 13K reactions \| A ustedes le ha pasado lo mismo, Confirmen 😂🤣 #freefire #freefirelatam #freefireedit #garenafreefire #reels #viral #humor \| El ingeniero FF" | 200, JPEG 88.109 B, 675×1200 |

## 3. OCR real sobre los cuadros (dos motores locales)

### RapidOCR (ONNX local, instalado por pip en `~/.ocr-venv`) — motor de referencia

| Imagen | Texto OCR extraído |
|---|---|
| Yofer t0.10 | *(nada: cuadro oscuro de intro)* |
| Yofer t3.66 (nítido) | "50.000+VISTAS" (0,98), "GRACIASPORELAPOYO!" (0,99), "MILESTONEACHIEVED" (0,99) + "popayosmdpsyumoy" (0,64, basura de la firma cursiva "Lágrimas de mi soledad") |
| Yofer t7.32 (desenfoque de movimiento) | "STTO+VISTAS" (0,91), "PSPORELAPOYO!" (0,84), "KSTONEOCHIET" (0,72) — degradado pero parcial |
| Yofer t10.97 | "VISTAS" (0,97), "ELAPOYO!" (0,91) |
| Yofer t14.48 | igual que t3.66 |
| Yofer og:image | "TUBE" (0,82), "ATION" (0,99) — solo fragmentos: el póster es un recorte con el texto cortado ("YOUTUBE", "SENSATION") |
| TEC t0.10 / t72.30 | *(nada)* |
| TEC t24.10 | un carácter chino (0,74) |
| TEC t48.20 | "13:25" (0,96), "Amoo，你的全邮饮伴" (0,84, chino) |
| TEC t96.25 | "C" (0,83), "TEC" (0,99, logo) |
| TEC og:image | *(nada)* |
| Free Fire t0.10 | "YO CUANDO SEA" (0,95), "PAPA Y ESTE EN" (0,91), "PLENA PARTIDA" (0,98), "DE FREE FRE" (0,90) |
| Free Fire t3.22 | "2026" (0,82), "#DaniDlop" (0,96), "INGENIERO" (0,99) |
| Free Fire t6.43 / t9.65 | "#DaniDlop" (0,96), "NGENIERO" (0,94–0,97), "FF" (0,99–1,00) |
| Free Fire t12.72 | *(nada: cuadro final negro)* |
| Free Fire og:image | "YO CUANDO SEA" (0,96), "PAPAYESTE EN" (0,94), "PLENA PARTIDA" (0,97), "DE FREE FIRE" (0,93) — el texto del meme completo |

Observación: RapidOCR pega las palabras sin espacios ("GRACIASPORELAPOYO!") y falla en la
letra cursiva decorativa, pero captura el texto impreso grande con confianza 0,9+.

### Tesseract 5.3.4 (instalado a mano por .deb; ver §6)

Con `-l spa+eng --psm 11` es **mucho más ruidoso** que RapidOCR: en los cuadros nítidos de Yofer
sí saca "50.000+ VISTAS", "¡GRACIAS POR EL APOYO!", "MILESTONE ACHIEVED" (con espacios correctos),
pero rodeado de basura de 1–2 caracteres por línea; en Free Fire solo saca "DE FREE FIRE" y pierde
por completo las 4 líneas del meme que RapidOCR lee bien. En TEC y en las og:image, casi nada útil.
Conclusión: para este uso RapidOCR es mejor referencia que Tesseract; ML Kit (el OCR real de la
app en Android) queda por probar en el teléfono y es, en general, superior a ambos.

## 4. Clasificación con el .tflite real (venv de entrenamiento, tokenización idéntica a `verificar.py`, umbral 0,5 → "Otros")

### 4.1 Textos og:title (la señal que la app tendría con la ruta del rastreador)

| Entrada | Resultado | Veredicto |
|---|---|---|
| Yofer og:title completo | **Entretenimiento 0,532** (Noticias 0,206, Tecnología 0,087) | ✓ correcto, confianza justa por encima del umbral |
| TEC og:title | **Tecnología y celulares 0,984** | ✓ correcto, muy seguro |
| Free Fire og:title | Juegos 0,468 → **Otros** (Entretenimiento 0,270, Tecnología 0,184) | ~ el modelo apunta a Juegos pero bajo el umbral: el título es casi todo hashtags + "Confirmen 😂🤣" |
| EterniCodIA (texto exacto de `verificar.py`) | **Tecnología y celulares 0,971** | ✓ (fix-4 reportaba 0,936; misma categoría) |
| Soylorainegodoy (texto OCR exacto de `generar_datos.py`) | **Entretenimiento 0,979** | ✓ el patrón Fix 5 funciona |
| "Nuevo juego para celular gratis" | **Juegos 0,983** | ✓ bug Fix 4 corregido (antes Tecnología 0,931) |
| "Gameplay de Minecraft: construyendo mi base" | **Juegos 0,984** | ✓ |
| "facebook.com" (solo dominio, el bug original) | Tecnología 0,496 → **Otros** | ✓ ya no hay categoría confiada y errónea; cae en "Otros" por el umbral (por 0,004) |

### 4.2 Textos OCR (lo que la app tendría SIN título, solo con el video)

| Entrada | Resultado | Veredicto |
|---|---|---|
| Yofer OCR cuadros ("50.000+VISTAS GRACIASPORELAPOYO! MILESTONEACHIEVED") | Tecnología 0,458 → **Otros** (Entretenimiento 0,165) | ✗ el OCR solo no clasifica el video musical |
| Yofer og:title + OCR | **Entretenimiento 0,532** (idéntico al título solo; el OCR queda fuera de los 64 tokens) | = el OCR no aporta ni estorba |
| Free Fire OCR cuadros ("YO CUANDO SEA PAPA Y ESTE EN PLENA PARTIDA DE FREE FIRE INGENIERO #DaniDlop FF") | **Entretenimiento 0,534** (Tecnología 0,172, Juegos 0,140) | ✗✗ CLASIFICA MAL: el texto del meme domina y "FREE FIRE" no empuja a Juegos |
| Free Fire og:title + OCR | Juegos 0,429 → **Otros** | ~ sigue bajo el umbral |
| Free Fire og:image OCR ("YO CUANDO SEA PAPA Y ESTE EN PLENA PARTIDA DE FREE FIRE") | **Entretenimiento 0,617** (Juegos 0,251) | ✗ mal, con confianza 0,617 |
| "FREE FIRE" solo | Juegos 0,459 → **Otros** | ~ a 0,041 del umbral |
| "FREE FIRE INGENIERO #DaniDlop FF" (solo cuadros de gameplay) | Tecnología 0,263 → **Otros** (Juegos 0,138) | ✗ los fragmentos OCR no señalan Juegos |
| "Partida de Free Fire gameplay" (frase natural) | **Juegos 0,989** | ✓ el modelo necesita palabras de contexto ("partida", "gameplay"), que el OCR crudo no da |
| TEC OCR ("13:25 Amoo TEC") | **Autos y motos 0,736** | ✗✗ basura OCR clasificada con confianza alta y errónea |
| TEC og:title + OCR | **Tecnología y celulares 0,984** (idéntico al título solo) | ✓ el título domina y el ruido OCR no estorba |
| Yofer og:image OCR ("TUBE ATION") | DIY 0,419 → **Otros** | ~ inútil |

### 4.3 Lección de diseño medida

- **og:title manda.** Cuando existe, clasifica bien (Yofer 0,532; TEC 0,984) y el OCR añadido no cambia nada
  (el título ya llena los 64 tokens).
- **OCR solo es frágil.** Funciona solo si el texto en pantalla es prosa temática natural
  (Soylorainegodoy 0,979). Con texto de meme, rótulos o basura, clasifica mal o con confianza espuria
  (Free Fire → Entretenimiento; TEC → Autos y motos 0,736). **No alimentar al clasificador con OCR
  crudo sin filtrar:** un OCR corto/ruidoso debería ir a "Otros" o combinarse con el título, nunca
  solo. Además, el texto OCR pierde los espacios en RapidOCR ("GRACIASPORELAPOYO!") y eso degrada
  la señal frente a texto natural.
- **Párrafos sueltos del título son inestables** (medido por accidente al partir el título de Yofer
  por líneas: "¡¡¡50.000 VISITAS!!!" solo → Viajes 0,920; "¡¡¡AHORA A POR LAS 60.000!!!" →
  Dinero y finanzas 0,589; "#Yofer #LágrimasDeMiSoledad #50k #Youtube #Spotify #itunes" →
  Tecnología 0,927). El título hay que pasarlo **completo y en una sola entrada**.

## 5. ¿Sirve la og:image como cuadro para OCR/clasificación?

- **Como imagen para OCR: regular o mala.** Yofer: solo fragmentos "TUBE"/"ATION" (es un recorte ampliado
  con texto cortado en los bordes; en el Hilo A era 1312×1576, en esta repetición 999×1200: la rendition
  varía por petición). TEC: 0 líneas (póster pequeño 640×1136, 9,7 KB). Free Fire: SÍ da el texto
  completo del meme (4 líneas) — pero ese texto clasifica MAL (Entretenimiento 0,617).
- **Como miniatura para guardar: SÍ.** Es el póster real del video, descargable sin sesión (HTTP 200).
  Sirve como miniatura de la tarjeta; no sirve como sustituto fiable de los cuadros del video para OCR.
- **Los cuadros del .mp4 son mejores para OCR** que la og:image (texto completo y nítido en los cuadros
  buenos; la og:image es un recorte arbitrario).

## 6. OCR: intentos de instalación (tesseract)

- `tesseract` NO estaba instalado. `apt-get install` falló primero ("Unable to locate package":
  listas desactualizadas); `apt-get update` se quedó **>25 min descargando ~418 MB de listas**
  (espejos Ubuntu azure + cogentco duplicados) sin terminar; el reinicio del servicio lo mató
  (SIGTERM) y borró `/tmp`.
- Solución que funcionó: **descarga directa de los .deb** desde `azure.archive.ubuntu.com`
  con versiones consultadas en la API de Launchpad
  (`tesseract-ocr_5.3.4-1build5`, `libtesseract5_5.3.4-1build5`, `liblept5_1.82.0-3build4`,
  `tesseract-ocr-spa/eng_4.1.0-2`, `libgif7_5.2.2-1ubuntu1.2`) + `dpkg -i` + `dpkg --configure -a`.
  Tesseract 5.3.4 funciona con `eng`+`spa`. Nota: `dpkg -s tesseract-ocr` queda en
  "install ok unpacked" (error de configuración residual) pero el binario funciona.
- Alternativa que SÍ funcionó antes y mejor: **RapidOCR ONNX por pip** (`rapidocr-onnxruntime`,
  venv en `/home/hatch/.ocr-venv`, sobrevive al reinicio porque vive en home, no en `/tmp`).

## 7. Casos exactos del repo: qué se pudo recuperar y qué no

- **EterniCodIA:** sin ID/URL canónica en el repo (solo el texto exacto en `verificar.py` /
  `generar_datos.py`). Clasificación por texto: **Tecnología y celulares 0,971** ✓. No hay video
  descargable sin el ID.
- **Video de juego (caso Fix 4):** sin ID/URL en el repo: el único texto que la app le dio al modelo
  era `"facebook.com"` (respaldo por dominio). No hay video que descargar. Se usó el **sustituto**
  Free Fire (reel público 914666554561073) + los textos sintéticos del repo ("Nuevo juego para
  celular gratis" → Juegos 0,983 ✓).
- **Soylorainegodoy (video emotivo sobre los padres):** URL completa desconocida
  (`/share/r/1FKC67bv…` truncada en la captura; fix-5-diagnostico.md). **No se puede descargar.**
  Su texto OCR exacto está en `generar_datos.py` (TEXTO_PRUEBA_SOYLO) y clasifica
  **Entretenimiento 0,979** ✓.
- **Sustituto tecnología:** TEC / STEPX Neo (reel 1395074825861831), video real de tecnología/IA
  en español. **No es el caso exacto de EterniCodIA**; se reporta como sustituto.

## 8. Hallazgos adicionales (medidos, no pedidos pero relevantes)

1. **El "video completo" del Hilo A era solo audio.** La URL de `/tmp/miniatura-real/mp4-url.txt`
   (732 caracteres) es la rendition de audio (`vencodetag: dash_nl_heaac_vbr3_audio`, 60.394 bps,
   110.435 B, solo stream AAC, 14,63 s). Las renditions de video son las de mayor bitrate
   (337.156 y 1.236.940 bps en Yofer). Hay que elegir por bitrate/`vencodetag`, no coger la primera `.mp4`.
2. **403 "Bad URL hash" = URL truncada, no bloqueo.** Las firmas `oh`/`oe` se cortan si el extractor
   no decodifica `\u0025` (`%`) y `\u0026` (`&`) dentro del JSON. Con URLs completas, las 3 renditions
   de los 3 videos descargan con 200 (verificado: Yofer-lo pasó de 403 a 200/616.716 B).
3. **La og:image cambia de rendition entre peticiones** (Yofer: 157.257 B 1312×1576 en Hilo A;
   78.491 B 999×1200 ahora). Hay que descargarla a local al guardar (la URL lleva firma temporal),
   como ya hace la app.
4. **Los cuadros con desenfoque de movimiento degradan el OCR** (Yofer t7.32: texto parcial y con
   errores); conviene muestrear varios cuadros y quedarse con el mejor, no con uno solo.
5. **`/tmp` se borra en reinicios del servicio.** Toda la evidencia del Hilo A se perdió; este hilo
   se rehízo entero y se reprodujo byte a byte, pero el riesgo es real para cualquier trabajo
   futuro en `/tmp`.

## 9. Resumen por video

| Video | og:title → clasificador | OCR cuadros → clasificador | og:image como OCR | Pipeline punta a punta |
|---|---|---|---|---|
| Yofer 1168661422485798 | Entretenimiento 0,532 ✓ | Otros 0,458 (Tecnología) ✗ | "TUBE ATION" → Otros | **SÍ** (título manda) |
| TEC 1395074825861831 (sustituto) | Tecnología 0,984 ✓ | Autos y motos 0,736 ✗ (basura OCR) | 0 líneas → n/a | **SÍ** (título manda) |
| Free Fire 914666554561073 (sustituto) | Juegos 0,468 → Otros ~ | Entretenimiento 0,534 ✗ | Entretenimiento 0,617 ✗ | **PARCIAL**: descarga+OCR funcionan; la clasificación por título u OCR queda en Otros o mal |
| EterniCodIA (solo texto) | Tecnología 0,971 ✓ | n/a (sin video) | n/a | n/a (sin ID canónico) |
| Soylorainegodoy (solo texto OCR) | Entretenimiento 0,979 ✓ | n/a (sin video) | n/a | n/a (URL desconocida) |

**Conclusión final:** el pipeline mecánico (canónica → og:image + .mp4 → cuadros → OCR → .tflite)
funciona de punta a punta sin sesión. La clasificación es correcta cuando hay og:title con
contenido temático; el OCR solo es un respaldo débil y a veces engañoso, y la app debería tratarlo
como tal (combinarlo con el título o descartarlo si es corto/ruidoso, nunca clasificar solo con él).
El hueco que queda fuera de este hilo es el mismo del Hilo A: resolver `/share/r/` → URL canónica.
