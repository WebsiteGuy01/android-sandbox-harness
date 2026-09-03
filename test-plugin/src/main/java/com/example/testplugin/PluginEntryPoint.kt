package com.example.testplugin

/**
 * Minimal, side-effect-free entry point for the host harness.
 * It deliberately uses only Kotlin/JVM types so it can be loaded as a test APK
 * without requiring host framework hooks or privileged services.
 */
class PluginEntryPoint {
    fun pluginName(): String = "sandbox-test-plugin"

    fun protocolVersion(): Int = 1

    fun selfCheck(): Boolean = true
}
