
package com.bsai.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private var ready = false
    private var voiceType = "female"
    private var listening = false
    private lateinit var orb: AIOrbView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(8, 13, 32)
        window.navigationBarColor = Color.rgb(8, 13, 32)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 18)
            setBackgroundColor(Color.rgb(8, 13, 32))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "BS AI ASSISTANT"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.08f
        }

        header.addView(title, LinearLayout.LayoutParams(0, 60, 1f))

        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        header.addView(settings, LinearLayout.LayoutParams(48, 60))

        root.addView(header)

        orb = AIOrbView(this)
        root.addView(
            orb,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        status = TextView(this).apply {
            text = ""
            setTextColor(Color.LTGRAY)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        root.addView(status)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val plus = makeButton("+", 44)
        plus.setOnClickListener { showAttachmentMenu() }
        bottom.addView(plus)

        val message = EditText(this).apply {
            hint = "মেসেজ লিখুন..."
            setHintTextColor(Color.rgb(170, 180, 210))
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            setPadding(18, 0, 12, 0)
            background = roundedBackground(
                Color.rgb(32, 42, 76), 35
            )
        }

        val messageParams = LinearLayout.LayoutParams(
            0, 50, 1f
        ).apply {
            setMargins(9, 0, 9, 0)
        }
        bottom.addView(message, messageParams)

        val mic = makeButton("🎙", 48)
        mic.setOnClickListener {
            if (!listening) startListening()
            else {
                speech?.cancel()
                listening = false
                orb.setState(false, false)
            }
        }
        bottom.addView(mic)

        root.addView(bottom)
        setContentView(root)

        tts = TextToSpeech(this, this)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    private fun makeButton(symbol: String, size: Int): TextView {
        return TextView(this).apply {
            text = symbol
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(
                Color.rgb(48, 68, 135), 100
            )
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun roundedBackground(
        color: Int, radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun showAttachmentMenu() {
        val options = arrayOf(
            "🖼   ছবি যোগ করুন",
            "📕   PDF যোগ করুন",
            "📁   ফাইল যোগ করুন"
        )

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFile("image/*", 201)
                    1 -> openFile("application/pdf", 202)
                    2 -> openFile("*/*", 203)
                }
            }
            .show()
    }

    private fun openFile(type: String, code: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
        }
        startActivityForResult(intent, code)
    }

    @Deprecated("Used for document selection")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data?.data != null) {
            Toast.makeText(
                this,
                "ফাইল নির্বাচন হয়েছে",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            ready = true
            tts?.language = Locale("bn", "BD")
            applyVoice()

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        runOnUiThread {
                            orb.setState(false, true)
                        }
                    }

                    override fun onDone(id: String?) {
                        runOnUiThread {
                            orb.setState(false, false)
                            startListening()
                        }
                    }

                    override fun onError(id: String?) {
                        runOnUiThread {
                            orb.setState(false, false)
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
                name.contains("male") &&
                    !name.contains("female")
            } else {
                name.contains("female")
            }
        }

        if (selected != null) {
            tts?.voice = selected
        }
    }

    private fun speak(text: String) {
        if (!ready) return
        orb.setState(false, true)
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "BS_REPLY"
        )
    }

    private fun startListening() {
        if (!ready || isFinishing) return
        if (tts?.isSpeaking == true) return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        try {
            speech?.destroy()
            speech = SpeechRecognizer.createSpeechRecognizer(this)

            speech?.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                        orb.setState(true, false)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        orb.setLevel(
                            ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        )
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        listening = false
                    }

                    override fun onError(error: Int) {
                        listening = false
                        orb.setState(false, false)
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

                    override fun onPartialResults(results: Bundle?) {}
                    override fun onEvent(type: Int, params: Bundle?) {}
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            )

            speech?.startListening(intent)

        } catch (e: Exception) {
            status.text = "মাইক্রোফোন চালু করা যায়নি"
        }
    }

    private fun handleCommand(original: String) {
        val command = original.lowercase(Locale.ROOT)

        if (command.contains("মহিলা কণ্ঠ") ||
            command.contains("female voice")) {
            voiceType = "female"
            applyVoice()
            startListening()
            return
        }

        if (command.contains("পুরুষ কণ্ঠ") ||
            command.contains("male voice")) {
            voiceType = "male"
            applyVoice()
            startListening()
            return
        }

        if (command.contains("তোমাকে কে তৈরি করেছে") ||
            command.contains("তোমাকে কে বানিয়েছে") ||
            command.contains("who created you") ||
            command.contains("who made you")) {
            if (command.contains("who")) {
                tts?.language = Locale.US
                speak("I was created by Basudeb Sarkar.")
            } else {
                tts?.language = Locale("bn", "BD")
                speak("আমাকে বাসুদেব সরকার তৈরি করেছেন।")
            }
            return
        }

        val appName = when {
            command.contains("facebook") ||
                command.contains("ফেসবুক") -> "facebook"
            command.contains("youtube") ||
                command.contains("ইউটিউব") -> "youtube"
            command.contains("whatsapp") ||
                command.contains("হোয়াটসঅ্যাপ") -> "whatsapp"
            command.contains("instagram") ||
                command.contains("ইনস্টাগ্রাম") -> "instagram"
            command.contains("tiktok") ||
                command.contains("টিকটক") -> "tiktok"
            else -> null
        }

        if (appName != null) {
            askPermissionToOpen(appName)
            return
        }

        speak(
            "Gemini AI সংযোগ চালু হলে আমি আপনার প্রশ্নের উত্তর দিতে পারব।"
        )
    }

    private fun askPermissionToOpen(appName: String) {
        val apps = packageManager.getInstalledApplications(0)

        val app = apps.firstOrNull {
            packageManager.getApplicationLabel(it)
                .toString().lowercase(Locale.ROOT)
                .contains(appName)
        }

        if (app == null) {
            speak("এই অ্যাপটি খুঁজে পাইনি।")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("আপনার অনুমতি দরকার")
            .setMessage("$appName খুলব কি?")
            .setPositiveButton("হ্যাঁ") { _, _ ->
                val launch = packageManager
                    .getLaunchIntentForPackage(app.packageName)
                if (launch != null) startActivity(launch)
            }
            .setNegativeButton("না", null)
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
            if (ready) speak("BS AI Assistant-এ আপনাকে স্বাগতম।")
        }
    }

    override fun onPause() {
        super.onPause()
        speech?.cancel()
        speech?.destroy()
        speech = null
        listening = false
    }

    override fun onDestroy() {
        speech?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

class AIOrbView(context: android.content.Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var angle = 0f
    private var level = 0f
    private var active = false
    private var speaking = false

    private fun dp(value: Float) =
        value * resources.displayMetrics.density

    fun setState(listening: Boolean, talking: Boolean) {
        active = listening
        speaking = talking
        invalidate()
    }

    fun setLevel(value: Float) {
        level = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.30f

        angle += if (active || speaking) 2.2f else 0.65f
        val pulse = sin(Math.toRadians(angle.toDouble())).toFloat()
        val power = if (active || speaking) {
            1f + level * 0.15f
        } else {
            1f + pulse * 0.025f
        }

        val radius = base * power

        // Outer glow
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius * 1.65f,
            intArrayOf(
                Color.argb(85, 100, 60, 255),
                Color.argb(35, 20, 200, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.65f, paint)
        paint.shader = null

        // Rotating colorful rings
        canvas.save()
        canvas.rotate(angle, cx, cy)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED,
                Color.YELLOW,
                Color.CYAN
            ),
            null
        )

        val oval = RectF(
            cx - radius * 1.32f,
            cy - radius * 0.55f,
            cx + radius * 1.32f,
            cy + radius * 0.55f
        )
        canvas.drawOval(oval, paint)
        paint.shader = null

        paint.strokeWidth = dp(2f)
        paint.color = Color.rgb(75, 220, 255)
        val oval2 = RectF(
            cx - radius * 1.27f,
            cy - radius * 0.48f,
            cx + radius * 1.27f,
            cy + radius * 0.48f
        )
        canvas.rotate(70f, cx, cy)
        canvas.drawOval(oval2, paint)
        canvas.restore()

        // 3D sphere
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - radius * 0.34f,
            cy - radius * 0.40f,
            radius * 1.55f,
            intArrayOf(
                Color.WHITE,
                Color.rgb(100, 245, 255),
                Color.rgb(40, 115, 255),
                Color.rgb(120, 40, 230),
                Color.rgb(235, 40, 180),
                Color.rgb(15, 18, 65)
            ),
            floatArrayOf(0f, 0.13f, 0.38f, 0.65f, 0.83f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        // Light reflection
        paint.color = Color.argb(100, 255, 255, 255)
        canvas.drawOval(
            RectF(
                cx - radius * 0.55f,
                cy - radius * 0.72f,
                cx + radius * 0.15f,
                cy - radius * 0.48f
            ),
            paint
        )

        // Moving AI waves
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.shader = LinearGradient(
            cx - radius,
            cy,
            cx + radius,
            cy,
            intArrayOf(
                Color.CYAN,
                Color.WHITE,
                Color.MAGENTA
            ),
            null,
            Shader.TileMode.CLAMP
        )

        val path = Path()
        for (i in 0..80) {
            val x = cx - radius * 0.75f +
                (radius * 1.5f * i / 80f)
            val wave = sin(
                i * 0.25 + angle * 0.08
            ).toFloat()
            val y = cy + wave *
                dp(12f) * (1f + level * 1.8f)

            if (i == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        canvas.drawPath(path, paint)
        paint.shader = null

        // Shine dots
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(
            cx + radius * 0.82f,
            cy - radius * 0.75f,
            dp(3f), paint
        )
        canvas.drawCircle(
            cx - radius * 0.85f,
            cy + radius * 0.55f,
            dp(2.5f), paint
        )

        postInvalidateDelayed(30)
    }
}
