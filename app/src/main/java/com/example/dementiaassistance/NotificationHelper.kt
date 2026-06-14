package com.example.dementiaassistance

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.*

class NotificationHelper(private val context: Context) {

    private val CHANNEL_ID = "REMINDER_CHANNEL_ID"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val format = if (hour >= 12) "PM" else "AM"
        val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        return String.format("%02d:%02d %s", displayHour, minute, format)
    }

    fun scheduleReminderDaily(
        id: Int, title: String, titleUr: String,
        hour: Int, minute: Int, isUrdu: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeText = formatTime(hour, minute)

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("reminderTitle", title)
            putExtra("reminderTitleUrdu", titleUr)
            putExtra("reminderId", id)
            putExtra("isUrdu", isUrdu)
            putExtra("isDaily", true)
            putExtra("reminderTime", timeText)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pi
        )
    }

    fun scheduleReminderOnce(
        id: Int, title: String, titleUr: String,
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, isUrdu: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeText = formatTime(hour, minute)

        // 1. Pehle calendar ko setup karein (Initialize)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 2. Ab check karein ke kya ye guzra hua waqt hai? (Usage)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            android.util.Log.d("NotificationHelper", "Past time! Alarm nahi lagega.")
            return
        }

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("reminderTitle", title)
            putExtra("reminderTitleUrdu", titleUr)
            putExtra("reminderId", id)
            putExtra("isUrdu", isUrdu)
            putExtra("isDaily", false)
            putExtra("reminderTime", timeText)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val pi = PendingIntent.getBroadcast(
            context, id + 5000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Ab alarm schedule karein
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pi
        )

        android.util.Log.d("NotificationHelper", "📅 One-time scheduled: $title")
    }
    fun scheduleReminderWithSnooze(
        id: Int, title: String, titleUr: String,
        hour: Int, minute: Int, isUrdu: Boolean
    ) {
        scheduleReminderDaily(id, title, titleUr, hour, minute, isUrdu)
    }

    fun scheduleCaregiverFollowupNow(
        id: Int, title: String, titleUr: String, isUrdu: Boolean
    ) {
        scheduleCaregiverFollowup(
            id, title, titleUr, isUrdu,
            triggerAtMillis = System.currentTimeMillis() + (5 * 60 * 1000L)
        )
    }

    private fun scheduleCaregiverFollowup(
        id: Int, title: String, titleUr: String,
        isUrdu: Boolean, triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderFollowupReceiver::class.java).apply {
            putExtra("reminderId", id)
            putExtra("title", title)
            putExtra("titleUrdu", titleUr)
            putExtra("isUrdu", isUrdu)
            putExtra("isSecondChance", true)
        }

        val pi = PendingIntent.getBroadcast(
            context, id + 10000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pi
        )
    }

    fun cancelReminder(reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val receiverIntent = Intent(context, ReminderBroadcastReceiver::class.java)

        val pi1 = PendingIntent.getBroadcast(
            context, reminderId, receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi1)

        val pi2 = PendingIntent.getBroadcast(
            context, reminderId + 5000, receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi2)

        val followupIntent = Intent(context, ReminderFollowupReceiver::class.java)
        val pi3 = PendingIntent.getBroadcast(
            context, reminderId + 10000, followupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi3)

        android.util.Log.d("NotificationHelper", "🗑️ Cancelled all for ID: $reminderId")
    }

    fun showAlarmNotification(
        reminderId: Int, titleEn: String, titleUr: String,
        isUrdu: Boolean, timeText: String = ""
    ) {
        val activityIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra("reminderTitle", titleEn)
            putExtra("reminderTitleUrdu", titleUr)
            putExtra("reminderId", reminderId)
            putExtra("isUrdu", isUrdu)
            putExtra("reminderTime", timeText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, reminderId, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ Notification dismiss action — seedha notification se OK kar sako
        val dismissIntent = Intent(context, ReminderDismissReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            reminderId + 20000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (isUrdu && titleUr.isNotEmpty()) titleUr else titleEn
        val displayText = if (timeText.isNotEmpty()) {
            if (isUrdu) "وقت: $timeText" else "Time: $timeText"
        } else {
            if (isUrdu) "فوری یاد دہانی!" else "Urgent Reminder!"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)


        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(reminderId, builder.build())
    }
    // NotificationHelper.kt ke andar end mein add karein
    fun clearAllNotifications() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }
}