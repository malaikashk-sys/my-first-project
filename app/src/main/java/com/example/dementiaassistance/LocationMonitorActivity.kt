package com.example.dementiaassistance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationMonitorActivity : AppCompatActivity() {

    private var isUrdu = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var txtTitle: TextView
    private lateinit var txtAddress: TextView
    private lateinit var txtLastUpdate: TextView
    private lateinit var btnGetLocation: Button
    private lateinit var btnOpenMaps: Button
    private lateinit var btnBack: Button

    private var currentLat = 0.0
    private var currentLng = 0.0

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_monitor)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        txtTitle       = findViewById(R.id.txtTitle)
        txtAddress     = findViewById(R.id.txtAddress)
        txtLastUpdate  = findViewById(R.id.txtLastUpdate)
        btnGetLocation = findViewById(R.id.btnGetLocation)
        btnOpenMaps    = findViewById(R.id.btnOpenMaps)
        btnBack        = findViewById(R.id.btnBack)
        val txtLocationLabel = findViewById<TextView>(R.id.txtLocationLabel)

        if (isUrdu) {
            txtTitle.text        = "مریض کی لوکیشن"
            txtLocationLabel.text = "📍 آخری معلوم مقام"
            txtAddress.text      = "لوکیشن حاصل کرنے کے لیے بٹن دبائیں"
            btnGetLocation.text  = "📍  مریض کی لوکیشن حاصل کریں"
            btnOpenMaps.text     = "🗺️  گوگل میپس میں کھولیں"
            btnBack.text         = "واپس"
        }

        btnGetLocation.setOnClickListener { fetchPatientIdAndGetLocation() }

        btnOpenMaps.setOnClickListener {
            if (currentLat != 0.0 && currentLng != 0.0) {
                val uri = Uri.parse("geo:$currentLat,$currentLng?q=$currentLat,$currentLng")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } else {
                Toast.makeText(this,
                    if (isUrdu) "پہلے لوکیشن حاصل کریں" else "Please get location first",
                    Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { finish() }
    }

    // ✅ Caregiver ke Firestore document se linkedPatientId fetch karo
    private fun fetchPatientIdAndGetLocation() {
        txtAddress.text = if (isUrdu) "لوکیشن حاصل کی جا رہی ہے..." else "Fetching location..."

        val caregiverUid = FirebaseAuth.getInstance().currentUser?.uid
        if (caregiverUid == null) {
            txtAddress.text = if (isUrdu) "لاگ ان نہیں ہے" else "Not logged in"
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("caregivers").document(caregiverUid)
            .get()
            .addOnSuccessListener { doc ->
                val patientId = doc.getString("linkedPatientId")
                if (patientId.isNullOrEmpty()) {
                    txtAddress.text = if (isUrdu)
                        "مریض ابھی تک لنک نہیں ہوا"
                    else
                        "Patient not linked yet"
                } else {
                    getLocationFromFirestore(patientId)
                }
            }
            .addOnFailureListener { e ->
                txtAddress.text = "Error: ${e.message}"
            }
    }

    // ✅ Patient ka location Firestore se fetch karo
    private fun getLocationFromFirestore(patientId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("locations").document(patientId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentLat = document.getDouble("latitude") ?: 0.0
                    currentLng = document.getDouble("longitude") ?: 0.0

                    val timestamp = document.getTimestamp("lastUpdated")?.toDate() ?: Date()
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

                    txtLastUpdate.text = if (isUrdu)
                        "آخری اپڈیٹ: ${timeFormat.format(timestamp)}"
                    else
                        "Last updated: ${timeFormat.format(timestamp)}"

                    updateAddressFromCoords(currentLat, currentLng)
                } else {
                    txtAddress.text = if (isUrdu) "ڈیٹا نہیں ملا" else "No location data yet"
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationMonitor", "Error: ${e.message}")
                txtAddress.text = "Error: ${e.message}"
            }
    }

    private fun updateAddressFromCoords(lat: Double, lng: Double) {
        try {
            val geocoder  = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val addressStr = "${address.subLocality ?: ""}, ${address.locality ?: ""}, ${address.adminArea ?: ""}"
                txtAddress.text = addressStr.trim(',', ' ')
            } else {
                txtAddress.text = "$lat, $lng"
            }
        } catch (e: Exception) {
            txtAddress.text = "$lat, $lng"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchPatientIdAndGetLocation()
        }
    }
}