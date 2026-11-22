
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
    private var isRecognizing = false
    private var currentVoice = "female"

    private val micPermission = 101

    private val restartListeningRunnable = Runnable {
        if (listeningEnabled && !isSpeaking) {
            startListening()
        }
    }

    private var isSpeaking = false

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
            background = rounded(Color.rgb(17, 25, 58), 28)
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
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(35, 47, 83), 18)
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

        val sendButton = makeButton(
            "পাঠান", Color.rgb(37, 115, 220)
        )

        micButton = makeButton(
            "🎤", Color.rgb(20, 155, 120)
        )

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

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    // বাংলা Text-to-Speech চালু
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText.text = "ভয়েস চালু করা যায়নি"
            return
        }

        val banglaBD = Locale("bn", "BD")
        val banglaIN = Locale("bn", "IN")
        val bangla = Locale("bn")

        var languageResult = tts.setLanguage(banglaBD)

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            languageResult = tts.setLanguage(banglaIN)
        }

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            languageResult = tts.setLanguage(bangla)
        }

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            statusText.text = "ফোনে বাংলা ভয়েস ইনস্টল করুন"
            return
        }

        ttsReady = true
        tts.setSpeechRate(0.95f)
        tts.setPitch(1.0f)

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(id: String?) {
                    handler.post {
                        isSpeaking = true
                    }
                }

                override fun onDone(id: String?) {
                    handler.post {
                        isSpeaking = false

                        if (listeningEnabled) {
                            scheduleListening(10000)
                        } else {
                            statusText.text =
                                "আপনার নির্দেশের অপেক্ষায়"
                        }
                    }
                }

                override fun onError(id: String?) {
                    handler.post {
                        isSpeaking = false

                        if (listeningEnabled) {
                            scheduleListening(3000)
                        }
                    }
                }
            }
        )

        speak("BS AI Assistant-এ আপনাকে স্বাগতম।")
    }

    // উত্তর বলার ব্যবস্থা
    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return

        runOnUiThread {
            handler.removeCallbacks(restartListeningRunnable)

            isSpeaking = true
            statusText.text = "উত্তর দিচ্ছি..."

            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "BS_AI_REPLY"
            )
        }
    }

    // ভয়েস রিকগনিশন তৈরি
    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "এই ফোনে Voice Recognition নেই"
            return
        }

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
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )
        }

        createRecognizer()
    }

    private fun createRecognizer() {
        try {
            speechRecognizer?.destroy()

            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        isRecognizing = true
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
                        isRecognizing = false

                        if (listeningEnabled && !isSpeaking) {
                            if (
                                error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ) {
                                statusText.text = "আবার বলুন..."
                                scheduleListening(1500)
                            } else {
                                statusText.text =
                                    "ভয়েস আবার চালু হচ্ছে..."
                                scheduleListening(2500)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isRecognizing = false

                        val words = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val spokenText = words?.firstOrNull()

                        if (!spokenText.isNullOrBlank()) {
                            handleCommand(spokenText)
                        } else if (listeningEnabled) {
                            scheduleListening(1500)
                        }
                    }

                    override fun onPartialResults(results: Bundle?) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

        } catch (e: Exception) {
            statusText.text = "মাইক্রোফোন প্রস্তুত করা যায়নি"
        }
    }

    private fun startVoiceMode() {
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micPermission
            )
            return
        }

        if (speechRecognizer == null) {
            createRecognizer()
        }

        listeningEnabled = true
        micButton.text = "বন্ধ করুন"

        startListening()
    }

    private fun stopVoiceMode() {
        listeningEnabled = false
        isRecognizing = false

        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}

        micButton.text = "🎤"
        statusText.text = "ভয়েস বন্ধ আছে"
    }

    private fun scheduleListening(delay: Long) {
        handler.removeCallbacks(restartListeningRunnable)

        if (listeningEnabled) {
            handler.postDelayed(
                restartListeningRunnable,
                delay
            )
        }
    }

    private fun startListening() {
        if (!listeningEnabled || isSpeaking) return

        try {
            if (speechRecognizer == null) {
                createRecognizer()
            }

            if (isRecognizing) return

            speechRecognizer?.startListening(recognizerIntent)

        } catch (e: Exception) {
            isRecognizing = false
            statusText.text = "আবার মাইক্রোফোন চালু হচ্ছে..."
            scheduleListening(2500)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == micPermission &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceMode()
        } else {
            statusText.text = "মাইক্রোফোন অনুমতি প্রয়োজন"
        }
    }

    // নির্দেশ বুঝে কাজ করা
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
            speak("ঠিক আছে, এখন মহিলা কণ্ঠে কথা বলার চেষ্টা করব।")
            return
        }

        if (command.contains("পুরুষ কণ্ঠ")) {
            currentVoice = "male"
            changeVoice()
            speak("ঠিক আছে, এখন পুরুষ কণ্ঠে কথা বলার চেষ্টা করব।")
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

        for ((name, appPackage) in apps) {
            if (command.contains(name)) {
                openApp(appPackage)
                return
            }
        }

        askGemini(original)
    }

    private fun changeVoice() {
        if (!ttsReady) return

        try {
            val voices = tts.voices ?: return

            val matchingVoice = voices.firstOrNull {
                val name = it.name.lowercase(Locale.ROOT)

                if (currentVoice == "female") {
                    name.contains("female") ||
                    name.contains("woman")
                } else {
                    name.contains("male") ||
                    name.contains("man")
                }
            }

            if (matchingVoice != null) {
                tts.voice = matchingVoice
            }

        } catch (_: Exception) {}
    }

    private fun openApp(appPackage: String) {
        try {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(appPackage)

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

    // Gemini AI-এর সঙ্গে সংযোগ
    private fun askGemini(prompt: String) {
        runOnUiThread {
            statusText.text = "Gemini-এর সঙ্গে সংযোগ হচ্ছে..."
        }

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(workerUrl)

                connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 45000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val instruction = """
                    তুমি BS AI Assistant।
                    ব্যবহারকারীর প্রশ্ন মনোযোগ দিয়ে বুঝবে।
                    প্রশ্ন অনুযায়ী সঠিক, পরিষ্কার এবং প্রাসঙ্গিক উত্তর দেবে।
                    ব্যবহারকারী বাংলায় প্রশ্ন করলে সহজ ও স্বাভাবিক বাংলায় উত্তর দেবে।
                    ইংরেজিতে প্রশ্ন করলে ইংরেজিতে উত্তর দিতে পারো।
                    অপ্রয়োজনীয় কথা বলবে না।
                    কোনো তথ্য নিশ্চিত না হলে অনুমান করে সত্য বলে জানাবে না।
                    ব্যবহারকারী যা জানতে চেয়েছে, সেটির সম্পূর্ণ উত্তর দেওয়ার চেষ্টা করবে।

                    ব্যবহারকারীর প্রশ্ন:
                    $prompt
                """.trimIndent()

                val json = JSONObject()
                json.put("prompt", instruction)

                connection.outputStream.use {
                    it.write(
                        json.toString().toByteArray(Charsets.UTF_8)
                    )
                    it.flush()
                }

                val responseCode = connection.responseCode

                val responseText =
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader(
                            Charsets.UTF_8
                        ).use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader(
                            Charsets.UTF_8
                        )?.use { it.readText() } ?: ""
                    }

                if (responseCode !in 200..299) {
                    val detail = extractServerError(responseText)

                    throw Exception(
                        "HTTP $responseCode: $detail"
                    )
                }

                val responseJson = JSONObject(responseText)
                val reply = responseJson.optString("reply").trim()

                if (reply.isBlank()) {
                    throw Exception(
                        "সার্ভার থেকে উত্তর পাওয়া যায়নি: " +
                        responseText.take(250)
                    )
                }

                runOnUiThread {
                    speak(reply)
                }

            } catch (e: Exception) {
                val errorMessage =
                    e.message ?: "অজানা নেটওয়ার্ক সমস্যা"

                runOnUiThread {
                    speak(
                        "দুঃখিত, Gemini-এর সঙ্গে সংযোগে সমস্যা হয়েছে। " +
                        "স্ক্রিনে বিস্তারিত দেখুন।"
                    )

                    statusText.text =
                        "ত্রুটি: ${errorMessage.take(180)}"

                    Toast.makeText(
                        this,
                        errorMessage.take(200),
                        Toast.LENGTH_LONG
                    ).show()
                }

            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun extractServerError(body: String): String {
        if (body.isBlank()) {
            return "সার্ভার কোনো বিস্তারিত জানায়নি"
        }

        return try {
            val json = JSONObject(body)
            val error = json.opt("error")

            when (error) {
                is JSONObject -> error.optString(
                    "message",
                    error.toString()
                )

                null -> body

                else -> error.toString()
            }.take(250)

        } catch (_: Exception) {
            body.take(250)
        }
    }

    override fun onDestroy() {
        listeningEnabled = false
        isRecognizing = false

        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {}

        super.onDestroy()
    }

    // AI Orb
    class AIOrbView(context: android.content.Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.27f

            paint.style = Paint.Style.FILL

            paint.shader = android.graphics.RadialGradient(
                cx,
                cy,
                radius,
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

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.72f,
                paint
            )

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.12f,
                paint
            )
        }
    }
}
