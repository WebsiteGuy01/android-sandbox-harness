package com.example.sandbox.core

import android.app.Activity
import android.content.Context
import android.view.View

/** Stable contract implemented by plugins loaded through PluginContext. */
interface SandboxPlugin {
    fun getPluginName(): String
    fun getProtocolVersion(): Int
    fun createView(hostActivity: Activity, pluginContext: Context): View?
}
