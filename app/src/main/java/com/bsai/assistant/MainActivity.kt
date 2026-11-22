
package com.bsai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val workerUrl =
        "https://bsaiassistant.bashudebsarkar61.workers.dev"

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent

    private lateinit var statusText: TextView
    private lateinit var messageInput: EditText
    private lateinit var micButton: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var listeningEnabled = false
    private var ttsReady = false
    private var currentVoice = "female"

    private val micPermission = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(8, 13, 35)
        window.navigationBarColor = Color.rgb(8, 13, 35)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        tts = TextToSpeech(this, this)

        createInterface()
        setupSpeechRecognizer()
    }

    private fun createInterface() {
        val screenHeight = resources.displayMetrics.heightPixels
        val panelHeight = (screenHeight * 0.82f).toInt()

        val outer = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 11, 30))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(
                Color.rgb(17, 25, 58), 28
            )
        }

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            panelHeight,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
        }

        val title = TextView(this).apply {
            text = "BS AI ASSISTANT"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create(
                "sans-serif-black", Typeface.BOLD
            )
            letterSpacing = 0.08f
        }

        panel.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val orb = AIOrbView(this)
        panel.addView(
            orb,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        statusText = TextView(this).apply {
            text = "আপনার নির্দেশের অপেক্ষায়"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(190, 205, 255))
        }

        panel.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(35)
            )
        )

        messageInput = EditText(this).apply {
            hint = "এখানে লিখুন..."
            textSize = 15f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(
                Color.rgb(35, 47, 83), 18
            )
        }

        panel.addView(
            messageInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val sendButton = makeButton("পাঠান", Color.rgb(37, 115, 220))
        micButton = makeButton("🎤", Color.rgb(20, 155, 120))

        controls.addView(
            micButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                rightMargin = dp(6)
            }
        )

        controls.addView(
            sendButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(6)
            }
        )

        panel.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                messageInput.setText("")
                handleCommand(text)
            }
        }

        micButton.setOnClickListener {
            if (!listeningEnabled) {
                startVoiceMode()
            } else {
                stopVoiceMode()
            }
        }

        outer.addView(panel, panelParams)
        setContentView(outer)
    }

    private fun makeButton(
        text: String,
        color: Int
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(color, 16)
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts.language = Locale("bn", "BD")

            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        if (listeningEnabled) {
                            handler.postDelayed({
                                startListening()
                            }, 10000)
                        }
                    }

                    override fun onError(id: String?) {
                        if (listeningEnabled) {
                            handler.postDelayed({
                                startListening()
                            }, 3000)
                        }
                    }
                }
            )

            speak("BS AI Assistant-এ আপনাকে স্বাগতম।")
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return

        runOnUiThread {
            statusText.text = "উত্তর দিচ্ছি..."
            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "BS_AI_REPLY"
            )
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "এই ফোনে Voice Recognition নেই"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizerIntent = Intent(
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
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "bn-BD"
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )
        }

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "বলুন, আমি শুনছি..."
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "শুনছি..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusText.text = "আপনার কথা বুঝছি..."
                }

                override fun onError(error: Int) {
                    if (listeningEnabled) {
                        handler.postDelayed({
                            startListening()
                        }, 2000)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val spokenText = words?.firstOrNull()

                    if (!spokenText.isNullOrBlank()) {
                        handleCommand(spokenText)
                    } else if (listeningEnabled) {
                        handler.postDelayed({
                            startListening()
                        }, 2000)
                    }
                }

                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
    }

    private fun startVoiceMode() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micPermission
            )
            return
        }

        listeningEnabled = true
        micButton.text = "বন্ধ করুন"
        startListening()
    }

    private fun stopVoiceMode() {
        listeningEnabled = false
        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}

        micButton.text = "🎤"
        statusText.text = "ভয়েস বন্ধ আছে"
    }

    private fun startListening() {
        if (!listeningEnabled) return

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(recognizerIntent)
        } catch (_: Exception) {
            statusText.text = "আবার মাইক্রোফোন চালু করুন"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == micPermission &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceMode()
        } else {
            statusText.text = "মাইক্রোফোন অনুমতি প্রয়োজন"
        }
    }

    private fun handleCommand(original: String) {
        val command = original.lowercase(Locale.ROOT).trim()

        if (
            command.contains("কে তৈরি") ||
            command.contains("কে বানিয়েছে") ||
            command.contains("তোমার নির্মাতা")
        ) {
            speak("আমাকে বাসুদেব সরকার তৈরি করেছেন।")
            return
        }

        if (command.contains("মহিলা কণ্ঠ")) {
            currentVoice = "female"
            changeVoice()
            speak("ঠিক আছে, এখন মহিলা কণ্ঠে কথা বলব।")
            return
        }

        if (command.contains("পুরুষ কণ্ঠ")) {
            currentVoice = "male"
            changeVoice()
            speak("ঠিক আছে, এখন পুরুষ কণ্ঠে কথা বলব।")
            return
        }

        if (
            command.contains("নিচে স্ক্রল") ||
            command.contains("উপরে স্ক্রল") ||
            command.contains("পিছনে যাও") ||
            command.contains("হোমে যাও")
        ) {
            val intent = Intent(
                PhoneControlService.ACTION_COMMAND
            ).apply {
                setPackage(packageName)
                putExtra("command", original)
            }

            sendBroadcast(intent)
            speak("ঠিক আছে।")
            return
        }

        val apps = mapOf(
            "facebook" to "com.facebook.katana",
            "ফেসবুক" to "com.facebook.katana",
            "tiktok" to "com.zhiliaoapp.musically",
            "টিকটক" to "com.zhiliaoapp.musically",
            "youtube" to "com.google.android.youtube",
            "ইউটিউব" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "ইনস্টাগ্রাম" to "com.instagram.android"
        )

        for ((name, packageName) in apps) {
            if (command.contains(name)) {
                openApp(packageName)
                return
            }
        }

        askGemini(original)
    }

    private fun changeVoice() {
        if (!ttsReady) return

        val voices = tts.voices ?: return
        val matchingVoice = voices.firstOrNull {
            val name = it.name.lowercase(Locale.ROOT)
            if (currentVoice == "female") {
                name.contains("female") || name.contains("woman")
            } else {
                name.contains("male") || name.contains("man")
            }
        }

        if (matchingVoice != null) {
            tts.voice = matchingVoice
        }
    }

    private fun openApp(packageName: String) {
        try {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                startActivity(launchIntent)
                speak("অ্যাপটি খুলছি।")
            } else {
                speak("এই অ্যাপটি ফোনে ইনস্টল করা নেই।")
            }
        } catch (_: Exception) {
            speak("অ্যাপটি খোলা যাচ্ছে না।")
        }
    }

    private fun askGemini(prompt: String) {
        statusText.text = "Gemini-এর সঙ্গে সংযোগ হচ্ছে..."

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(workerUrl)
                connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val json = JSONObject()
                json.put("prompt", prompt)

                connection.outputStream.use {
                    it.write(
                        json.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode = connection.responseCode

                val responseText =
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use {
                            it.readText()
                        }
                    } else {
                        connection.errorStream?.bufferedReader()?.use {
                            it.readText()
                        } ?: ""
                    }

                if (responseCode !in 200..299) {
                    throw Exception("Server error: $responseCode")
                }

                val responseJson = JSONObject(responseText)
                val reply = responseJson.optString("reply")

                runOnUiThread {
                    if (reply.isNotBlank()) {
                        speak(reply)
                    } else {
                        speak("দুঃখিত, কোনো উত্তর পাওয়া যায়নি।")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "সংযোগে সমস্যা হয়েছে"
                    speak(
                        "Gemini সংযোগে সমস্যা হয়েছে। " +
                        "ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।"
                    )
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    override fun onDestroy() {
        listeningEnabled = false
        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.destroy()
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {}

        super.onDestroy()
    }

    class AIOrbView(context: android.content.Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.27f

            paint.style = Paint.Style.FILL
            paint.shader = android.graphics.RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    Color.rgb(90, 220, 255),
                    Color.rgb(65, 90, 255),
                    Color.rgb(125, 40, 220),
                    Color.TRANSPARENT
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )

            canvas.drawCircle(cx, cy, radius, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.rgb(120, 220, 255)
            canvas.drawCircle(cx, cy, radius * 0.72f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, radius * 0.12f, paint)
        }
    }
}
