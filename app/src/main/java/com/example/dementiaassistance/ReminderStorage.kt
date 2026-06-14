package com.example.dementiaassistance

import android.content.Context
import android.util.Log
import org.json.JSONArray

object ReminderStorage {
    private const val TAG = "ReminderStorage"

    fun loadReminders(context: Context): JSONArray {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val json = prefs.getString("data", "[]")
        val array = JSONArray(json)
        Log.d(TAG, "✅ Loaded ${array.length()} reminders")
        return array
    }

    fun saveReminders(context: Context, reminders: JSONArray) {
        Log.d(TAG, "💾 Saving ${reminders.length()} reminders")
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        prefs.edit().putString("data", reminders.toString()).apply()
        Log.d(TAG, "✅ Saved to storage")
    }
}