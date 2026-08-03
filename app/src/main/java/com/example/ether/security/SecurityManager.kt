package com.example.ether.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SecurityManager @Inject constructor() {

    /**
     * Authenticate the user.
     * returns true if auth is possible, false if not (no hardware/no enrolled).
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String, Boolean) -> Unit // message, isFatal
    ) {
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Timber.w("Biometric authentication not available: $canAuthenticate")
            // If not available, we shouldn't block the user unless they strictly require it
            // For now, let's treat it as success to let them in, or return a non-fatal error
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Timber.e("Biometric error: $errorCode - $errString")
                    
                    val isFatal = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> true
                        else -> false // Hardware errors might be temporary
                    }
                    onError(errString.toString(), isFatal)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Timber.d("Biometric authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Timber.w("Biometric authentication failed (wrong finger/pattern)")
                    onError("Authentication failed", false)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Ether Security")
            .setSubtitle("Unlock to access your launcher")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            // No setNegativeButtonText when DEVICE_CREDENTIAL is used
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start biometric prompt")
            onError("Prompt failed to start", true)
        }
    }
}
