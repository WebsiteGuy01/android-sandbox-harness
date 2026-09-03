package com.example.sandbox.core

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** Result of inspecting and optionally validating an APK signing certificate. */
data class PluginVerificationResult(
    val packageName: String?,
    val fingerprints: Set<String>,
    val verified: Boolean,
    val error: String? = null
)

/**
 * Verifies APK signing certificates before any guest class is loaded.
 *
 * The allowlist is supplied by the host build configuration. An empty
 * allowlist fails closed; the verifier never treats an unsigned or unparsed
 * APK as trusted.
 */
object PluginVerifier {
    private const val SHA256 = "SHA-256"

    fun verify(
        context: Context,
        apkPath: String,
        allowedFingerprints: Set<String>
    ): PluginVerificationResult {
        return try {
            val packageInfo = archiveInfo(context, apkPath)
                ?: return PluginVerificationResult(
                    packageName = null,
                    fingerprints = emptySet(),
                    verified = false,
                    error = "Unable to parse APK package metadata"
                )
            val fingerprints = signatures(packageInfo)
                .mapTo(linkedSetOf(), ::sha256Fingerprint)
            val normalizedExpected = allowedFingerprints
                .map(::normalizeFingerprint)
                .filter(String::isNotEmpty)
                .toSet()
            val normalizedActual = fingerprints
                .map(::normalizeFingerprint)
                .filter(String::isNotEmpty)
                .toSet()
            val verified = normalizedActual.isNotEmpty() &&
                normalizedExpected.isNotEmpty() &&
                normalizedActual.any { it in normalizedExpected }
            PluginVerificationResult(
                packageName = packageInfo.packageName,
                fingerprints = fingerprints,
                verified = verified,
                error = if (verified) null else buildString {
                    append("Signature mismatch.\nExpected: ")
                    append(normalizedExpected.joinToString(", ").ifEmpty { "<none>" })
                    append("\nGot: ")
                    append(normalizedActual.joinToString(", ").ifEmpty { "<none>" })
                }
            )
        } catch (t: Throwable) {
            PluginVerificationResult(
                packageName = null,
                fingerprints = emptySet(),
                verified = false,
                error = "${t::class.java.simpleName}: ${t.message}"
            )
        }
    }

    /** Pure verification seam used by JVM tests and host policy code. */
    fun isAllowlisted(
        fingerprints: Collection<String>,
        allowedFingerprints: Collection<String>
    ): Boolean {
        val allowlist = allowedFingerprints.mapTo(hashSetOf(), ::normalizeFingerprint)
        return allowlist.isNotEmpty() &&
            fingerprints.any { normalizeFingerprint(it) in allowlist }
    }

    private fun normalizeFingerprint(fingerprint: String): String {
        return fingerprint.replace(":", "").replace(" ", "").uppercase()
    }

    internal fun sha256Fingerprint(signature: Signature): String {
        val digest = MessageDigest.getInstance(SHA256).digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun archiveInfo(context: Context, apkPath: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(apkPath, flags)
    }

    @Suppress("DEPRECATION")
    private fun signatures(packageInfo: PackageInfo): Array<Signature> {
        if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = packageInfo.signingInfo ?: return emptyArray()
            return if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners ?: emptyArray()
            } else {
                signingInfo.signingCertificateHistory ?: emptyArray()
            }
        }
        return packageInfo.signatures ?: emptyArray()
    }
}
