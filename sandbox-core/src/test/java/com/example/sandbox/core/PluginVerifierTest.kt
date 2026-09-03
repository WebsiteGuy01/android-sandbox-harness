package com.example.sandbox.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginVerifierTest {
    private val trusted = "AA:BB:CC:DD:EE:FF"

    @Test
    fun allowlistedSignedFingerprintPasses() {
        assertTrue(
            PluginVerifier.isAllowlisted(
                fingerprints = listOf(trusted),
                allowedFingerprints = listOf("aa bb cc dd ee ff")
            )
        )
    }

    @Test
    fun tamperedFingerprintFails() {
        assertFalse(
            PluginVerifier.isAllowlisted(
                fingerprints = listOf("00:11:22:33:44:55"),
                allowedFingerprints = listOf(trusted)
            )
        )
    }

    @Test
    fun unsignedApkWithNoFingerprintsFailsClosed() {
        assertFalse(
            PluginVerifier.isAllowlisted(
                fingerprints = emptyList(),
                allowedFingerprints = listOf(trusted)
            )
        )
        assertFalse(
            PluginVerifier.isAllowlisted(
                fingerprints = listOf(trusted),
                allowedFingerprints = emptyList()
            )
        )
    }
}
