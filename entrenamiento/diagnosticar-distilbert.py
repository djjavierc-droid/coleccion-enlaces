#!/usr/bin/env python3
"""Diagnóstico del DistilBERT base sin entrenar (opción A).

Carga distilbert-base-uncased desde modelo_base_distilbert y hace un
pase hacia adelante con 8 ejemplos de validación. Comprueba:
- los logits son sanos (decenas, no millones como MobileBERT);
- si el modelo acepta token_type_ids (el código Kotlin pasa 3
  entradas: input_ids, attention_mask, token_type_ids);
- la pérdida manual sobre esos 8 ejemplos.
"""

import csv
import os
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

DIR = Path(__file__).resolve().parent
BASE = DIR / "modelo_base_distilbert"


def main() -> int:
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

    etiquetas = (DIR / "datos" / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    indice = {n: i for i, n in enumerate(etiquetas)}
    with (DIR / "datos" / "validacion.csv").open(encoding="utf-8", newline="") as f:
        pares = [(r["texto"], r["categoria"]) for r in csv.DictReader(f)][:8]

    tokenizer = AutoTokenizer.from_pretrained(str(BASE))
    textos = [t for t, _ in pares]
    y = np.array([indice[c] for _, c in pares], dtype=np.int64)
    tok = tokenizer(
        textos, padding="max_length", truncation=True,
        max_length=64, return_tensors="tf",
    )
    print("Claves del tokenizador:", list(tok.keys()))

    modelo = TFAutoModelForSequenceClassification.from_pretrained(
        str(BASE), num_labels=len(etiquetas)
    )
    print("Clase del modelo:", type(modelo).__name__)
    print("Atributo base:", hasattr(modelo, "distilbert"))
    print("Capas del codificador:", len(modelo.distilbert.transformer.layer))

    # Pase con token_type_ids (como lo haría el pipeline de MobileBERT).
    try:
        salida = modelo(
            input_ids=tok["input_ids"],
            attention_mask=tok["attention_mask"],
            token_type_ids=tok["token_type_ids"],
        )
        print("token_type_ids: ACEPTADO")
    except Exception as e:
        print("token_type_ids: RECHAZADO ->", type(e).__name__, str(e)[:200])
        salida = modelo(
            input_ids=tok["input_ids"],
            attention_mask=tok["attention_mask"],
        )
        print("Pase sin token_type_ids: OK")

    logits = salida.logits.numpy()
    print(f"logits min/max: {logits.min():.2f} / {logits.max():.2f}")
    print(f"logits media abs: {np.abs(logits).mean():.2f}")
    pred = logits.argmax(axis=1)
    print("Predicciones:", [etiquetas[p] for p in pred])
    print("Esperadas:   ", [c for _, c in pares])
    # Pérdida manual (cross-entropy con softmax estable).
    m = logits.max(axis=1, keepdims=True)
    logsum = m[:, 0] + np.log(np.exp(logits - m).sum(axis=1))
    perdida = float(np.mean(logsum - logits[np.arange(len(y)), y]))
    print(f"Pérdida manual (8 ejemplos): {perdida:.4f}  (azar ln16 = 2.7726)")
    print(f"Acierto en los 8: {(pred == y).sum()}/8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
