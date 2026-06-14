package com.example.dementiaassistance

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray

class ReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("reminderId", 0)

        // ✅ interacted = true — followup caregiver ko alert nahi karega
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("interacted_$id", true)
            .apply()

        // ─── AUTO-DELETE LOGIC FOR SPECIFIC DATE REMINDERS ──────────────────
        try {
            val reminders = ReminderStorage.loadReminders(context)
            val newList = JSONArray()
            var wasFoundAndOneTime = false

            for (i in 0 until reminders.length()) {
                val item = reminders.getJSONObject(i)
                if (item.optInt("id") == id) {
                    val isDaily = item.optBoolean("isDaily", true)
                    // Agar daily hai to list mein rehne do, agar specific date hai to list mein mat dalo (delete)
                    if (isDaily) {
                        newList.put(item)
                    } else {
                        wasFoundAndOneTime = true
                    }
                } else {
                    newList.put(item)
                }
            }

            if (wasFoundAndOneTime) {
                ReminderStorage.saveReminders(context, newList)
                Log.d("DismissReceiver", "🗑️ Specific date reminder $id deleted from storage")
            }
        } catch (e: Exception) {
            Log.e("DismissReceiver", "Error cleaning up storage: ${e.message}")
        }
        // ─────────────────────────────────────────────────────────────────────

        // ✅ Notification dismiss karo
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id)

        // ✅ Pending followup bhi cancel karo
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val followupIntent = Intent(context, ReminderFollowupReceiver::class.java)
        val pi = android.app.PendingIntent.getBroadcast(
            context,
            id + 10000,
            followupIntent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let {
            alarmManager.cancel(it)
            it.cancel() // Intent ko bhi cancel karein
            Log.d("DismissReceiver", "✅ Dismissed reminder ID: $id")
        }
    }
}