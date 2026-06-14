package com.example.dementiaassistance

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmergencySettingsActivity : AppCompatActivity() {

    private var isUrdu = false
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    companion object {
        const val PREFS_NAME          = "EmergencyPrefs"
        const val KEY_EMERGENCY_NUMBER = "emergencyNumber"
        const val DEFAULT_NUMBER       = "03001234567"

        // ✅ Local se number lo (fast, offline safe)
        fun getSavedNumber(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_EMERGENCY_NUMBER, DEFAULT_NUMBER) ?: DEFAULT_NUMBER
        }

        // ✅ Patient ke liye: Firestore se caregiver ka number fetch karo
        fun fetchCaregiverNumber(context: Context, linkedCaregiverId: String, onResult: (String) -> Unit) {
            val db = FirebaseFirestore.getInstance()
            db.collection("caregivers")
                .document(linkedCaregiverId)
                .get()
                .addOnSuccessListener { doc ->
                    val number = doc.getString("emergencyNumber") ?: getSavedNumber(context)
                    // ✅ Local mein bhi save karo (offline ke liye)
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_EMERGENCY_NUMBER, number).apply()
                    onResult(number)
                }
                .addOnFailureListener {
                    // Internet nahi — local number use karo
                    onResult(getSavedNumber(context))
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_settings)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle       = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle    = findViewById<TextView>(R.id.txtSubtitle)
        val txtCurrentLabel = findViewById<TextView>(R.id.txtCurrentLabel)
        val txtCurrentNumber = findViewById<TextView>(R.id.txtCurrentNumber)
        val etPhoneNumber  = findViewById<EditText>(R.id.etPhoneNumber)
        val btnSaveNumber  = findViewById<Button>(R.id.btnSaveNumber)
        val btnBack        = findViewById<Button>(R.id.btnBack)

        if (isUrdu) {
            txtTitle.text        = "ایمرجنسی رابطہ ترتیبات"
            txtSubtitle.text     = "یہ نمبر مریض کی ایمرجنسی پر SMS اور کال وصول کرے گا"
            txtCurrentLabel.text = "موجودہ ایمرجنسی نمبر:"
            etPhoneNumber.hint   = "فون نمبر درج کریں"
            btnSaveNumber.text   = "💾  نمبر محفوظ کریں"
            btnBack.text         = "واپس"
        }

        // ✅ Pehle local show karo, phir Firestore se update karo
        val savedNumber = getSavedNumber(this)
        txtCurrentNumber.text = savedNumber

        // ✅ Firestore se latest number fetch karo
        val caregiverId = auth.currentUser?.uid
        if (caregiverId != null) {
            db.collection("caregivers").document(caregiverId).get()
                .addOnSuccessListener { doc ->
                    val firestoreNumber = doc.getString("emergencyNumber")
                    if (!firestoreNumber.isNullOrEmpty()) {
                        txtCurrentNumber.text = firestoreNumber
                        // Local bhi update karo
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_EMERGENCY_NUMBER, firestoreNumber).apply()
                    }
                }
        }

        btnSaveNumber.setOnClickListener {
            val newNumber = etPhoneNumber.text.toString().trim()

            if (newNumber.isEmpty()) {
                val msg = if (isUrdu) "نمبر درج کریں" else "Please enter a number"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newNumber.length < 10) {
                val msg = if (isUrdu) "نمبر درست نہیں" else "Invalid number"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ 1. Local mein save karo (same device fast access)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_EMERGENCY_NUMBER, newNumber).apply()

            // ✅ 2. Firestore mein save karo (patient device fetch kar sake)
            if (caregiverId != null) {
                db.collection("caregivers").document(caregiverId)
                    .update("emergencyNumber", newNumber)
                    .addOnFailureListener {
                        // Document exist nahi — set karo
                        db.collection("caregivers").document(caregiverId)
                            .set(mapOf("emergencyNumber" to newNumber))
                    }
            }

            txtCurrentNumber.text = newNumber
            etPhoneNumber.text.clear()

            val msg = if (isUrdu) "نمبر محفوظ ہو گیا!" else "Number saved successfully!"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener { finish() }
    }
}