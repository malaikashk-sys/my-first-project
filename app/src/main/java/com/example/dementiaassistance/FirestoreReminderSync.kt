package com.example.dementiaassistance

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

object FirestoreReminderSync {

    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "FirestoreSync"

    // ✅ Caregiver — reminder Firestore mein save kare
    fun saveReminderToFirestore(
        caregiverId: String,
        reminderId: Int,
        title: String,
        titleUrdu: String,
        hour: Int,
        minute: Int,
        isDaily: Boolean,
        date: String = "Daily"
    ) {
        val reminderData = hashMapOf(
            "id" to reminderId,
            "title" to title,
            "titleUrdu" to titleUrdu,
            "hour" to hour,
            "minute" to minute,
            "isDaily" to isDaily,
            "date" to date,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("caregivers")
            .document(caregiverId)
            .collection("reminders")
            .document(reminderId.toString())
            .set(reminderData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reminder saved to Firestore: $title")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore save error: ${e.message}")
            }
    }

    // ✅ Caregiver — reminder Firestore se delete kare
    fun deleteReminderFromFirestore(caregiverId: String, reminderId: Int) {
        db.collection("caregivers")
            .document(caregiverId)
            .collection("reminders")
            .document(reminderId.toString())
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "✅ Reminder deleted from Firestore: $reminderId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore delete error: ${e.message}")
            }
    }

    // ✅ Patient — linked caregiver ki reminders fetch kare aur local schedule kare
    fun fetchAndSyncReminders(context: Context, caregiverId: String, isUrdu: Boolean) {
        db.collection("caregivers")
            .document(caregiverId)
            .collection("reminders")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d(TAG, "No reminders found for caregiver: $caregiverId")
                    return@addOnSuccessListener
                }

                val helper = NotificationHelper(context)
                val localReminders = JSONArray()

                for (doc in documents) {
                    val id = doc.getLong("id")?.toInt() ?: continue
                    val title = doc.getString("title") ?: continue
                    val titleUr = doc.getString("titleUrdu") ?: ""
                    val hour = doc.getLong("hour")?.toInt() ?: continue
                    val minute = doc.getLong("minute")?.toInt() ?: 0
                    val isDaily = doc.getBoolean("isDaily") ?: true
                    val date = doc.getString("date") ?: "Daily"

                    // ✅ Local alarm schedule karo
                    if (isDaily) {
                        helper.scheduleReminderDaily(id, title, titleUr, hour, minute, isUrdu)
                    } else if (date.contains("/")) {
                        val parts = date.split("/")
                        if (parts.size == 3) {
                            val day = parts[0].toIntOrNull() ?: 1
                            val month = (parts[1].toIntOrNull() ?: 1) - 1
                            val year = parts[2].toIntOrNull() ?: 2024
                            helper.scheduleReminderOnce(id, title, titleUr, year, month, day, hour, minute, isUrdu)
                        }
                    }

                    // ✅ Local storage mein bhi save karo
                    val json = JSONObject().apply {
                        put("id", id)
                        put("title", title)
                        put("titleUrdu", titleUr)
                        put("hour", hour)
                        put("minute", minute)
                        put("isDaily", isDaily)
                        put("date", date)
                    }
                    localReminders.put(json)

                    Log.d(TAG, "✅ Synced reminder: $title at $hour:$minute")
                }

                // ✅ Local storage update karo
                ReminderStorage.saveReminders(context, localReminders)
                Log.d(TAG, "✅ Total ${localReminders.length()} reminders synced from Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore fetch error: ${e.message}")
            }
    }
}