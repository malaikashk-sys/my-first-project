package com.example.dementiaassistance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val title = intent.getStringExtra("reminderTitle") ?: "Reminder"
        val titleUr = intent.getStringExtra("reminderTitleUrdu") ?: ""
        val timeText = intent.getStringExtra("reminderTime") ?: ""
        val isUrdu = intent.getBooleanExtra("isUrdu", false)
        val id = intent.getIntExtra("reminderId", 0)
        val isDaily = intent.getBooleanExtra("isDaily", true)
        val isAutoSnoozed = intent.getBooleanExtra("isAutoSnoozed", false)
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", 0)

        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

        // ✅ Caregiver already alert ho gaya hai toh dobara fire mat karo
        val caregiverAlerted = prefs.getBoolean("caregiver_alerted_$id", false)
        if (caregiverAlerted && isAutoSnoozed) {
            Log.d("ReminderReceiver", "⛔ Caregiver already alerted for ID $id — skipping snooze fire")
            return
        }

        // ✅ interacted flag reset
        prefs.edit().putBoolean("interacted_$id", false).apply()

        try {
            val helper = NotificationHelper(context)
            helper.showAlarmNotification(id, title, titleUr, isUrdu, timeText)

            val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                putExtra("reminderTitle", title)
                putExtra("reminderTitleUrdu", titleUr)
                putExtra("reminderTime", timeText)
                putExtra("reminderId", id)
                putExtra("isUrdu", isUrdu)
                putExtra("isAutoSnoozed", isAutoSnoozed)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(alarmIntent)

            Log.d("ReminderReceiver", "✅ Launched: $title | Time: $timeText")

            // ✅ Followup sirf tab schedule karo jab caregiver alert nahi hua
            if (!isAutoSnoozed && !caregiverAlerted) {
                helper.scheduleCaregiverFollowupNow(id, title, titleUr, isUrdu)
            }


            // ✅ Daily reminder — kal ke liye reschedule + flag reset
            if (isDaily && !isAutoSnoozed && hour != -1) {
                helper.scheduleReminderDaily(id, title, titleUr, hour, minute, isUrdu)
                prefs.edit().putBoolean("caregiver_alerted_$id", false).apply()
                Log.d("ReminderReceiver", "✅ Rescheduled for tomorrow: $title")
            }

// ✅ One-time reminder — sirf tab delete karo
            else if (!isDaily) {
                deleteOneTimeReminder(context, id)
            }

// ✅ isAutoSnoozed=true wala case — kuch mat karo (snooze already scheduled hai)

        } catch (e: Exception) {
            Log.e("ReminderReceiver", "❌ Error: ${e.message}")
        }
    }

    private fun deleteOneTimeReminder(context: Context, reminderId: Int) {
        try {
            val reminders = ReminderStorage.loadReminders(context)
            val newList = org.json.JSONArray()
            var found = false
            for (i in 0 until reminders.length()) {
                val json = reminders.getJSONObject(i)
                if (json.optInt("id") == reminderId && !json.optBoolean("isDaily", true)) {
                    found = true
                } else {
                    newList.put(json)
                }
            }
            if (found) {
                ReminderStorage.saveReminders(context, newList)
                Log.d("ReminderReceiver", "✅ One-time deleted (ID: $reminderId)")
            }
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "❌ Delete error: ${e.message}")
        }
    }
}