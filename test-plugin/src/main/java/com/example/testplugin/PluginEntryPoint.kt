package com.example.testplugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button

/** Graphical entry point for verifying XML inflation and routed plugin intents. */
class PluginEntryPoint {
    fun pluginName(): String = "sandbox-test-plugin"

    fun protocolVersion(): Int = 1

    fun selfCheck(): Boolean = true

    fun createView(hostActivity: Activity, pluginContext: Context): View? {
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
