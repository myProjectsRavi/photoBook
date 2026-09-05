package com.photobook.app.feature.vault

import android.content.Context
import android.os.Build
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultAuthKeyEnvelopeInstrumentedTest {
    private lateinit var context: Context
    private lateinit var envelope: VaultAuthKeyEnvelope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        envelope = VaultAuthKeyEnvelope(context)
        envelope.clearForTesting()
    }

    @After
    fun tearDown() {
        envelope.clearForTesting()
    }

    @Test
    fun unauthenticatedCreate_isRejectedAndDoesNotPersistEnvelope() {
        val request = envelope.prepareAuthentication()
        assertTrue(request is VaultAuthKeyEnvelope.AuthenticationRequest.Create)
        val create = request as VaultAuthKeyEnvelope.AuthenticationRequest.Create
        val secret = ByteArray(64) { index -> (index + 1).toByte() }

        val result = runCatching {
            envelope.completeCreate(create, create.cryptoObject, secret)
        }

        assertTrue("unauthenticated key wrapping must fail", result.isFailure)
        assertTrue(
            "failure must represent missing user authentication: ${result.exceptionOrNull()}",
            isAuthenticationRequiredFailure(result.exceptionOrNull()),
        )
        assertFalse("failed authentication must not persist an envelope", envelope.hasEnvelope())
        assertFalse(VaultAuthKeyEnvelope.envelopePath(context).exists())
        secret.fill(0)
    }

    @Test
    fun corruptEnvelope_isRejectedWithoutReplacement() {
        // First create the Keystore alias without authenticating or persisting an envelope.
        val request = envelope.prepareAuthentication()
        assertTrue(request is VaultAuthKeyEnvelope.AuthenticationRequest.Create)

        val file = VaultAuthKeyEnvelope.envelopePath(context)
        file.parentFile?.mkdirs()
        val corrupt = byteArrayOf(0x50, 0x42, 0x4B, 0x32, 0x01, 0x0C, 0x01)
        file.writeBytes(corrupt)

        try {
            envelope.prepareAuthentication()
            fail("corrupt Vault key envelope must fail closed")
        } catch (expected: VaultKeyEnvelopeCorruptException) {
            // Required: never overwrite or silently recreate corrupt protected state.
        }

        assertArrayEquals(corrupt, file.readBytes())
    }

    @Test
    fun existingEnvelopeWithMissingKeystoreKey_failsClosedWithoutRegeneration() {
        // clearForTesting removes both file and alias. Write a syntactically valid envelope only;
        // prepareAuthentication must refuse it before attempting any replacement key generation.
        envelope.clearForTesting()
        val file = VaultAuthKeyEnvelope.envelopePath(context)
        file.parentFile?.mkdirs()
        val encoded = validDummyEnvelope()
        file.writeBytes(encoded)

        try {
            envelope.prepareAuthentication()
            fail("missing Keystore key for an existing envelope must fail closed")
        } catch (expected: VaultWrappingKeyUnavailableException) {
            // Required: existing wrapped material must never be rebound to a newly generated key.
        }

        assertArrayEquals(encoded, file.readBytes())
    }

    @Test
    fun promptAuthenticators_matchPlatformCryptoObjectSupport() {
        val actual = envelope.promptAuthenticators()
        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        assertTrue("Vault prompt/key authenticator policy changed unexpectedly", actual == expected)
    }

    private fun validDummyEnvelope(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.write(byteArrayOf('P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '2'.code.toByte()))
            output.writeByte(1)
            output.writeByte(12)
            output.write(ByteArray(12) { index -> (index + 1).toByte() })
            output.writeInt(16)
            output.write(ByteArray(16) { index -> (index + 17).toByte() })
        }
        bytes.toByteArray()
    }

    private fun isAuthenticationRequiredFailure(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            if (current is UserNotAuthenticatedException) return true
            val message = current.message.orEmpty()
            if (
                message.contains("Key user not authenticated", ignoreCase = true) ||
                message.contains("KEY_USER_NOT_AUTHENTICATED", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
