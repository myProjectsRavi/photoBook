package com.photobook.app.feature.vault

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.photobook.app.R

private enum class VaultCredentialAction {
    SETUP,
    RECOVER,
}

/**
 * Owns the complete Vault authentication UX while keeping the decrypt-capable key behind
 * Android Keystore authentication. Android 11+ uses the CryptoObject prompt directly;
 * Android 8-10 adds secure-lock-screen confirmation only for dual-wrap setup/recovery.
 */
@Composable
internal fun rememberVaultAuthenticator(
    vaultService: VaultService,
): ((VaultCryptoSession) -> Unit) -> Unit {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pendingCredentialAction by remember { mutableStateOf<VaultCredentialAction?>(null) }
    var pendingCompletion by remember {
        mutableStateOf<((VaultCryptoSession) -> Unit)?>(null)
    }

    fun showFailure(message: CharSequence? = null) {
        Toast.makeText(
            context,
            message ?: context.getString(R.string.vault_biometric_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun launchBiometric(
        preparation: VaultAuthPreparation,
        onAuthenticated: (VaultCryptoSession) -> Unit,
    ) {
        val host = activity
        if (host == null) {
            vaultService.cancelAuthentication(preparation)
            showFailure()
            return
        }
        if (
            BiometricManager.from(context).canAuthenticate(preparation.promptAuthenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            vaultService.cancelAuthentication(preparation)
            showFailure()
            return
        }

        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher == null) {
                        vaultService.cancelAuthentication(preparation)
                        showFailure()
                        return
                    }
                    val session = try {
                        vaultService.completeAuthentication(preparation, authenticatedCipher)
                    } catch (_: Throwable) {
                        vaultService.cancelAuthentication(preparation)
                        showFailure()
                        return
                    }
                    onAuthenticated(session)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    vaultService.cancelAuthentication(preparation)
                    if (
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        showFailure(errString)
                    }
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.vault_biometric_title))
            .setSubtitle(context.getString(R.string.vault_biometric_subtitle))
            .setAllowedAuthenticators(preparation.promptAuthenticators)
        if (
            preparation.promptAuthenticators and
            BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0
        ) {
            builder.setNegativeButtonText(context.getString(android.R.string.cancel))
        }
        try {
            prompt.authenticate(
                builder.build(),
                BiometricPrompt.CryptoObject(preparation.cipher),
            )
        } catch (_: Throwable) {
            vaultService.cancelAuthentication(preparation)
            showFailure()
        }
    }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val action = pendingCredentialAction
        val completion = pendingCompletion
        pendingCredentialAction = null
        pendingCompletion = null
        if (result.resultCode != Activity.RESULT_OK || action == null || completion == null) {
            return@rememberLauncherForActivityResult
        }
        val credentialResult = try {
            when (action) {
                VaultCredentialAction.SETUP ->
                    vaultService.preparePreREnrollmentAfterCredential()
                VaultCredentialAction.RECOVER ->
                    vaultService.preparePreRRecoveryAfterCredential()
            }
        } catch (_: Throwable) {
            showFailure()
            return@rememberLauncherForActivityResult
        }
        when (credentialResult) {
            is VaultPreRCredentialResult.CredentialOnly ->
                completion(credentialResult.session)
            is VaultPreRCredentialResult.RequiresBiometric ->
                launchBiometric(credentialResult.preparation, completion)
        }
    }

    @Suppress("DEPRECATION")
    fun launchCredentialConfirmation(
        action: VaultCredentialAction,
        onAuthenticated: (VaultCryptoSession) -> Unit,
    ) {
        val host = activity
        if (host == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showFailure()
            return
        }
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure != true) {
            showFailure()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            context.getString(R.string.vault_biometric_title),
            context.getString(R.string.vault_biometric_subtitle),
        ) ?: run {
            showFailure()
            return
        }
        pendingCredentialAction = action
        pendingCompletion = onAuthenticated
        try {
            credentialLauncher.launch(intent)
        } catch (_: Throwable) {
            pendingCredentialAction = null
            pendingCompletion = null
            showFailure()
        }
    }

    return { onAuthenticated ->
        if (activity == null) {
            showFailure()
        } else {
            try {
                launchBiometric(vaultService.prepareAuthentication(), onAuthenticated)
            } catch (_: VaultCredentialSetupRequiredException) {
                launchCredentialConfirmation(VaultCredentialAction.SETUP, onAuthenticated)
            } catch (_: VaultCredentialRecoveryRequiredException) {
                launchCredentialConfirmation(VaultCredentialAction.RECOVER, onAuthenticated)
            } catch (_: Throwable) {
                showFailure()
            }
        }
    }
}
