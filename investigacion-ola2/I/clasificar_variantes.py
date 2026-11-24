#!/usr/bin/env python3
"""HILO I (ola 2) — ¿Quitar emojis y hashtags estabiliza la clasificación del og:title?

Clasifica con el .tflite REAL de la app (byte-idéntico al de entrenamiento,
md5 d1b393265ae70107b2a9b4731df08fb6) las variantes de limpieza del og:title
de 3 videos, 2 veces por variante dentro del proceso. Ejecutar el script
2 veces (2 procesos distintos) = 4 observaciones por variante, para probar
determinismo intra-proceso e inter-proceso.

Uso:  entrenamiento/venv/bin/python clasificar_variantes.py <n_proceso>
Salida: resultados-proceso<N>.json (este script NO sobreescribe resultados
de otro proceso; la consolidación se hace aparte).
"""
import json
import os
import re
import sys
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

BASE = Path("/home/hatch/workspace/coleccion-enlaces")
DIR = BASE / "entrenamiento"
OUT = BASE / "investigacion-ola2" / "I"
UMBRAL = 0.5

VIDEOS = {
    "yofer": {
        "reel_id": "1168661422485798",
        "esperado": "Entretenimiento",
        "og_file": BASE / "investigacion-hilo-B" / "yofer-og.txt",
    },
    "tec": {
        "reel_id": "1395074825861831",
        "esperado": "Tecnología y celulares",
        "og_file": BASE / "investigacion-hilo-B" / "tec-ai-og.txt",
    },
    "freefire": {
        "reel_id": "914666554561073",
        "esperado": "Juegos",
        "og_file": BASE / "investigacion-hilo-B" / "game-freefire-og.txt",
    },
}

# --- extracción del og:title (multilínea, hasta og:description:) ---
def extraer_og_title(path: Path) -> str:
    txt = path.read_text(encoding="utf-8")
    m = re.search(r"^og:title: (.*?)^og:description:", txt, re.S | re.M)
    assert m, f"og:title no encontrado en {path}"
    return m.group(1).strip()

# --- limpiezas ---
EMOJI_RE = re.compile(
    "[\U0001F000-\U0001FAFF\U00002600-\U000027BF\U00002B00-\U00002BFF"
    "\u2190-\u21FF\u2300-\u23FF\u25A0-\u25FF\u00A9\u00AE\u203C\u2049"
    "\u3030\u303D\u3297\u3299\uFE0F\u200D]",
    flags=re.UNICODE,
)
HASHTAG_RE = re.compile(r"#\S+")
URL_RE = re.compile(r"https?://\S+")
PREFIJO_RE = re.compile(
    r"^\s*[\d.,]+[KM]?\s+views\s*·\s*[\d.,]+[KM]?\s+reactions\s*\|\s*",
    flags=re.I,
)
SUFIJO_RE = re.compile(r"\s*\|\s*[^|]+$")


def colapsar(t: str) -> str:
    return re.sub(r"\s+", " ", t).strip()


def sin_emojis(t: str) -> str:
    return colapsar(EMOJI_RE.sub("", t))


def sin_hashtags(t: str) -> str:
    return colapsar(HASHTAG_RE.sub("", t))


def sin_estructura(t: str) -> str:
    """Quita prefijo 'N views · N reactions |', sufijo '| Autor' y URLs."""
    t = PREFIJO_RE.sub("", t)
    t = URL_RE.sub("", t)
    t = SUFIJO_RE.sub("", t)
    return colapsar(t)


def variantes(crudo: str) -> dict:
    a = colapsar(crudo)
    return {
        "a_crudo": a,
        "b_sin_emojis": sin_emojis(a),
        "c_sin_hashtags": sin_hashtags(a),
        "d_sin_emojis_ni_hashtags": sin_hashtags(sin_emojis(a)),
        "e_d_sin_estructura": sin_estructura(sin_hashtags(sin_emojis(a))),
        "f_a_sin_estructura": sin_estructura(a),
        "g_a_sin_sufijo": colapsar(SUFIJO_RE.sub("", a)),
        # (h) texto EXACTO del hilo D (bateria.json YOFER-A3_ogtitle_completo),
        # solo existe para Yofer; se añade en main().
        # (i) limpieza estilo D: sin emojis, "#" quitado pero la palabra se
        # conserva, sin comillas «»"", sin URLs, sin prefijo/sufijo, "–"→espacio.
        "i_limpieza_estilo_D": limpieza_estilo_D(a),
        # (j) combo prometedor para Free Fire: sin emojis + sin estructura,
        # CON hashtags (los hashtags llevan la señal de Juegos).
        "j_sin_emojis_sin_estructura": sin_estructura(sin_emojis(a)),
    }


def limpieza_estilo_D(t: str) -> str:
    t = sin_emojis(t)
    t = re.sub(r"#(\S+)", r"\1", t)  # quita "#" pero conserva la palabra
    t = URL_RE.sub("", t)
    t = PREFIJO_RE.sub("", t)
    t = SUFIJO_RE.sub("", t)
    t = t.replace("«", "").replace("»", "").replace("“", "").replace("”", "")
    t = t.replace("–", " ").replace("—", " ")
    return colapsar(t)


# Texto exacto que el hilo D llamó "og:title completo" (bateria.json,
# YOFER-A3_ogtitle_completo): limpieza manual, NO el og:title crudo.
TEXTO_D_YOFER = ("¡¡¡50.000 VISITAS!!! ¡¡¡LO HEMOS CONSEGUIDO!!! LÁGRIMAS DE MI SOLEDAD "
                 "sigue conquistando corazones. GRACIAS POR HACERLO POSIBLE AHORA A POR "
                 "LAS 60.000 YOFER LÁGRIMAS DE MI SOLEDAD Youtube Spotify itunes")


def main() -> int:
    n_proceso = sys.argv[1] if len(sys.argv) > 1 else "1"
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer

    etiquetas = (DIR / "datos" / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    tokenizer = AutoTokenizer.from_pretrained(str(DIR / "modelo_tf"))
    interp = tf.lite.Interpreter(
        model_content=(DIR / "modelo_clasificador.tflite").read_bytes()
    )
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
        return {
            "categoria_top1": cat,
            "confianza_top1": round(conf, 6),
            "final_app": cat if conf >= UMBRAL else "Otros",
            "top3": [[c, round(p, 6)] for c, p in top3],
            "tokens": n_tokens,
            "truncado_64": n_tokens >= 64,
        }

    resultados = {"proceso": n_proceso, "umbral": UMBRAL, "videos": {}}
    for nombre, meta in VIDEOS.items():
        crudo = extraer_og_title(meta["og_file"])
        vars_ = variantes(crudo)
        if nombre == "yofer":
            vars_["h_texto_exactohiloD"] = TEXTO_D_YOFER
        resultados["videos"][nombre] = {
            "reel_id": meta["reel_id"],
            "esperado": meta["esperado"],
            "og_title_crudo": crudo,
            "variantes": {},
        }
        for vnombre, texto in vars_.items():
            runs = [predecir(texto), predecir(texto)]  # 2 repeticiones intra-proceso
            resultados["videos"][nombre]["variantes"][vnombre] = {
                "texto": texto,
                "n_chars": len(texto),
                "runs": runs,
                "determinista_intra": runs[0] == runs[1],
            }
            r = runs[0]
            print(f"{nombre} {vnombre}: {r['final_app']} ({r['confianza_top1']:.3f}) "
                  f"top3={r['top3']} tokens={r['tokens']} "
                  f"intra_det={runs[0] == runs[1]}")
    out = OUT / f"resultados-proceso{n_proceso}.json"
    out.write_text(json.dumps(resultados, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Guardado:", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
