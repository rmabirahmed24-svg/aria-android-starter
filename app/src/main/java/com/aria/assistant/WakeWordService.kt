package com.aria.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.Locale

/**
 * User-enabled wake-word listener. Uses the device speech-recognition provider,
 * so availability, language support, battery policy and OEM behavior vary.
 * The persistent notification makes microphone use visible and provides Stop.
 */
class WakeWordService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var restarting = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Listening for ARIA"))
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
            startRecognition()
        } else {
            stopSelf()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { isListening = true; updateNotification("Listening for ‘ARIA’ / ‘Wake up ARIA’") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        override fun onError(error: Int) { isListening = false; scheduleRestart() }
        override fun onResults(results: Bundle?) {
            isListening = false
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (isWakePhrase(heard)) {
                getSharedPreferences("aria_private", Context.MODE_PRIVATE).edit().putBoolean("wake_phrase_detected", true).apply()
                val open = Intent(this@WakeWordService, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_WAKE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pending = PendingIntent.getActivity(this@WakeWordService, 72, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = Notification.Builder(this@WakeWordService, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("ARIA is awake")
                    .setContentText("Tap to talk to ARIA").setContentIntent(pending).setAutoCancel(true).build()
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(WAKE_NOTIFICATION_ID, notification)
                updateNotification("Wake phrase detected • tap ARIA notification")
                // Avoid immediately re-triggering while the user responds.
                stopListening()
            } else scheduleRestart()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun isWakePhrase(text: String): Boolean {
        val q = text.lowercase(Locale.ROOT)
        return q.contains("aria") || q.contains("আড়িয়া") || q.contains("আরিয়া") || q.contains("wake up")
    }

    private fun startRecognition() {
        if (isListening || recognizer == null) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) { scheduleRestart() }
    }

    private fun scheduleRestart() {
        if (restarting) return
        restarting = true
        android.os.Handler(mainLooper).postDelayed({
            restarting = false
            if (getSharedPreferences("aria_private", MODE_PRIVATE).getBoolean("wake_enabled", false)) startRecognition()
            else stopSelf()
        }, 1200)
    }

    private fun stopListening() { try { recognizer?.cancel() } catch (_: Exception) {}; isListening = false }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ARIA Wake Word", NotificationManager.IMPORTANCE_LOW))
        }
    }
    private fun buildNotification(message: String): Notification {
        val stop = PendingIntent.getService(this, 73, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ARIA Wake Word is ON").setContentText(message)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop listening", stop).build()).setOngoing(true).build()
    }
    private fun updateNotification(message: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(message))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences("aria_private", MODE_PRIVATE).edit().putBoolean("wake_enabled", false).apply()
            stopSelf(); return START_NOT_STICKY
        }
        if (!getSharedPreferences("aria_private", MODE_PRIVATE).getBoolean("wake_enabled", false)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onDestroy() { stopListening(); recognizer?.destroy(); recognizer = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "aria_wake_channel"
        private const val NOTIFICATION_ID = 7401
        private const val WAKE_NOTIFICATION_ID = 7402
        private const val ACTION_STOP = "com.aria.assistant.STOP_WAKE"
    }
}
