"""Diagnóstico rápido: ¿por qué la pérdida del humo fue 5.185.248?
Carga el modelo guardado, predice 8 ejemplos de validación y muestra
la magnitud de los logits + la pérdida manual.
Uso: ./venv/bin/python diagnosticar.py
"""
import os
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf
from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

DIR = Path(__file__).resolve().parent
SALIDA_TF = DIR / "modelo_tf"
DATOS = DIR / "datos"


def cargar_csv(ruta):
    import csv
    pares = []
    with open(ruta, encoding="utf-8", newline="") as f:
        for fila in csv.DictReader(f):
            pares.append((fila["texto"], fila["categoria"]))
    return pares


def main():
    ruta_modelo = Path(sys.argv[1]) if len(sys.argv) > 1 else SALIDA_TF
    tok = AutoTokenizer.from_pretrained(str(ruta_modelo))
    modelo = TFAutoModelForSequenceClassification.from_pretrained(
        str(ruta_modelo), num_labels=16)
    valid = cargar_csv(DATOS / "validacion.csv")[:8]
    textos = [t for t, _ in valid]
    reales = [c for _, c in valid]
    print("Ejemplo tokenizado 0:", textos[0][:80])
    ids = tok(textos[0], return_tensors="tf")
    print("input_ids[0][:20]:", ids["input_ids"][0][:20].numpy().tolist())
    print("token_type_ids[0][:20]:", ids["token_type_ids"][0][:20].numpy().tolist())
    lotes = tok(
        textos, padding="max_length", truncation=True, max_length=64,
        return_tensors="tf",
    )
    salida = modelo(lotes)
    logits = salida.logits.numpy()
    print("logits forma:", logits.shape)
    print("logits min/max/media:", float(logits.min()), float(logits.max()),
          float(logits.mean()))
    print("logits abs medio:", float(np.abs(logits).mean()))
    with open(DATOS / "etiquetas.txt", encoding="utf-8") as f:
        etiquetas = [l.strip() for l in f if l.strip()]
    pred = logits.argmax(axis=-1)
    for i in range(len(valid)):
        print(f"  real={reales[i]!r} pred={etiquetas[pred[i]]!r} "
              f"logit_max={logits[i].max():.2f}")
    y = np.array([etiquetas.index(c) for c in reales])
    perdida = tf.keras.losses.SparseCategoricalCrossentropy(
        from_logits=True)(y, logits).numpy()
    print("Pérdida manual en estos 8:", float(perdida))
    return 0


if __name__ == "__main__":
    sys.exit(main())
