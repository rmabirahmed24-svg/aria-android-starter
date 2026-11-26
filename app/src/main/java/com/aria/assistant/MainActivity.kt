package com.aria.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val prefs by lazy { getSharedPreferences("aria_secure_settings", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var transcript: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var orb: AriaOrbView
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private var listening = false
    private val history = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 11, 20)
        window.navigationBarColor = Color.rgb(5, 11, 20)
        tts = TextToSpeech(this, this)
        buildInterface()
        restoreHistory()
        if (history.length() == 0) addMessage("ARIA", "Hello. আমি ARIA। কীভাবে সাহায্য করতে পারি?")
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(Color.rgb(5, 11, 20))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "A.R.I.A."
            textSize = 25f
            setTextColor(Color.rgb(90, 224, 255))
            typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
            letterSpacing = .22f
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        val settings = button("⚙") { showSettings() }
        header.addView(settings, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(header)

        orb = AriaOrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(-1, dp(205)))

        status = TextView(this).apply {
            text = "ONLINE  •  READY"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = .18f
            setTextColor(Color.rgb(79, 218, 255))
            setPadding(0, dp(2), 0, dp(12))
        }
        root.addView(status)

        val scroll = ScrollView(this).apply { isFillViewport = false }
        transcript = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Message ARIA…"
            setHintTextColor(Color.rgb(113, 137, 158))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(false)
            maxLines = 3
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.rgb(14, 27, 42), 18)
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        val mic = button("🎙") { startListening() }
        row.addView(mic, LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(6) })
        val send = button("➤") { sendMessage() }
        row.addView(send, LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(6) })
        root.addView(row)
        val footer = TextView(this).apply {
            text = "GEMINI AI  •  BANGLA + ENGLISH  •  VOICE"
            textSize = 9f
            gravity = Gravity.CENTER
            letterSpacing = .12f
            setTextColor(Color.rgb(80, 116, 142))
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(footer)
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(91, 224, 255))
        background = rounded(Color.rgb(13, 35, 52), 16)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(28, 93, 119))
        }

    private fun addMessage(who: String, message: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(if (who == "YOU") Color.rgb(12, 35, 49) else Color.rgb(11, 22, 36), 14)
        }
        val label = TextView(this).apply {
            text = who
            textSize = 10f
            letterSpacing = .15f
            setTextColor(if (who == "YOU") Color.rgb(93, 190, 224) else Color.rgb(68, 224, 255))
        }
        val body = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(Color.rgb(225, 239, 247))
            setPadding(0, dp(5), 0, 0)
        }
        card.addView(label)
        card.addView(body)
        transcript.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        addMessage("YOU", prompt)
        history.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        persistHistory()
        val key = prefs.getString("gemini_key", "").orEmpty()
        if (key.isBlank()) {
            addMessage("ARIA", "Gemini API key সেট করতে উপরের ⚙ Settings চাপো।")
            return
        }
        status.text = "THINKING…"
        orb.mode = "THINKING"
        executor.execute {
            try {
                val answer = askGemini(key, prompt)
                runOnUiThread {
                    addMessage("ARIA", answer)
                    history.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", answer))))
                    persistHistory()
                    status.text = "ONLINE  •  READY"
                    orb.mode = "IDLE"
                    speak(answer)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("ARIA", "সংযোগে সমস্যা হয়েছে: ${e.message ?: "Unknown error"}")
                    status.text = "CONNECTION ISSUE"
                    orb.mode = "IDLE"
                }
            }
        }
    }

    private fun askGemini(key: String, prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + java.net.URLEncoder.encode(key, "UTF-8")
        val contents = JSONArray()
        val start = maxOf(0, history.length() - 12)
        for (i in start until history.length()) contents.put(history.getJSONObject(i))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                "You are ARIA, a helpful personal AI assistant. Reply naturally in Bangla (Bangladesh) or English, matching the user. Be concise but useful. Never claim you performed device actions. Ask permission before sensitive actions. You cannot bypass Android or Windows security."))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 2048))
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val msg = try { JSONObject(response).getJSONObject("error").optString("message") } catch (_: Exception) { "HTTP $code" }
                throw Exception(msg)
            }
            val obj = JSONObject(response)
            val candidates = obj.getJSONArray("candidates")
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val out = StringBuilder()
            for (i in 0 until parts.length()) out.append(parts.getJSONObject(i).optString("text"))
            return out.toString().trim().ifBlank { "আমি উত্তর তৈরি করতে পারিনি। আবার চেষ্টা করো।" }
        } finally { conn.disconnect() }
    }

    private fun showSettings() {
        val field = EditText(this).apply {
            hint = "Gemini API key"
            setText(prefs.getString("gemini_key", ""))
            inputType = 129
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
            addView(TextView(this@MainActivity).apply {
                text = "API key এই ফোনের private app storage-এ রাখা হয়। কাউকে শেয়ার কোরো না। API quota Google-এর নিয়ম অনুযায়ী।"
                textSize = 12f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(10), 0, 0)
            })
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("ARIA Settings")
            .setView(box)
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString("gemini_key", field.text.toString().trim()).apply()
                Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 401)
            return
        }
        try {
            if (speech == null) speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "LISTENING…"; orb.mode = "LISTENING" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "PROCESSING…" }
                override fun onError(error: Int) {
                    status.text = "ONLINE  •  READY"; orb.mode = "IDLE"
                    Toast.makeText(this@MainActivity, "Voice input failed ($error)", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) { input.setText(spoken); sendMessage() }
                    status.text = "ONLINE  •  READY"; orb.mode = "IDLE"
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "ARIA is listening…")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speech?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Voice error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts?.language = Locale("bn", "BD")
            tts?.setSpeechRate(0.96f)
        }
    }

    private fun speak(text: String) {
        val engine = tts ?: return
        val bangla = text.any { it.code in 0x0980..0x09FF }
        engine.language = if (bangla) Locale("bn", "BD") else Locale.US
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aria_reply")
    }

    private fun persistHistory() {
        prefs.edit().putString("history", history.toString()).apply()
    }

    private fun restoreHistory() {
        val raw = prefs.getString("history", null) ?: return
        try {
            val saved = JSONArray(raw)
            for (i in 0 until saved.length()) {
                val item = saved.getJSONObject(i)
                history.put(item)
                val role = item.optString("role")
                val text = item.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                if (text.isNotBlank()) addMessage(if (role == "user") "YOU" else "ARIA", text)
            }
        } catch (_: Exception) { prefs.edit().remove("history").apply() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        speech?.destroy()
        tts?.stop()
        tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }
}

class AriaOrbView(context: Context) : View(context) {
    var mode: String = "IDLE"
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * .31f
        phase += .035f
        val pulse = 1f + .035f * kotlin.math.sin(phase.toDouble()).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(90, 45, 204, 255)
        for (i in 0..3) {
            val r = base * (1.1f + i * .22f) * pulse
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.strokeWidth = 3f
        paint.color = Color.rgb(50, 210, 255)
        val rect = RectF(cx-base, cy-base, cx+base, cy+base)
        canvas.drawArc(rect, phase * 40, 235f, false, paint)
        paint.style = Paint.Style.FILL
        val shader = RadialGradient(cx, cy, base, intArrayOf(Color.WHITE, Color.rgb(50, 207, 255), Color.rgb(3, 37, 68), Color.TRANSPARENT), floatArrayOf(0f, .16f, .65f, 1f), Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawCircle(cx, cy, base, paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
        paint.textSize = 17f
        canvas.drawText("A R I A", cx, cy + base + 37f, paint)
        paint.textSize = 9f
        paint.color = Color.rgb(105, 218, 245)
        canvas.drawText(mode, cx, cy + base + 53f, paint)
        postInvalidateDelayed(40)
    }
}
