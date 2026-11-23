#!/usr/bin/env python3
"""Genera los datos de entrenamiento del clasificador LiteRT.

Lee las listas de palabras clave del código (PalabrasClave.kt y el
archivo local XxxKeywords.kt) y genera ejemplos de títulos y
descripciones por categoría. Los datos generados NUNCA se commitean
(datos/ está en .gitignore): contienen las palabras de la lista
sensible. Este script NO contiene esas palabras: las lee en tiempo
de ejecución.

Salida (datos/):
- entrenamiento.csv: texto,categoria (90% de los ejemplos)
- validacion.csv:   texto,categoria (10%, para medir precisión y calibrar el umbral)
- genericos.csv:    texto,categoria="Otros" (títulos genéricos SIN tema, solo para
                    calibrar el umbral de confianza: deben quedar en "Otros")
- etiquetas.txt:    las 16 categorías (todas menos "Otros"), en el orden
                    de CategoriasPredefinidas.

Reglas:
- El título/descripción EXACTOS del video de EterniCodIA NO entran en
  entrenamiento (son el caso de prueba): se excluyen por coincidencia
  de texto.
- Semilla fija (42): la generación es reproducible.
"""

import csv
import random
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
RUTA_PALABRAS = RAIZ / "app/src/main/java/com/coleccionenlaces/app/domain/PalabrasClave.kt"
RUTA_XXX = RAIZ / "app/src/main/java/com/coleccionenlaces/app/domain/XxxKeywords.kt"
DIR_DATOS = Path(__file__).resolve().parent / "datos"

# Orden de las 17 predefinidas (CategoriasPredefinidas.lista). "Otros" no
# es una clase: es el respaldo cuando la confianza es baja.
ORDEN_CATEGORIAS = [
    "Cocina",
    "Videos de mecánica",
    "Videos de construcción",
    "Contenido XXX",
    "Entretenimiento",
    "Tecnología y celulares",
    "Autos y motos",
    "Salud y ejercicio",
    "Dinero y finanzas",
    "Noticias",
    "Juegos",
    "Hazlo tú mismo (DIY)",
    "Jardín y plantas",
    "Pesca y campo",
    "Moda y ropa",
    "Viajes",
    "Otros",
]

# Texto exacto del caso de prueba (EterniCodIA): excluido del entrenamiento.
TEXTO_PRUEBA = "contratamos a una ia... y el primer día ya vendía croquetas en marte"

# Plantillas de título: {kw} es la palabra o frase clave.
PLANTILLAS_TITULO = [
    "{kw}",
    "Cómo {kw}",
    "El mejor {kw}",
    "Tutorial de {kw}",
    "{kw} paso a paso",
    "{kw} en casa",
    "Aprende sobre {kw}",
    "Video de {kw}",
    "{kw} para principiantes",
    "Todo sobre {kw}",
    "{kw} fácil y rápido",
    "{kw} | guía completa",
    "Descubre {kw}",
    "Mi experiencia con {kw}",
    "¿Qué es {kw}?",
    "{kw} 2026",
    "{kw} que no conocías",
    "Guía de {kw}",
    "Consejos sobre {kw}",
]

# Plantillas de descripción (40% de los ejemplos llevan una).
PLANTILLAS_DESCRIPCION = [
    "En este video te muestro {kw} paso a paso.",
    "Aprende todo sobre {kw}.",
    "Te explico {kw} de forma sencilla.",
    "Mira cómo hacer {kw} en casa.",
    "Todo lo que necesitas saber de {kw}.",
    "El video completo sobre {kw}.",
]

# Títulos realistas escritos a mano por categoría (variedad natural).
# NINGUNO usa la lista sensible: esa categoría se genera solo con
# plantillas + sus palabras clave (leídas en tiempo de ejecución).
TITULOS_EXTRA = {
    "Cocina": [
        "Receta de arroz con pollo para el almuerzo",
        "Cómo hacer pan casero sin horno",
        "El flan perfecto en 10 minutos",
        "Sopa de verduras para el frío",
        "Pastel de chocolate fácil",
        # Contrapeso: "croquetas" es comida cuando el tema es la receta
        # (ver Tecnología: con IA/robot + vender, el tema es tecnología).
        "Receta de croquetas caseras paso a paso",
        "Croquetas de jamón para el almuerzo",
    ],
    "Videos de mecánica": [
        "Por qué se calienta el motor del carro",
        "Cómo cambiar las pastillas de freno",
        "El taller me cobró de más por el aceite",
        "Ruido raro al arrancar el auto",
        "Revisión del radiador antes del viaje",
    ],
    "Videos de construcción": [
        "Cómo levantar una pared de ladrillo",
        "Mezcla de cemento: proporciones correctas",
        "Poniendo azulejos en el baño",
        "Los cimientos de mi casa nueva",
        "Techo de zinc: instalación paso a paso",
    ],
    "Entretenimiento": [
        "El tráiler de la película del año",
        "La canción que está sonando en todos lados",
        "El partido de fútbol que nadie se esperaba",
        "El chiste que me hizo reír toda la noche",
        "El concierto en vivo desde el estadio",
    ],
    "Tecnología y celulares": [
        "La IA que responde tus mensajes sola",
        "ChatGPT me hizo la tarea en 5 minutos",
        "El celular que dura dos días sin cargar",
        "Contratamos a una IA para vender en internet",
        "El robot que limpia la casa solo",
        "Probando la nueva actualización de Android",
        # Señal mixta: el tema es la IA/el robot aunque aparezcan
        # palabras de comida o de ventas (croquetas, vender, Marte).
        # El texto EXACTO del caso EterniCodIA NO está aquí: sigue
        # excluido por el filtro TEXTO_PRUEBA; estas son variaciones
        # que enseñan el patrón, no la respuesta memorizada.
        "Contratamos a una IA para vender croquetas en Marte",
        "La IA que vende croquetas en internet",
        "Un robot que vende comida en Marte",
        "Contratamos un robot para atender la tienda",
        "La inteligencia artificial que vende en línea",
        "ChatGPT vendiendo croquetas en Marte",
        "El robot que cocina y vende en el mercado",
        "Una IA que atiende clientes y vende más",
        "Contratamos una IA para la tienda en línea",
        "El asistente de IA que vende por WhatsApp",
        "Robot vendiendo tacos en la feria",
        "La IA que hace la tarea y vende en internet",
    ],
    "Autos y motos": [
        "El auto eléctrico más barato del mercado",
        "Prueba de manejo en la montaña",
        "Mi moto nueva: primera impresión",
        "Cuánto gasta de gasolina este carro",
        "El examen de manejo: consejos para pasar",
    ],
    "Salud y ejercicio": [
        "Rutina de abdominales en 10 minutos",
        "Cómo dormir bien toda la noche",
        "El ejercicio que me quitó el dolor de espalda",
        "Yoga para principiantes en casa",
        "Qué comer antes de entrenar",
    ],
    "Dinero y finanzas": [
        "Cómo ahorrar con un sueldo bajo",
        "Invertir en la bolsa con poco dinero",
        "El préstamo que casi me arruina",
        "Cuánto vale el dólar hoy",
        "Presupuesto familiar para fin de mes",
        # Contrapeso: "vender" es dinero cuando el tema es el negocio,
        # no cuando el tema es la IA (ver Tecnología).
        "Cómo vender más en mi negocio",
        "Vender en línea: guía para empezar",
    ],
    "Noticias": [
        "Última hora: temblor en la capital",
        "El gobierno anuncia nuevas medidas",
        "Accidente en la autopista esta mañana",
        "Protesta frente al congreso",
        "El incendio que afectó a varias familias",
    ],
    "Juegos": [
        "Gameplay de Minecraft: construyendo mi base",
        "El torneo de videojuegos más grande",
        "Jugando con el mando nuevo",
        "Speedrun del nivel más difícil",
        "Mi partida en multijugador con amigos",
    ],
    "Hazlo tú mismo (DIY)": [
        "Mueble casero con madera reciclada",
        "Cómo restaurar una silla vieja",
        "Pintar la pared sin dejar marcas",
        "Hice un estante con un taladro y tornillos",
        "Manualidades fáciles para la casa",
    ],
    "Jardín y plantas": [
        "Cómo sembrar tomates en maceta",
        "Mi huerto en el balcón",
        "La orquídea que por fin floreció",
        "Riego y abono para las plantas",
        "Trasplantar un cactus sin pincharte",
    ],
    "Pesca y campo": [
        "Pesca en el río al amanecer",
        "La cosecha de este año en la finca",
        "Ordeñando las vacas del potrero",
        "Pesca deportiva en el lago",
        "El tractor nuevo para la granja",
    ],
    "Moda y ropa": [
        "Outfit para la fiesta de fin de año",
        "El vestido que está de moda",
        "Probando zapatillas nuevas",
        "Maquillaje fácil para todos los días",
        "La chaqueta perfecta para el frío",
    ],
    "Viajes": [
        "Vacaciones en la playa: hotel y vuelo",
        "El volcán más bonito de Costa Rica",
        "Mochilero por la montaña",
        "Qué llevar en la maleta de viaje",
        "El crucero por el Caribe",
    ],
}


def extraer_mapa(ruta: Path, marcador: str) -> dict:
    """Extrae un mapOf Kotlin 'Categoría' to listOf(...) de un archivo."""
    texto = ruta.read_text(encoding="utf-8")
    inicio = texto.index(marcador)
    # El mapa termina con el primer "    )" a nivel de mapa tras el inicio.
    fragmento = texto[inicio:]
    # Cortar antes del siguiente mapa (dominiosPorCategoria), si lo hay:
    # si no, el segundo mapa sobrescribiría las listas de palabras.
    corte = fragmento.find("val dominiosPorCategoria")
    if corte != -1:
        fragmento = fragmento[:corte]
    # Pares "Nombre" to listOf( ... )
    pares = re.findall(
        r'"([^"]+)"\s+to\s+listOf\((.*?)\)\s*,?\s*(?="\n|"[^"]+"\s+to\s+listOf|\n\s*\))',
        fragmento,
        re.DOTALL,
    )
    resultado = {}
    for nombre, cuerpo in pares:
        palabras = re.findall(r'"([^"]+)"', cuerpo)
        if palabras:
            resultado[nombre] = palabras
    return resultado


def main() -> int:
    azar = random.Random(42)
    palabras = extraer_mapa(RUTA_PALABRAS, "val porCategoria")
    if not RUTA_XXX.exists():
        print("FALTA el archivo local XxxKeywords.kt; sin él no se entrena la categoría sensible.")
        return 1
    # El mapa de XXX es un listOf simple, no un mapOf: extraerlo aparte.
    texto_xxx = RUTA_XXX.read_text(encoding="utf-8")
    m = re.search(r"val palabrasClave[^=]*=\s*listOf\((.*?)\)", texto_xxx, re.DOTALL)
    palabras_xxx = re.findall(r'"([^"]+)"', m.group(1)) if m else []
    palabras["Contenido XXX"] = palabras_xxx

    # Solo las 16 clases (todas menos "Otros").
    clases = [c for c in ORDEN_CATEGORIAS if c != "Otros"]
    for clase in clases:
        if clase not in palabras or not palabras[clase]:
            print(f"FALTA lista de palabras para {clase}")
            return 1

    ejemplos = []  # (texto, categoria)
    for clase in clases:
        lista = palabras[clase]
        # Una muestra por palabra con varias plantillas (rotando).
        for i, kw in enumerate(lista):
            for plantilla in PLANTILLAS_TITULO:
                if azar.random() < 0.28:  # ~5 plantillas por palabra
                    titulo = plantilla.format(kw=kw)
                    titulo = titulo[0].upper() + titulo[1:]
                    descripcion = ""
                    if azar.random() < 0.4:
                        descripcion = azar.choice(PLANTILLAS_DESCRIPCION).format(kw=kw)
                    texto = f"{titulo} {descripcion}".strip()
                    ejemplos.append((texto, clase))
        # Pares de palabras de la misma categoría en un título.
        for _ in range(25):
            kw1, kw2 = azar.sample(lista, 2)
            titulo = f"{kw1.capitalize()} con {kw2}"
            ejemplos.append((titulo, clase))
        # Títulos realistas escritos a mano.
        for titulo in TITULOS_EXTRA.get(clase, []):
            ejemplos.append((titulo, clase))
            if azar.random() < 0.5:
                ejemplos.append(
                    (f"{titulo}. {azar.choice(PLANTILLAS_DESCRIPCION).format(kw=lista[0])}", clase)
                )

    # Excluir el texto exacto del caso de prueba (EterniCodIA).
    ejemplos = [
        (t, c)
        for (t, c) in ejemplos
        if TEXTO_PRUEBA not in t.lower().replace("…", "...")
    ]
    # Sin duplicados exactos.
    vistos = set()
    unicos = []
    for texto, clase in ejemplos:
        clave = (texto.lower(), clase)
        if clave in vistos:
            continue
        vistos.add(clave)
        unicos.append((texto, clase))
    ejemplos = unicos
    azar.shuffle(ejemplos)

    # División 90/10 estratificada por categoría.
    por_clase: dict = {c: [] for c in clases}
    for texto, clase in ejemplos:
        por_clase[clase].append((texto, clase))
    entren, valid = [], []
    for clase in clases:
        lista = por_clase[clase]
        corte = int(len(lista) * 0.9)
        entren.extend(lista[:corte])
        valid.extend(lista[corte:])
    azar.shuffle(entren)
    azar.shuffle(valid)

    # Títulos genéricos sin tema (solo para calibrar el umbral -> "Otros").
    genericos = [
        "Mira esto",
        "El mejor video de la semana",
        "No te lo vas a creer",
        "Video increíble, mira",
        "Mira este video",
        "Esto es increíble",
        "No puedo creer lo que pasó",
        "El video del día",
        "Comparte esto",
        "Mira qué pasó aquí",
        "El mejor momento",
        "Esto me hizo el día",
        "Video para ver con calma",
        "Qué locura de video",
        "Mira esto hasta el final",
        "El video que todos comentan",
        "No sé qué decir",
        "Simplemente mira",
        "Esto es otra cosa",
        "El video más visto hoy",
    ]

    DIR_DATOS.mkdir(parents=True, exist_ok=True)

    def escribir_csv(ruta: Path, filas):
        with ruta.open("w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(["texto", "categoria"])
            w.writerows(filas)

    escribir_csv(DIR_DATOS / "entrenamiento.csv", entren)
    escribir_csv(DIR_DATOS / "validacion.csv", valid)
    escribir_csv(DIR_DATOS / "genericos.csv", [(t, "Otros") for t in genericos])
    (DIR_DATOS / "etiquetas.txt").write_text("\n".join(clases) + "\n", encoding="utf-8")

    conteos = {c: len(por_clase[c]) for c in clases}
    print(f"Entrenamiento: {len(entren)} ejemplos, validación: {len(valid)}")
    print(f"Por categoría: {conteos}")
    print(f"Total clases: {len(clases)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
