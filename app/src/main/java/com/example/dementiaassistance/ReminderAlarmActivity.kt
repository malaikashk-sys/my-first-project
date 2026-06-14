package com.example.dementiaassistance

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class ReminderAlarmActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var reminderTitle = ""
    private var reminderTitleUrdu = ""
    private var isUrdu = false
    private var isAutoSnoozed = false
    private var reminderId = 0
    private var isTtsReady = false
    private var userInteracted = false
    private var isDaily = false // 👈 یہ نیا ویری ایبل ہے

    private val handler = Handler(Looper.getMainLooper())

    // ✅ نوٹیفکیشن بٹن سے سکرین بند کرنے کے لیے ریسیور
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "FINISH_ALARM_ACTIVITY") {
                Log.d("AlarmActivity", "Closing screen via Notification Button")
                dismissAlarm()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_reminder_alarm)
        supportActionBar?.hide()

        // ✅ ریسیور رجسٹر کریں
        registerReceiver(finishReceiver, IntentFilter("FINISH_ALARM_ACTIVITY"), RECEIVER_NOT_EXPORTED)

        reminderTitle = intent.getStringExtra("reminderTitle") ?: "Reminder"
        reminderTitleUrdu = intent.getStringExtra("reminderTitleUrdu") ?: ""
        val reminderTime = intent.getStringExtra("reminderTime") ?: ""
        reminderId = intent.getIntExtra("reminderId", 0)
        isUrdu = intent.getBooleanExtra("isUrdu", false)
        isAutoSnoozed = intent.getBooleanExtra("isAutoSnoozed", false)
        isDaily = intent.getBooleanExtra("isDaily", false)

        val prefs = getSharedPreferences("reminders", MODE_PRIVATE)
        prefs.edit().putBoolean("interacted_$reminderId", false).apply()

        val txtTitle = findViewById<TextView>(R.id.txtReminderTitle)
        val txtDetails = findViewById<TextView>(R.id.txtReminderDetails)
        val btnDismiss = findViewById<Button>(R.id.btnDismiss)
        val btnSnooze = findViewById<Button>(R.id.btnSnooze)
        val txtAlarmLabel = findViewById<TextView>(R.id.txtAlarmLabel)

        if (isUrdu && reminderTitleUrdu.isNotEmpty()) {
            txtAlarmLabel.text = "⏰ یاد دہانی" // ✅ اردو کے لیے
            txtTitle.text = reminderTitleUrdu
            txtDetails.text = if (reminderTime.isNotEmpty()) "وقت: $reminderTime" else ""
            btnDismiss.text = "✅ ٹھیک ہے، سمجھ گیا"
            btnSnooze.text = "⏰ 2 منٹ بعد یاد دلائیں"
        } else {
            txtAlarmLabel.text = "⏰ REMINDER" // ✅ انگلش کے لیے یہ لائن بھی ڈال دیں
            txtTitle.text = reminderTitle
            txtDetails.text = if (reminderTime.isNotEmpty()) "Time: $reminderTime" else ""
            btnDismiss.text = "✅ OK, Got It"
            btnSnooze.text = "⏰ Remind in 2 Minutes"
        }

        btnDismiss.setOnClickListener {
            userInteracted = true
            markInteracted()
            dismissAlarm()
        }

        btnSnooze.setOnClickListener {
            userInteracted = true
            markInteracted()
            snoozeAlarm()
        }

        startAlarmSound()
        tts = TextToSpeech(this, this)

        handler.postDelayed({ handleAutoSnooze() }, 120_000)
    }

    private fun markInteracted() {
        getSharedPreferences("reminders", MODE_PRIVATE)
            .edit().putBoolean("interacted_$reminderId", true).apply()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            val locale = if (isUrdu) Locale("ur", "PK") else Locale.US
            tts?.setLanguage(locale)
            handler.postDelayed({ startRepeatingSpeech() }, 1000)
        }
    }

    private fun startRepeatingSpeech() {
        if (tts == null || !isTtsReady || userInteracted) return

        val speechText = if (isUrdu && reminderTitleUrdu.isNotEmpty()) {
            "یاد دہانی: $reminderTitleUrdu"
        } else {
            "Reminder: $reminderTitle"
        }

        tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "ALARM_TTS")

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (!userInteracted && mediaPlayer?.isPlaying == true) {
                    handler.postDelayed({ startRepeatingSpeech() }, 5000)
                }
            }
            override fun onError(utteranceId: String?) {}
        })
    }

    private fun startAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(this, uri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("AlarmActivity", "Sound error: ${e.message}")
        }
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) { }
    }

    private fun dismissAlarm() {
        handler.removeCallbacksAndMessages(null)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(reminderId)
        stopAlarmSound()
        tts?.stop()
        finish()
    }

    private fun snoozeAlarm() {
        scheduleSnooze()
        dismissAlarm()
    }

    private fun handleAutoSnooze() {
        if (!userInteracted) {
            if (isAutoSnoozed) {
                // ✅ Yahan se CaregiverNotifier.sendCaregiverAlert hata dein
                // Kyunke FollowupReceiver background mein khud hi alert bhej dega
                Log.d("AlarmActivity", "Auto-snoozed twice - closing activity")
                dismissAlarm()
            } else {
                // Pehli baar hai, sirf snooze karein
                scheduleSnooze()
                dismissAlarm()
            }
        }
    }

    private fun scheduleSnooze() {
        val snoozeIntent = Intent(this, ReminderBroadcastReceiver::class.java).apply {
            putExtra("reminderTitle", reminderTitle)
            putExtra("reminderTitleUrdu", reminderTitleUrdu)
            putExtra("reminderId", reminderId)
            putExtra("isUrdu", isUrdu)
            putExtra("isDaily", isDaily) // 👈 اب یہ 'false' کے بجائے اصل ویلیو بھیجے گا
            putExtra("isAutoSnoozed", true)
            putExtra("reminderTime", intent.getStringExtra("reminderTime") ?: "")
            // ✅ وقت بھی دوبارہ بھیجنا ضروری ہے تاکہ ری شیڈولنگ صحیح ہو سکے
            putExtra("hour", intent.getIntExtra("hour", -1))
            putExtra("minute", intent.getIntExtra("minute", 0))
        }
        // ... باقی کوڈ وہی رہے گا

        val pi = PendingIntent.getBroadcast(
            this, reminderId + 999, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 120_000L,
            pi
        )
    }

    override fun onDestroy() {
        unregisterReceiver(finishReceiver) // ✅ ریسیور ختم کریں
        stopAlarmSound()
        tts?.shutdown()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}