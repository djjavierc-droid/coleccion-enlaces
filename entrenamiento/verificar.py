#!/usr/bin/env python3
"""Verifica un .tflite ya convertido, con el intérprete base.

Las entradas se asignan POR NOMBRE (el orden de get_input_details
no es el de las entradas de Keras: el conversor las ordena por
nombre, 'input_word_mask' antes que 'input_ids').

Uso: ./venv/bin/python verificar.py [ruta.tflite]
"""

import csv
import json
import sys
from pathlib import Path

DIR = Path(__file__).resolve().parent
DIR_DATOS = DIR / "datos"
MODELO_TF = DIR / "modelo_tf"

TEXTO_ETERNICODIA = "Contratamos a una IA... y el primer día ya vendía croquetas en Marte..."
DESC_ETERNICODIA = "Hoy hemos contratado a una"
CATEGORIA_ESPERADA = "Tecnología y celulares"
UMBRAL_CONFIANZA = 0.5


def leer_csv(ruta):
    with ruta.open(encoding="utf-8", newline="") as f:
        return [(fila["texto"], fila["categoria"]) for fila in csv.DictReader(f)]


def main() -> int:
    import numpy as np
    import tensorflow as tf
    from transformers import AutoTokenizer

    ruta_tflite = Path(sys.argv[1]) if len(sys.argv) > 1 else DIR / "modelo_clasificador.tflite"
    etiquetas = (DIR_DATOS / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    tokenizer = AutoTokenizer.from_pretrained(str(MODELO_TF))

    tflite_bytes = ruta_tflite.read_bytes()
    interprete = tf.lite.Interpreter(model_content=tflite_bytes)
    detalles_entrada = interprete.get_input_details()
    detalles_salida = interprete.get_output_details()
    print("Entradas:", [(d["name"], d["shape"].tolist()) for d in detalles_entrada])
    print("Salidas:", [(d["name"], d["shape"].tolist()) for d in detalles_salida])
    # Asignación POR NOMBRE, no por posición.
    idx_ids = next(i for i, d in enumerate(detalles_entrada) if "input_ids" in d["name"])
    idx_mask = next(
        i for i, d in enumerate(detalles_entrada)
        if "mask" in d["name"] or "attention" in d["name"]
    )
    print(f"input_ids -> detalle {idx_ids}; máscara -> detalle {idx_mask}")
    for indice in range(len(detalles_entrada)):
        interprete.resize_tensor_input(indice, [1, 64])
    interprete.allocate_tensors()

    def softmax(logits):
        e = np.exp(logits - logits.max())
        return e / e.sum()

    def predecir(texto):
        tok = tokenizer(
            texto, padding="max_length", truncation=True,
            max_length=64, return_tensors="np",
        )
        interprete.set_tensor(detalles_entrada[idx_ids]["index"], tok["input_ids"].astype(np.int32))
        interprete.set_tensor(detalles_entrada[idx_mask]["index"], tok["attention_mask"].astype(np.int32))
        interprete.invoke()
        logits = interprete.get_tensor(detalles_salida[0]["index"])[0]
        probs = softmax(logits.astype(np.float64))
        mejor = int(np.argmax(probs))
        return etiquetas[mejor], float(probs[mejor])

    cat_eterni, conf_eterni = predecir(f"{TEXTO_ETERNICODIA} {DESC_ETERNICODIA}")
    print(f"EterniCodIA -> {cat_eterni} ({conf_eterni:.3f}) [esperado: {CATEGORIA_ESPERADA}]")

    valid = leer_csv(DIR_DATOS / "validacion.csv")
    correctas = 0
    confianzas = []
    for texto, esperada in valid:
        cat, conf = predecir(texto)
        if conf < UMBRAL_CONFIANZA:
            cat = "Otros"
        confianzas.append(conf)
        if cat == esperada:
            correctas += 1
    precision = correctas / len(valid)
    print(f"Validación con umbral {UMBRAL_CONFIANZA}: {correctas}/{len(valid)} = {precision:.3f}")

    genericos = leer_csv(DIR_DATOS / "genericos.csv")
    conf_genericos = []
    en_otros = 0
    for texto, _ in genericos:
        cat, conf = predecir(texto)
        conf_genericos.append(conf)
        if conf < UMBRAL_CONFIANZA:
            en_otros += 1
    print(
        f"Genéricos en Otros: {en_otros}/{len(genericos)} "
        f"(confianza media {float(np.mean(conf_genericos)):.3f}, "
        f"máx {max(conf_genericos):.3f})"
    )

    verif = {
        "eternicodia_categoria": cat_eterni,
        "eternicodia_confianza": conf_eterni,
        "eternicodia_esperada": CATEGORIA_ESPERADA,
        "precision_validacion_umbral": precision,
        "umbral_confianza": UMBRAL_CONFIANZA,
        "genericos_en_otros": en_otros,
        "genericos_total": len(genericos),
        "confianza_media_validacion": float(np.mean(confianzas)),
        "tamano_mb": len(tflite_bytes) / 1e6,
    }
    (DIR / "verificacion.json").write_text(
        json.dumps(verif, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    ok = (
        verif["eternicodia_categoria"] == CATEGORIA_ESPERADA
        and verif["eternicodia_confianza"] >= UMBRAL_CONFIANZA
        and precision >= 0.80
    )
    print("VERIFICACIÓN:", "PASA" if ok else "NO PASA")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
