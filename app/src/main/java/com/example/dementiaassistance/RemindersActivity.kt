package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import android.app.NotificationManager
import android.content.Context
import java.util.*

class RemindersActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var listView: ListView
    private lateinit var btnAdd: Button
    private lateinit var btnBack: Button
    private lateinit var txtTitle: TextView
    private var isUrdu = false
    private var isCaregiver = false
    private lateinit var reminderList: JSONArray
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)
        isCaregiver = intent.getBooleanExtra("isCaregiver", false)

        listView = findViewById(R.id.listViewReminders)
        btnAdd = findViewById(R.id.btnAddReminder)
        btnBack = findViewById(R.id.btnBack)
        txtTitle = findViewById(R.id.txtPageTitle)

        tts = TextToSpeech(this, this)

        if (isUrdu) {
            txtTitle.text = "⏰ یاد دہانی"
            btnBack.text = "← واپس"
            btnAdd.text = "+ شامل کریں"
        } else {
            txtTitle.text = "⏰ Reminders"
            btnBack.text = "← Back"
            btnAdd.text = "+ Add Reminder"
        }

        btnAdd.visibility = if (isCaregiver) View.VISIBLE else View.GONE
        loadAndDisplayReminders()

        btnBack.setOnClickListener { finish() }
        btnAdd.setOnClickListener {
            startActivity(
                Intent(this, AddReminderActivity::class.java)
                    .putExtra("isUrdu", isUrdu)
            )
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            if (isCaregiver) showCaregiverOptionsDialog(position)
            else speakReminder(position)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(if (isUrdu) Locale("ur", "PK") else Locale.US)
        }
    }

    private fun loadAndDisplayReminders() {
        try {
            reminderList = ReminderStorage.loadReminders(this)
            val data = ArrayList<Map<String, String>>()

            for (i in 0 until reminderList.length()) {
                val json = reminderList.getJSONObject(i)
                val reminderDate = json.optString("date", "Daily")

                // ✅ Agar purani date hai toh list mein mat dikhao
                if (reminderDate != "Daily") {
                    if (isPastDate(reminderDate)) continue
                }

                val map = HashMap<String, String>()
                val uTitle = json.optString("titleUrdu", "")
                val eTitle = json.optString("title", "")
                map["title"] = if (isUrdu && uTitle.isNotEmpty()) uTitle else eTitle

                val timeText = json.optString("timeText", "")

                if (isUrdu) {
                    val urduDate = if (reminderDate == "Daily") "روزانہ" else reminderDate
                    val urduTime = timeText.replace("AM", "صبح").replace("PM", "شام")
                    map["time"] = "$urduDate — $urduTime"
                } else {
                    map["time"] = if (reminderDate == "Daily") "Daily — $timeText" else "$reminderDate — $timeText"
                }
                data.add(map)
            }

            listView.adapter = SimpleAdapter(
                this, data, R.layout.item_reminder,
                arrayOf("title", "time"), intArrayOf(R.id.tvTitle, R.id.tvTime)
            )
        } catch (e: Exception) {
            android.util.Log.e("RemindersActivity", "Load Error: ${e.message}")
        }
    }

    // ✅ Purani date check karne ka helper function
    private fun isPastDate(dateStr: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val reminderDate = sdf.parse(dateStr)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            reminderDate != null && reminderDate.before(today)
        } catch (e: Exception) {
            false
        }
    }

    private fun speakReminder(position: Int) {
        try {
            val reminder = reminderList.getJSONObject(position)
            val uTitle = reminder.optString("titleUrdu")
            val eTitle = reminder.optString("title")
            val textToSpeak = if (isUrdu && uTitle.isNotEmpty()) "یاد دہانی: $uTitle" else "Reminder: $eTitle"
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "REM_$position")
        } catch (e: Exception) { }
    }

    private fun showCaregiverOptionsDialog(position: Int) {
        val options = if (isUrdu) arrayOf("تبدیل کریں", "ڈیلیٹ کریں") else arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle(if (isUrdu) "اختیارات" else "Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editReminder(position)
                    1 -> confirmDeleteReminder(position)
                }
            }.show()
    }

    private fun editReminder(position: Int) {
        val reminder = reminderList.getJSONObject(position)
        startActivity(
            Intent(this, AddReminderActivity::class.java).apply {
                putExtra("editIndex", position)
                putExtra("editName", reminder.optString("title"))
                putExtra("editNameUrdu", reminder.optString("titleUrdu"))
                putExtra("isUrdu", isUrdu)
                putExtra("editHour", reminder.optInt("hour", -1))
                putExtra("editMinute", reminder.optInt("minute", 0))
                putExtra("editIsDaily", reminder.optBoolean("isDaily", true))
                putExtra("editDate", reminder.optString("date", ""))
            }
        )
    }

    private fun confirmDeleteReminder(position: Int) {
        AlertDialog.Builder(this)
            .setTitle(if (isUrdu) "ڈیلیٹ کریں؟" else "Delete?")
            .setMessage(if (isUrdu) "کیا آپ واقعی یہ یاد دہانی ہٹانا چاہتے ہیں؟"
            else "Are you sure you want to delete this reminder?")
            .setPositiveButton(if (isUrdu) "ہاں" else "Yes") { _, _ -> deleteReminder(position) }
            .setNegativeButton(if (isUrdu) "نہیں" else "No", null)
            .show()
    }

    private fun deleteReminder(position: Int) {
        try {
            val reminder = reminderList.getJSONObject(position)
            val reminderId = reminder.optInt("id", -1)

            if (reminderId != -1) {
                NotificationHelper(this).cancelReminder(reminderId)
                val snoozeIntent = Intent(this, ReminderBroadcastReceiver::class.java)
                val snoozePi = android.app.PendingIntent.getBroadcast(
                    this, reminderId + 999, snoozeIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(snoozePi)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(reminderId)

                getSharedPreferences("reminders", MODE_PRIVATE).edit().remove("interacted_$reminderId").apply()
            }

            val newList = JSONArray()
            for (i in 0 until reminderList.length()) {
                if (i != position) newList.put(reminderList.get(i))
            }
            ReminderStorage.saveReminders(this, newList)
            loadAndDisplayReminders()

            Toast.makeText(this, if (isUrdu) "ختم ہو گیا ✅" else "Deleted ✅", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("RemindersActivity", "Delete error: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayReminders()
        NotificationHelper(this).clearAllNotifications()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}