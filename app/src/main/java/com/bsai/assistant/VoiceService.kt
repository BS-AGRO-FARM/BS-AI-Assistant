
package com.bsai.assistant

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceService : Service() {

    companion object {
        const val VOICE_COMMAND = "com.bsai.assistant.VOICE_COMMAND"
        const val ACTION_PAUSE = "com.bsai.assistant.PAUSE_VOICE"
        const val ACTION_RESUME = "com.bsai.assistant.RESUME_VOICE"
    }

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var paused = false

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE -> {
                    paused = true
                    handler.removeCallbacksAndMessages(null)
                    recognizer?.cancel()
                    listening = false
                }

                ACTION_RESUME -> {
                    paused = false
                    startListening()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                controlReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(controlReceiver, filter)
        }

        createNotification()
        startListening()
    }

    private fun createNotification() {
        val channelId = "bs_voice_channel"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "BS Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("BS AI Assistant")
            .setContentText("ভয়েস অ্যাসিস্ট্যান্ট চালু আছে")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(101, notification)
        }
    }

    private fun startListening() {
        if (paused || listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("BSVoice", "Speech recognition unavailable")
            scheduleRestart()
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        listening = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        listening = false
                    }

                    override fun onError(error: Int) {
                        listening = false
                        scheduleRestart()
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        listening = false

                        val words = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val spoken = words?.firstOrNull().orEmpty()

                        if (spoken.isNotBlank()) {
                            Log.d("BSVoice", "শোনা হয়েছে: $spoken")

                            // কথা পাওয়ার ১০ সেকেন্ড পরে কমান্ড পাঠাবে
                            handler.postDelayed({
                                if (!paused) {
                                    sendBroadcast(
                                        Intent(VOICE_COMMAND)
                                            .setPackage(packageName)
                                            .putExtra("command", spoken)
                                    )
                                }
                            }, 10_000)

                        } else {
                            scheduleRestart()
                        }
                    }

                    override fun onPartialResults(
                        partialResults: android.os.Bundle?
                    ) {}

                    override fun onEvent(
                        eventType: Int,
                        params: android.os.Bundle?
                    ) {}
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "bn-BD"
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }

            recognizer?.startListening(intent)

        } catch (e: Exception) {
            listening = false
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        handler.removeCallbacksAndMessages(null)

        if (!paused) {
            handler.postDelayed({
                startListening()
            }, 2500)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        paused = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null

        try {
            unregisterReceiver(controlReceiver)
        } catch (_: Exception) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
