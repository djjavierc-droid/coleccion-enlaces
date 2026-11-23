"""Diagnóstico 4: dentro de una capa (22 o 23), recorre cada sub-bloque
(bottleneck, atención, ffn.0/1/2, intermediate, output) y muestra dónde
explota la magnitud.
Uso: ./venv/bin/python diagnosticar4.py modelo_base 22
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


def mag(nombre, x):
    a = np.asarray(x)
    print(f"  {nombre}: max_abs={np.abs(a).max():.4g} media_abs={np.abs(a).mean():.4g}")


def main():
    ruta = Path(sys.argv[1]) if len(sys.argv) > 1 else DIR / "modelo_base"
    objetivo = int(sys.argv[2]) if len(sys.argv) > 2 else 22
    tok = AutoTokenizer.from_pretrained(str(ruta))
    modelo = TFAutoModelForSequenceClassification.from_pretrained(
        str(ruta), num_labels=16)
    base = modelo.mobilebert
    texto = "Mirador en casa El video completo sobre mirador."
    lotes = tok(texto, padding="max_length", truncation=True, max_length=64,
                return_tensors="tf")
    emb = base.embeddings(
        input_ids=lotes["input_ids"],
        token_type_ids=lotes["token_type_ids"],
        training=False,
    )
    ext = tf.reshape(lotes["attention_mask"], (1, 1, 1, 64))
    ext = tf.cast(ext, dtype=emb.dtype)
    ext = tf.multiply(tf.subtract(tf.constant(1.0, dtype=emb.dtype), ext),
                      tf.constant(-10000.0, dtype=emb.dtype))
    h = emb
    for i, capa in enumerate(base.encoder.layer):
        if i < objetivo:
            h = capa(h, ext, None, False, training=False)[0]
            continue
        print(f"=== capa {i} (entrada max={np.abs(h.numpy()).max():.4g}) ===")
        q, k, v, layer_input = capa.bottleneck(h)
        mag("bottleneck query", q.numpy())
        mag("bottleneck key", k.numpy())
        mag("bottleneck value", v.numpy())
        mag("bottleneck layer_input", layer_input.numpy())
        att = capa.attention(q, k, v, layer_input, ext, None, False,
                             training=False)
        mag("attention output", att[0].numpy())
        a = att[0]
        for j, ffn in enumerate(capa.ffn):
            a = ffn(a)
            mag(f"ffn.{j} output", a.numpy())
        inter = capa.intermediate(a)
        mag("intermediate", inter.numpy())
        out = capa.mobilebert_output(inter, a, h, training=False)
        mag("output (salida capa)", out.numpy())
        break
    return 0


if __name__ == "__main__":
    sys.exit(main())
