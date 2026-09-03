package com.example.sandbox.core

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import java.io.File

/**
 * OS-registered shell for explicitly routed plugin screens.
 *
 * This does not instantiate an arbitrary Android Activity subclass. A guest
 * screen must expose the narrow PluginUiComponent-style methods that the host
 * can safely delegate through ordinary reflection.
 */
class ProxyActivity : Activity() {
    private var pluginContext: PluginContext? = null
    private var lifecycleDelegate: SandboxActivityLifecycle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val targetName = intent.getStringExtra(EXTRA_TARGET_GUEST_ACTIVITY)
                ?: error("Missing guest activity target")
            require(targetName.startsWith(PLUGIN_PACKAGE_PREFIX)) {
                "Guest activity target is outside the plugin package"
            }

            val context = PluginContext(
                host = applicationContext,
                pluginArtifact = stagePluginAsset(),
                pluginPackageName = PLUGIN_PACKAGE_NAME
            )
            val target = context.loadPluginClass(targetName)
                .getDeclaredConstructor().newInstance()

            val delegate = SandboxActivityLifecycle(
                hostActivity = this,
                pluginContext = context
            ) {
                ReflectiveScreenComponent(target)
            }
            pluginContext = context
            lifecycleDelegate = delegate
            delegate.onCreate(savedInstanceState)
            setContentView(delegate.createView() ?: errorView("Guest screen returned no view"))
        } catch (t: Throwable) {
            setContentView(errorView(t.stackTraceToString()))
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleDelegate?.onStart()
    }

    override fun onResume() {
        super.onResume()
        lifecycleDelegate?.onResume()
    }

    override fun onPause() {
        lifecycleDelegate?.onPause()
        super.onPause()
    }

    override fun onStop() {
        lifecycleDelegate?.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lifecycleDelegate?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lifecycleDelegate?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onDestroy() {
        lifecycleDelegate?.onDestroy()
        lifecycleDelegate = null
        pluginContext?.close()
        pluginContext = null
        super.onDestroy()
    }

    private fun stagePluginAsset(): File {
        val root = File(filesDir, "staged").canonicalFile
        val destination = File(root, PLUGIN_ASSET_NAME).canonicalFile
        require(destination.parentFile == root) { "Invalid plugin asset path" }
        root.mkdirs()
        assets.open(PLUGIN_ASSET_NAME).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun errorView(message: String): TextView = TextView(this).apply {
        text = "Guest screen error:\n\n$message"
        setTextColor(android.graphics.Color.RED)
        setBackgroundColor(android.graphics.Color.BLACK)
        setPadding(48, 48, 48, 48)
        textSize = 14f
    }

    private class ReflectiveScreenComponent(private val target: Any) : PluginUiComponent {
        override fun onCreate(state: Bundle?) { invoke("onCreate", state) }
        override fun onStart() { invoke("onStart") }
        override fun onResume() { invoke("onResume") }
        override fun onPause() { invoke("onPause") }
        override fun onStop() { invoke("onStop") }
        override fun onDestroy() { invoke("onDestroy") }
        override fun onSaveInstanceState(outState: Bundle) { invoke("onSaveInstanceState", outState) }
        override fun onRestoreInstanceState(savedInstanceState: Bundle) {
            invoke("onRestoreInstanceState", savedInstanceState)
        }

        override fun createView(hostActivity: Activity, pluginContext: Context): View? {
            val method = target.javaClass.getMethod(
                "createView",
                Activity::class.java,
                android.content.Context::class.java
            )
            return method.invoke(target, hostActivity, pluginContext) as? View
        }

        private fun invoke(name: String, vararg args: Any?) {
            val method = target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.size == args.size
            } ?: return
            method.invoke(target, *args)
        }
    }

    companion object {
        const val EXTRA_TARGET_GUEST_ACTIVITY = "TARGET_GUEST_ACTIVITY"
        private const val PLUGIN_PACKAGE_NAME = "com.example.testplugin"
        private const val PLUGIN_PACKAGE_PREFIX = "$PLUGIN_PACKAGE_NAME."
        private const val PLUGIN_ASSET_NAME = "test_plugin.apk"
    }
}
