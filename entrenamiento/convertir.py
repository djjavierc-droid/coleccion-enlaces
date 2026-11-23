#!/usr/bin/env python3
"""Convierte el DistilBERT afinado a .tflite (rango dinámico) y lo verifica.

Modelo: distilbert-base-uncased afinado por entrenar.py (Fix 3,
opción A). DistilBERT NO usa token_type_ids, así que el .tflite tiene
DOS entradas: input_ids e input_word_mask (la app pasa 2 o 3 según
`inputTensorCount`; ver ClasificadorModelo.kt).

Pasos:
1. Carga el SavedModel afinado (entrenar.py) y lo envuelve en un
   modelo Keras con las entradas que espera la app.
2. Conversión a TFLite con cuantización de RANGO DINÁMICO (pesos
   INT8, activaciones float32).
3. Verificación con el intérprete base de TF Lite (EL MISMO motor que
   usa la app en Kotlin, con la misma tokenización WordPiece de
   transformers): caso EterniCodIA, validación completa y textos
   genéricos (deben quedar en "Otros" por baja confianza).

CUANTIZACIÓN — por qué NO INT8 completo (medido 2026-11-23,
medir-confianza.py y convertir-experimento.py): con
representative_dataset + TFLITE_BUILTINS_INT8 el modelo queda
DESTRUIDO: precisión argmax en validación 0.0906 (azar = 0.0625),
confianza media 0.366, correlación de logits con el modelo TF de
0.07. Las activaciones INT8 (softmax de atención, LayerNorm, GELU)
pierden toda la señal. Con rango dinámico la precisión es idéntica
a TF (argmax 0.9531, confianza media 0.862) con el mismo tamaño
(~67.8 MB). La primera verificación 0/319 se atribuyó al principio
a un intercambio de entradas; la medida posterior demostró que la
causa real era la cuantización INT8, no el orden de las entradas.

NO se añaden metadatos NLClassifier: tflite-support no tiene ruedas
para Python 3.12 y la app no los necesita (intérprete base +
TokenizadorWordPiece en Kotlin + etiquetas.txt en assets).

Salida:
- modelo_clasificador.tflite (en esta carpeta; se copia a
  app/src/main/assets/ cuando la verificación pase).
- verificacion.json : precisión en validación, confianza del caso
  EterniCodIA y distribución de confianza en genéricos.
"""

import csv
import json
import sys
from pathlib import Path

DIR = Path(__file__).resolve().parent
DIR_DATOS = DIR / "datos"
MODELO_TF = DIR / "modelo_tf"
SALIDA = DIR / "modelo_clasificador.tflite"
SALIDA_VERIF = DIR / "verificacion.json"

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
    from transformers import AutoTokenizer, TFAutoModelForSequenceClassification

    etiquetas = (DIR_DATOS / "etiquetas.txt").read_text(encoding="utf-8").splitlines()
    tokenizer = AutoTokenizer.from_pretrained(str(MODELO_TF))

    # ---- 1+2. Conversión con rango dinámico (pesos INT8, activaciones float) ----
    # NO INT8 completo: destruye DistilBERT (ver docstring).
    # La conversión se hace con from_concrete_functions y una firma de
    # entrada explícita [input_ids, input_word_mask]: from_keras_model
    # (TF 2.20) emite las entradas en orden inverso (la máscara en el
    # índice 0, con nombres serving_default_*) y la app alimenta POR
    # POSICIÓN (índice 0 = input_ids, índice 1 = attention_mask; ver
    # ClasificadorModelo.kt), así que ese .tflite clasificaría basura
    # en producción aunque la verificación por nombre pasara. Con la
    # firma explícita el orden es el de la v1.0.3: (0, 'input_ids'),
    # (1, 'input_word_mask'). Medido en Fix 5 (2026-11-23).
    base = TFAutoModelForSequenceClassification.from_pretrained(
        str(MODELO_TF), num_labels=len(etiquetas)
    )

    @tf.function(input_signature=[
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="input_ids"),
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="input_word_mask"),
    ])
    def servir(ids, mask):
        return base(input_ids=ids, attention_mask=mask).logits

    funcion = servir.get_concrete_function()
    conversor = tf.lite.TFLiteConverter.from_concrete_functions([funcion], servir)
    conversor.optimizations = [tf.lite.Optimize.DEFAULT]
    conversor.inference_output_type = tf.float32
    tflite_bytes = conversor.convert()
    SALIDA.write_bytes(tflite_bytes)
    print(f"TFLite: {len(tflite_bytes)/1e6:.1f} MB -> {SALIDA}")

    # ---- 3. Verificación con el intérprete base (mismo que la app) ----
    interprete = tf.lite.Interpreter(model_content=tflite_bytes)
    detalles_entrada = interprete.get_input_details()
    detalles_salida = interprete.get_output_details()
    print("Entradas TFLite:", [(d["name"], d["shape"].tolist(), str(d["dtype"])) for d in detalles_entrada])
    print("Salidas TFLite:", [(d["name"], d["shape"].tolist(), str(d["dtype"])) for d in detalles_salida])
    assert len(detalles_entrada) == 2, "el modelo debe tener 2 entradas"
    assert len(detalles_salida) == 1 and detalles_salida[0]["shape"].tolist()[-1] == len(etiquetas)
    # Contrato de la app (ClasificadorModelo.kt alimenta POR POSICIÓN):
    # índice 0 = input_ids, índice 1 = attention_mask. Si el conversor
    # invierte el orden, la verificación por nombre pasaría pero la app
    # clasificaría basura: fallar aquí, no en producción.
    assert "input_ids" in detalles_entrada[0]["name"], (
        f"la entrada 0 debe ser input_ids, es {detalles_entrada[0]['name']}"
    )
    assert "mask" in detalles_entrada[1]["name"], (
        f"la entrada 1 debe ser la máscara, es {detalles_entrada[1]['name']}"
    )
    # Las entradas se asignan POR NOMBRE: el conversor las ordena
    # por nombre ('input_word_mask' antes que 'input_ids'), no en el
    # orden de las entradas de Keras. Asignar por posición alimenta
    # los ids a la máscara y al revés.
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
    SALIDA_VERIF.write_text(json.dumps(verif, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Verificación en {SALIDA_VERIF}")

    ok = (
        verif["eternicodia_categoria"] == CATEGORIA_ESPERADA
        and verif["eternicodia_confianza"] >= UMBRAL_CONFIANZA
        and precision >= 0.80
    )
    print("VERIFICACIÓN:", "PASA" if ok else "NO PASA")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
