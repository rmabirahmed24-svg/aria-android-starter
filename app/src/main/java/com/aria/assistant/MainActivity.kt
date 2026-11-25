package com.aria.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }
        status = TextView(this).apply {
            textSize = 18f
            text = "A.R.I.A. প্রস্তুত। কথা বলতে মাইক্রোফোন বোতাম চাপুন।"
        }
        val talk = Button(this).apply {
            text = "কথা বলুন"
            setOnClickListener { beginListening() }
        }
        val privacy = TextView(this).apply {
            textSize = 14f
            text = "\nগোপনীয়তা: এই প্রাথমিক সংস্করণে অ্যাপ নিজে থেকে ক্যামেরা, ফাইল, কন্টাক্ট বা মেসেজ পড়ে না। সংবেদনশীল কাজ এখনো সক্রিয় করা হয়নি।"
        }
        layout.addView(status)
        layout.addView(talk)
        layout.addView(privacy)
        setContentView(layout)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    private fun beginListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            status.text = "প্রথমে মাইক্রোফোনের অনুমতি দিন।"
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "এই ডিভাইসে Android speech recognition পাওয়া যাচ্ছে না।"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "শুনছি..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "বোঝার চেষ্টা করছি..." }
            override fun onError(error: Int) { status.text = "শোনা যায়নি। আবার চেষ্টা করুন।" }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                status.text = if (phrase.isBlank()) "কিছু বুঝতে পারিনি।" else "তুমি বলেছ: $phrase"
                respondTo(phrase)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    private fun respondTo(phrase: String) {
        val reply = when {
            phrase.contains("ইংলিশ", ignoreCase = true) ||
                phrase.contains("english", ignoreCase = true) ->
                "ঠিক আছে। আমি এখন ইংরেজিতে কথা বলব।"
            phrase.contains("বাংলা") ->
                "ঠিক আছে। আমি বাংলায় ফিরে এলাম।"
            phrase.contains("তোমার নাম") || phrase.contains("তোমার নাম কি") ->
                "আমার নাম আরিয়া।"
            else ->
                "আমি শুনেছি: $phrase। এই প্রাথমিক সংস্করণে এখনো ফোনের কাজ করার সুবিধা যোগ করা হয়নি।"
        }
        status.text = reply
        if (ttsReady) {
            tts.language = Locale("bn", "BD")
            tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "aria-reply")
        }
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale("bn", "BD")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
