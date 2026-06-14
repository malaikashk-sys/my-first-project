package com.example.dementiaassistance

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View

import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.button.MaterialButton
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class AddReminderActivity : AppCompatActivity() {

    private var isUrdu = false
    private var selectedHour = -1
    private var selectedMinute = -1
    private var selectedYear = -1
    private var selectedMonth = -1
    private var selectedDay = -1
    private var selectedDateStr: String? = null
    private var selectedTimeStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_reminder)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val etEnglish = findViewById<EditText>(R.id.etReminderTitle)
        val etUrdu = findViewById<EditText>(R.id.etReminderTitleUrdu)
        val switchDate = findViewById<SwitchMaterial>(R.id.switchSpecificDate)
        val btnSave = findViewById<Button>(R.id.btnSaveReminder)
        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        val btnCancel = findViewById<Button>(R.id.btnCancelReminder)
        val txtDate = findViewById<TextView>(R.id.txtSelectedDate)
        val txtTime = findViewById<TextView>(R.id.txtSelectedTime)
        val layoutDate = findViewById<LinearLayout>(R.id.layoutDatePicker)

        // ─── EDIT MODE ────────────────────────────────────────────────────────
        val editIndex = intent.getIntExtra("editIndex", -1)
        val isEditMode = editIndex != -1

        if (isEditMode) {
            etEnglish.setText(intent.getStringExtra("editName") ?: "")
            etUrdu.setText(intent.getStringExtra("editNameUrdu") ?: "")

            selectedHour = intent.getIntExtra("editHour", -1)
            selectedMinute = intent.getIntExtra("editMinute", 0)

            if (selectedHour != -1) {
                val fmt = if (selectedHour >= 12) "PM" else "AM"
                val dh = if (selectedHour > 12) selectedHour - 12
                else if (selectedHour == 0) 12 else selectedHour
                selectedTimeStr = String.format("%02d:%02d %s", dh, selectedMinute, fmt)
                txtTime.text = selectedTimeStr
            }

            val isDaily = intent.getBooleanExtra("editIsDaily", true)
            switchDate.isChecked = !isDaily
            if (!isDaily) {
                layoutDate.visibility = View.VISIBLE
                val dateStr = intent.getStringExtra("editDate") ?: ""
                if (dateStr.contains("/")) {
                    txtDate.text = dateStr
                    selectedDateStr = dateStr
                    val parts = dateStr.split("/")
                    if (parts.size == 3) {
                        selectedDay = parts[0].toIntOrNull() ?: -1
                        selectedMonth = (parts[1].toIntOrNull() ?: 1) - 1
                        selectedYear = parts[2].toIntOrNull() ?: -1
                    }
                }
            }
        }

        // ─── DATE SWITCH ──────────────────────────────────────────────────────
        switchDate.setOnCheckedChangeListener { _, isChecked ->
            layoutDate.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // ─── DATE PICKER ──────────────────────────────────────────────────────
        btnPickDate.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                selectedDateStr = String.format("%02d/%02d/%04d", day, month + 1, year)
                txtDate.text = selectedDateStr
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        // ─── TIME PICKER ──────────────────────────────────────────────────────
        btnPickTime.setOnClickListener {
            val c = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                val fmt = if (hour >= 12) "PM" else "AM"
                val dh = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
                selectedTimeStr = String.format("%02d:%02d %s", dh, minute, fmt)
                txtTime.text = selectedTimeStr
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }

        // ─── CANCEL ───────────────────────────────────────────────────────────
        btnCancel.setOnClickListener {
            finish()
        }

        // ─── SAVE ─────────────────────────────────────────────────────────────
        btnSave.setOnClickListener {
            val titleEng = etEnglish.text.toString().trim()
            val titleUrdu = etUrdu.text.toString().trim()

            if (titleEng.isEmpty() && titleUrdu.isEmpty()) {
                Toast.makeText(this, "Please enter reminder title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedHour == -1) {
                Toast.makeText(this, "Please select time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isDaily = !switchDate.isChecked

            if (!isDaily && (selectedYear == -1 || selectedDay == -1)) {
                Toast.makeText(this, "Please select date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val currentReminders = ReminderStorage.loadReminders(this)
                val helper = NotificationHelper(this)

                // ✅ Edit mode: purana delete karo
                if (isEditMode) {
                    val oldReminder = currentReminders.optJSONObject(editIndex)
                    if (oldReminder != null) {
                        helper.cancelReminder(oldReminder.optInt("id"))
                    }
                    val newList = org.json.JSONArray()
                    for (i in 0 until currentReminders.length()) {
                        if (i != editIndex) newList.put(currentReminders.get(i))
                    }
                    ReminderStorage.saveReminders(this, newList)
                }

                // ✅ Naya reminder ID
                val reminderId = (System.currentTimeMillis()/1000).toInt()

                // ✅ Alarm schedule
                if (isDaily) {
                    helper.scheduleReminderWithSnooze(
                        reminderId, titleEng, titleUrdu,
                        selectedHour, selectedMinute, isUrdu
                    )
                } else {
                    helper.scheduleReminderOnce(
                        reminderId, titleEng, titleUrdu,
                        selectedYear, selectedMonth, selectedDay,
                        selectedHour, selectedMinute, isUrdu
                    )
                }

                // ✅ Storage mein save
                val updatedList = ReminderStorage.loadReminders(this)
                val newReminder = org.json.JSONObject().apply {
                    put("id", reminderId)
                    put("title", titleEng)
                    put("titleUrdu", titleUrdu)
                    put("hour", selectedHour)
                    put("minute", selectedMinute)
                    put("isDaily", isDaily)
                    put("date", if (isDaily) "Daily" else selectedDateStr)
                    put("timeText", selectedTimeStr)
                }
                updatedList.put(newReminder)
                ReminderStorage.saveReminders(this, updatedList)

                Toast.makeText(
                    this,
                    if (isEditMode) "Reminder updated ✅" else "Reminder saved ✅",
                    Toast.LENGTH_SHORT
                ).show()
                finish()

            } catch (e: Exception) {
                Log.e("AddReminder", "Error: ${e.message}")
                Toast.makeText(this, "Error saving reminder", Toast.LENGTH_SHORT).show()
            }
        }
    }
}