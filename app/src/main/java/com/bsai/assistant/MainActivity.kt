
package com.bsai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.*
import android.view.ViewGroup
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private var ready = false
    private var listening = false
    private var voiceType = "female"
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(24, 30, 24, 30)
        layout.setBackgroundColor(Color.rgb(12, 18, 38))

        val title = TextView(this)
        title.text = "BS AI ASSISTANT"
        title.textSize = 27f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        title.setPadding(0, 20, 0, 25)
        layout.addView(title)

        status = TextView(this)
        status.text = "আপনার কথা শোনার প্রস্তুতি নিচ্ছে..."
        status.textSize = 17f
        status.setTextColor(Color.WHITE)
        status.gravity = Gravity.CENTER
        status.setPadding(0, 20, 0, 30)
        layout.addView(status)

        val male = Button(this)
        male.text = "পুরুষ কণ্ঠ"
        layout.addView(male)

        male.setOnClickListener {
            voiceType = "male"
            applyVoice()
            speak("আমি এখন পুরুষ কণ্ঠে কথা বলার চেষ্টা করব।")
        }

        val female = Button(this)
        female.text = "মহিলা কণ্ঠ"
        layout.addView(female)

        female.setOnClickListener {
            voiceType = "female"
            applyVoice()
            speak("আমি এখন মহিলা কণ্ঠে কথা বলার চেষ্টা করব।")
        }

        setContentView(layout)

        tts = TextToSpeech(this, this)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            ready = true
            tts?.language = Locale("bn", "BD")
            applyVoice()

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}

                    override fun onDone(id: String?) {
                        runOnUiThread {
                            startListening()
                        }
                    }

                    override fun onError(id: String?) {
                        runOnUiThread {
                            startListening()
                        }
                    }
                }
            )

            speak("BS AI Assistant-এ আপনাকে স্বাগতম।")
        }
    }

    private fun applyVoice() {
        val voices = tts?.voices ?: return

        val selected = voices.firstOrNull {
            val name = it.name.lowercase()
            if (voiceType == "male") {
                name.contains("male")
            } else {
                name.contains("female")
            }
        }

        if (selected != null) {
            tts?.voice = selected
        }
    }

    private fun speak(message: String) {
        if (!ready) return

        status.text = message
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "BS_SPEECH"
        )
    }

    private fun startListening() {
        if (!ready || isFinishing) return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        if (tts?.isSpeaking == true) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition পাওয়া যাচ্ছে না।"
            return
        }

        try {
            speech?.destroy()
            speech = SpeechRecognizer.createSpeechRecognizer(this)

            speech?.setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    status.text = "আমি শুনছি..."
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(error: Int) {
                    listening = false
                    if (!isFinishing && ready && tts?.isSpeaking != true) {
                        status.postDelayed({
                            startListening()
                        }, 1000)
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false

                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    if (!words.isNullOrEmpty()) {
                        handleCommand(words[0])
                    } else {
                        startListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )

            speech?.startListening(intent)

        } catch (e: Exception) {
            status.text = "মাইক্রোফোন চালু করা যায়নি।"
        }
    }

    private fun handleCommand(original: String) {
        val command = original.lowercase(Locale.ROOT).trim()

        if (command.contains("পুরুষ কণ্ঠ") ||
            command.contains("male voice")) {
            voiceType = "male"
            applyVoice()
            speak("পুরুষ কণ্ঠ নির্বাচন করা হয়েছে।")
            return
        }

        if (command.contains("মহিলা কণ্ঠ") ||
            command.contains("female voice")) {
            voiceType = "female"
            applyVoice()
            speak("মহিলা কণ্ঠ নির্বাচন করা হয়েছে।")
            return
        }

        if (command.contains("facebook") ||
            command.contains("ফেসবুক") ||
            command.contains("youtube") ||
            command.contains("ইউটিউব") ||
            command.contains("whatsapp") ||
            command.contains("হোয়াটসঅ্যাপ") ||
            command.contains("instagram") ||
            command.contains("ইনস্টাগ্রাম") ||
            command.contains("tiktok") ||
            command.contains("টিকটক")) {

            val appName = when {
                command.contains("facebook") || command.contains("ফেসবুক") ->
                    "facebook"
                command.contains("youtube") || command.contains("ইউটিউব") ->
                    "youtube"
                command.contains("whatsapp") || command.contains("হোয়াটসঅ্যাপ") ->
                    "whatsapp"
                command.contains("instagram") || command.contains("ইনস্টাগ্রাম") ->
                    "instagram"
                else -> "tiktok"
            }

            askPermissionToOpen(appName)
            return
        }

        speak("আপনার কথা শুনেছি। AI সংযোগ চালু হলে আমি প্রশ্নের উত্তর দিতে পারব।")
    }

    private fun askPermissionToOpen(appName: String) {
        val apps = packageManager.getInstalledApplications(0)

        val app = apps.firstOrNull {
            val label = packageManager.getApplicationLabel(it)
                .toString().lowercase(Locale.ROOT)

            label.contains(appName)
        }

        if (app == null) {
            speak("এই অ্যাপটি ফোনে খুঁজে পাইনি।")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("আপনার অনুমতি দরকার")
            .setMessage("$appName খুলব কি?")
            .setPositiveButton("হ্যাঁ") { _, _ ->
                val launchIntent =
                    packageManager.getLaunchIntentForPackage(app.packageName)

                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    speak("অ্যাপটি খোলা যাচ্ছে না।")
                }
            }
            .setNegativeButton("না") { _, _ ->
                speak("ঠিক আছে।")
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ready && tts?.isSpeaking != true) {
            startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        listening = false
        speech?.cancel()
        speech?.destroy()
        speech = null
    }

    override fun onDestroy() {
        speech?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
