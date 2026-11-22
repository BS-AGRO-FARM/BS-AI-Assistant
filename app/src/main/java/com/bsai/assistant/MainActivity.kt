
package com.bsai.assistant

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
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
    private var ready = false
    private var voiceType = "female"
    private lateinit var orb: AIOrbView

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra("command") ?: return
            handleCommand(command)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(8, 13, 32)
        window.navigationBarColor = Color.rgb(8, 13, 32)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 8, 14, 12)
            setBackgroundColor(Color.rgb(8, 13, 32))
        }

        val title = TextView(this).apply {
            text = "BS AI ASSISTANT"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 48
        ))

        orb = AIOrbView(this)

        root.addView(
            orb,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val plus = makeButton("+", 42)
        plus.setOnClickListener { showAttachmentMenu() }
        bottom.addView(plus)

        val message = EditText(this).apply {
            hint = "মেসেজ লিখুন..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            setPadding(14, 0, 10, 0)
            background = roundedBackground(
                Color.rgb(32, 42, 76), 35
            )
        }

        val messageParams = LinearLayout.LayoutParams(
            0, 48, 1f
        ).apply {
            setMargins(8, 0, 8, 0)
        }
        bottom.addView(message, messageParams)

        val mic = makeButton("🎙", 46)
        mic.setOnClickListener {
            startVoiceService()
            Toast.makeText(
                this,
                "ভয়েস অ্যাসিস্ট্যান্ট চালু করার চেষ্টা করা হচ্ছে",
                Toast.LENGTH_SHORT
            ).show()
        }

        bottom.addView(mic)
        root.addView(bottom)

        setContentView(root)

        val filter = IntentFilter(VoiceService.VOICE_COMMAND)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                voiceReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(voiceReceiver, filter)
        }

        tts = TextToSpeech(this, this)

        requestVoicePermission()
    }

    private fun requestVoicePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO
            )

            if (Build.VERSION.SDK_INT >= 33) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            requestPermissions(
                permissions.toTypedArray(), 100
            )
        } else {
            startVoiceService()
        }
    }

    private fun startVoiceService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        try {
            val intent = Intent(this, VoiceService::class.java)

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "ভয়েস সার্ভিস চালু হয়নি",
                Toast.LENGTH_LONG
            ).show()
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
            "🖼 ছবি যোগ করুন",
            "📕 PDF যোগ করুন",
            "📁 ফাইল যোগ করুন"
        )

        android.app.AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                val type = when (which) {
                    0 -> "image/*"
                    1 -> "application/pdf"
                    else -> "*/*"
                }

                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    this.type = type
                }

                startActivityForResult(intent, 201)
            }
            .show()
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
                            sendBroadcast(
                                Intent(VoiceService.ACTION_RESUME)
                                    .setPackage(packageName)
                            )
                        }
                    }

                    override fun onError(id: String?) {
                        runOnUiThread {
                            orb.setState(false, false)
                            sendBroadcast(
                                Intent(VoiceService.ACTION_RESUME)
                                    .setPackage(packageName)
                            )
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
            val name = it.name.lowercase(Locale.ROOT)

            if (voiceType == "male") {
                name.contains("male") && !name.contains("female")
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

        sendBroadcast(
            Intent(VoiceService.ACTION_PAUSE)
                .setPackage(packageName)
        )

        orb.setState(false, true)

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "BS_REPLY"
        )
    }

    private fun handleCommand(original: String) {
        val command = original.lowercase(Locale.ROOT)

        if (command.contains("মহিলা কণ্ঠ") ||
            command.contains("female voice")) {
            voiceType = "female"
            applyVoice()
            return
        }

        if (command.contains("পুরুষ কণ্ঠ") ||
            command.contains("male voice")) {
            voiceType = "male"
            applyVoice()
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

        val apps = listOf(
            "facebook" to "com.facebook.katana",
            "ফেসবুক" to "com.facebook.katana",
            "youtube" to "com.google.android.youtube",
            "ইউটিউব" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "ইনস্টাগ্রাম" to "com.instagram.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "টিকটক" to "com.zhiliaoapp.musically"
        )

        val matched = apps
            .map { Pair(it, command.lastIndexOf(it.first)) }
            .filter { it.second >= 0 }
            .maxByOrNull { it.second }

        if (matched != null) {
            openApp(matched.first.second)
            return
        }

        speak(
            "Gemini AI সংযোগ চালু হলে আমি আপনার প্রশ্নের উত্তর দিতে পারব।"
        )
    }

    private fun openApp(packageName: String) {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } catch (e: Exception) {
                speak("অ্যাপটি খোলা যায়নি।")
            }
        } else {
            speak("এই অ্যাপটি ফোনে ইনস্টল করা নেই।")
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
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startVoiceService()
        }
    }

    override fun onPause() {
        super.onPause()
        // VoiceService চালু থাকবে।
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(voiceReceiver)
        } catch (_: Exception) {}

        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

class AIOrbView(context: Context) : View(context) {

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        // ছোট 3D Orb
        val base = min(width, height) * 0.20f

        angle += if (active || speaking) 2.2f else 0.65f

        val pulse = sin(Math.toRadians(angle.toDouble())).toFloat()
        val power = if (active || speaking) {
            1f + level * 0.12f
        } else {
            1f + pulse * 0.025f
        }

        val radius = base * power

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius * 1.6f,
            intArrayOf(
                Color.argb(85, 100, 60, 255),
                Color.argb(35, 20, 200, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius * 1.6f, paint)
        paint.shader = null

        canvas.save()
        canvas.rotate(angle, cx, cy)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)

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

        canvas.drawOval(
            RectF(
                cx - radius * 1.35f,
                cy - radius * 0.55f,
                cx + radius * 1.35f,
                cy + radius * 0.55f
            ),
            paint
        )

        paint.shader = null
        paint.color = Color.CYAN
        canvas.rotate(70f, cx, cy)

        canvas.drawOval(
            RectF(
                cx - radius * 1.25f,
                cy - radius * 0.48f,
                cx + radius * 1.25f,
                cy + radius * 0.48f
            ),
            paint
        )

        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - radius * 0.34f,
            cy - radius * 0.40f,
            radius * 1.55f,
            intArrayOf(
                Color.WHITE,
                Color.CYAN,
                Color.BLUE,
                Color.rgb(120, 40, 230),
                Color.MAGENTA,
                Color.rgb(15, 18, 65)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

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

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.strokeCap = Paint.Cap.ROUND

        paint.shader = LinearGradient(
            cx - radius,
            cy,
            cx + radius,
            cy,
            intArrayOf(Color.CYAN, Color.WHITE, Color.MAGENTA),
            null,
            Shader.TileMode.CLAMP
        )

        val path = Path()

        for (i in 0..80) {
            val x = cx - radius * 0.75f +
                radius * 1.5f * i / 80f

            val wave = sin(i * 0.25 + angle * 0.08).toFloat()

            val y = cy + wave * dp(9f) *
                (1f + level * 1.5f)

            if (i == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        canvas.drawPath(path, paint)
        paint.shader = null

        postInvalidateDelayed(30)
    }
}
