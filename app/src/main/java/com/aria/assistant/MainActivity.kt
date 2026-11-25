
package com.aria.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var response: TextView
    private var recognizer: SpeechRecognizer? = null
    private var english = false
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 40, 24, 25)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(1, 5, 18),
                    Color.rgb(3, 18, 40),
                    Color.rgb(0, 3, 13)
                )
            )
        }

        val title = TextView(this).apply {
            text = "A.R.I.A."
            textSize = 34f
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.rgb(0, 220, 255))
        }

        val subtitle = TextView(this).apply {
            text = "ADVANCED INTELLIGENCE SYSTEM"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setTextColor(Color.LTGRAY)
        }

        val hologram = HologramView(this)

        status = TextView(this).apply {
            text = "SYSTEM ONLINE"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 255, 220))
        }

        response = TextView(this).apply {
            text = "Good evening. I am ARIA.\nAwaiting your command."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(10, 20, 10, 20)
        }

        val voice = createButton("◉  ACTIVATE VOICE")
        voice.setOnClickListener { listen() }

        val language = createButton("LANGUAGE  |  বাংলা / ENGLISH")
        language.setOnClickListener {
            english = !english
            status.text = if (english) "ENGLISH MODE ACTIVE" else "বাংলা মোড চালু"
            speak(if (english) "English mode activated."
                  else "বাংলা মোড চালু হয়েছে।")
        }

        root.addView(title)
        root.addView(subtitle)

        root.addView(hologram, LinearLayout.LayoutParams(
            -1, 310
        ))

        root.addView(status)
        root.addView(response)
        root.addView(voice)
        root.addView(language)

        setContentView(root)

        if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.rgb(0, 230, 255))
            background = GradientDrawable().apply {
                setColor(Color.rgb(4, 30, 55))
                cornerRadius = 20f
                setStroke(2, Color.rgb(0, 180, 230))
            }
            layoutParams = LinearLayout.LayoutParams(
                -1, 58
            ).apply {
                topMargin = 12
            }
        }
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "VOICE SYSTEM UNAVAILABLE"
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "LISTENING..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text = "PROCESSING..."
                }

                override fun onError(error: Int) {
                    status.text = "VOICE ERROR - TRY AGAIN"
                }

                override fun onResults(results: Bundle?) {
                    val command = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull().orEmpty()

                    response.text = command
                    processCommand(command)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
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
                if (english) "en-US" else "bn-BD"
            )
        }

        recognizer?.startListening(intent)
    }

    private fun processCommand(command: String) {
        val q = command.lowercase(Locale.ROOT)

        val answer = when {
            q.contains("তোমার নাম") ||
            q.contains("your name") ->
                if (english) "My name is ARIA."
                else "আমার নাম ARIA।"

            q.contains("hello") ||
            q.contains("হ্যালো") ||
            q.contains("হাই") ->
                if (english) "Hello! How can I help you?"
                else "হ্যালো! আমি তোমাকে কীভাবে সাহায্য করতে পারি?"

            q.contains("বাংলা") ->
                "ঠিক আছে, আমি বাংলায় কথা বলব।"

            q.contains("ইংলিশ") ||
            q.contains("english") -> {
                english = true
                "English mode activated."
            }

            q.contains("time") ||
            q.contains("সময়") ->
                "এখন সময় ${java.text.SimpleDateFormat(
                    "hh:mm a", Locale.getDefault()
                ).format(java.util.Date())}"

            else ->
                if (english)
                    "I heard you say: $command. Connect an AI brain to answer intelligently."
                else
                    "আমি শুনেছি: $command। বুদ্ধিমান উত্তর দেওয়ার জন্য AI Brain সংযুক্ত করতে হবে।"
        }

        response.text = answer
        status.text = "SYSTEM RESPONSE"
        speak(answer)
    }

    private fun speak(message: String) {
        if (ttsReady) {
            tts.language = if (english)
                Locale.US else Locale("bn", "BD")

            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "aria-response"
            )
        }
    }

    override fun onInit(code: Int) {
        ttsReady = code == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale("bn", "BD")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    class HologramView(context: android.content.Context) :
        View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var rotation = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f

            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 180, 255)
                maskFilter = BlurMaskFilter(
                    35f, BlurMaskFilter.Blur.NORMAL
                )
            }

            canvas.drawCircle(cx, cy, 70f, glow)

            for (i in 0..4) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (i == 2) 3f else 1.5f
                paint.color = Color.rgb(0, 200, 255)
                paint.alpha = 220

                val radius = 45f + i * 23f

                canvas.save()
                canvas.rotate(
                    rotation * (if (i % 2 == 0) 1 else -1),
                    cx, cy
                )

                canvas.drawCircle(cx, cy, radius, paint)

                val path = Path()
                path.moveTo(cx - radius, cy)
                path.lineTo(cx + radius, cy)
                path.moveTo(cx, cy - radius)
                path.lineTo(cx, cy + radius)

                canvas.drawPath(path, paint)

                canvas.restore()
            }

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, 5f, paint)

            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 12f
            paint.color = Color.rgb(0, 220, 255)

            canvas.drawText(
                "ARIA CORE",
                cx,
                cy + 125f,
                paint
            )

            rotation += 0.35f
            postInvalidateDelayed(35)
        }
    }
}
