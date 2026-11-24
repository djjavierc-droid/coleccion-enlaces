# HILO I (ola 2) — ¿Quitar emojis y hashtags estabiliza la clasificación del og:title?

- **Fecha:** 2026-11-23 ~18:00–18:30 CST
- **Modelo:** `.tflite` REAL de la app (`app/src/main/assets/modelo_clasificador.tflite`),
  verificado byte-idéntico (md5 `d1b393265ae70107b2a9b4731df08fb6`) al de
  `entrenamiento/modelo_clasificador.tflite`. Tokenización idéntica a `verificar.py`
  (DistilBERT, `max_length=64`, truncado a 64 tokens). Umbral de la app 0,5 → si no, "Otros".
- **Método:** 9 variantes de limpieza por video × 2 repeticiones intra-proceso × 2 procesos
  independientes = **4 observaciones por variante** (31 filas en total, incluidos 3
  diagnósticos extra para Yofer). Sin cambios de código, sin commits.
- **Evidencia:** este informe + `clasificar_variantes.py`, `diagnostico_yofer.py`,
  `resultados-proceso1.json`, `resultados-proceso2.json`, `diagnostico-yofer.json`,
  `resultados-consolidado.json` y `resultados-consolidado.csv` en esta misma carpeta.

## Veredicto en 4 líneas

1. **El modelo es 100 % determinista:** las 4 observaciones de cada variante son idénticas
   hasta el 6.º decimal (31/31 filas, 0 inconsistencias). La inestabilidad entre hilos NO es
   aleatoriedad del modelo: es sensibilidad al texto exacto de entrada.
2. **Quitar hashtags es PELIGROSO, no estabiliza:** en Free Fire los hashtags LLEVAN la señal
   de Juegos (`#freefire …`); quitarlos convierte un casi-acierto (Juegos 0,468 → Otros) en un
   fallo con confianza alta (Entretenimiento 0,788, y 0,840 si además se quitan emojis).
3. **La limpieza que mejor funciona es la "estilo D" (variante i):** sin emojis, `#` quitado
   pero la palabra conservada, sin URLs, sin prefijo `N views · N reactions |`, sin sufijo
   `| Autor`, sin `«»` ni `–`. Mejora los 3 videos a la vez: Yofer 0,532 → **0,643** ✓,
   TEC 0,984 → **0,986** ✓, Free Fire 0,468 → Otros pasa a Juegos **0,670** ✓. Es la única
   variante con 3/3 aciertos y con margen en los 3.
4. **Pero la limpieza NO estabiliza Yofer en general:** el caso "0,430 → Otros" del hilo D
   NO era el título crudo (el crudo da Entretenimiento 0,532 ✓, reproducido exacto aquí)
   sino el texto limpiado A MANO por el hilo D — y esa limpieza manual concreta EMPEORA
   (Viajes 0,430). El resultado de Yofer oscila entre 0,375 y 0,643 según micro-detalles
   de puntuación (ver §3): el modelo es determinista pero frágil ante perturbaciones
   pequeñas del texto dentro de la ventana de 64 tokens.

## 1. Tabla antes/después por video

Confianza = top1. "Final app" aplica el umbral 0,5. ✓ = acierto (final = esperado).

### Yofer 1168661422485798 — esperado Entretenimiento (título crudo: 316 caracteres, 64 tokens → TRUNCADO)

| Variante | Top1 | Conf. | Final app | ¿Acierto? |
|---|---|---|---|---|
| (a) crudo completo | Entretenimiento | 0,532 | Entretenimiento | ✓ |
| (b) sin emojis | Entretenimiento | 0,526 | Entretenimiento | ✓ |
| (c) sin hashtags | Entretenimiento | 0,532 | Entretenimiento | ✓ (idéntico a (a)) |
| (d) sin emojis ni hashtags | Entretenimiento | 0,526 | Entretenimiento | ✓ (idéntico a (b)) |
| (e) (d) + sin estructura | Entretenimiento | 0,526 | Entretenimiento | ✓ (idéntico a (b)) |
| (f) (a) + sin estructura | Entretenimiento | 0,532 | Entretenimiento | ✓ (idéntico a (a)) |
| (g) (a) sin sufijo `\| Yofer` | Entretenimiento | 0,532 | Entretenimiento | ✓ (idéntico a (a)) |
| **(i) limpieza estilo D** | **Entretenimiento** | **0,643** | **Entretenimiento** | **✓ mejor margen** |
| (j) sin emojis + sin estructura | Entretenimiento | 0,526 | Entretenimiento | ✓ (idéntico a (b)) |
| (h) texto EXACTO del hilo D | Viajes | 0,430 | Otros | ✗ (reproduce D) |

Por qué (c)/(f)/(g) son idénticas a (a): el título de Yofer llena los 64 tokens y se
trunca; los hashtags y el sufijo `| Yofer` quedan FUERA de la ventana, así que quitarlos
no cambia ni un token de entrada. La única limpieza que mueve algo en Yofer es la que
toca el principio del texto (emojis, `«»`, `–`, `!!!`).

### TEC STEPX Neo 1395074825861831 — esperado Tecnología y celulares (64 tokens, truncado)

| Variante | Top1 | Conf. | Final app | ¿Acierto? |
|---|---|---|---|---|
| (a) crudo completo | Tecnología y celulares | 0,984 | Tecnología y celulares | ✓ |
| (b) sin emojis | Tecnología y celulares | 0,984 | Tecnología y celulares | ✓ |
| (c) sin hashtags | Tecnología y celulares | 0,984 | Tecnología y celulares | ✓ |
| (d) sin emojis ni hashtags | Tecnología y celulares | 0,984 | Tecnología y celulares | ✓ |
| (e) (d) + sin estructura | Tecnología y celulares | 0,986 | Tecnología y celulares | ✓ |
| (f) (a) + sin estructura | Tecnología y celulares | 0,989 | Tecnología y celulares | ✓ |
| (g) (a) sin sufijo `\| TEC` | Tecnología y celulares | 0,984 | Tecnología y celulares | ✓ |
| (i) limpieza estilo D | Tecnología y celulares | 0,986 | Tecnología y celulares | ✓ |
| (j) sin emojis + sin estructura | Tecnología y celulares | 0,986 | Tecnología y celulares | ✓ |

TEC es roca: todas las variantes 0,984–0,989. La limpieza es irrelevante aquí.

### Free Fire 914666554561073 — esperado Juegos (57 tokens, SIN truncar: aquí la limpieza sí se nota)

| Variante | Top1 | Conf. | Final app | ¿Acierto? |
|---|---|---|---|---|
| (a) crudo completo | Juegos | 0,468 | Otros | ✗ (a 0,032 del umbral) |
| (b) sin emojis | Juegos | 0,507 | **Juegos** | ✓ (justo por encima) |
| (c) sin hashtags | Entretenimiento | 0,788 | **Entretenimiento** | ✗✗ fallo con conf. alta |
| (d) sin emojis ni hashtags | Entretenimiento | 0,840 | **Entretenimiento** | ✗✗ peor aún |
| (e) (d) + sin estructura | Entretenimiento | 0,647 | **Entretenimiento** | ✗ (título ya sin señal) |
| (f) (a) + sin estructura | Juegos | 0,558 | **Juegos** | ✓ |
| (g) (a) sin sufijo `\| El ingeniero FF` | Juegos | 0,479 | Otros | ✗ (mejora leve) |
| **(i) limpieza estilo D** | **Juegos** | **0,670** | **Juegos** | **✓ mejor margen** |
| (j) sin emojis + sin estructura | Juegos | 0,541 | **Juegos** | ✓ |

Lección de Free Fire: **los hashtags son la señal, no el ruido.** Quitar `#freefire
#freefirelatam #freefireedit #garenafreefire` deja solo "A ustedes le ha pasado lo mismo,
Confirmen", que el modelo lee como Entretenimiento con 0,788–0,840. En cambio, quitar el
`#` pero conservar la palabra (`freefire freefirelatam …`, variante i) SUBE Juegos a 0,670.
Quitar el prefijo `173K views · 13K reactions |` también ayuda (0,468 → 0,558 en (f);
0,507 → 0,541 en (j) frente a (b)).

## 2. La discrepancia B/D queda explicada (no es inestabilidad del modelo)

El hilo D no clasificó el og:title crudo: su entrada `YOFER-A3_ogtitle_completo`
(`bateria.json`, recuperado de `/home/hatch/tmp/investigacion-1-hora/D/`) era una limpieza
MANUAL: sin emojis, sin URL de YouTube, sin `«»`, sin `–`, sin sufijo `| Yofer`, sin `!!!`
tras "POSIBLE" y "60.000", y con las palabras de hashtag REDUCIDAS a solo
"Youtube Spotify itunes" (eliminó `#Yofer #LágrimasDeMiSoledad #50k` por completo).

- Texto exacto de D, reclasificado aquí (variante h): **Viajes 0,430 → Otros** — reproduce
  el 0,430 de D al milésimo (top3: Viajes 0,430, Entretenimiento 0,340, Tecnología 0,055).
- og:title crudo, reclasificado aquí (variante a): **Entretenimiento 0,532** — reproduce el
  0,532 de B al milésimo (top3: Entretenimiento 0,532, Noticias 0,206, Tecnología 0,087).

Ambos hilos midieron bien; midieron ENTRADAS DISTINTAS. La lección es que la clasificación
es sensible al texto exacto: dos limpiezas manuales "razonables" del mismo título dan
categorías distintas.

## 3. Diagnóstico Yofer: la limpieza manual es frágil a nivel de token

Para aislar qué micro-detalle separa (h) 0,430 de (i) 0,643, tres controles sobre el
mismo título (2 repeticiones cada uno, deterministas):

| Control | Qué cambia respecto a su referencia | Resultado |
|---|---|---|
| k1 = (h) + `!!!` restaurados tras POSIBLE y 60.000 | devuelve las exclamaciones finales a D | Viajes 0,375 → Otros (sigue mal) |
| k2 = (i) con todos los `!` ASCII eliminados | quita `!` de la limpieza estilo D | Entretenimiento 0,477 → Otros (empeora: 0,643 → 0,477) |
| k3 = (i) sin las palabras `Yofer LágrimasDeMiSoledad 50k` | quita esas 3 palabras de hashtag | Entretenimiento 0,643 (idéntico a (i)) |

Conclusión del diagnóstico: las palabras de hashtag de Yofer no importan (k3 = i; están
fuera de la ventana de 64 tokens), pero la puntuación de exclamación SÍ mueve el resultado
decenas de puntos (k2: 0,643 → 0,477 solo por quitar `!`; k1 no recupera a (h) porque a D
también le faltan los `¡¡¡` iniciales antes de GRACIAS/AHORA). Es decir: **en títulos
largos truncados a 64 tokens, el resultado depende de qué tokens exactos entran en la
ventana, y un `¡` de más o de menos puede cruzar el umbral.** Ninguna de las limpiezas
probadas "estabiliza" Yofer en el sentido de hacerlo robusto: lo mejor que se consigue es
subir el margen (0,532 → 0,643 con la variante i), pero otra limpieza igual de razonable
(D manual) lo hunde a 0,430.

## 4. Determinismo

- **Intra-proceso:** 2 invocaciones seguidas del mismo texto dan el mismo resultado en
  las 31 filas (incluidos los 3 diagnósticos).
- **Inter-proceso:** 2 procesos Python independientes (con su propio intérprete TFLite)
  dan resultados idénticos hasta el 6.º decimal en las 28 variantes principales.
- **Total: 4 observaciones por variante, 0 inconsistencias.**
- El aviso de TensorFlow sobre oneDNN ("slightly different numerical results due to
  floating-point round-off") NO se materializó en ninguna de las 124 clasificaciones.

## 5. Recomendación (para el coordinador, no para aplicar sin decidir)

1. **NO quitar hashtags.** Es la peor limpieza probada: en Free Fire destruye la señal y
   produce un fallo con confianza alta (0,840). Si se limpia, que sea convirtiendo
   `#palabra` → `palabra` (variante i), nunca borrándola.
2. **La limpieza estilo D (variante i) es la mejor candidata global:** 3/3 aciertos con el
   mejor margen en cada video (Yofer 0,643; TEC 0,986; Free Fire 0,670). Reglas exactas:
   quitar emojis (incl. VS16/ZWJ), `#` → palabra, quitar URLs, quitar prefijo
   `N views · N reactions |`, quitar sufijo `| Autor`, quitar `«»""`, `–`/`—` → espacio,
   colapsar espacios. Definición reproducible en `clasificar_variantes.py::limpieza_estilo_D`.
3. **Pero no confiarse:** Yofer demuestra que dos limpiezas razonables difieren en 0,21 de
   confianza y cambian la categoría. La limpieza sube el margen pero no hace robusto el
   modelo ante el texto exacto. Si se quiere estabilidad de verdad, el camino es el
   reentrenamiento (o subir el umbral para títulos con hashtags dominantes), no más
   limpieza manual.
4. **El caso "Yofer 0,430 → Otros" NO existe con el título crudo.** Con lo que la app
   recibe hoy (og:title crudo), Yofer clasifica bien (0,532). El problema real y sin
   resolver sigue siendo Free Fire crudo (0,468 → Otros), que la variante (i) sí arregla
   (0,670).

## 6. Textos exactos clasificados (para reproducir)

Ver `resultados-consolidado.csv` (columna `texto`) y `resultados-consolidado.json`.
og:title crudos extraídos de `investigacion-hilo-B/{yofer-og,tec-ai-og,game-freefire-og}.txt`
(saltos de línea colapsados a un espacio; es la misma forma que usó el hilo B en
`yofer-final2.txt` / `textos-final.txt`).
