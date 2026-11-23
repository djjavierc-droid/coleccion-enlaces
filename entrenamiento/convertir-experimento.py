#!/usr/bin/env python3
"""Experimento decisivo: ¿qué rompe la conversión a TFLite?

Convierte el modelo afinado de tres formas y mide cada una en la
validación (argmax + confianza media):
  1. float32 (sin cuantizar): si también falla, el problema es el
     envoltorio/conversión, no la cuantización.
  2. rango dinámico (pesos INT8, activaciones float): candidato para
     la app si conserva la precisión.
  3. INT8 completo (el actual): referencia, ya medido (argmax 0.09).
"""

import os
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

DIR = Path(__file__).resolve().parent


def softmax(logits):
    import numpy as np
    e = np.exp(logits - logits.max(axis=-1, keepdims=True))
    return e / e.sum(axis=-1, keepdims=True)


def envoltorio(base):
    import tensorflow as tf
    entrada_ids = tf.keras.Input(shape=(None,), dtype=tf.int32, name="input_ids")
    entrada_mask = tf.keras.Input(shape=(None,), dtype=tf.int32, name="input_word_mask")
    salida = base(entrada_ids, entrada_mask)
    return tf.keras.Model(inputs=[entrada_ids, entrada_mask], outputs=salida.logits)


def evaluar_tflite(tflite_bytes, textos, y, tokenizer):
    import numpy as np
    import tensorflow as tf
    interp = tf.lite.Interpreter(model_content=tflite_bytes)
    det_e = interp.get_input_details()
    det_s = interp.get_output_details()
    idx_ids = next(i for i, d in enumerate(det_e) if "input_ids" in d["name"])
    idx_mask = next(i for i, d in enumerate(det_e) if "mask" in d["name"] or "attention" in d["name"])
    for i in range(len(det_e)):
        interp.resize_tensor_input(i, [1, 64])
    interp.allocate_tensors()
    tok_np = tokenizer(textos, padding="max_length", truncation=True, max_length=64, return_tensors="np")
    logits = np.zeros((len(textos), 16), dtype=np.float32)
    for i in range(len(textos)):
        interp.set_tensor(det_e[idx_ids]["index"], tok_np["input_ids"][i:i+1].astype(np.int32))
        interp.set_tensor(det_e[idx_mask]["index"], tok_np["attention_mask"][i:i+1].astype(np.int32))
        interp.invoke()
        logits[i] = interp.get_tensor(det_s[0]["index"])[0]
    probs = softmax(logits)
    return float((probs.argmax(axis=1) == y).mean()), float(probs.max(axis=1).mean())


def main() -> int:
    import csv
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

    etiquetas = (DIR / "datos" / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    indice = {n: i for i, n in enumerate(etiquetas)}
    with open(DIR / "datos" / "validacion.csv", encoding="utf-8") as f:
        pares = [(r["texto"], r["categoria"]) for r in csv.DictReader(f)]
    textos = [t for t, _ in pares]
    y = np.array([indice[c] for _, c in pares])

    tokenizer = AutoTokenizer.from_pretrained(str(DIR / "modelo_tf"))
    base = TFAutoModelForSequenceClassification.from_pretrained(
        str(DIR / "modelo_tf"), num_labels=len(etiquetas)
    )
    modelo = envoltorio(base)

    # 0) El envoltorio Keras en TF (sin convertir): debe dar ~0.95.
    tok = tokenizer(textos, padding="max_length", truncation=True, max_length=64, return_tensors="tf")
    logits = modelo([tok["input_ids"], tok["attention_mask"]], training=False).numpy()
    probs = softmax(logits)
    print(f"Envoltorio TF: argmax={float((probs.argmax(axis=1) == y).mean()):.4f} "
          f"conf_media={float(probs.max(axis=1).mean()):.3f}")

    # 1) Float32.
    conv = tf.lite.TFLiteConverter.from_keras_model(modelo)
    b_float = conv.convert()
    print(f"Float32 TFLite: {len(b_float)/1e6:.1f} MB")
    acc, conf = evaluar_tflite(b_float, textos, y, tokenizer)
    print(f"Float32 TFLite: argmax={acc:.4f} conf_media={conf:.3f}")

    # 2) Rango dinámico (pesos INT8, activaciones float).
    conv2 = tf.lite.TFLiteConverter.from_keras_model(modelo)
    conv2.optimizations = [tf.lite.Optimize.DEFAULT]
    b_dinamico = conv2.convert()
    print(f"Rango dinámico: {len(b_dinamico)/1e6:.1f} MB")
    acc, conf = evaluar_tflite(b_dinamico, textos, y, tokenizer)
    print(f"Rango dinámico: argmax={acc:.4f} conf_media={conf:.3f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
