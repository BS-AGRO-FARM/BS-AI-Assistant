
package com.bsai.assistant

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var input: EditText

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var foreground = false

    private val bg = Color.rgb(10, 12, 30)
    private val white = Color.WHITE
    private val purple = Color.rgb(135, 75, 255)
    private val blue = Color.rgb(35, 145, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(25), dp(22), dp(30))
        }

        scroll.addView(page)
        root.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val logo = TextView(this).apply {
            text = "✦"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = gradient(purple, blue, 18)
        }

        header.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))

        val heading = TextView(this).apply {
            text = "  BS AI ASSISTANT"
            textSize = 19f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }

        header.addView(heading)
        page.addView(header)

        page.addView(space(30))
        page.addView(label("আপনাকে স্বাগতম!", 27f, white, true))
        page.addView(label("আমি আপনার ব্যক্তিগত AI সহকারী",
            15f, Color.LTGRAY, false))

        page.addView(space(35))

        val micCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(25), dp(18), dp(25))
            background = rounded(Color.rgb(23, 25, 52), 28)
        }

        micCard.addView(label("আপনার কথা শুনছি", 19f, white, true))
        micCard.addView(space(22))

        val mic = TextView(this).apply {
            text = "🎙️"
            textSize = 43f
            gravity = Gravity.CENTER
            background = ovalGradient()
            elevation = dp(12).toFloat()
            setOnClickListener { startVoice() }
        }

        micCard.addView(mic, LinearLayout.LayoutParams(dp(112), dp(112)))
        micCard.addView(space(18))

        micCard.addView(label(
            "বলুন: হ্যালো BS, তারপর আপনার নির্দেশ দিন",
            14f, Color.LTGRAY, false
        ))

        status = label("আমি প্রস্তুত আছি 😊", 15f,
            Color.rgb(150, 190, 255), true)

        micCard.addView(space(15))
        micCard.addView(status)

        page.addView(micCard, LinearLayout.LayoutParams(-1, -2))

        page.addView(space(30))
        page.addView(label("⚡ দ্রুত অ্যাপ খুলুন", 19f, white, true))
        page.addView(space(14))

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        row1.addView(appCard("▶", "YouTube") {
            openApp("com.google.android.youtube", "https://m.youtube.com")
        }, weight())

        row1.addView(appCard("f", "Facebook") {
            openApp("com.facebook.katana", "https://m.facebook.com")
        }, weight())

        page.addView(row1)
        page.addView(space(12))

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        row2.addView(appCard("📷", "Camera") {
            try {
                startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            } catch (e: Exception) {
                say("ক্যামেরা খোলা যাচ্ছে না")
            }
        }, weight())

        row2.addView(appCard("🌐", "Chrome") {
            openApp("com.android.chrome", "https://www.google.com")
        }, weight())

        page.addView(row2)
        page.addView(space(28))

        page.addView(label("💬 AI Chat", 20f, white, true))
        page.addView(space(12))

        val chatBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(27, 29, 55), 18)
        }

        input = EditText(this).apply {
            hint = "এখানে লিখুন..."
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            textSize = 15f
            setSingleLine(true)
            background = null
            setPadding(dp(8), dp(8), dp(5), dp(8))
        }

        chatBox.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        val send = TextView(this).apply {
            text = "➤"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = gradient(purple, blue, 15)
            setOnClickListener { sendMessage() }
        }

        chatBox.addView(send, LinearLayout.LayoutParams(dp(48), dp(45)))
        page.addView(chatBox)

        page.addView(space(25))
        page.addView(label("BS AI ASSISTANT • VERSION 1.0",
            12f, Color.GRAY, false).apply {
            gravity = Gravity.CENTER
        })
    }

    override fun onResume() {
        super.onResume()
        foreground = true

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            handler.postDelayed({ startVoice() }, 700)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    override fun onPause() {
        foreground = false
        handler.removeCallbacksAndMessages(null)
        listening = false

        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null

        super.onPause()
    }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }
    }

    private fun weight(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(100), 1f).apply {
            marginEnd = dp(7)
        }
    }

    private fun appCard(
        emoji: String,
        title: String,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(27, 29, 57), 20)
            setPadding(dp(8), dp(15), dp(8), dp(15))

            addView(label(emoji, 27f, white, true))
            addView(space(7))
            addView(label(title, 15f, white, true))
            setOnClickListener { action() }
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(65, 65, 110))
        }
    }

    private fun gradient(
        first: Int,
        second: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(first, second)
        ).apply {
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun ovalGradient(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(purple, blue)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(4), Color.rgb(75, 80, 160))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun startVoice() {
        if (!foreground || listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "এই ফোনে Voice Recognition পাওয়া যাচ্ছে না"
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)

            recognizer?.setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "শুনছি... বলুন হ্যালো BS"
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text = "আপনার কথা বুঝছি..."
                }

                override fun onError(error: Int) {
                    listening = false
                    restartListening()
                }

                override fun onResults(results: Bundle?) {
                    listening = false

                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val command = words?.firstOrNull() ?: ""

                    if (command.isNotBlank()) {
                        status.text = "আপনি বলেছেন: $command"
                        handleCommand(command)
                    }

                    restartListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            listening = true
            recognizer?.startListening(intent)

        } catch (e: Exception) {
            listening = false
            status.text = "ভয়েস চালু করা যায়নি"
            restartListening()
        }
    }

    private fun restartListening() {
        handler.postDelayed({
            if (foreground && !listening) {
                startVoice()
            }
        }, 1200)
    }

    private fun handleCommand(command: String) {
        var text = command.lowercase(Locale.ROOT)
            .replace(",", " ")
            .replace(".", " ")
            .trim()

        val wakeWords = listOf(
            "হ্যালো বিএস",
            "হ্যালো বি এস",
            "হ্যালো বিস",
            "hello bs",
            "হ্যালো বাস"
        )

        var wakeFound = false

        for (wake in wakeWords) {
            if (text.contains(wake)) {
                text = text.replace(wake, " ").trim()
                wakeFound = true
            }
        }

        if (!wakeFound) {
            status.text = "নির্দেশ দিতে আগে বলুন: হ্যালো BS"
            return
        }

        if (text.isBlank()) {
            status.text = "জি, বলুন। আমি শুনছি।"
            say("জি, বলুন। আমি শুনছি।")
            return
        }

        when {
            text.contains("ক্যামেরা") || text.contains("camera") -> {
                say("ক্যামেরা খুলছি")
                try {
                    startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                } catch (e: Exception) {
                    status.text = "ক্যামেরা খোলা যাচ্ছে না"
                }
            }

            text.contains("গুগল") && text.contains("সার্চ") -> {
                val query = text
                    .replace("গুগল", "")
                    .replace("সার্চ", "")
                    .replace("কর", "")
                    .trim()

                startActivity(Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://www.google.com/search?q=" +
                            android.net.Uri.encode(query)
                    )
                ))
            }

            else -> openAnyApp(text)
        }
    }

    private fun openAnyApp(command: String) {
        val cleaned = command
            .replace("ওপেন", "")
            .replace("খোলো", "")
            .replace("খুলে দাও", "")
            .replace("চালু কর", "")
            .replace("চালু করো", "")
            .replace("খুলে দাও", "")
            .replace("খুলুন", "")
            .replace("খুলে", "")
            .replace("করো", "")
            .replace("কর", "")
            .replace("open", "")
            .replace("launch", "")
            .replace("please", "")
            .trim()

        val aliases = mapOf(
            "ফেসবুক" to "facebook",
            "হোয়াটসঅ্যাপ" to "whatsapp",
            "হোয়াটসঅ্যাপ" to "whatsapp",
            "টিকটক" to "tiktok",
            "ইউটিউব" to "youtube",
            "প্লে স্টোর" to "play store",
            "গ্যালারি" to "gallery",
            "ক্রোম" to "chrome",
            "মেসেঞ্জার" to "messenger",
            "ইনস্টাগ্রাম" to "instagram",
            "সেটিংস" to "settings"
        )

        val searchName = aliases[cleaned] ?: cleaned

        try {
            val apps = packageManager.getInstalledApplications(0)

            val match = apps.firstOrNull { app ->
                val launchIntent =
                    packageManager.getLaunchIntentForPackage(app.packageName)

                val appName = packageManager
                    .getApplicationLabel(app)
                    .toString()
                    .lowercase(Locale.ROOT)

                launchIntent != null &&
                    (appName == searchName ||
                    appName.contains(searchName))
            }

            if (match != null) {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage(match.packageName)

                if (launchIntent != null) {
                    say("${packageManager.getApplicationLabel(match)} খুলছি")
                    startActivity(launchIntent)
                    return
                }
            }

            status.text = "$cleaned অ্যাপটি খুঁজে পাওয়া যায়নি"
            say("$cleaned অ্যাপটি খুঁজে পাওয়া যায়নি")

        } catch (e: Exception) {
            status.text = "অ্যাপ খোলা যাচ্ছে না"
        }
    }

    private fun openApp(packageName: String, webAddress: String) {
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)

            if (launch != null) {
                startActivity(launch)
            } else {
                startActivity(Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(webAddress)
                ))
            }
        } catch (e: Exception) {
            status.text = "অ্যাপটি খোলা যাচ্ছে না"
        }
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return

        status.text = "আপনার মেসেজ: $message"
        say("আপনার মেসেজ পেয়েছি। AI Chat সংযোগ পরবর্তী ধাপে যোগ করব।")
        input.text.clear()
    }

    private fun say(message: String) {
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "bs_ai")
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale("bn", "BD")
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

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startVoice()
        } else {
            status.text = "Microphone অনুমতি দিন"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()

        if (::tts.isInitialized) {
            tts.shutdown()
        }

        super.onDestroy()
    }
}
