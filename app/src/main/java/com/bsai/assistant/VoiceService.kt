
package com.bsai.assistant

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
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

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        createNotification()
        startListening()
    }

    private fun createNotification() {
        val channelId = "bs_voice_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "BS Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("BS AI Assistant")
                .setContentText("ভয়েস অ্যাসিস্ট্যান্ট চালু আছে")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("BS AI Assistant")
                .setContentText("ভয়েস অ্যাসিস্ট্যান্ট চালু আছে")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
        }

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
        if (listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("BSVoice", "Speech recognition unavailable")
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
                        restartListening()
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        listening = false

                        val words = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val spoken = words?.firstOrNull() ?: ""

                        Log.d("BSVoice", "শোনা হয়েছে: $spoken")

                        sendBroadcast(
                            Intent("com.bsai.assistant.VOICE_COMMAND")
                                .putExtra("command", spoken)
                        )

                        restartListening()
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer?.startListening(intent)

        } catch (e: Exception) {
            listening = false
            restartListening()
        }
    }

    private fun restartListening() {
        handler.postDelayed({
            if (!listening) startListening()
        }, 1200)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
