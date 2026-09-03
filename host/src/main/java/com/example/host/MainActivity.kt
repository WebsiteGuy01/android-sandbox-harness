package com.example.host

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.host.sandbox.PluginContext
import com.example.host.sandbox.PluginUiComponent
import com.example.host.sandbox.SandboxActivityLifecycle
import java.io.File

class MainActivity : Activity() {
    private var pluginContext: PluginContext? = null
    private var lifecycleDelegate: SandboxActivityLifecycle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Loading plugin UI…" })

        Thread {
            try {
                val artifact = stagePluginAsset()
                val context = PluginContext(
                    host = applicationContext,
                    pluginArtifact = artifact,
                    pluginPackageName = "com.example.testplugin"
                )
                val entryPointClass = context.loadPluginClass(
                    "com.example.testplugin.PluginEntryPoint"
                )
                val entryPoint = entryPointClass.getDeclaredConstructor().newInstance()

                runOnUiThread {
                    pluginContext = context
                    val delegate = SandboxActivityLifecycle(
                        hostActivity = this,
                        pluginContext = context
                    ) {
                        ReflectivePluginComponent(entryPoint)
                    }
                    lifecycleDelegate = delegate
                    delegate.onCreate(savedInstanceState)
                    setContentView(delegate.createView() ?: TextView(this).apply {
                        text = "Plugin did not provide a UI view"
                    })
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    setContentView(TextView(this).apply {
                        text = "Plugin initialization failed: ${t.message}"
                    })
                }
            }
        }.start()
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
        val stagedRoot = File(filesDir, "staged").canonicalFile
        val destination = File(stagedRoot, "test_plugin.apk").canonicalFile
        require(destination.parentFile == stagedRoot) { "Invalid staged plugin path" }
        stagedRoot.mkdirs()
        assets.open("test_plugin.apk").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }
}

/**
 * Small compatibility adapter for the test plugin. It keeps the plugin
 * independent of the host implementation while exposing the lifecycle contract
 * through ordinary reflection on known, optional method names.
 */
private class ReflectivePluginComponent(
    private val target: Any
) : PluginUiComponent {
    override fun onCreate(state: Bundle?) {
        invokeOptional("onCreate", state)
    }

    override fun onStart() {
        invokeOptional("onStart")
    }

    override fun onResume() {
        invokeOptional("onResume")
    }

    override fun onPause() {
        invokeOptional("onPause")
    }

    override fun onStop() {
        invokeOptional("onStop")
    }

    override fun onDestroy() {
        invokeOptional("onDestroy")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        invokeOptional("onSaveInstanceState", outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        invokeOptional("onRestoreInstanceState", savedInstanceState)
    }

    override fun createView(hostActivity: Activity): View? {
        return invokeOptional("createView", hostActivity) as? View
    }

    private fun invokeOptional(name: String, vararg args: Any?): Any? {
        val method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == args.size
        } ?: return null
        return runCatching { method.invoke(target, *args) }.getOrNull()
    }
}
