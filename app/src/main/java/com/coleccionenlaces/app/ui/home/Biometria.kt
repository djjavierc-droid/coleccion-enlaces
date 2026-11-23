package com.coleccionenlaces.app.ui.home

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Capa fina de biometría (Fase 9, orden de Javier).
 *
 * La huella NO se pide para abrir la app, solo al abrir cada
 * carpeta. Este archivo es solo el llamado real a [BiometricPrompt]:
 * la decisión de qué está desbloqueado vive en
 * [com.coleccionenlaces.app.domain.BloqueoCarpetas], que es lógica
 * pura y se prueba sin dispositivo. Por eso la biometría real NO se
 * puede probar en la JVM ni en CI: no hay huella ni credencial de
 * dispositivo en el runner, y este código solo se ejecuta en un
 * teléfono real.
 *
 * - Autenticación fuerte (huella o rostro fuerte) o credencial del
 *   dispositivo (PIN, patrón o contraseña), con
 *   `setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL)`
 *   en API 30+; en API 26–29 la credencial del dispositivo se
 *   permite con `setDeviceCredentialAllowed` (la combinación no
 *   existe en esas APIs).
 * - Si el dispositivo no tiene ningún método de bloqueo configurado,
 *   [puedeIntentarAutenticacion] devuelve false y la UI muestra un
 *   mensaje amigable en vez de abrir el prompt.
 */
object Biometria {

    /** ¿Puede intentarse la autenticación en este dispositivo? */
    fun puedeIntentarAutenticacion(actividad: FragmentActivity): Boolean {
        val gestor = BiometricManager.from(actividad)
        val resultado = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            gestor.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            gestor.canAuthenticate(BIOMETRIC_STRONG)
        }
        if (resultado == BiometricManager.BIOMETRIC_SUCCESS) return true
        // Sin biometría inscrita, el prompt con credencial del
        // dispositivo aún puede abrirse si el teléfono tiene un
        // bloqueo seguro (PIN, patrón o contraseña).
        val cerrojo = actividad.getSystemService(KeyguardManager::class.java)
        return cerrojo?.isDeviceSecure == true
    }

    /**
     * Abre el prompt de huella o credencial del dispositivo.
     *
     * - [onExito]: la autenticación fue correcta; la UI desbloquea
     *   la carpeta para la sesión.
     * - [onError]: error real (p. ej. sin método de bloqueo, fallo
     *   de hardware). El código de error de
     *   [BiometricPrompt.ERROR_CANCELED],
     *   [BiometricPrompt.ERROR_USER_CANCELED] y
     *   [BiometricPrompt.ERROR_NEGATIVE_BUTTON] significa que el
     *   usuario cerró el prompt: la UI lo ignora en silencio.
     * - Un intento fallido (huella no reconocida) no llega aquí:
     *   [BiometricPrompt.AuthenticationCallback.onAuthenticationFailed]
     *   no hace nada y el prompt deja intentarlo de nuevo.
     */
    fun autenticar(
        actividad: FragmentActivity,
        titulo: String,
        subtitulo: String,
        onExito: () -> Unit,
        onError: (codigoError: Int, mensaje: CharSequence) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            actividad,
            ContextCompat.getMainExecutor(actividad),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onExito()
                }

                override fun onAuthenticationError(codigoError: Int, mensaje: CharSequence) {
                    onError(codigoError, mensaje)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setSubtitle(subtitulo)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }
}
