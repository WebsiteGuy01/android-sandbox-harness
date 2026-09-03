package com.example.testplugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.example.sandbox.core.SandboxPlugin

/** Graphical entry point for verifying XML inflation and routed plugin intents. */
class PluginEntryPoint : SandboxPlugin {
    override fun getPluginName(): String = "sandbox-test-plugin"

    override fun getProtocolVersion(): Int = 1

    fun pluginName(): String = getPluginName()

    fun protocolVersion(): Int = getProtocolVersion()

    fun selfCheck(): Boolean = true

    override fun createView(hostActivity: Activity, pluginContext: Context): View? {
        val inflater = LayoutInflater.from(pluginContext)
        val view = inflater.inflate(R.layout.plugin_main, null)

        view.findViewById<Button>(R.id.launch_button)?.setOnClickListener {
            val intent = Intent().apply {
                setClassName(
                    "com.example.testplugin",
                    "com.example.testplugin.SecondaryPluginActivity"
                )
            }
            pluginContext.startActivity(intent)
        }
        return view
    }
}
