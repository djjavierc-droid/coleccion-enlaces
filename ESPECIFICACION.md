# App colección de enlaces — Especificación final (Javier, 2026-11-23)

Plan base de Claude: `~/workspace/user/files/prompt_muse_coleccion_enlaces_0_j3sd.txt` (10 fases, Compose + Material 3, Room, OkHttp + Jsoup, clasificación por palabras clave, cuadrícula).

## Cambios dictados por Javier (reemplazan lo del plan base)

### Categorías predefinidas: 17 (máximo 20)
1. Cocina (recetas y videos juntos)
2. Videos de mecánica
3. Videos de construcción
4. Contenido XXX
5. Entretenimiento (música, películas y series, fútbol y deportes)
6. Tecnología y celulares
7. Autos y motos
8. Salud y ejercicio
9. Dinero y finanzas
10. Noticias
11. Juegos
12. Hazlo tú mismo (DIY)
13. Jardín y plantas
14. Pesca y campo
15. Moda y ropa
16. Viajes
17. Otros

Quedan 3 libres hasta el máximo de 20.

### Carpetas propias del usuario
- La persona puede crear su propia carpeta/categoría.
- Puede mover enlaces desde otras carpetas a la suya (cambiar categoría desde el menú de la tarjeta, como ya dice el plan, más crear carpetas nuevas).
- Regla de Claude: la elección manual tiene prioridad sobre la automática y NO se sobrescribe al editar el enlace.

### Huella por carpeta
- Cada carpeta se abre con la huella dactilar (autenticación biométrica al entrar a cada categoría).

### Detección automática
- La app detecta sola a qué categoría pertenece cada enlace (clasificación automática del plan base).
- NO se crean categorías automáticamente (descartado por Javier 2026-11-23 00:22): las categorías son las 17 predefinidas más las que el usuario cree a mano.

## Estado
- Plan base leído; especificación actualizada con los cambios de Javier.
- PENDIENTE: confirmación de Javier para iniciar la construcción (Fase 1).
- Build: en GitHub Actions (CI), Gradle local no funciona en esta VM.
