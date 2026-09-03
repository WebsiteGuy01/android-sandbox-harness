package com.example.testplugin

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView

/** Resource-independent secondary screen for validating routed plugin intents. */
class SecondaryPluginActivity {
    fun createView(hostActivity: Activity, pluginContext: Context): View {
        return TextView(hostActivity).apply {
            text = "Routed to Secondary Screen Successfully"
            setTextColor(Color.CYAN)
            setBackgroundColor(Color.DKGRAY)
            textSize = 24f
            setPadding(48, 48, 48, 48)
        }
    }
}
