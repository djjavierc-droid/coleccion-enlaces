package com.coleccionenlaces.app.domain

/**
 * Mapeo de etiquetas de imagen (ML Kit Image Labeling, en inglés) a
 * las 16 categorías de la app (Fix 5).
 *
 * ML Kit devuelve etiquetas CONCRETAS ("Food", "Laptop", "Stadium"),
 * no temas: el mapeo a mano es lo que las convierte en categoría.
 * Solo se mapean etiquetas con una categoría clara y sin ambigüedad:
 * lo que no está en el mapa se IGNORA (no vota). Por eso no hay
 * nada para Noticias ni para Contenido XXX (deliberado: jamás se
 * infiere XXX de una etiqueta) y "Person" no se mapea (una persona
 * sale en vídeos de cualquier tema).
 *
 * Las etiquetas son las del modelo base de ML Kit (400+); el mapa
 * cubre las que aparecen en vídeos habituales. Si ML Kit devuelve
 * una etiqueta que no está aquí, simplemente no vota: el voto es
 * conservador a propósito.
 *
 * El voto suma las confianzas de las etiquetas mapeadas por
 * categoría: gana una categoría solo si su total es >=
 * [UMBRAL_VOTO] y supera a la segunda por >= [MARGEN_VOTO]. Sin
 * ganador claro no hay señal (el enlace queda en "Otros").
 */
object MapeoEtiquetas {

    /** Etiqueta (minúsculas) -> categoría. */
    val mapa: Map<String, String> = mapOf(
        // Cocina.
        "food" to "Cocina",
        "dish" to "Cocina",
        "cuisine" to "Cocina",
        "cooking" to "Cocina",
        "eating" to "Cocina",
        "meal" to "Cocina",
        "fruit" to "Cocina",
        "vegetable" to "Cocina",
        "bread" to "Cocina",
        "cake" to "Cocina",
        "dessert" to "Cocina",
        "coffee" to "Cocina",
        "drink" to "Cocina",
        "beverage" to "Cocina",
        "restaurant" to "Cocina",
        "kitchen" to "Cocina",
        // Videos de mecánica.
        "engine" to "Videos de mecánica",
        "tire" to "Videos de mecánica",
        // Videos de construcción.
        "construction" to "Videos de construcción",
        "crane" to "Videos de construcción",
        // Entretenimiento (deportes incluidos: "Fútbol y deportes"
        // se fusionó en Entretenimiento).
        "sports" to "Entretenimiento",
        "soccer" to "Entretenimiento",
        "football" to "Entretenimiento",
        "basketball" to "Entretenimiento",
        "stadium" to "Entretenimiento",
        "concert" to "Entretenimiento",
        "music" to "Entretenimiento",
        "dance" to "Entretenimiento",
        "movie" to "Entretenimiento",
        "film" to "Entretenimiento",
        "guitar" to "Entretenimiento",
        "piano" to "Entretenimiento",
        "singing" to "Entretenimiento",
        "party" to "Entretenimiento",
        "festival" to "Entretenimiento",
        // Tecnología y celulares.
        "laptop" to "Tecnología y celulares",
        "computer" to "Tecnología y celulares",
        "mobile phone" to "Tecnología y celulares",
        "smartphone" to "Tecnología y celulares",
        "tablet" to "Tecnología y celulares",
        "television" to "Tecnología y celulares",
        "keyboard" to "Tecnología y celulares",
        "mouse" to "Tecnología y celulares",
        "monitor" to "Tecnología y celulares",
        "camera" to "Tecnología y celulares",
        // Autos y motos.
        "car" to "Autos y motos",
        "vehicle" to "Autos y motos",
        "motorcycle" to "Autos y motos",
        "truck" to "Autos y motos",
        "bus" to "Autos y motos",
        // Salud y ejercicio.
        "exercise" to "Salud y ejercicio",
        "fitness" to "Salud y ejercicio",
        "yoga" to "Salud y ejercicio",
        "running" to "Salud y ejercicio",
        "gym" to "Salud y ejercicio",
        "dumbbell" to "Salud y ejercicio",
        "swimming" to "Salud y ejercicio",
        "cycling" to "Salud y ejercicio",
        // Dinero y finanzas.
        "money" to "Dinero y finanzas",
        "cash" to "Dinero y finanzas",
        "coin" to "Dinero y finanzas",
        // Juegos.
        "video game" to "Juegos",
        "game controller" to "Juegos",
        "board game" to "Juegos",
        "chess" to "Juegos",
        "dice" to "Juegos",
        // Hazlo tú mismo (DIY).
        "hammer" to "Hazlo tú mismo (DIY)",
        "screwdriver" to "Hazlo tú mismo (DIY)",
        "wrench" to "Hazlo tú mismo (DIY)",
        "tool" to "Hazlo tú mismo (DIY)",
        "drill" to "Hazlo tú mismo (DIY)",
        // Jardín y plantas.
        "plant" to "Jardín y plantas",
        "flower" to "Jardín y plantas",
        "garden" to "Jardín y plantas",
        "tree" to "Jardín y plantas",
        "leaf" to "Jardín y plantas",
        "grass" to "Jardín y plantas",
        // Pesca y campo.
        "fish" to "Pesca y campo",
        "fishing" to "Pesca y campo",
        "farm" to "Pesca y campo",
        "horse" to "Pesca y campo",
        "cow" to "Pesca y campo",
        "tractor" to "Pesca y campo",
        // Moda y ropa.
        "fashion" to "Moda y ropa",
        "clothing" to "Moda y ropa",
        "dress" to "Moda y ropa",
        "shirt" to "Moda y ropa",
        "shoes" to "Moda y ropa",
        "hat" to "Moda y ropa",
        "jacket" to "Moda y ropa",
        // Viajes.
        "beach" to "Viajes",
        "airplane" to "Viajes",
        "hotel" to "Viajes",
        "suitcase" to "Viajes",
        "mountain" to "Viajes",
    )

    /** Suma de confianzas mínima para que una categoría gane el voto. */
    const val UMBRAL_VOTO = 0.8f

    /** Ventaja mínima del ganador sobre la segunda categoría. */
    const val MARGEN_VOTO = 0.3f

    /**
     * Categoría ganadora del voto de [etiquetas], o null si no hay
     * ganador claro: sin etiquetas mapeadas, con total bajo o con
     * empate. Las etiquetas con confianza < 0,5 no votan.
     */
    fun votar(
        etiquetas: List<EtiquetaImagen>,
        umbralVoto: Float = UMBRAL_VOTO,
        margenVoto: Float = MARGEN_VOTO,
    ): String? {
        val votos = mutableMapOf<String, Float>()
        for (etiqueta in etiquetas) {
            if (etiqueta.confianza < 0.5f) continue
            val categoria = mapa[etiqueta.texto.trim().lowercase()] ?: continue
            votos[categoria] = (votos[categoria] ?: 0f) + etiqueta.confianza
        }
        if (votos.isEmpty()) return null
        val ordenadas = votos.entries.sortedByDescending { it.value }
        val ganadora = ordenadas[0]
        if (ganadora.value < umbralVoto) return null
        val segunda = ordenadas.getOrNull(1)?.value ?: 0f
        if (ganadora.value - segunda < margenVoto) return null
        return ganadora.key
    }
}
