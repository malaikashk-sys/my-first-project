package com.example.dementiaassistance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.*

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot event: ${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            rescheduleReminders(context)
        }
    }

    private fun rescheduleReminders(context: Context) {
        val reminders = ReminderStorage.loadReminders(context)
        Log.d("BootReceiver", "Found ${reminders.length()} reminders to reschedule")

        val helper = NotificationHelper(context)
        var count = 0

        for (i in 0 until reminders.length()) {
            try {
                val reminder = reminders.getJSONObject(i)

                val title = reminder.optString("title", "")
                val titleUrdu = reminder.optString("titleUrdu", title)
                val hour = reminder.optInt("hour", 9)
                val minute = reminder.optInt("minute", 0)
                val isUrdu = reminder.optBoolean("isUrdu", false)
                val isSpecificDate = reminder.optBoolean("isSpecificDate", false)

                if (title.isEmpty()) {
                    Log.w("BootReceiver", "Skipping reminder $i — no title")
                    continue
                }

                // Check if it's a daily or one-time reminder
                if (isSpecificDate) {
                    // ONE-TIME reminder with specific date
                    val year = reminder.optInt("year", Calendar.getInstance().get(Calendar.YEAR))
                    val month = reminder.optInt("month", Calendar.getInstance().get(Calendar.MONTH))
                    val day = reminder.optInt("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

                    // Use scheduleReminderOnce for specific date
                    helper.scheduleReminderOnce(
                        id = i,
                        title = title,
                        titleUr = titleUrdu,
                        year = year,
                        month = month,
                        day = day,
                        hour = hour,
                        minute = minute,
                        isUrdu = isUrdu
                    )

                    Log.d("BootReceiver", "✅ Rescheduled ONE-TIME: $title for $day/${month+1}/$year at $hour:$minute")
                } else {
                    // DAILY reminder (no specific date)
                    // Use scheduleReminderDaily
                    helper.scheduleReminderDaily(
                        id = i,
                        title = title,
                        titleUr = titleUrdu,
                        hour = hour,
                        minute = minute,
                        isUrdu = isUrdu
                    )

                    Log.d("BootReceiver", "✅ Rescheduled DAILY: $title at $hour:$minute")
                }

                count++

            } catch (e: Exception) {
                Log.e("BootReceiver", "Error rescheduling reminder $i", e)
            }
        }

        Log.d("BootReceiver", "✅ Successfully rescheduled $count reminders after boot")
    }
}