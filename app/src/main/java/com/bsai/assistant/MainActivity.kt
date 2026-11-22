
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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val workerUrl =
        "https://bsaiassistant.bashudebsarkar61.workers.dev"

    private lateinit var tts: TextToSpeech
    private lateinit var statusText: TextView
    private lateinit var answerText: TextView
    private lateinit var inputText: EditText
    private lateinit var micButton: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var listeningEnabled = false
    private var isSpeaking = false
    private var isRecognizing = false
    private var ttsReady = false

    private val handler = Handler(Looper.getMainLooper())

    private val restartListening = Runnable {
        if (listeningEnabled && !isSpeaking && !isRecognizing) {
            startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(8, 15, 40)
        window.navigationBarColor = Color.rgb(8, 15, 40)

        createScreen()

        tts = TextToSpeech(this, this)

        setupSpeechRecognizer()

        speak("BS AI Assistant-এ আপনাকে স্বাগতম।")
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(5, 10, 28))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 22, 18, 20)
            background = roundedBackground(
                Color.rgb(15, 27, 62),
                28
            )
        }

        val title = TextView(this).apply {
            text = "BS AI ASSISTANT"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val orb = AIOrbView(this)

        statusText = TextView(this).apply {
            text = "আপনার প্রশ্ন লিখুন অথবা মাইক্রোফোন চাপুন"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(5, 12, 5, 15)
        }

        answerText = TextView(this).apply {
            text = "আমি আপনাকে সাহায্য করতে প্রস্তুত।"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 12)
        }

        val scroll = ScrollView(this)
        scroll.addView(answerText)

        inputText = EditText(this).apply {
            hint = "এখানে আপনার প্রশ্ন লিখুন..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(15, 12, 15, 12)
            background = roundedBackground(
                Color.rgb(34, 48, 82),
                18
            )
        }

        val sendButton = Button(this).apply {
            text = "প্রশ্ন পাঠান"
            setOnClickListener {
                val question = inputText.text.toString().trim()
                if (question.isNotEmpty()) {
                    inputText.setText("")
                    handleCommand(question)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "আগে একটি প্রশ্ন লিখুন",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        micButton = Button(this).apply {
            text = "🎤 কথা বলুন"
            setOnClickListener {
                if (listeningEnabled) {
                    stopVoiceMode()
                } else {
                    startVoiceMode()
                }
            }
        }

        panel.addView(title)
        panel.addView(
            orb,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                155
            )
        )
        panel.addView(statusText)

        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                100
            )
        )

        panel.addView(
            inputText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10
            }
        )

        panel.addView(sendButton)
        panel.addView(micButton)

        root.addView(
            panel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var result = tts.setLanguage(Locale("bn", "BD"))

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                result = tts.setLanguage(Locale("bn", "IN"))
            }

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.setLanguage(Locale("bn"))
            }

            tts.setSpeechRate(0.95f)
            ttsReady = true

            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        handler.postDelayed(restartListening, 10000)
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        handler.postDelayed(restartListening, 2000)
                    }
                }
            )
        }
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            answerText.text = message
            return
        }

        isSpeaking = true
        tts.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "BS_AI_REPLY"
        )
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "এই ফোনে Voice Recognition পাওয়া যাচ্ছে না"
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "আমি শুনছি..."
                    isRecognizing = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText.text = "আপনার কথা বুঝছি..."
                }

                override fun onError(error: Int) {
                    isRecognizing = false

                    if (listeningEnabled) {
                        statusText.text = "আবার শুনতে প্রস্তুত হচ্ছি..."
                        handler.removeCallbacks(restartListening)
                        handler.postDelayed(restartListening, 2000)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isRecognizing = false

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val question = matches?.firstOrNull()

                    if (!question.isNullOrBlank()) {
                        handleCommand(question)
                    } else if (listeningEnabled) {
                        handler.postDelayed(restartListening, 2000)
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun startVoiceMode() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                101
            )
            return
        }

        listeningEnabled = true
        micButton.text = "🔴 কথা বলা বন্ধ"
        startListening()
    }

    private fun stopVoiceMode() {
        listeningEnabled = false
        isRecognizing = false
        handler.removeCallbacks(restartListening)
        speechRecognizer?.cancel()
        micButton.text = "🎤 কথা বলুন"
        statusText.text = "ভয়েস বন্ধ আছে"
    }

    private fun startListening() {
        if (!listeningEnabled || isSpeaking || isRecognizing) {
            return
        }

        if (speechRecognizer == null) {
            setupSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
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

        try {
            isRecognizing = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isRecognizing = false
            statusText.text = "মাইক্রোফোন চালু করা যায়নি"
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

        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceMode()
        } else {
            Toast.makeText(
                this,
                "মাইক্রোফোন ব্যবহারের অনুমতি প্রয়োজন",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleCommand(original: String) {
        val command = original.lowercase(Locale.getDefault())

        answerText.text = original

        if (command.contains("তোমাকে কে তৈরি") ||
            command.contains("তোমার নির্মাতা") ||
            command.contains("কে বানিয়েছে")
        ) {
            reply("আমাকে বাসুদেব সরকার তৈরি করেছেন।")
            return
        }

        if (command.contains("নিচে স্ক্রল")) {
            sendPhoneCommand("scroll_down")
            reply("নিচে স্ক্রল করছি।")
            return
        }

        if (command.contains("উপরে স্ক্রল")) {
            sendPhoneCommand("scroll_up")
            reply("উপরে স্ক্রল করছি।")
            return
        }

        if (command.contains("পিছনে যাও")) {
            sendPhoneCommand("back")
            reply("পিছনে যাচ্ছি।")
            return
        }

        if (command.contains("হোমে যাও")) {
            sendPhoneCommand("home")
            reply("হোমে যাচ্ছি।")
            return
        }

        if (command.contains("মহিলা কণ্ঠ")) {
            changeVoice(true)
            return
        }

        if (command.contains("পুরুষ কণ্ঠ")) {
            changeVoice(false)
            return
        }

        if (openApp(command)) {
            return
        }

        askGemini(original)
    }

    private fun reply(message: String) {
        answerText.text = message
        statusText.text = "উত্তর দেওয়া হচ্ছে..."
        speak(message)
    }

    private fun sendPhoneCommand(command: String) {
        try {
            val intent = Intent(
                PhoneControlService.ACTION_COMMAND
            ).apply {
                setPackage(packageName)
                putExtra("command", command)
            }

            sendBroadcast(intent)
        } catch (e: Exception) {
            statusText.text = "ফোন কন্ট্রোল কমান্ড পাঠানো যায়নি"
        }
    }

    private fun openApp(command: String): Boolean {
        val apps = listOf(
            Triple("facebook", "com.facebook.katana", "Facebook"),
            Triple("টিকটক", "com.zhiliaoapp.musically", "TikTok"),
            Triple("tiktok", "com.zhiliaoapp.musically", "TikTok"),
            Triple("ইউটিউব", "com.google.android.youtube", "YouTube"),
            Triple("youtube", "com.google.android.youtube", "YouTube"),
            Triple("হোয়াটসঅ্যাপ", "com.whatsapp", "WhatsApp"),
            Triple("whatsapp", "com.whatsapp", "WhatsApp"),
            Triple("ইনস্টাগ্রাম", "com.instagram.android", "Instagram"),
            Triple("instagram", "com.instagram.android", "Instagram")
        )

        for ((keyword, packageName, appName) in apps) {
            if (command.contains(keyword)) {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    startActivity(launchIntent)
                    reply("$appName খুলছি।")
                } else {
                    reply("$appName অ্যাপটি ফোনে ইনস্টল করা নেই।")
                }

                return true
            }
        }

        return false
    }

    private fun changeVoice(female: Boolean) {
        if (!ttsReady) return

        val voices = tts.voices ?: return

        val selected = voices.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)

            if (female) {
                name.contains("female") || name.contains("woman")
            } else {
                name.contains("male") || name.contains("man")
            }
        }

        if (selected != null) {
            tts.voice = selected
            reply(
                if (female) "মহিলা কণ্ঠ নির্বাচন করা হয়েছে।"
                else "পুরুষ কণ্ঠ নির্বাচন করা হয়েছে।"
            )
        } else {
            reply("এই ফোনের ভয়েস ইঞ্জিনে এই কণ্ঠ পাওয়া যায়নি।")
        }
    }

    private fun askGemini(prompt: String) {
        statusText.text = "Gemini AI-এর সঙ্গে সংযোগ হচ্ছে..."
        answerText.text = "একটু অপেক্ষা করুন..."

        thread {
            var connection: HttpURLConnection? = null

            try {
                connection = (URL(workerUrl).openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 45000
                    doOutput = true
                    doInput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                    setRequestProperty("Accept", "application/json")
                }

                val requestBody = JSONObject().apply {
                    put("prompt", prompt)
                }.toString()

                connection.outputStream.use { output ->
                    output.write(requestBody.toByteArray(Charsets.UTF_8))
                    output.flush()
                }

                val responseCode = connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val responseText = responseStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: "সার্ভার থেকে কোনো বিস্তারিত উত্তর পাওয়া যায়নি।"

                val json = try {
                    JSONObject(responseText)
                } catch (e: Exception) {
                    JSONObject().put("error", responseText)
                }

                if (responseCode !in 200..299) {
                    val detail = json.optString(
                        "error",
                        "অজানা সার্ভার সমস্যা"
                    )

                    throw Exception(
                        "HTTP $responseCode: $detail"
                    )
                }

                val answer = json.optString("reply", "").trim()

                if (answer.isEmpty()) {
                    throw Exception(
                        "সার্ভার থেকে AI-এর উত্তর পাওয়া যায়নি।"
                    )
                }

                runOnUiThread {
                    answerText.text = answer
                    statusText.text = "উত্তর পাওয়া গেছে"
                    speak(answer)
                }

            } catch (e: Exception) {
                val errorMessage =
                    e.message ?: "অজানা সমস্যা হয়েছে"

                runOnUiThread {
                    answerText.text = errorMessage
                    statusText.text = "AI সংযোগে সমস্যা হয়েছে"

                    Toast.makeText(
                        this,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()

                    speak("দুঃখিত, উত্তর পাওয়া যায়নি।")
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        listeningEnabled = false
        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.destroy()
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}

class AIOrbView(context: android.content.Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.35f

        paint.shader = android.graphics.RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
              
