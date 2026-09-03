package com.example.host

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            val report = SandboxTestRunner(
                hostContext = applicationContext,
                assetName = "test_plugin.apk",
                pluginPackageName = "com.example.testplugin",
                entryPointClassName = "com.example.testplugin.PluginEntryPoint"
            ).run()

            Log.i("SandboxTestRunner", report.toString())
        }
    }
}
