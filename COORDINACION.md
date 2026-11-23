# COORDINACIÓN — App Colección de enlaces

Documento de trabajo para los agentes de cada fase. Léelo completo antes de empezar tu fase.

## Proyecto
- App Android "Colección de enlaces" (nombre visible: "Colección").
- Paquete: `com.coleccionenlaces.app`. Idioma UI: español. minSdk 26, targetSdk la estable más reciente.
- Plan base (10 fases): `~/workspace/user/files/prompt_muse_coleccion_enlaces_0_j3sd.txt` — síguelo exactamente.
- Cambios de Javier: `~/workspace/coleccion-enlaces/ESPECIFICACION.md` — 17 categorías predefinidas, carpetas propias del usuario, mover enlaces entre carpetas (la elección manual tiene prioridad y NO se sobrescribe), cada carpeta se abre con huella dactilar, NO se crean categorías automáticamente, máximo 20 categorías.

## Repo y copia de trabajo
- Repo PRIVADO: https://github.com/djjavierc-droid/coleccion-enlaces.git (cuenta djjavierc-droid).
- Copia de trabajo local: `~/workspace/coleccion-enlaces/` (aquí mismo; este archivo vive en el repo).
- Rama: `main`. Antes de cualquier push, verificar con `git ls-remote` que la conexión funciona.
- Auth GitHub: el token está en `~/.git-credentials`. Para `gh`, exportar `GH_TOKEN` extraído de ahí (nunca imprimir el token):
  `export GH_TOKEN=$(python3 -c "lines=[l.strip() for l in open('/home/hatch/.git-credentials') if 'github.com' in l]; print(lines[0].split(':')[2].split('@')[0])")`
- `git` usa el credential helper con `~/.git-credentials` para push/pull por HTTPS.

## Reglas absolutas
- TODAS las llamadas a IAs por OmniRoute (http://localhost:20128/v1) con `~/workspace/omni-client.mjs` (importar `chat`/`models`/`ensureOmni`). NUNCA directo a ningún proveedor. Lista de modelos listos: `~/workspace/modelos-listos-trabajo.json` (288 respondieron el 2026-11-22; núcleo seguro ddgw 6/6 y cohere 6/6).
- Compilación SOLO en GitHub Actions (CI). Gradle local NO funciona en esta VM (el daemon necesita TCP local, bloqueado). PROHIBIDO pedir permisos de puerto o correr Gradle en local. Verificación de código sin compilar: `kotlinc` no está garantizado; la verificación real es el CI.
- NUNCA empaquetar fuentes en la app: solo fuentes del sistema.
- Sin servidor, sin cuenta, sin nube, sin analítica ni publicidad.
- No agregues funciones fuera del plan (anótalas en "Ideas futuras" en tu resumen de fase).
- No declares nada terminado sin haberlo ejecutado o probado en CI. Si algo no se pudo probar, dilo explícitamente.

## CI (GitHub Actions)
- Workflow: `.github/workflows/build.yml` (lo crea la Fase 1): en push/PR a `main`, corre `./gradlew testDebugUnitTest assembleDebug` con JDK 17 (temurin) y Gradle wrapper. Sin puertos locales.
- Para ver el resultado: `gh run list --repo djjavierc-droid/coleccion-enlaces` y `gh run watch <id>` (con GH_TOKEN), o la API de GitHub. Esperar a verde antes de dar la fase por terminada.
- El APK de depuración queda como artefacto del workflow (subirlo con `actions/upload-artifact`).

## DECISIÓN — palabras clave XXX (archivo separado, nunca en el repo)
- El archivo con las palabras clave de "Contenido XXX" vive SOLO en la copia de trabajo local, en la ruta que defina la Fase 4 (p. ej. `app/src/main/java/com/coleccionenlaces/app/domain/XxxKeywords.kt`), y está en `.gitignore` (nunca hace commit ni push).
- En CI, el workflow genera ese archivo antes de compilar, decodificándolo desde el secreto de GitHub `XXX_KEYWORDS_B64` (base64 del contenido del archivo). La Fase 4 crea ese secreto con `gh secret set XXX_KEYWORDS_B64 --repo djjavierc-droid/coleccion-enlaces` (contenido base64, sin imprimirlo en ningún log ni reporte).
- El workflow NO debe imprimir el contenido del secreto. Si el secreto no existe y el código lo referencia, el build falla (comportamiento correcto).
- NUNCA escribas las palabras XXX en reportes, resúmenes, mensajes de commit ni logs. Los tests de clasificación que las usen corren solo en CI (con el secreto) y en la copia local; sus nombres de casos deben ser genéricos ("caso XXX 1", etc.), sin reproducir las palabras.

## Librería extra (única fuera del plan)
- `androidx.biometric:biometric` (versión estable) para la huella por carpeta. Todo lo demás, exactamente lo del plan sección 5.

## Fases (en orden, sin saltar)
1. Proyecto base (Compose + Material 3, Gradle + librerías sec. 5, permiso INTERNET, tema personalizado, workflow CI). Compila.
2. Modelo de datos (entidad Enlace, DAO, Room, pruebas DAO).
3. Vista previa (OkHttp + Jsoup, og: tags con respaldos, pruebas con HTML local).
4. Clasificación (función pura, palabras clave, archivo XXX separado + secreto CI, 15+ pruebas).
5. Repositorio y ViewModels (StateFlow).
6. Pantalla principal (encabezado, búsqueda, chips, cuadrícula agrupada, tarjetas, vacío, shimmer).
7. Agregar enlace (diálogo, validación, vista previa en curso, guardado, intent-filter ACTION_SEND).
8. Menú de tarjeta (bottom sheet: cambiar categoría, favorito, compartir, borrar con confirmación; abrir enlace al tocar).
9. Pulido visual (animaciones, haptics, predictive back, edge-to-edge, SplashScreen, claro/oscuro, tablet) + huella por carpeta (biometric) + carpetas propias del usuario.
10. Verificación final y entrega (README, APK debug que compila e instala, checklist sec. 7 cumple/no cumple con evidencia).

## Entrega de cada fase
- Commits pequeños y claros en `main`, push, CI verde.
- Resumen corto en `~/workspace/coleccion-enlaces/fase-N-resumen.md`: archivos creados/modificados, qué quedó funcionando, qué falta.
- Reportar al coordinador al terminar (el coordinador no escribe a Javier).
