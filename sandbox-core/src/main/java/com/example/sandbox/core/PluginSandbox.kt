package com.example.sandbox.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.DexClassLoader
import java.io.Closeable
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * A user-space context for a plugin APK or DEX/JAR.
 *
 * This class does not alter ActivityThread, ART, Binder, SELinux, permissions,
 * or framework classes. The plugin artifact must be supplied by the host and
 * copied into the host application's private storage before construction.
 */
class PluginContext(
    host: Context,
    pluginArtifact: File,
    private val pluginPackageName: String,
    private val allowedServices: Set<String> = setOf(
        Context.NOTIFICATION_SERVICE,
        Context.POWER_SERVICE
    )
) : ContextWrapper(host), Closeable {

    private val pluginRoot: File = File(host.filesDir, "sandbox/$pluginPackageName").apply {
        mkdirs()
    }

    private val optimizedDexDir: File = File(pluginRoot, "optimized-dex").apply {
        mkdirs()
    }

    private val nativeLibDir: File = File(pluginRoot, "native-libs").apply {
        mkdirs()
    }

    private val artifact: File = pluginArtifact.canonicalFile
    private val pluginClassLoader: ClassLoader
    private val pluginResources: Resources by lazy { buildPluginResources() }

    init {
        require(artifact.isFile) { "Plugin artifact does not exist: $artifact" }
        require(artifact.startsWith(host.filesDir.canonicalFile)) {
            "Plugin artifacts must be staged inside the host's private files directory"
        }

        pluginClassLoader = DexClassLoader(
            artifact.absolutePath,
            optimizedDexDir.absolutePath,
            nativeLibDir.absolutePath,
            host.classLoader
        )
    }

    override fun getClassLoader(): ClassLoader = pluginClassLoader

    override fun getFilesDir(): File = File(pluginRoot, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(pluginRoot, "cache").apply { mkdirs() }

    override fun getCodeCacheDir(): File = File(pluginRoot, "code-cache").apply { mkdirs() }

    override fun getNoBackupFilesDir(): File = File(pluginRoot, "no-backup").apply { mkdirs() }

    override fun getDatabasePath(name: String): File {
        requireSafeName(name)
        return File(File(pluginRoot, "databases").apply { mkdirs() }, name)
    }

    override fun getSharedPreferences(name: String, mode: Int) =
        super.getSharedPreferences("plugin_${pluginPackageName}_$name", mode)

    override fun startActivity(intent: android.content.Intent) {
        val target = intent.component?.className
            ?: throw IllegalArgumentException("Plugin activities must use an explicit component")
        require(target.startsWith("$pluginPackageName.")) {
            "Plugin activity target is outside the declared plugin package"
        }

        val routed = android.content.Intent().apply {
            action = intent.action
            data = intent.data
            intent.categories?.forEach { addCategory(it) }
            flags = intent.flags or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            component = android.content.ComponentName(this@PluginContext, ProxyActivity::class.java)
            putExtra(ProxyActivity.EXTRA_TARGET_GUEST_ACTIVITY, target)
        }
        super.startActivity(routed)
    }

    override fun getSystemService(name: String): Any? {
        // LayoutInflater must be bound to this context so XML uses plugin resources.
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            val baseInflater = android.view.LayoutInflater.from(baseContext)
            return baseInflater.cloneInContext(this)
        }

        // Foundation for future WindowMetrics adaptation. Keep the lookup
        // defensive because API 30+ services may be absent on an API 29 host.
        if (Context.WINDOW_SERVICE == name) {
            return try {
                baseContext.getSystemService(name)
            } catch (e: Throwable) {
                android.util.Log.w(
                    "SandboxEngine",
                    "Intercepted unsupported WindowManager request",
                    e
                )
                null
            }
        }

        // Deny-by-default prevents accidental access to sensitive host services.
        if (name !in allowedServices) return null
        return try {
            super.getSystemService(name)
        } catch (e: Throwable) {
            android.util.Log.w(
                "SandboxEngine",
                "Intercepted unsupported service request: $name",
                e
            )
            null
        }
    }

    override fun getAssets(): AssetManager = pluginResources.assets

    override fun getResources(): Resources = pluginResources

    private fun buildPluginResources(): Resources {
        val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val addAssetPath = AssetManager::class.java.getMethod(
            "addAssetPath",
            String::class.java
        )
        val cookie = addAssetPath.invoke(assetManager, artifact.absolutePath) as? Int ?: 0
        check(cookie != 0) { "Unable to add plugin asset path" }
        val hostResources = baseContext.resources
        return Resources(
            assetManager,
            hostResources.displayMetrics,
            hostResources.configuration
        )
    }

    fun loadPluginClass(binaryName: String): Class<*> {
        require(binaryName.matches(Regex("[A-Za-z_$][A-Za-z0-9_$.]*"))) {
            "Invalid class name"
        }
        return Class.forName(binaryName, false, pluginClassLoader)
    }

    fun sandboxRoot(): File = pluginRoot

    override fun close() {
        // DexClassLoader has no public close contract on all API levels.
        // Do not delete active code; cleanup can be performed at next startup.
    }

    private fun requireSafeName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..")
        require(!name.contains('/') && !name.contains('\\')) {
            "Path separators are not allowed"
        }
    }

    private fun File.startsWith(parent: File): Boolean {
        val childPath = canonicalPath + File.separator
        val parentPath = parent.canonicalPath + File.separator
        return childPath.startsWith(parentPath)
    }
}

/**
 * Creates proxies only for interfaces. Concrete framework classes cannot be
 * safely replaced with java.lang.reflect.Proxy and must use an explicit adapter.
 */
object SafeProxyFactory {
    private val cache = ConcurrentHashMap<ProxyKey, Any>()

    inline fun <reified T : Any> create(
        fallback: Map<String, Any?> = emptyMap(),
        noinline onInvocation: ((Method, Array<out Any?>) -> Any?)? = null
    ): T = create(T::class.java, fallback, onInvocation)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(
        interfaceType: Class<T>,
        fallback: Map<String, Any?> = emptyMap(),
        onInvocation: ((Method, Array<out Any?>) -> Any?)? = null
    ): T {
        require(interfaceType.isInterface) {
            "SafeProxyFactory accepts interfaces only; use an explicit adapter for classes"
        }

        val key = ProxyKey(interfaceType, fallback)
        return cache.getOrPut(key) {
            Proxy.newProxyInstance(
                interfaceType.classLoader ?: SafeProxyFactory::class.java.classLoader,
                arrayOf(interfaceType),
                SafeInvocationHandler(fallback, onInvocation)
            )
        } as T
    }

    private data class ProxyKey(
        val type: Class<*>,
        val fallback: Map<String, Any?>
    )

    private class SafeInvocationHandler(
        private val fallback: Map<String, Any?>,
        private val onInvocation: ((Method, Array<out Any?>) -> Any?)?
    ) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            if (method.declaringClass == Any::class.java) {
                return when (method.name) {
                    "toString" -> "SafeProxy(${proxy.javaClass.interfaces.joinToString()})"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }

            val safeArgs = args ?: emptyArray()
            return try {
                onInvocation?.invoke(method, safeArgs)
                    ?: fallback[method.name]
                    ?: defaultValue(method.returnType)
            } catch (_: Throwable) {
                // A compatibility stub must never turn a missing optional API
                // into an uncaught exception on the plugin thread.
                defaultValue(method.returnType)
            }
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Byte::class.javaPrimitiveType -> 0.toByte()
        type == Short::class.javaPrimitiveType -> 0.toShort()
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Float::class.javaPrimitiveType -> 0f
        type == Double::class.javaPrimitiveType -> 0.0
        type == Char::class.javaPrimitiveType -> '\u0000'
        type == Void.TYPE -> Unit
        else -> null
    }
}
