package com.example.dementiaassistance

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notificationId = intent?.getIntExtra("notificationId", -1) ?: return
        if (notificationId == -1) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }
}