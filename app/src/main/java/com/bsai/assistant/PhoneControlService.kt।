
package com.bsai.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class PhoneControlService : AccessibilityService() {

    companion object {
        const val ACTION_COMMAND =
            "com.bsai.assistant.PHONE_COMMAND"
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra("command") ?: return
            handlePhoneCommand(command)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val filter = IntentFilter(ACTION_COMMAND)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun handlePhoneCommand(command: String) {
        when {
            command.contains("নিচে স্ক্রল") ->
                scrollScreen(true)

            command.contains("উপরে স্ক্রল") ->
                scrollScreen(false)

            command.contains("পিছনে যাও") ->
                performGlobalAction(GLOBAL_ACTION_BACK)

            command.contains("হোমে যাও") ->
                performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun scrollScreen(down: Boolean) {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()

        val path = Path().apply {
            moveTo(width / 2, if (down) height * 0.8f else height * 0.3f)
            lineTo(width / 2, if (down) height * 0.3f else height * 0.8f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 100, 450))
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }
}
