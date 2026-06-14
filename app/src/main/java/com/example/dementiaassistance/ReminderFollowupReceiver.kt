package com.example.dementiaassistance

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderFollowupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("reminderId", 0)
        val title = intent.getStringExtra("title") ?: "Reminder"
        val titleUrdu = intent.getStringExtra("titleUrdu") ?: title
        val isSecondChance = intent.getBooleanExtra("isSecondChance", false)

        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val interacted = prefs.getBoolean("interacted_$id", false)

        if (!interacted) {
            if (!isSecondChance) {
                Log.d("FollowupReceiver", "First miss for ID $id — waiting for second chance")
            } else {
                // ✅ Doosri baar bhi miss — caregiver ko alert karo
                CaregiverNotifier.sendCaregiverAlert(
                    context = context,
                    title = title,
                    details = "Patient missed reminder twice (no response)"
                )
                Log.d("FollowupReceiver", "✅ Caregiver alerted for ID $id")

                // ✅ Mark karo — is reminder ke liye dobara alert mat karo aaj
                prefs.edit().putBoolean("caregiver_alerted_$id", true).apply()

                // ✅ Pending followup alarm cancel karo — baar baar fire nahi hoga
                cancelPendingFollowup(context, id)
            }
        } else {
            Log.d("FollowupReceiver", "User interacted with ID $id — no caregiver alert")
        }
    }

    private fun cancelPendingFollowup(context: Context, reminderId: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val followupIntent = Intent(context, ReminderFollowupReceiver::class.java)

            // requestCode = reminderId + 10000 (NotificationHelper se match karta hai)
            val pi = PendingIntent.getBroadcast(
                context,
                reminderId + 10000,
                followupIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pi?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d("FollowupReceiver", "✅ Pending followup cancelled for ID $reminderId")
            }
        } catch (e: Exception) {
            Log.e("FollowupReceiver", "❌ Cancel error: ${e.message}")
        }
    }
}