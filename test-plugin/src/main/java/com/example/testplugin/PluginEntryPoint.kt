package com.example.testplugin

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View

/** Minimal graphical entry point for verifying plugin resource inflation. */
class PluginEntryPoint {
    fun pluginName(): String = "sandbox-test-plugin"

    fun protocolVersion(): Int = 1

    fun selfCheck(): Boolean = true

    fun createView(hostActivity: Activity, pluginContext: Context): View {
        // Inflate using PluginContext so R.layout.plugin_main resolves from the APK.
        return LayoutInflater.from(pluginContext).inflate(R.layout.plugin_main, null)
    }
}
