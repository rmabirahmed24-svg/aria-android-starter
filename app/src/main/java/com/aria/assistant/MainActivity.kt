package com.aria.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val prefs by lazy { getSharedPreferences("aria_private", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var transcript: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var orb: AriaOrbView
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private val history = JSONArray()
    private var speakingEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 10, 18)
        window.navigationBarColor = Color.rgb(4, 10, 18)
        tts = TextToSpeech(this, this)
        buildInterface()
        restoreHistory()
        if (history.length() == 0) addMessage("ARIA", "আমি ARIA। আমি প্রস্তুত। কীভাবে সাহায্য করতে পারি?")
        if (intent?.action == ACTION_WAKE) {
            status.text = "WAKE WORD DETECTED"
            orb.mode = "AWAKE"
            startListening()
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setBackgroundColor(Color.rgb(4, 10, 18))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "A.R.I.A."; textSize = 25f; setTextColor(Color.rgb(87, 222, 255))
            typeface = Typeface.create("sans-serif-light", Typeface.BOLD); letterSpacing = .22f
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(button("⚙") { showSettings() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(header)
        orb = AriaOrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(-1, dp(190)))
        status = TextView(this).apply {
            text = if (prefs.getBoolean("wake_enabled", false)) "WAKE WORD • ARMED" else "ONLINE • READY"
            textSize = 11f; gravity = Gravity.CENTER; letterSpacing = .18f
            setTextColor(Color.rgb(79, 218, 255)); setPadding(0, dp(2), 0, dp(10))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(12)) }
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Message ARIA…"; setHintTextColor(Color.rgb(113, 137, 158)); setTextColor(Color.WHITE)
            textSize = 15f; maxLines = 3; setPadding(dp(14), dp(10), dp(14), dp(10)); background = rounded(Color.rgb(14, 27, 42), 18)
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(button("🎙") { startListening() }, LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(6) })
        row.addView(button("➤") { sendMessage() }, LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(6) })
        root.addView(row)
        root.addView(TextView(this).apply {
            text = "AUTO AI  •  BANGLA + ENGLISH  •  PERSONAL ASSISTANT"
            textSize = 8.5f; gravity = Gravity.CENTER; letterSpacing = .10f
            setTextColor(Color.rgb(80, 116, 142)); setPadding(0, dp(11), 0, 0)
        })
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.rgb(91, 224, 255))
        background = rounded(Color.rgb(13, 35, 52), 16); setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), Color.rgb(28, 93, 119))
    }

    private fun addMessage(who: String, message: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(if (who == "YOU") Color.rgb(12, 35, 49) else Color.rgb(11, 22, 36), 14)
        }
        card.addView(TextView(this).apply {
            text = who; textSize = 10f; letterSpacing = .15f
            setTextColor(if (who == "YOU") Color.rgb(93, 190, 224) else Color.rgb(68, 224, 255))
        })
        card.addView(TextView(this).apply { text = message; textSize = 15f; setTextColor(Color.rgb(225, 239, 247)); setPadding(0, dp(5), 0, 0) })
        transcript.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        addMessage("YOU", prompt)
        history.put(JSONObject().put("role", "user").put("text", prompt)); trimHistory(); persistHistory()
        if (handleLocalCommand(prompt)) return
        val geminiKey = prefs.getString("gemini_key", "").orEmpty()
        val openAiKey = prefs.getString("openai_key", "").orEmpty()
        if (geminiKey.isBlank() && openAiKey.isBlank()) {
            addMessage("ARIA", "প্রথমে Settings থেকে Gemini API Key অথবা OpenAI API Key যোগ করো।"); return
        }
        status.text = "THINKING • AUTO BRAIN"; orb.mode = "THINKING"
        executor.execute {
            try {
                var answer: String? = null
                var provider = ""
                if (geminiKey.isNotBlank()) {
                    try { answer = askGemini(geminiKey, prompt); provider = "GEMINI" }
                    catch (e: Exception) { if (openAiKey.isBlank()) throw e }
                }
                if (answer.isNullOrBlank() && openAiKey.isNotBlank()) { answer = askOpenAI(openAiKey, prompt); provider = "OPENAI BACKUP" }
                val finalAnswer = answer ?: "আমি উত্তর তৈরি করতে পারিনি। আবার চেষ্টা করো।"
                runOnUiThread {
                    addMessage("ARIA • $provider", finalAnswer)
                    history.put(JSONObject().put("role", "assistant").put("text", finalAnswer)); trimHistory(); persistHistory()
                    status.text = if (prefs.getBoolean("wake_enabled", false)) "WAKE WORD • ARMED" else "ONLINE • READY"
                    orb.mode = "IDLE"
                    if (speakingEnabled) speak(finalAnswer)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("ARIA", "সংযোগে সমস্যা: ${e.message ?: "Unknown error"}")
                    status.text = "CONNECTION ISSUE"; orb.mode = "IDLE"
                }
            }
        }
    }

    private fun askGemini(key: String, prompt: String): String {
        val body = JSONObject()
            .put("model", "gemini-3.8-flash")
            .put("input", prompt)
            .put("tools", JSONArray().put(JSONObject().put("type", "google_search")))
            .put("system_instruction", "You are ARIA, the user's personal AI assistant. Reply naturally in Bangla (Bangladesh) or English matching the user's language. Be intelligent, calm, practical and honest. Use Google Search for current facts when useful. Never claim you performed actions you did not perform. Ask before sensitive actions. Respect Android security and privacy.")
        val previousId = prefs.getString("gemini_previous_interaction_id", "").orEmpty()
        if (previousId.isNotBlank()) body.put("previous_interaction_id", previousId)
        val response = postJson("https://generativelanguage.googleapis.com/v1beta/interactions", body, mapOf("x-goog-api-key" to key))
        val obj = JSONObject(response)
        obj.optString("id").takeIf { it.isNotBlank() }?.let { prefs.edit().putString("gemini_previous_interaction_id", it).apply() }
        val steps = obj.optJSONArray("steps") ?: JSONArray()
        val out = StringBuilder()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("type") != "model_output") continue
            val content = step.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "text") {
                    out.append(part.optString("text"))
                    val annotations = part.optJSONArray("annotations") ?: JSONArray()
                    for (a in 0 until annotations.length()) {
                        val annotation = annotations.optJSONObject(a) ?: continue
                        if (annotation.optString("type") == "url_citation") {
                            val title = annotation.optString("title", "Source")
                            val uri = annotation.optString("url", "")
                            if (uri.startsWith("https://") || uri.startsWith("http://")) out.append("\n\n$title: $uri")
                        }
                    }
                }
            }
        }
        return out.toString().trim().ifBlank { "আমি উত্তর তৈরি করতে পারিনি। আবার চেষ্টা করো।" }
    }

    private fun askOpenAI(key: String, prompt: String): String {
        val body = JSONObject().put("model", "gpt-4.1-mini")
            .put("instructions", "You are ARIA, a helpful personal AI assistant. Reply in Bangla (Bangladesh) or English matching the user. Be honest, intelligent and concise. Never claim to perform device actions without actually doing them.")
            .put("input", prompt)
        val previous = prefs.getString("openai_previous_response_id", "").orEmpty()
        if (previous.isNotBlank()) body.put("previous_response_id", previous)
        val response = JSONObject(postJson("https://api.openai.com/v1/responses", body, mapOf("Authorization" to "Bearer $key")))
        response.optString("id").takeIf { it.isNotBlank() }?.let { prefs.edit().putString("openai_previous_response_id", it).apply() }
        val output = response.optJSONArray("output") ?: JSONArray()
        val text = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") text.append(part.optString("text"))
            }
        }
        return text.toString().trim().ifBlank { "OpenAI থেকে কোনো টেক্সট উত্তর পাওয়া যায়নি।" }
    }

    private fun postJson(endpoint: String, body: JSONObject, headers: Map<String, String>): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try { JSONObject(response).getJSONObject("error").optString("message", "HTTP $code") }
                catch (_: Exception) { "HTTP $code: $response" }
                throw IllegalStateException(message)
            }
            return response
        } finally { conn.disconnect() }
    }

    // Safe, explicit local actions. Anything outside this small allowlist is answered by the AI.
    private fun handleLocalCommand(raw: String): Boolean {
        val q = raw.lowercase(Locale.ROOT).trim()
        val target = when {
            q.contains("open youtube") || q.contains("ইউটিউব খোলো") -> "https://www.youtube.com"
            q.contains("open google") || q.contains("গুগল খোলো") -> "https://www.google.com"
            q.contains("open gmail") || q.contains("জিমেইল খোলো") -> "https://mail.google.com"
            q.contains("open maps") || q.contains("ম্যাপ খোলো") -> "https://maps.google.com"
            q.contains("open browser") || q.contains("ব্রাউজার খোলো") -> "https://www.google.com"
            else -> null
        }
        if (target != null) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))); addMessage("ARIA", "তোমার অনুরোধে লিংকটি খুলছি।") }
            catch (e: Exception) { addMessage("ARIA", "লিংক খোলা যায়নি: ${e.message}") }
            return true
        }
        return false
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val gemini = EditText(this).apply { hint = "Gemini API Key"; inputType = 129; setText(prefs.getString("gemini_key", "")) }
        val openai = EditText(this).apply { hint = "OpenAI API Key (backup brain)"; inputType = 129; setText(prefs.getString("openai_key", "")) }
        box.addView(gemini); box.addView(openai)
        box.addView(TextView(this).apply {
            text = "AUTO: Gemini আগে চেষ্টা করবে; ব্যর্থ হলে OpenAI backup ব্যবহার করবে। দুই Provider-এর API Key ও usage quota আলাদা। Key এই ফোনের private app storage-এ রাখা হয়; production release-এর জন্য backend proxy বেশি নিরাপদ।"
            textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0, dp(8), 0, dp(8))
        })
        val wake = Switch(this).apply { text = "Always listen for ‘ARIA’ wake word"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("wake_enabled", false) }
        box.addView(wake)
        val voice = Switch(this).apply { text = "Speak replies aloud"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("voice_enabled", true) }
        box.addView(voice)
        AlertDialog.Builder(this).setTitle("ARIA • Settings").setView(box)
            .setPositiveButton("SAVE") { _, _ ->
                prefs.edit().putString("gemini_key", gemini.text.toString().trim())
                    .putString("openai_key", openai.text.toString().trim())
                    .putBoolean("voice_enabled", voice.isChecked).apply()
                speakingEnabled = voice.isChecked
                if (wake.isChecked) enableWakeWord() else disableWakeWord()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }.setNeutralButton("CLEAR CHAT") { _, _ ->
                prefs.edit().remove("history").remove("gemini_previous_interaction_id").remove("openai_previous_response_id").apply()
                history.length().let { while (history.length() > 0) history.remove(history.length() - 1) }
                transcript.removeAllViews(); addMessage("ARIA", "Chat history cleared.")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun enableWakeWord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_WAKE_AUDIO); return
        }
        prefs.edit().putBoolean("wake_enabled", true).apply()
        try {
            val i = Intent(this, WakeWordService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "WAKE WORD • ARMED"
        } catch (e: Exception) {
            prefs.edit().putBoolean("wake_enabled", false).apply()
            Toast.makeText(this, "Wake service could not start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableWakeWord() {
        prefs.edit().putBoolean("wake_enabled", false).apply()
        stopService(Intent(this, WakeWordService::class.java))
        status.text = "ONLINE • READY"
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_LONG).show(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MANUAL_AUDIO); return
        }
        try {
            if (speech == null) speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "LISTENING…"; orb.mode = "LISTENING" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "PROCESSING…" }
                override fun onError(error: Int) { status.text = if (prefs.getBoolean("wake_enabled", false)) "WAKE WORD • ARMED" else "ONLINE • READY"; orb.mode = "IDLE" }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) { input.setText(spoken); sendMessage() }
                    status.text = "ONLINE • READY"; orb.mode = "IDLE"
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD"); putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "ARIA is listening…"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speech?.startListening(intent)
        } catch (e: Exception) { Toast.makeText(this, e.message ?: "Voice error", Toast.LENGTH_LONG).show() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_WAKE_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) enableWakeWord()
        else if (requestCode == REQ_MANUAL_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else if (requestCode == REQ_WAKE_AUDIO || requestCode == REQ_MANUAL_AUDIO) Toast.makeText(this, "Microphone permission is required for voice features.", Toast.LENGTH_LONG).show()
    }

    override fun onInit(code: Int) {
        if (code == TextToSpeech.SUCCESS) { tts?.language = Locale("bn", "BD"); tts?.setSpeechRate(.96f) }
    }
    private fun speak(text: String) {
        val engine = tts ?: return
        val bangla = text.any { it.code in 0x0980..0x09FF }
        engine.language = if (bangla) Locale("bn", "BD") else Locale.US
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aria_reply")
    }
    private fun trimHistory() { while (history.length() > 30) history.remove(0) }
    private fun persistHistory() { prefs.edit().putString("history", history.toString()).apply() }
    private fun restoreHistory() {
        val raw = prefs.getString("history", null) ?: return
        try {
            val saved = JSONArray(raw)
            for (i in 0 until saved.length()) {
                val item = saved.getJSONObject(i); history.put(item)
                val role = item.optString("role"); val text = item.optString("text")
                if (text.isNotBlank()) addMessage(if (role == "user") "YOU" else "ARIA", text)
            }
        } catch (_: Exception) { prefs.edit().remove("history").apply() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_WAKE) {
            status.text = "WAKE WORD DETECTED"
            orb.mode = "AWAKE"
            startListening()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        speech?.destroy(); tts?.stop(); tts?.shutdown(); executor.shutdownNow(); super.onDestroy()
    }
    companion object { const val ACTION_WAKE = "com.aria.assistant.ACTION_WAKE"; private const val REQ_WAKE_AUDIO = 401; private const val REQ_MANUAL_AUDIO = 402 }
}

class AriaOrbView(context: Context) : android.view.View(context) {
    var mode: String = "IDLE"; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private var phase = 0f
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val cx = width / 2f; val cy = height / 2f; val base = minOf(width, height) * .30f
        phase += .035f; val pulse = 1f + .035f * kotlin.math.sin(phase.toDouble()).toFloat()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = Color.argb(90, 45, 204, 255)
        for (i in 0..3) canvas.drawCircle(cx, cy, base * (1.1f + i * .22f) * pulse, paint)
        paint.strokeWidth = 3f; paint.color = Color.rgb(50, 210, 255)
        canvas.drawArc(RectF(cx-base, cy-base, cx+base, cy+base), phase * 40, 235f, false, paint)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, base, intArrayOf(Color.WHITE, Color.rgb(50,207,255), Color.rgb(3,37,68), Color.TRANSPARENT), floatArrayOf(0f,.16f,.65f,1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, base, paint); paint.shader = null
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
        paint.textSize = 17f; canvas.drawText("A R I A", cx, cy + base + 34f, paint)
        paint.textSize = 9f; paint.color = Color.rgb(105,218,245); canvas.drawText(mode, cx, cy + base + 49f, paint)
        postInvalidateDelayed(40)
    }
}
