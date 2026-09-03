package com.example.host.sandbox

import android.app.Activity
import android.os.Bundle
import android.view.View
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Narrow UI contract implemented by a plugin entry point.
 *
 * The plugin owns this object, while the host owns the Activity. A plugin must
 * not retain the Activity or host Context after the delegate is destroyed.
 */
interface PluginUiComponent {
    fun onCreate(state: Bundle?) {}
    fun onStart() {}
    fun onResume() {}
    fun onPause() {}
    fun onStop() {}
    fun onDestroy() {}
    fun onSaveInstanceState(outState: Bundle) {}
    fun onRestoreInstanceState(savedInstanceState: Bundle) {}

    /** Optional view supplied by the plugin; null is a valid result. */
    fun createView(hostActivity: Activity): View? = null
}

/**
 * Bridges host Activity lifecycle events to a plugin component without
 * registering global callbacks or modifying ActivityThread.
 */
class SandboxActivityLifecycle(
    private val hostActivity: Activity,
    private val pluginContext: PluginContext,
    private val componentFactory: (PluginContext) -> PluginUiComponent
) {
    private var component: PluginUiComponent? = null
    private var destroyed = false

    fun onCreate(savedInstanceState: Bundle?) {
        check(!destroyed) { "Lifecycle delegate has been destroyed" }
        if (component == null) component = componentFactory(pluginContext)
        component?.onCreate(StateBundleSanitizer.copyOf(savedInstanceState))
    }

    fun onStart() {
        checkAlive()
        component?.onStart()
    }

    fun onResume() {
        checkAlive()
        component?.onResume()
    }

    fun onPause() {
        if (!destroyed) component?.onPause()
    }

    fun onStop() {
        if (!destroyed) component?.onStop()
    }

    fun onSaveInstanceState(outState: Bundle) {
        checkAlive()
        val isolated = Bundle()
        component?.onSaveInstanceState(isolated)
        outState.putBundle(STATE_KEY, StateBundleSanitizer.copyOf(isolated))
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        checkAlive()
        val isolated = savedInstanceState?.getBundle(STATE_KEY)
            ?: savedInstanceState
        component?.onRestoreInstanceState(StateBundleSanitizer.copyOf(isolated))
    }

    fun createView(): View? {
        checkAlive()
        return component?.createView(hostActivity)
    }

    fun onDestroy() {
        if (destroyed) return
        try {
            component?.onDestroy()
        } finally {
            component = null
            destroyed = true
        }
    }

    private fun checkAlive() {
        check(!destroyed) { "Lifecycle delegate has been destroyed" }
    }

    private companion object {
        const val STATE_KEY = "sandbox_plugin_state"
    }
}

/**
 * Copies only Bundle types that are safe to pass through the host lifecycle.
 * Plugin Parcelable/Serializable objects are intentionally discarded, avoiding
 * plugin ClassLoader references in host-saved state.
 */
object StateBundleSanitizer {
    fun copyOf(source: Bundle?): Bundle? {
        if (source == null) return null
        val result = Bundle()
        for (key in source.keySet()) {
            val value = source.get(key)
            when (value) {
                null -> result.putString(key, null)
                is String -> result.putString(key, value)
                is CharSequence -> result.putCharSequence(key, value.toString())
                is Boolean -> result.putBoolean(key, value)
                is Byte -> result.putByte(key, value)
                is Short -> result.putShort(key, value)
                is Int -> result.putInt(key, value)
                is Long -> result.putLong(key, value)
                is Float -> result.putFloat(key, value)
                is Double -> result.putDouble(key, value)
                is Char -> result.putChar(key, value)
                is IntArray -> result.putIntArray(key, value.copyOf())
                is LongArray -> result.putLongArray(key, value.copyOf())
                is BooleanArray -> result.putBooleanArray(key, value.copyOf())
                is ByteArray -> result.putByteArray(key, value.copyOf())
                is Bundle -> result.putBundle(key, copyOf(value))
                else -> Unit // Drop Parcelable, Serializable, Binder, and plugin objects.
            }
        }
        result.classLoader = null
        return result
    }
}

data class SandboxDiagnosticReport(
    val artifactStaged: Boolean,
    val pluginClassLoaded: Boolean,
    val classLoaderIsolated: Boolean,
    val windowMetrics: SandboxWindowMetrics?,
    val validFd: Int,
    val validPathAccepted: Boolean,
    val outOfBoundsFd: Int,
    val outOfBoundsRejected: Boolean,
    val symlinkFd: Int,
    val symlinkRejected: Boolean,
    val error: String? = null
)

/**
 * Runs the non-privileged pipeline. Invoke from a worker thread, not the main
 * thread, because asset staging and class loading may perform disk I/O.
 */
class SandboxTestRunner(
    private val hostContext: android.content.Context,
    private val assetName: String,
    private val pluginPackageName: String,
    private val entryPointClassName: String
) {
    fun run(): SandboxDiagnosticReport {
        var pluginContext: PluginContext? = null
        var validFd = -1
        var outOfBoundsFd = -1
        var symlinkFd = -1
        var staged = false
        var loaded = false
        var isolated = false
        var metrics: SandboxWindowMetrics? = null

        return try {
            val stagedFile = stageAsset()
            staged = true
            pluginContext = PluginContext(hostContext, stagedFile, pluginPackageName)

            val entryPoint = pluginContext.loadPluginClass(entryPointClassName)
            loaded = true
            isolated = entryPoint.classLoader === pluginContext.classLoader

            metrics = WindowMetricsAdapter.current(hostContext)
            val root = pluginContext.sandboxRoot()
            val validFile = File(pluginContext.filesDir, "validation/ok.txt").apply {
                parentFile?.mkdirs()
                if (!exists()) writeText("sandbox-test")
            }
            validFd = NativePathMapper.openValidated(
                root.absolutePath,
                validFile.absolutePath,
                android.system.OsConstants.O_RDONLY
            )

            outOfBoundsFd = NativePathMapper.openValidated(
                root.absolutePath,
                "/data/system/packages.xml",
                android.system.OsConstants.O_RDONLY
            )

            val link = File(pluginContext.filesDir, "validation/outside-link")
            runCatching { Files.deleteIfExists(link.toPath()) }
            runCatching {
                Files.createSymbolicLink(link.toPath(), File("/data/system/packages.xml").toPath())
            }
            if (Files.isSymbolicLink(link.toPath())) {
                symlinkFd = NativePathMapper.openValidated(
                    root.absolutePath,
                    link.absolutePath,
                    android.system.OsConstants.O_RDONLY
                )
            }

            SandboxDiagnosticReport(
                artifactStaged = staged,
                pluginClassLoaded = loaded,
                classLoaderIsolated = isolated,
                windowMetrics = metrics,
                validFd = validFd,
                validPathAccepted = validFd >= 0,
                outOfBoundsFd = outOfBoundsFd,
                outOfBoundsRejected = outOfBoundsFd < 0,
                symlinkFd = symlinkFd,
                symlinkRejected = symlinkFd < 0
            )
        } catch (t: Throwable) {
            SandboxDiagnosticReport(
                artifactStaged = staged,
                pluginClassLoaded = loaded,
                classLoaderIsolated = isolated,
                windowMetrics = metrics,
                validFd = validFd,
                validPathAccepted = validFd >= 0,
                outOfBoundsFd = outOfBoundsFd,
                outOfBoundsRejected = outOfBoundsFd < 0,
                symlinkFd = symlinkFd,
                symlinkRejected = symlinkFd < 0,
                error = "${t::class.java.simpleName}: ${t.message}"
            )
        } finally {
            closeQuietly(validFd)
            closeQuietly(outOfBoundsFd)
            closeQuietly(symlinkFd)
            pluginContext?.close()
        }
    }

    private fun stageAsset(): File {
        val destination = File(hostContext.filesDir, "staged/$assetName").canonicalFile
        val stagedRoot = File(hostContext.filesDir, "staged").canonicalFile
        require(destination.parentFile == stagedRoot) { "assetName must be a simple file name" }
        stagedRoot.mkdirs()
        hostContext.assets.open(assetName).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun closeQuietly(fd: Int) {
        if (fd >= 0) runCatching { android.system.Os.close(fd) }
    }
}
