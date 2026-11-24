#!/usr/bin/env python3
"""Clasifica textos con el .tflite real de Coleccion de enlaces.
Uso: ./venv/bin/python clasificar.py <archivo_textos.txt>
"""
import os
import sys
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

DIR = Path("/home/hatch/workspace/coleccion-enlaces/entrenamiento")
UMBRAL = 0.5


def main() -> int:
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer

    etiquetas = (DIR / "datos" / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    tokenizer = AutoTokenizer.from_pretrained(str(DIR / "modelo_tf"))
    interp = tf.lite.Interpreter(model_content=(DIR / "modelo_clasificador.tflite").read_bytes())
    det_e = interp.get_input_details()
    det_s = interp.get_output_details()
    idx_ids = next(i for i, d in enumerate(det_e) if "input_ids" in d["name"])
    idx_mask = next(i for i, d in enumerate(det_e) if "mask" in d["name"] or "attention" in d["name"])
    for i in range(len(det_e)):
        interp.resize_tensor_input(i, [1, 64])
    interp.allocate_tensors()

    def softmax(logits):
        e = np.exp(logits - logits.max())
        return e / e.sum()

    def predecir(texto):
        tok = tokenizer(texto, padding="max_length", truncation=True, max_length=64, return_tensors="np")
        interp.set_tensor(det_e[idx_ids]["index"], tok["input_ids"].astype(np.int32))
        interp.set_tensor(det_e[idx_mask]["index"], tok["attention_mask"].astype(np.int32))
        interp.invoke()
        probs = softmax(interp.get_tensor(det_s[0]["index"])[0].astype(np.float64))
        top = np.argsort(probs)[::-1][:3]
        return [(etiquetas[i], float(probs[i])) for i in top]

    for texto in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
        if not texto.strip():
            continue
        top = predecir(texto)
        cat, conf = top[0]
        final = cat if conf >= UMBRAL else "Otros"
        print(f"TEXTO: {texto[:120]}")
        print(f"  -> {final} ({conf:.3f})  top3: " + ", ".join(f"{c} {p:.3f}" for c, p in top))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
