package com.example.dementiaassistance

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isUrdu = false
    private var lastSpokenText = ""
    private var isTtsReady = false

    private lateinit var txtTitle: TextView
    private lateinit var cardFamily: View
    private lateinit var cardReminders: View
    private lateinit var cardExercise: View
    private lateinit var cardFace: View
    private lateinit var btnRepeat: Button
    private lateinit var btnEmergency: View
    private lateinit var txtEmergencySub: TextView

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var emergencyNumber = EmergencySettingsActivity.DEFAULT_NUMBER
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        isUrdu = appPrefs.getBoolean("isUrdu", false)

        tts = TextToSpeech(this, this)

        txtTitle        = findViewById(R.id.txtTitle)
        cardFamily      = findViewById(R.id.cardFamily)
        cardReminders   = findViewById(R.id.cardReminders)
        cardExercise    = findViewById(R.id.cardExercise)
        cardFace        = findViewById(R.id.cardFace)
        btnRepeat       = findViewById(R.id.btnRepeat)
        btnEmergency    = findViewById(R.id.btnEmergency)
        txtEmergencySub = findViewById(R.id.txtEmergencySub)

        requestExactAlarmPermission()
        syncRemindersFromFirestore(appPrefs)
        Thread { refreshAllAlarms() }.start()
        fetchEmergencyNumber(appPrefs)
        syncPatientIdToFirestore(appPrefs)
        updateLanguage()

        // ✅ Foreground service start karo
        startLocationService()

        cardFamily.setOnClickListener {
            speakOut(if (isUrdu) "خاندان کی تصاویر کھل رہی ہیں" else "Opening Family Photos")
            startActivity(Intent(this, FamilyPhotosActivity::class.java)
                .putExtra("isUrdu", isUrdu).putExtra("isCaregiverMode", false))
        }

        cardReminders.setOnClickListener {
            speakOut(if (isUrdu) "یاد دہانی کھل رہی ہے" else "Opening Reminders")
            startActivity(Intent(this, RemindersActivity::class.java)
                .putExtra("isUrdu", isUrdu).putExtra("isCaregiver", false))
        }

        cardExercise.setOnClickListener {
            speakOut(if (isUrdu) "ورزش کھل رہی ہے" else "Opening Exercise")
            startActivity(Intent(this, ExerciseActivity::class.java).putExtra("isUrdu", isUrdu))
        }

        cardFace.setOnClickListener {
            speakOut(if (isUrdu) "چہرہ پہچان کھل رہی ہے" else "Opening Face Recognition")
            startActivity(Intent(this, FaceRecognitionActivity::class.java).putExtra("isUrdu", isUrdu))
        }

        btnRepeat.setOnClickListener { repeatLastMessage() }

        btnEmergency.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) startEmergencyCountdown()
            else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) cancelEmergencyCountdown()
            true
        }
    }

    // ✅ Foreground location service start
    private fun startLocationService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            val serviceIntent = Intent(this, ForegroundLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.SEND_SMS),
                PERMISSION_REQUEST_CODE)
        }
    }

    // ✅ Patient ID Firestore mein sync
    private fun syncPatientIdToFirestore(appPrefs: android.content.SharedPreferences) {
        val linkedCaregiverId = appPrefs.getString("linkedCaregiverId", null) ?: return
        val patientId = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: return

        appPrefs.edit().putString("patientId", patientId).apply()

        FirebaseFirestore.getInstance()
            .collection("caregivers").document(linkedCaregiverId)
            .update("linkedPatientId", patientId)
            .addOnSuccessListener { Log.d("MainActivity", "✅ PatientId synced: $patientId") }
            .addOnFailureListener { Log.e("MainActivity", "❌ PatientId sync failed") }
    }

    private fun fetchEmergencyNumber(appPrefs: android.content.SharedPreferences) {
        emergencyNumber = EmergencySettingsActivity.getSavedNumber(this)
        val linkedCaregiverId = appPrefs.getString("linkedCaregiverId", null)
        if (!linkedCaregiverId.isNullOrEmpty()) {
            EmergencySettingsActivity.fetchCaregiverNumber(this, linkedCaregiverId) { number ->
                emergencyNumber = number
            }
        }
    }

    private fun syncRemindersFromFirestore(appPrefs: android.content.SharedPreferences) {
        val linkedCaregiverId = appPrefs.getString("linkedCaregiverId", null)
        if (!linkedCaregiverId.isNullOrEmpty()) {
            FirestoreReminderSync.fetchAndSyncReminders(this, linkedCaregiverId, isUrdu)
        }
    }

    private fun refreshAllAlarms() {
        try {
            val reminders = ReminderStorage.loadReminders(this)
            val helper = NotificationHelper(this)
            for (i in 0 until reminders.length()) {
                val json = reminders.getJSONObject(i)
                helper.scheduleReminderDaily(json.optInt("id", i), json.optString("title", ""),
                    json.optString("titleUrdu", ""), json.optInt("hour", 9), json.optInt("minute", 0), isUrdu)
            }
        } catch (e: Exception) { Log.e("MainActivity", "Error: ${e.message}") }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { isTtsReady = true; updateLanguage() }
    }

    private fun speakOut(text: String) {
        lastSpokenText = text
        if (isTtsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun updateLanguage() {
        val locale = if (isUrdu) Locale("ur", "PK") else Locale.US
        if (isTtsReady) tts.language = locale
        if (isUrdu) {
            txtTitle.text = "خوش آمدید"
            btnRepeat.text = "🔊 دوبارہ سنیں"
            findViewById<TextView>(R.id.txtFamilyLabel).text = "👨‍👩‍👧‍👦 خاندان دیکھیں"
            findViewById<TextView>(R.id.txtRemindersLabel).text = "🔔 یاد دہانی"
            findViewById<TextView>(R.id.txtExerciseLabel).text = "🧠 ورزش"
            findViewById<TextView>(R.id.txtFaceLabel).text = "👁️ یہ کون ہے؟"
            txtEmergencySub.text = "الرٹ کے لیے دبائے رکھیں"
        } else {
            txtTitle.text = "Patient Home"
            btnRepeat.text = "🔊 Repeat Last Message"
            findViewById<TextView>(R.id.txtFamilyLabel).text = "👨‍👩‍👧‍👦 View Family"
            findViewById<TextView>(R.id.txtRemindersLabel).text = "🔔 Reminders"
            findViewById<TextView>(R.id.txtExerciseLabel).text = "🧠 Exercise"
            findViewById<TextView>(R.id.txtFaceLabel).text = "👁️ Who Is This?"
            txtEmergencySub.text = "Hold for Emergency"
        }
    }

    private fun startEmergencyCountdown() {
        var seconds = 3
        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                if (seconds > 0) {
                    txtEmergencySub.text = if (isUrdu) "$seconds سیکنڈ..." else "Wait $seconds..."
                    speakOut(seconds.toString())
                    seconds--
                    countdownHandler?.postDelayed(this, 1000)
                } else {
                    txtEmergencySub.text = if (isUrdu) "بھیج دیا گیا" else "Sent!"
                    checkPermissionsAndSendEmergency()
                }
            }
        }
        countdownHandler?.post(countdownRunnable!!)
    }

    private fun cancelEmergencyCountdown() {
        countdownHandler?.removeCallbacks(countdownRunnable!!)
        txtEmergencySub.text = if (isUrdu) "الرٹ کے لیے دبائے رکھیں" else "Hold for Emergency"
    }

    private fun sendEmergencyAlert() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val lat  = location?.latitude ?: 0.0
                val long = location?.longitude ?: 0.0

                val msg = if (isUrdu)
                    "ہنگامی الرٹ! میری موجودہ لوکیشن: https://www.google.com/maps?q=$lat,$long"
                else "EMERGENCY! My location: https://www.google.com/maps?q=$lat,$long"

                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        this.getSystemService(SmsManager::class.java)
                    else @Suppress("DEPRECATION") SmsManager.getDefault()
                    smsManager?.sendTextMessage(emergencyNumber, null, msg, null, null)
                    Toast.makeText(this, if (isUrdu) "الرٹ بھیج دیا گیا" else "Alert Sent", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Log.e("Emergency", "SMS Failed: ${e.message}") }

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$emergencyNumber")))
                }
            }
        }
    }

    private fun repeatLastMessage() { if (lastSpokenText.isNotEmpty()) speakOut(lastSpokenText) }

    private fun checkPermissionsAndSendEmergency() {
        val perms = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })
            sendEmergencyAlert()
        else ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            sendEmergencyAlert()
            startLocationService()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        // ✅ Service stop karo jab app band ho
        stopService(Intent(this, ForegroundLocationService::class.java))
        super.onDestroy()
    }}