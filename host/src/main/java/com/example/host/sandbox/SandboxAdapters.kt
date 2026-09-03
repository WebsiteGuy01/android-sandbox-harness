package com.example.host.sandbox

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi

/** API-29-compatible value object used instead of directly exposing WindowMetrics. */
data class SandboxWindowMetrics(
    val bounds: Rect,
    val density: Float
)

/**
 * Explicit adapter for window queries. It uses WindowMetrics only when the host
 * supports it; otherwise it derives equivalent bounds from DisplayMetrics.
 */
object WindowMetricsAdapter {
    fun current(context: Context): SandboxWindowMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return fromDisplayMetrics(context)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fromApi30(context, wm)
        } else {
            fromDisplayMetrics(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun fromApi30(context: Context, windowManager: WindowManager): SandboxWindowMetrics {
        val metrics = windowManager.currentWindowMetrics
        return SandboxWindowMetrics(
            Rect(metrics.bounds),
            context.resources.displayMetrics.density
        )
    }

    @Suppress("DEPRECATION")
    private fun fromDisplayMetrics(context: Context): SandboxWindowMetrics {
        val displayMetrics = context.resources.displayMetrics
        return SandboxWindowMetrics(
            bounds = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels),
            density = displayMetrics.density
        )
    }
}

/** Opaque handle returned by the back-dispatch adapter. */
fun interface BackRegistration {
    fun unregister()
}

/**
 * Explicit compatibility adapter for modern back callbacks.
 *
 * On API 29 and below it returns a no-op registration. On newer hosts it uses
 * the public OnBackInvokedDispatcher API. It never claims that a callback ran
 * and never fabricates navigation or permission results.
 */
object BackDispatcherAdapter {
    fun register(
        context: Context,
        priority: Int = 0,
        onBack: () -> Unit
    ): BackRegistration {
        if (Build.VERSION.SDK_INT < 33) return BackRegistration { }
        return registerApi33(context, priority, onBack)
    }

    @RequiresApi(33)
    private fun registerApi33(
        context: Context,
        priority: Int,
        onBack: () -> Unit
    ): BackRegistration {
        val activity = context as? android.app.Activity
            ?: return BackRegistration { }
        val dispatcher = activity.onBackInvokedDispatcher
        val callback = android.window.OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(priority, callback)
        return BackRegistration {
            dispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }
}

/** Kotlin-facing bridge to the native traversal-safe path mapper. */
object NativePathMapper {
    init {
        System.loadLibrary("sandbox_native")
    }

    /**
     * Opens a path only when its canonical location is below sandboxRoot.
     * Returns an owned file descriptor, or -1 when validation/opening fails.
     */
    external fun openValidated(
        sandboxRoot: String,
        requestedPath: String,
        flags: Int,
        mode: Int = 0
    ): Int
}
