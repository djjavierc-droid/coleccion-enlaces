"""Hilo E ola 2 — MobileNetV2 ImageNet sobre cuadros reales (referencia ML Kit Image Labeling).
Equivalente aproximado de ML Kit Image Labeling: etiquetas ImageNet top-5 por cuadro.
Mapeo manual ImageNet -> 16 categorias de la app (ver MAPPING abajo).
Uso: venv de entrenamiento (tensorflow 2.21).
"""
import json, glob, os, sys
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
import numpy as np
from PIL import Image

FRAMES_DIR = "/home/hatch/workspace/coleccion-enlaces/investigacion-ola2/E/frames"
OUT = "/home/hatch/workspace/coleccion-enlaces/investigacion-ola2/E/mobilenet-resultados.json"

# Mapeo ImageNet (etiqueta en minusculas, coincidencia por subcadena) -> categoria app
MAPPING = [
    # Entretenimiento: musica, instrumentos, escenario, cantante
    ("guitar", "Entretenimiento"), ("banjo", "Entretenimiento"),
    ("grand piano", "Entretenimiento"), ("upright piano", "Entretenimiento"),
    ("organ", "Entretenimiento"), ("accordion", "Entretenimiento"),
    ("microphone", "Entretenimiento"), ("stage", "Entretenimiento"),
    ("sax", "Entretenimiento"), ("trumpet", "Entretenimiento"),
    ("drum", "Entretenimiento"), ("violin", "Entretenimiento"),
    ("cello", "Entretenimiento"), ("harp", "Entretenimiento"),
    ("flute", "Entretenimiento"), ("clarinet", "Entretenimiento"),
    ("maraca", "Entretenimiento"), ("gong", "Entretenimiento"),
    ("singer", "Entretenimiento"), ("concert", "Entretenimiento"),
    # Tecnologia y celulares
    ("cellular telephone", "Tecnología y celulares"),
    ("hand-held computer", "Tecnología y celulares"),
    ("laptop", "Tecnología y celulares"),
    ("notebook", "Tecnología y celulares"),
    ("desktop computer", "Tecnología y celulares"),
    ("screen", "Tecnología y celulares"),
    ("monitor", "Tecnología y celulares"),
    ("ipod", "Tecnología y celulares"),
    ("remote control", "Tecnología y celulares"),
    ("web site", "Tecnología y celulares"),
    ("keyboard", "Tecnología y celulares"),
    ("mouse", "Tecnología y celulares"),
    ("printer", "Tecnología y celulares"),
    # Juegos: ImageNet NO tiene clase de videojuego; el gameplay de movil cae en
    # 'cellular telephone'/'screen' -> Tecnologia (limitacion documentada).
    ("joystick", "Juegos"),
    # Autos y motos
    ("sports car", "Autos y motos"), ("racer", "Autos y motos"),
    ("motor scooter", "Autos y motos"), ("mountain bike", "Autos y motos"),
    ("bicycle", "Autos y motos"), ("limousine", "Autos y motos"),
    ("pickup", "Autos y motos"), ("minivan", "Autos y motos"),
    ("school bus", "Autos y motos"), ("trolleybus", "Autos y motos"),
    ("crane", "Autos y motos"), ("trailer truck", "Autos y motos"),
    ("passenger car", "Autos y motos"), ("fire engine", "Autos y motos"),
    ("ambulance", "Autos y motos"), ("garbage truck", "Autos y motos"),
    # Cocina
    ("frying pan", "Cocina"), ("dutch oven", "Cocina"),
    ("coffee pot", "Cocina"), ("crock pot", "Cocina"),
    ("mixing bowl", "Cocina"), ("spatula", "Cocina"),
    ("stove", "Cocina"), ("refrigerator", "Cocina"),
    # Construccion / DIY
    ("hammer", "Hazlo tú mismo (DIY)"), ("screwdriver", "Hazlo tú mismo (DIY)"),
    ("drill", "Hazlo tú mismo (DIY)"), ("saw", "Hazlo tú mismo (DIY)"),
    # Jardin y plantas
    ("pot", "Jardín y plantas"), ("flower", "Jardín y plantas"),
    # Salud y ejercicio
    ("dumbbell", "Salud y ejercicio"), ("barbell", "Salud y ejercicio"),
    # Moda y ropa
    ("sunglasses", "Moda y ropa"), ("sweatshirt", "Moda y ropa"),
    ("jersey", "Moda y ropa"), ("gown", "Moda y ropa"),
    ("suit", "Moda y ropa"), ("jean", "Moda y ropa"),
    ("sneaker", "Moda y ropa"), ("loafer", "Moda y ropa"),
    ("cowboy boot", "Moda y ropa"), ("handbag", "Moda y ropa"),
    # Viajes
    ("airliner", "Viajes"), ("airship", "Viajes"), ("speedboat", "Viajes"),
    ("catamaran", "Viajes"), ("beach", "Viajes"),
    # Pesca y campo
    ("fishing", "Pesca y campo"), ("cowboy hat", "Pesca y campo"),
    ("horse", "Pesca y campo"),
    # Noticias
    ("newspaper", "Noticias"),
]

def map_label(label):
    l = label.lower()
    for needle, cat in MAPPING:
        if needle in l:
            return cat
    return None

def main():
    from tensorflow.keras.applications import MobileNetV2
    from tensorflow.keras.applications.mobilenet_v2 import preprocess_input, decode_predictions
    model = MobileNetV2(weights="imagenet")
    results = {}
    for path in sorted(glob.glob(os.path.join(FRAMES_DIR, "*.jpg"))):
        img = Image.open(path).convert("RGB").resize((224, 224))
        x = preprocess_input(np.expand_dims(np.array(img, dtype=np.float32), 0))
        preds = decode_predictions(model.predict(x, verbose=0), top=5)[0]
        top = [{"label": p[1], "score": round(float(p[2]), 4),
                "categoria_mapeada": map_label(p[1])} for p in preds]
        mapped = [t for t in top if t["categoria_mapeada"]]
        results[os.path.basename(path)] = {
            "top5": top,
            "categoria_por_top1": mapped[0]["categoria_mapeada"] if mapped else "Otros",
            "categoria_por_top1_label": mapped[0]["label"] if mapped else None,
        }
        print(os.path.basename(path), "->", results[os.path.basename(path)]["categoria_por_top1"],
              "| top1:", top[0]["label"], top[0]["score"], flush=True)
    with open(OUT, "w") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    print("Guardado en", OUT)

if __name__ == "__main__":
    main()
