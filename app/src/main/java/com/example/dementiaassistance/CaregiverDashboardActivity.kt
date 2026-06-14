package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CaregiverDashboardActivity : AppCompatActivity() {

    private var isUrdu = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var linkedPatientId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_dashboard)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val appPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        isUrdu = intent.getBooleanExtra("isUrdu", appPrefs.getBoolean("isUrdu", false))

        updateUI()
        loadPatientCode()

        findViewById<View>(R.id.cardReminders).setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java)
                .putExtra("isUrdu", isUrdu)
                .putExtra("isCaregiver", true))
        }

        findViewById<View>(R.id.cardLocation).setOnClickListener {
            startActivity(Intent(this, LocationMonitorActivity::class.java).putExtra("isUrdu", isUrdu))
        }

        findViewById<View>(R.id.cardEmergency).setOnClickListener {
            startActivity(Intent(this, EmergencySettingsActivity::class.java).putExtra("isUrdu", isUrdu))
        }

        findViewById<View>(R.id.cardPhotos).setOnClickListener {
            startActivity(Intent(this, FamilyPhotosActivity::class.java)
                .putExtra("isUrdu", isUrdu)
                .putExtra("isCaregiverMode", true))
        }

        findViewById<View>(R.id.cardExSettings).setOnClickListener {
            startActivity(Intent(this, ExerciseSettingsActivity::class.java).putExtra("isUrdu", isUrdu))
        }

        findViewById<View>(R.id.cardExReport).setOnClickListener {
            if (linkedPatientId != null) {
                val intent = Intent(this, ExerciseReportActivity::class.java)
                intent.putExtra("isUrdu", isUrdu)
                intent.putExtra("patientId", linkedPatientId)
                startActivity(intent)
            } else {
                val msg = if (isUrdu) "ڈیٹا لوڈ ہو رہا ہے..." else "Loading data..."
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                loadPatientCode()
            }
        }

        findViewById<ImageButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, CaregiverLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadPatientCode() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("caregivers").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val code = doc.getString("patientCode") ?: "------"
                        linkedPatientId = doc.getString("linkedPatientId")
                        findViewById<TextView>(R.id.txtPatientCode)?.text =
                            if (isUrdu) "پیشنٹ لنک کوڈ: $code" else "Patient Link Code: $code"
                    }
                }
        }
    }

    private fun updateUI() {
        findViewById<TextView>(R.id.txtCaregiverTitle)?.text =
            if (isUrdu) "کیئرگیور ڈیش بورڈ" else "Caregiver Dashboard"

        findViewById<TextView>(R.id.tvRemindersLabel)?.text =
            if (isUrdu) "یاد دہانی" else "Reminders"

        findViewById<TextView>(R.id.tvLocationLabel)?.text =
            if (isUrdu) "مقام" else "Location"

        findViewById<TextView>(R.id.tvPhotosLabel)?.text =
            if (isUrdu) "تصاویر" else "Photos"

        findViewById<TextView>(R.id.tvEmergencyLabel)?.text =
            if (isUrdu) "ایمرجنسی" else "Emergency"

        // Yeh 2 lines update karo:

        findViewById<TextView>(R.id.tvExercisesLabel)?.text =
            if (isUrdu) "ورزش کی ترتیبات" else "Exercise Settings"

        findViewById<TextView>(R.id.tvReportsLabel)?.text =
            if (isUrdu) "ورزش کی رپورٹ" else "Exercise Reports"
}}