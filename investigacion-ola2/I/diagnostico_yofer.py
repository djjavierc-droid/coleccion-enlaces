#!/usr/bin/env python3
"""Diagnóstico Yofer: ¿qué micro-detalle separa h (Viajes 0,430) de i (Entretenimiento 0,643)?

k1 = texto exacto hilo D + "!!!" restaurados tras POSIBLE y 60.000
k2 = limpieza estilo D con todos los "!" eliminados
k3 = limpieza estilo D sin las palabras de hashtag #Yofer #LágrimasDeMiSoledad #50k
"""
import json
import os
import re
import sys
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
sys.path.insert(0, str(Path(__file__).parent))
import clasificar_variantes as cv

BASE = Path("/home/hatch/workspace/coleccion-enlaces")
DIR = BASE / "entrenamiento"
OUT = BASE / "investigacion-ola2" / "I"
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
        tok = tokenizer(texto, padding="max_length", truncation=True, max_length=64,
                        return_tensors="np")
        n_tokens = int((tok["input_ids"][0] != 0).sum())
        interp.set_tensor(det_e[idx_ids]["index"], tok["input_ids"].astype(np.int32))
        interp.set_tensor(det_e[idx_mask]["index"], tok["attention_mask"].astype(np.int32))
        interp.invoke()
        probs = softmax(interp.get_tensor(det_s[0]["index"])[0].astype(np.float64))
        top = np.argsort(probs)[::-1][:3]
        top3 = [(etiquetas[i], float(probs[i])) for i in top]
        cat, conf = top3[0]
        return {"categoria_top1": cat, "confianza_top1": round(conf, 6),
                "final_app": cat if conf >= UMBRAL else "Otros",
                "top3": [[c, round(p, 6)] for c, p in top3],
                "tokens": n_tokens, "truncado_64": n_tokens >= 64}

    crudo = cv.extraer_og_title(BASE / "investigacion-hilo-B" / "yofer-og.txt")
    a = cv.colapsar(crudo)
    i_txt = cv.limpieza_estilo_D(a)
    h_txt = cv.TEXTO_D_YOFER

    k1 = h_txt.replace("GRACIAS POR HACERLO POSIBLE AHORA",
                       "GRACIAS POR HACERLO POSIBLE!!! AHORA").replace(
        "AHORA A POR LAS 60.000 YOFER", "AHORA A POR LAS 60.000!!! YOFER")
    k2 = re.sub(r"!+", "", i_txt)
    k2 = cv.colapsar(k2)
    k3 = cv.colapsar(i_txt.replace("Yofer LágrimasDeMiSoledad 50k ", ""))

    casos = {"k1_h_mas_exclamaciones": k1,
             "k2_i_sin_exclamaciones": k2,
             "k3_i_sin_palabras_hashtag_yofer": k3}
    res = {}
    for nombre, texto in casos.items():
        runs = [predecir(texto), predecir(texto)]
        res[nombre] = {"texto": texto, "runs": runs,
                       "determinista_intra": runs[0] == runs[1]}
        r = runs[0]
        print(f"{nombre}: {r['final_app']} ({r['confianza_top1']:.3f}) "
              f"top3={r['top3']} tokens={r['tokens']}")
        print(f"   texto: {texto}")
    (OUT / "diagnostico-yofer.json").write_text(
        json.dumps(res, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
