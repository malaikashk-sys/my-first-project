package com.example.dementiaassistance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

object CaregiverNotifier {

    private const val CHANNEL_ID = "caregiver_channel"
    private val db = FirebaseFirestore.getInstance()

    fun sendCaregiverAlert(context: Context, title: String, details: String) {

        // ✅ 1. Local Notification (patient ke phone pe bhi show ho — same device mode safe)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Caregiver Alerts", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Missed Reminder")
            .setContentText("$title: $details")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)

        // ✅ 2. Firestore alert (caregiver ka phone fetch kar ke dekhega)
        sendAlertToCloud(title, details)

        // ✅ 3. SMS: linkedCaregiverId se caregiver ka phone number fetch karo
        val appPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val linkedCaregiverId = appPrefs.getString("linkedCaregiverId", null)

        if (!linkedCaregiverId.isNullOrEmpty()) {
            EmergencySettingsActivity.fetchCaregiverNumber(context, linkedCaregiverId) { caregiverPhone ->
                if (caregiverPhone.isNotEmpty() && caregiverPhone != EmergencySettingsActivity.DEFAULT_NUMBER) {
                    sendSmsToCaregiver(caregiverPhone, title, details)
                }
            }
        }
    }

    private fun sendSmsToCaregiver(phoneNumber: String, title: String, details: String) {
        try {
            val msg = "Patient Reminder Alert: $title — $details"
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                android.app.Application().getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()

            smsManager?.sendTextMessage(phoneNumber, null, msg, null, null)
            Log.d("CaregiverNotifier", "✅ SMS sent to caregiver: $phoneNumber")
        } catch (e: Exception) {
            Log.e("CaregiverNotifier", "❌ SMS failed: ${e.message}")
        }
    }

    private fun sendAlertToCloud(title: String, details: String) {
        try {
            val alert = hashMapOf(
                "title"     to title,
                "message"   to details,
                "timestamp" to FieldValue.serverTimestamp(),
                "status"    to "unread"
            )
            db.collection("Alerts").add(alert)
                .addOnSuccessListener { Log.d("CloudAlert", "✅ Alert sent to Firestore") }
                .addOnFailureListener { e -> Log.e("CloudAlert", "❌ Failed: $e") }
        } catch (e: Exception) {
            Log.e("CaregiverNotifier", "❌ Cloud alert error: ${e.message}")
        }
    }
}