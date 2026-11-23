"""Diagnóstico 2: ¿dónde explota la magnitud? Revisa, en el modelo base
sin entrenar, las estadísticas de: salida del codificador, salida del
pooler, y los pesos del clasificador.
Uso: ./venv/bin/python diagnosticar2.py modelo_base
"""
import os
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
import sys
from pathlib import Path

import numpy as np
from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

DIR = Path(__file__).resolve().parent


def stats(nombre, x):
    a = np.asarray(x)
    print(f"{nombre}: forma={a.shape} min={a.min():.4g} max={a.max():.4g} "
          f"media={a.mean():.4g} abs_media={np.abs(a).mean():.4g}")


def main():
    ruta = Path(sys.argv[1]) if len(sys.argv) > 1 else DIR / "modelo_base"
    tok = AutoTokenizer.from_pretrained(str(ruta))
    modelo = TFAutoModelForSequenceClassification.from_pretrained(
        str(ruta), num_labels=16)
    texto = "Mirador en casa El video completo sobre mirador."
    lotes = tok(texto, padding="max_length", truncation=True, max_length=64,
                return_tensors="tf")
    # Salida completa del codificador (sin clasificador).
    base = modelo.mobilebert(
        lotes["input_ids"], attention_mask=lotes["attention_mask"],
        token_type_ids=lotes["token_type_ids"], return_dict=True)
    stats("last_hidden_state", base.last_hidden_state.numpy())
    stats("pooled_output", base.pooler_output.numpy())
    # Pesos del clasificador.
    stats("classifier kernel", modelo.classifier.kernel.numpy())
    stats("classifier bias", modelo.classifier.bias.numpy())
    # ¿Qué devuelve el modelo completo?
    salida = modelo(lotes)
    stats("logits", salida.logits.numpy())
    return 0


if __name__ == "__main__":
    sys.exit(main())
