#!/usr/bin/env python3
"""OCR con RapidOCR (ONNX local) sobre cuadros y og:image.
Uso: ocr.py <img1> ... -> escribe <img>.ocr.txt por imagen.
"""
import sys
from pathlib import Path

from rapidocr_onnxruntime import RapidOCR

engine = RapidOCR()

for path in sys.argv[1:]:
    p = Path(path)
    result, _ = engine(str(p))
    lines = []
    if result:
        for box, text, conf in result:
            lines.append(f"{float(conf):.2f}\t{text}")
    p.with_suffix(p.suffix + ".ocr.txt").write_text("\n".join(lines), encoding="utf-8")
    print(f"== {p.name}: {len(lines)} lineas")
    for line in lines:
        print(f"   {line}")
