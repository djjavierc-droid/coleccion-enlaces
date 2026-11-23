#!/usr/bin/env python3
"""Mide la confianza (softmax máx) de TF y TFLite en validación.

Distingue dos causas del 1/320 con umbral 0.5:
(a) la cuantización INT8 aplasta la confianza (TF alta, TFLite baja);
(b) el modelo en sí es poco seguro (ambas bajas).
"""

import os
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

DIR = Path(__file__).resolve().parent
N = 320  # toda la validación


def softmax(logits):
    import numpy as np
    e = np.exp(logits - logits.max(axis=-1, keepdims=True))
    return e / e.sum(axis=-1, keepdims=True)


def main() -> int:
    import csv
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

    etiquetas = (DIR / "datos" / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    indice = {n: i for i, n in enumerate(etiquetas)}
    with open(DIR / "datos" / "validacion.csv", encoding="utf-8") as f:
        pares = [(r["texto"], r["categoria"]) for r in csv.DictReader(f)][:N]
    textos = [t for t, _ in pares]
    y = np.array([indice[c] for _, c in pares])

    tokenizer = AutoTokenizer.from_pretrained(str(DIR / "modelo_tf"))
    tok = tokenizer(textos, padding="max_length", truncation=True, max_length=64, return_tensors="tf")
    modelo = TFAutoModelForSequenceClassification.from_pretrained(str(DIR / "modelo_tf"), num_labels=16)
    logits_tf = modelo(input_ids=tok["input_ids"], attention_mask=tok["attention_mask"]).logits.numpy()
    probs_tf = softmax(logits_tf)
    print(f"TF: argmax={float((probs_tf.argmax(axis=1) == y).mean()):.4f} "
          f"conf_media={float(probs_tf.max(axis=1).mean()):.3f} "
          f">=0.5: {int((probs_tf.max(axis=1) >= 0.5).sum())}/{len(y)}")

    interp = tf.lite.Interpreter(model_content=(DIR / "modelo_clasificador.tflite").read_bytes())
    det_e = interp.get_input_details()
    det_s = interp.get_output_details()
    idx_ids = next(i for i, d in enumerate(det_e) if "input_ids" in d["name"])
    idx_mask = next(i for i, d in enumerate(det_e) if "mask" in d["name"] or "attention" in d["name"])
    for i in range(len(det_e)):
        interp.resize_tensor_input(i, [1, 64])
    interp.allocate_tensors()
    tok_np = tokenizer(textos, padding="max_length", truncation=True, max_length=64, return_tensors="np")
    logits_lite = np.zeros((len(textos), 16), dtype=np.float32)
    for i in range(len(textos)):
        interp.set_tensor(det_e[idx_ids]["index"], tok_np["input_ids"][i:i+1].astype(np.int32))
        interp.set_tensor(det_e[idx_mask]["index"], tok_np["attention_mask"][i:i+1].astype(np.int32))
        interp.invoke()
        logits_lite[i] = interp.get_tensor(det_s[0]["index"])[0]
    probs_lite = softmax(logits_lite)
    print(f"TFLite: argmax={float((probs_lite.argmax(axis=1) == y).mean()):.4f} "
          f"conf_media={float(probs_lite.max(axis=1).mean()):.3f} "
          f">=0.5: {int((probs_lite.max(axis=1) >= 0.5).sum())}/{len(y)}")
    print(f"Correlación logits TF/TFLite: {float(np.corrcoef(logits_tf.ravel(), logits_lite.ravel())[0, 1]):.3f}")
    print(f"Escala: TF std={float(logits_tf.std()):.2f} TFLite std={float(logits_lite.std()):.2f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
