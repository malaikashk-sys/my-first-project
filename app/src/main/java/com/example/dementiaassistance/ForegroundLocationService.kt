package com.example.dementiaassistance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

class ForegroundLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000L // 5 minutes
        ).setMinUpdateIntervalMillis(120000L).build() // 2 minutes minimum

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    saveLocation(location.latitude, location.longitude)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationService", "Permission denied: ${e.message}")
        }
    }

    private fun saveLocation(lat: Double, lng: Double) {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val patientId = prefs.getString("patientId", null) ?: return

        val data = hashMapOf(
            "latitude"    to lat,
            "longitude"   to lng,
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        db.collection("locations").document(patientId).set(data)
            .addOnSuccessListener {
                Log.d("LocationService", "✅ Background location saved: $lat, $lng")
            }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dementia Assistance")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_MIN)  // ← MIN karo
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // ← SECRET karo
            .build()
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_MIN  // ← MIN karo
            )
            channel.setShowBadge(false)  // ← badge mat dikho
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}