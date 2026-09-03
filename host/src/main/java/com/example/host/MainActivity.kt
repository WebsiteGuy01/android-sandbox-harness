package com.example.host

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import com.example.host.sandbox.SandboxTestRunner
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : Activity() {
    private val crashLogFile by lazy { File(filesDir, "sandbox_crash_log.txt") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            setTextColor(Color.GREEN)
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
            textSize = 14f
        }
        val scrollView = ScrollView(this).apply { addView(textView) }
        setContentView(scrollView)

        if (crashLogFile.exists()) {
            textView.text = "FATAL CRASH LOG:\n\n${crashLogFile.readText()}"
            crashLogFile.delete()
            return
        }

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runCatching {
                val stackTrace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }
                crashLogFile.writeText(stackTrace.toString())
            }
            // Allow the process to terminate after persisting the diagnostic.
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        textView.text = "Initializing Sandbox Engine..."

        Thread {
            try {
                val report = SandboxTestRunner(
                    hostContext = applicationContext,
                    assetName = "test_plugin.apk",
                    pluginPackageName = "com.example.testplugin",
                    entryPointClassName = "com.example.testplugin.PluginEntryPoint"
                ).run()

                runOnUiThread {
                    textView.text = if (report.error != null) {
                        "Runner Exception Caught:\n${report.error}"
                    } else {
                        "Sandbox Diagnostics:\n\n" +
                            "Artifact Staged: ${report.artifactStaged}\n" +
                            "Plugin Class Loaded: ${report.pluginClassLoaded}\n" +
                            "ClassLoader Isolated: ${report.classLoaderIsolated}\n" +
                            "Valid Path Accepted: ${report.validPathAccepted}\n" +
                            "Escape Blocked: ${report.outOfBoundsRejected}\n" +
                            "Symlink Blocked: ${report.symlinkRejected}"
                    }
                }
            } catch (throwable: Throwable) {
                val stackTrace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }
                runOnUiThread { textView.text = "Thread Exception:\n$stackTrace" }
            }
        }.start()
    }
}
