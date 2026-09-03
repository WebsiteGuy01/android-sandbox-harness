package com.example.host

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.sandbox.core.PluginContext
import com.example.sandbox.core.PluginUiComponent
import com.example.sandbox.core.PluginVerifier
import com.example.sandbox.core.ProductionGuestDiagnostic
import com.example.sandbox.core.SandboxActivityLifecycle
import com.example.sandbox.core.SandboxPlugin
import com.example.sandbox.core.SandboxTestRunner
import java.io.File

class MainActivity : Activity() {
    private var pluginContext: PluginContext? = null
    private var lifecycleDelegate: SandboxActivityLifecycle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Loading plugin UI…" })

        Thread {
            try {
                val productionGuest = SandboxTestRunner(
                    hostContext = applicationContext,
                    assetName = "test_plugin.apk",
                    pluginPackageName = "com.example.testplugin",
                    entryPointClassName = "com.example.testplugin.PluginEntryPoint"
                ).inspectProductionGuest()
                val artifact = stagePluginAsset()
                val verification = PluginVerifier.verify(
                    applicationContext,
                    artifact.absolutePath,
                    setOf(BuildConfig.PLUGIN_SIGNATURE_SHA256)
                )
                if (!verification.verified) {
                    throw IllegalStateException(
                        verification.error ?: "Plugin signature verification failed"
                    )
                }
                val context = PluginContext(
                    host = applicationContext,
                    pluginArtifact = artifact,
                    pluginPackageName = "com.example.testplugin"
                )
                val plugin = context.loadPluginClass(
                    "com.example.testplugin.PluginEntryPoint"
                ).getDeclaredConstructor().newInstance() as SandboxPlugin

                runOnUiThread {
                    pluginContext = context
                    val delegate = SandboxActivityLifecycle(
                        hostActivity = this,
                        pluginContext = context
                    ) {
                        SandboxPluginComponent(plugin)
                    }
                    lifecycleDelegate = delegate
                    delegate.onCreate(savedInstanceState)
                    val pluginView = delegate.createView() ?: TextView(this).apply {
                        text = "Plugin did not provide a UI view"
                    }
                    val container = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    container.addView(
                        productionDiagnosticView(productionGuest),
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                    container.addView(
                        pluginView,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    )
                    setContentView(container)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    setContentView(TextView(this).apply {
                        text = "Plugin initialization failed:\n\n${t.stackTraceToString()}"
                        setTextColor(android.graphics.Color.RED)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setPadding(48, 48, 48, 48)
                        textSize = 14f
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

    private fun productionDiagnosticView(report: ProductionGuestDiagnostic): TextView =
        TextView(this).apply {
            text = buildString {
                append("Production APK manifest inspection\n")
                append("Package: ${report.packageName ?: "unavailable"}\n")
                append("Version: ${report.versionName ?: "unknown"} (${report.versionCode ?: "?"})\n")
                append("Primary activity: ${report.primaryActivity ?: "none"}\n")
                append("Declared activities: ${report.activityCount}")
                report.error?.let { append("\nInspection error: $it") }
            }
            setPadding(24, 24, 24, 24)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.DKGRAY)
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

private class SandboxPluginComponent(
    private val plugin: SandboxPlugin
) : PluginUiComponent {
    override fun createView(hostActivity: Activity, pluginContext: Context): View? =
        plugin.createView(hostActivity, pluginContext)
}
