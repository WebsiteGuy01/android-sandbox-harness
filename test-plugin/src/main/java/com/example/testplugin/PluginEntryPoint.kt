package com.example.testplugin

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.TextView

/**
 * Minimal graphical entry point for verifying host-owned Activity delegation.
 */
class PluginEntryPoint {
    fun pluginName(): String = "sandbox-test-plugin"

    fun protocolVersion(): Int = 1

    fun selfCheck(): Boolean = true

    fun createView(hostActivity: Activity): View = TextView(hostActivity).apply {
        text = "Plugin UI loaded\n\n$pluginName()\nProtocol $protocolVersion"
        textSize = 20f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(32, 42, 56))
        setPadding(48, 48, 48, 48)
    }
}
