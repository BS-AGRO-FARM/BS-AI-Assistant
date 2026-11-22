package com.bsai.assistant

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var result: TextView
    private lateinit var input: EditText
    private var speech: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(30, 30, 30, 30)
        layout.setBackgroundColor(Color.rgb(15, 23, 42))

        val title = TextView(this)
        title.text = "BS AI Assistant"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        result = TextView(this)
        result.text = "আসসালামু আলাইকুম! আমি আপনার AI Assistant।"
        result.textSize = 19f
        result.setTextColor(Color.WHITE)
        result.gravity = Gravity.CENTER
        result.setPadding(10, 35, 10, 35)

        input = EditText(this)
        input.hint = "এখানে লিখুন..."
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.LTGRAY)

        val send = Button(this)
        send.text = "Send"

        val mic = Button(this)
        mic.text = "🎙️ কথা বলুন"

        layout.addView(title)
        layout.addView(result)
        layout.addView(input)
        layout.addView(send)
        layout.addView(mic)

        setContentView(layout)

        send.setOnClickListener {
            processCommand(input.text.toString())
        }

        mic.setOnClickListener {
            startListening()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            result.text = "এই ফোনে Voice Recognition পাওয়া যাচ্ছে না।"
            return
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO), 100
            )
            return
        }

        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")

        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                result.text = "শুনছি..."
            }

            override fun onResults(data: Bundle?) {
                val words = data?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val command = words?.firstOrNull() ?: ""
                input.setText(command)
                processCommand(command)
            }

            override fun onError(error: Int) {
                result.text = "আবার চেষ্টা করুন।"
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        speech?.startListening(intent)
    }

    private fun processCommand(command: String) {
        val text = command.lowercase(Locale.ROOT)
        result.text = "আপনি বলেছেন: $command"

        when {
            text.contains("youtube") ->
                openApp("com.google.android.youtube")

            text.contains("facebook") ->
                openApp("com.facebook.katana")

            text.contains("chrome") ->
                openApp("com.android.chrome")

            text.contains("camera") || text.contains("ক্যামেরা") ->
                openCamera()

            else -> speak(
                "আপনার কথা বুঝেছি। এই কাজটি এখনো আমার শেখা হয়নি।"
            )
        }
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)

        if (intent != null) {
            result.text = "অ্যাপটি খুলছি..."
            startActivity(intent)
        } else {
            speak("অ্যাপটি ফোনে পাওয়া যায়নি।")
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            startActivity(intent)
        } catch (e: Exception) {
            speak("ক্যামেরা খুলতে পারছি না।")
        }
    }

    private fun speak(message: String) {
        result.text = message
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "BS_AI")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("bn", "BD")
        }
    }

    override fun onDestroy() {
        speech?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
