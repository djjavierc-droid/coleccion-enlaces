"""Diagnóstico 3: recorre el codificador capa por capa y muestra dónde
explota la magnitud (embeddings, capa 0, 1, ...).
Uso: ./venv/bin/python diagnosticar3.py modelo_base
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
    print(f"{nombre}: max_abs={np.abs(a).max():.4g} media_abs={np.abs(a).mean():.4g}")


def main():
    ruta = Path(sys.argv[1]) if len(sys.argv) > 1 else DIR / "modelo_base"
    tok = AutoTokenizer.from_pretrained(str(ruta))
    modelo = TFAutoModelForSequenceClassification.from_pretrained(
        str(ruta), num_labels=16)
    base = modelo.mobilebert
    texto = "Mirador en casa El video completo sobre mirador."
    lotes = tok(texto, padding="max_length", truncation=True, max_length=64,
                return_tensors="tf")
    # Embeddings (con máscara de atención extendida como la usa el codificador).
    emb = base.embeddings(
        input_ids=lotes["input_ids"],
        token_type_ids=lotes["token_type_ids"],
        training=False,
    )
    mag("embeddings", emb.numpy())
    # Máscara extendida, igual que en TFMobileBertMainLayer.call.
    ext = tf.reshape(lotes["attention_mask"], (1, 1, 1, 64))
    ext = tf.cast(ext, dtype=emb.dtype)
    ext = tf.multiply(tf.subtract(tf.constant(1.0, dtype=emb.dtype), ext),
                      tf.constant(-10000.0, dtype=emb.dtype))
    h = emb
    for i, capa in enumerate(base.encoder.layer):
        h = capa(h, ext, None, False, training=False)[0]
        mag(f"capa {i:2d}", h.numpy())
        if i >= 5 and np.abs(h.numpy()).max() > 1e6:
            print("  -> ya explotó; paro el recorrido")
            break
    return 0


if __name__ == "__main__":
    sys.exit(main())
