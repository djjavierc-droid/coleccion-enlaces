package com.coleccionenlaces.app.data

import com.coleccionenlaces.app.domain.CabezaAprendizaje
import com.coleccionenlaces.app.domain.esTextoSoloDominio
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Servicio de aprendizaje (Fix 3): guarda las correcciones de Javier
 * y mantiene la cabeza entrenada.
 *
 * - [registrarCorreccion]: guarda el ejemplo en Room y hace el paso
 *   en línea sobre la cabeza (mecanismo (a) del diseño: se nota desde
 *   la primera corrección). Solo entrena si la categoría es una de
 *   las 16; una corrección hacia "Otros" se guarda igual pero no
 *   entrena pesos (no tiene clase en la cabeza).
 * - [cargarPesos]: carga los pesos guardados en la cabeza (al arrancar
 *   la app). Sin fila guardada, la cabeza empieza en cero.
 * - [reentrenarEnReposo]: pasa varias épocas sobre todas las
 *   correcciones guardadas (mecanismo (b) del diseño) y persiste los
 *   pesos. Devuelve el número de ejemplos usados.
 *
 * Los pesos se protegen con un candado: el paso en línea (hilo de la
 * UI al mover un enlace) y el reentrenamiento (trabajo en segundo
 * plano) no pueden pisarse.
 *
 * Ninguna función lanza: si la base falla, el aprendizaje se salta y
 * la app sigue (mover un enlace nunca se rompe por aprender).
 */
class ServicioAprendizaje(
    private val dao: AprendizajeDao,
    private val cabeza: CabezaAprendizaje,
) {
    private val candado = Mutex()

    /** Carga los pesos guardados en la cabeza (al arrancar). */
    suspend fun cargarPesos() {
        try {
            val guardados = dao.obtenerPesos() ?: return
            val cargada = CabezaAprendizaje.desdeBytes(cabeza.etiquetas, guardados.pesos)
            candado.withLock {
                cabeza.copiarPesosDesde(cargada)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Registra una corrección: guarda el ejemplo y entrena la cabeza
     * con un paso en línea. Devuelve true si se registró.
     *
     * Fix 5, mina del diagnóstico (§6.3): un texto SOLO-dominio
     * ("facebook.com") NO se registra: no describe contenido y la
     * cabeza aprendería el dominio, no el vídeo.
     */
    suspend fun registrarCorreccion(texto: String, categoria: String): Boolean {
        if (texto.isBlank()) return false
        if (esTextoSoloDominio(texto)) return false
        return try {
            dao.insertarCorreccion(CorreccionAprendizaje(texto = texto, categoria = categoria))
            candado.withLock {
                cabeza.aprenderEnLinea(texto, categoria)
                dao.guardarPesos(PesosCabeza(pesos = cabeza.aBytes()))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Purga las correcciones solo-dominio ya guardadas (Fix 5, mina
     * del diagnóstico §6.3): borra los ejemplos cuyo texto es solo el
     * dominio ("facebook.com") y reconstruye la cabeza desde los
     * restantes, para que el veneno ya guardado (p. ej. "facebook.com"
     * -> Juegos, del vídeo de juego que Javier movió a mano) no suba
     * a ~0,64 en el próximo reentrenamiento en reposo y mande TODOS
     * los enlaces facebook.com sin texto a Juegos.
     *
     * La cabeza usa truco de hash (las palabras caen en cubos): no se
     * puede borrar quirúrgicamente el peso de una sola palabra, así
     * que se reinicia a cero y se reentrena por lotes con los ejemplos
     * limpios (el mismo entrenamiento que haría el trabajo en reposo).
     * Devuelve cuántas correcciones malas se borraron (0 si no había
     * ninguna: en ese caso no se toca la cabeza). Se llama al arrancar
     * (AppContainer.iniciarAprendizaje); es idempotente.
     */
    suspend fun purgarCorreccionesSoloDominio(): Int {
        return try {
            val correcciones = dao.obtenerCorrecciones()
            val malas = correcciones.filter { esTextoSoloDominio(it.texto) }
            if (malas.isEmpty()) return 0
            for (mala in malas) {
                dao.borrarCorreccion(mala.id)
            }
            candado.withLock {
                cabeza.reiniciar()
                val restantes = dao.obtenerCorrecciones().map { it.texto to it.categoria }
                cabeza.entrenarLote(restantes)
                dao.guardarPesos(PesosCabeza(pesos = cabeza.aBytes()))
            }
            malas.size
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Reentrena la cabeza sobre todas las correcciones guardadas.
     * Devuelve el número de ejemplos usados (0 si no hay ninguno).
     */
    suspend fun reentrenarEnReposo(): Int {
        return try {
            val correcciones = dao.obtenerCorrecciones()
            val ejemplos = correcciones.map { it.texto to it.categoria }
            val usados = candado.withLock {
                val n = cabeza.entrenarLote(ejemplos)
                if (n > 0) {
                    dao.guardarPesos(PesosCabeza(pesos = cabeza.aBytes()))
                }
                n
            }
            usados
        } catch (_: Exception) {
            0
        }
    }

    /** Número de correcciones guardadas (diagnóstico). */
    suspend fun contarCorrecciones(): Int {
        return try {
            dao.contarCorrecciones()
        } catch (_: Exception) {
            0
        }
    }
}
