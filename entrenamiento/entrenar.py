#!/usr/bin/env python3
"""Entrena el clasificador DistilBERT y lo exporta a .tflite INT8.

Modelo: distilbert-base-uncased (66M parámetros), afinado con los
datos de generar_datos.py (título + descripción -> una de 16
categorías; "Otros" NO es una clase: es el respaldo por baja
confianza). Entrenamiento UNA vez fuera del teléfono; el .tflite se
empaqueta en app/src/main/assets/.

NOTA (Fix 3): el diseño decía "MobileBERT (o DistilBERT)". MobileBERT
quedó BLOQUEADO: su checkpoint antiguo (transformers 4.6) explota en
las capas 22-23 con transformers 4.57.1 + TF 2.21 (logits de ±18
millones, pérdida 5.185.248). DistilBERT es el alternativo del propio
diseño y su pase base es sano (logits ±0,22, pérdida 2,82 ~ azar).

Diferencias con MobileBERT:
- DistilBERT tiene 6 capas (no 24) y NO usa token_type_ids (su
  forward lo rechaza con KeyError; el tokenizador tampoco lo produce).
- Se congelan los embeddings y las 4 primeras capas del codificador:
  solo se entrenan las 2 últimas + el clasificador (transfer learning).

Estrategia de memoria (la VM tiene poca RAM libre):
- Longitud máxima 64 tokens (los títulos son cortos).
- Lote pequeño (8) y 3 épocas.

Variables de entorno:
- EPOCHS (def. 3), BATCH (def. 8), MAX_LEN (def. 64)
- SMOKE=1: solo 200 ejemplos y 1 época (prueba del pipeline).

Salida:
- modelo_tf/ : modelo afinado en formato SavedModel (TF).
- modelo_clasificador.tflite : modelo cuantizado INT8 para el
  intérprete base de LiteRT (la app tokeniza con su propio
  TokenizadorWordPiece en Kotlin; no se usan metadatos NLClassifier:
  tflite-support no tiene ruedas para Python 3.12).
- metricas.json : precisión en validación y confianza media por clase.
"""

import csv
import json
import os
import sys
from pathlib import Path

DIR = Path(__file__).resolve().parent
DIR_DATOS = DIR / "datos"
SALIDA_TF = DIR / "modelo_tf"
SALIDA_TFLITE = DIR / "modelo_clasificador.tflite"
SALIDA_METRICAS = DIR / "metricas.json"

MODELO_BASE = str(DIR / "modelo_base_distilbert")  # Copia local
# (config + vocab + tokenizer + tf_model.h5 descargados una sola vez
# con curl; sin red en el entrenamiento: HF_HUB_OFFLINE=1).
EPOCHS = int(os.environ.get("EPOCHS", "3"))
BATCH = int(os.environ.get("BATCH", "8"))
MAX_LEN = int(os.environ.get("MAX_LEN", "64"))
SMOKE = os.environ.get("SMOKE", "") == "1"
CAPAS_CONGELADAS = 4  # de 6: se entrenan las 2 últimas + cabeza


def leer_csv(ruta):
    with ruta.open(encoding="utf-8", newline="") as f:
        lector = csv.DictReader(f)
        return [(fila["texto"], fila["categoria"]) for fila in lector]


def main() -> int:
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

    tf.config.threading.set_intra_op_parallelism_threads(2)
    tf.config.threading.set_inter_op_parallelism_threads(2)

    etiquetas = (DIR_DATOS / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    indice = {nombre: i for i, nombre in enumerate(etiquetas)}
    print(f"Clases ({len(etiquetas)}): {etiquetas}")

    entren = leer_csv(DIR_DATOS / "entrenamiento.csv")
    valid = leer_csv(DIR_DATOS / "validacion.csv")
    if SMOKE:
        entren = entren[:200]
        valid = valid[:60]
        global EPOCHS
        EPOCHS = 1
    print(f"Entrenamiento: {len(entren)} | Validación: {len(valid)} | épocas={EPOCHS} lote={BATCH}")

    tokenizer = AutoTokenizer.from_pretrained(MODELO_BASE)

    def tokenizar(pares):
        textos = [t for t, _ in pares]
        y = np.array([indice[c] for _, c in pares], dtype=np.int64)
        tok = tokenizer(
            textos, padding="max_length", truncation=True,
            max_length=MAX_LEN, return_tensors="tf",
        )
        return tok, y

    tok_tr, y_tr = tokenizar(entren)
    tok_va, y_va = tokenizar(valid)

    modelo = TFAutoModelForSequenceClassification.from_pretrained(
        MODELO_BASE, num_labels=len(etiquetas)
    )
    # Congelar embeddings + las 4 primeras capas del codificador
    # (DistilBERT tiene 6; se entrenan las 2 últimas + la cabeza).
    base = modelo.distilbert
    base.embeddings.trainable = False
    for capa in base.transformer.layer[:CAPAS_CONGELADAS]:
        capa.trainable = False
    entrenables = sum(int(np.prod(v.shape)) for v in modelo.trainable_variables)
    total = sum(int(np.prod(v.shape)) for v in modelo.variables)
    print(f"Parámetros entrenables: {entrenables/1e6:.1f}M de {total/1e6:.1f}M")

    # DistilBERT NO usa token_type_ids (su forward lo rechaza).
    ds_tr = tf.data.Dataset.from_tensor_slices((
        {
            "input_ids": tok_tr["input_ids"],
            "attention_mask": tok_tr["attention_mask"],
        },
        y_tr,
    )).shuffle(len(entren), seed=42).batch(BATCH)
    ds_va = tf.data.Dataset.from_tensor_slices((
        {
            "input_ids": tok_va["input_ids"],
            "attention_mask": tok_va["attention_mask"],
        },
        y_va,
    )).batch(BATCH)

    modelo.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=3e-5, weight_decay=0.0),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
        metrics=["accuracy"],
    )
    historial = modelo.fit(ds_tr, epochs=EPOCHS, validation_data=ds_va, verbose=2)
    perdida, precision = modelo.evaluate(ds_va, verbose=0)
    print(f"Validación: pérdida={perdida:.4f} precisión={precision:.4f}")

    # Guardar el modelo afinado (firma con los nombres de entrada de HF).
    modelo.save_pretrained(str(SALIDA_TF))
    tokenizer.save_pretrained(str(SALIDA_TF))

    metricas = {
        "precision_validacion": float(precision),
        "perdida_validacion": float(perdida),
        "epocas": EPOCHS,
        "lote": BATCH,
        "max_len": MAX_LEN,
        "ejemplos_entrenamiento": len(entren),
        "ejemplos_validacion": len(valid),
        "etiquetas": etiquetas,
        "historial_precision": [float(v) for v in historial.history.get("val_accuracy", [])],
    }
    SALIDA_METRICAS.write_text(json.dumps(metricas, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Métricas en {SALIDA_METRICAS}")
    print("Modelo afinado guardado en", SALIDA_TF)
    return 0


if __name__ == "__main__":
    sys.exit(main())
