# Consenso del clasificador — Colección de enlaces

Documento de diseño para implementar el clasificador pequeño en la app.
Fuente: censo de modelos por OmniRoute (2026-11-23 04:50) + investigación en navegador.
Archivo de respuestas crudas: `censo-clasificacion.json` (en esta misma carpeta).

## La pregunta exacta que se les hizo a las IAs

> "Una app Android guarda enlaces de videos (Reels de Facebook, TikTok, Shorts de YouTube) y debe clasificarlos sola en categorías (cocina, mecánica, tecnología, etc.) con ~99% de acierto, SIN servidor, SIN pagar nada, todo en el teléfono. ¿Qué método concreto recomiendas? Responde en 3 líneas: método, por qué funciona sin servidor, y su punto débil."

## Resultado del censo

- Consultados: 33 modelos. Respondieron con contenido: 16 (24,6 s).
- ddgw 6/6, cohere 6/6, groq 1/5 (2 vacías), nvidia 2/5, gemini 0/3, auto 1/8.
- Fallos: 7 timeouts, 2×404, 1×410, 3 vacías.

## Consenso (10/16): clasificador de texto pequeño en el teléfono

- **Motor:** TensorFlow Lite (hoy LiteRT), librería `org.tensorflow:tensorflow-lite-task-text` (NLClassifier).
- **Modelo:** MobileBERT (o DistilBERT) cuantizado INT8, **~15–25 MB**, entrenado con
  título + descripción + hashtags + autor del enlace. Inferencia <50 ms en CPU.
- **Entrenamiento:** se entrena UNA vez fuera del teléfono y el `.tflite` se empaqueta
  en la app (assets). No hay servidor ni costo.
- **Variante (3/16):** mismo clasificador de texto + miniatura (1–3 fotogramas, OCR,
  MobileNet/CLIP cuantizado) como segunda señal. Solo si el texto no basta.

## Punto débil unánime (16/16)

~99% es irrealista solo con metadatos. Falla en: videos sin texto descriptivo,
temas ambiguos o mezclados, clickbait y miniaturas engañosas. La precisión se mide
con un conjunto de pruebas etiquetado antes de prometer ningún porcentaje.

## Correcciones verificadas en navegador (2026-11-23)

- TikTok oEmbed NO devuelve título ni autor (solo html). YouTube oEmbed SÍ da
  título + miniatura directos. Facebook/Instagram oEmbed solo html.
- **ML Kit NO clasifica texto** en categorías arbitrarias (solo imágenes con modelo
  propio). El clasificador va directo con LiteRT, no con ML Kit.
- Tamaños LiteRT: average_word_vec <1 MB; MobileBERT 25 MB cuantizado / 100 MB
  sin cuantizar; BERT-Base 300 MB.
- LiteRT-LM (Gemma3-1B en el teléfono): pesado, carga hasta 10 s, salida no
  determinista (puede inventar categorías). No sirve para prometer 99%.
- Aprendizaje en el teléfono: TF Lite Model Personalization reentrena solo la
  última capa con ejemplos del usuario (el ejemplo oficial es imágenes/MobileNetV2;
  adaptarlo a texto lleva trabajo). Pocas correcciones → sobreajuste.

## Reglas de Javier (órdenes, no sugerencias)

1. **La app es DEFINITIVA, una sola versión, SIN actualizaciones.** Todo el
   aprendizaje ocurre dentro del teléfono.
2. **Si el clasificador no está seguro, va a "Otros".** Nunca adivina con palabras
   clave en ejecución. Las palabras clave solo sirven como datos de entrenamiento.
3. **Aprende cuando Javier mueve un enlace a su carpeta correcta.** Dos mecanismos:
   - (a) Capa inmediata: cada corrección actualiza al instante los pesos
     palabra→categoría en Room (se nota desde la primera corrección).
   - (b) Reentrenamiento en el teléfono: con varias correcciones acumuladas y el
     teléfono en reposo, se reentrena solo la última capa del modelo
     (transfer learning). **El reentrenamiento completo del modelo NO es posible
     en el teléfono.**
4. Proceso 100% automático: nadie escribe títulos. El título es automático
   (oEmbed → vista previa → texto compartido → texto del reproductor → dominio).
5. Sin servidor, sin cuentas, sin nube, sin anuncios. Solo fuentes del sistema.

## Datos de entrenamiento

- Las listas de palabras clave de las 17 categorías (ahora ampliadas, 225 palabras
  nuevas en fix-2) sirven como datos de entrenamiento.
- Caso de prueba obligatorio: el título/descripción exactos del video de EterniCodIA
  ("Contratamos a una IA... y el primer día ya vendía croquetas en Marte...")
  **debe clasificar en "Tecnología y celulares"**.

## Estado (2026-11-23 05:36)

- Fix 1 + fix 2 completos en código (ver `fix-1-resumen.md` y `fix-2-resumen.md`).
- **El clasificador LiteRT NO está implementado todavía.** Este documento es el
  diseño que faltaba para hacerlo.
- Bloqueo aparte: el CI de GitHub no arranca por facturación (ver reporte del
  coordinador). Sin CI verde no hay APK 1.0.1 verificable.
