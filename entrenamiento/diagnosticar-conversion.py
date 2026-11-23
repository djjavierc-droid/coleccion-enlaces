#!/usr/bin/env python3
"""Compara logits TF (modelo afinado) vs TFLite en el mismo texto.

Detecta si la conversión INT8 aplastó los logits o si el modelo
guardado no es el afinado.
"""

import os
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

DIR = Path(__file__).resolve().parent
TEXTO = "Contratamos a una IA... y el primer día ya vendía croquetas en Marte... Hoy hemos contratado a una"


def main() -> int:
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

    etiquetas = (DIR / "datos" / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    tokenizer = AutoTokenizer.from_pretrained(str(DIR / "modelo_tf"))
    tok = tokenizer(TEXTO, padding="max_length", truncation=True, max_length=64, return_tensors="tf")

    modelo_tf = TFAutoModelForSequenceClassification.from_pretrained(
        str(DIR / "modelo_tf"), num_labels=len(etiquetas)
    )
    logits_tf = modelo_tf(
        input_ids=tok["input_ids"], attention_mask=tok["attention_mask"]
    ).logits.numpy()[0]
    print("TF  logits:", np.round(logits_tf, 2))
    print("TF  argmax:", etiquetas[int(np.argmax(logits_tf))],
          "softmax max:", float(np.exp(logits_tf - logits_tf.max()).max() / np.exp(logits_tf - logits_tf.max()).sum()))

    tflite_bytes = (DIR / "modelo_clasificador.tflite").read_bytes()
    interp = tf.lite.Interpreter(model_content=tflite_bytes)
    det_e = interp.get_input_details()
    det_s = interp.get_output_details()
    idx_ids = next(i for i, d in enumerate(det_e) if "input_ids" in d["name"])
    idx_mask = next(i for i, d in enumerate(det_e) if "mask" in d["name"] or "attention" in d["name"])
    for i in range(len(det_e)):
        interp.resize_tensor_input(i, [1, 64])
    interp.allocate_tensors()
    tok_np = tokenizer(TEXTO, padding="max_length", truncation=True, max_length=64, return_tensors="np")
    interp.set_tensor(det_e[idx_ids]["index"], tok_np["input_ids"].astype(np.int32))
    interp.set_tensor(det_e[idx_mask]["index"], tok_np["attention_mask"].astype(np.int32))
    interp.invoke()
    logits_lite = interp.get_tensor(det_s[0]["index"])[0]
    print("TFLite logits:", np.round(logits_lite, 2))
    print("TFLite argmax:", etiquetas[int(np.argmax(logits_lite))])
    print("Correlación TF vs TFLite:", float(np.corrcoef(logits_tf, logits_lite)[0, 1]))
    print("Escala: max|TF|=%.2f max|TFLite|=%.2f" % (np.abs(logits_tf).max(), np.abs(logits_lite).max()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
