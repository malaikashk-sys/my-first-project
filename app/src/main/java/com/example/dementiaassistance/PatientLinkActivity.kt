package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class PatientLinkActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var isUrdu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_link)
        supportActionBar?.hide()

        db = FirebaseFirestore.getInstance()
        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle    = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)
        val etCode      = findViewById<EditText>(R.id.etCode)
        val btnLink     = findViewById<Button>(R.id.btnLink)
        val txtStatus   = findViewById<TextView>(R.id.txtStatus)

        if (isUrdu) {
            txtTitle.text    = "کیئرگیور سے جڑیں"
            txtSubtitle.text = "کیئرگیور کا دیا ہوا\n6 ہندسوں کا کوڈ درج کریں"
            etCode.hint      = "مثال: 616938"
            btnLink.text     = "جوڑیں"
        }

        btnLink.setOnClickListener {
            val code = etCode.text.toString().trim()

            if (code.isEmpty() || code.length != 6) {
                etCode.error = if (isUrdu) "6 ہندسوں کا کوڈ درج کریں" else "Enter 6-digit code"
                etCode.requestFocus()
                return@setOnClickListener
            }

            btnLink.isEnabled = false
            btnLink.text      = if (isUrdu) "تلاش ہو رہا ہے..." else "Searching..."
            txtStatus.text    = if (isUrdu) "کیئرگیور تلاش ہو رہا ہے..." else "Looking for caregiver..."

            db.collection("caregivers")
                .whereEqualTo("patientCode", code)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        btnLink.isEnabled = true
                        btnLink.text      = if (isUrdu) "جوڑیں" else "Link Now"
                        txtStatus.text    = ""
                        Toast.makeText(this,
                            if (isUrdu) "غلط کوڈ! دوبارہ کوشش کریں" else "Wrong code! Try again",
                            Toast.LENGTH_LONG).show()
                        etCode.text.clear()
                    } else {
                        val caregiverDoc  = documents.first()
                        val caregiverId   = caregiverDoc.id
                        val caregiverName = caregiverDoc.getString("name") ?: "Caregiver"

                        // ✅ Unique patient ID — device ID use karo
                        val patientId = android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: "patient_${System.currentTimeMillis()}"

                        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                        prefs.edit()
                            .putString("linkedCaregiverId", caregiverId)
                            .putString("caregiverName", caregiverName)
                            .putString("patientCode", code)
                            .putString("patientId", patientId)  // ✅ Location tracking ke liye
                            .putBoolean("isLinked", true)
                            .apply()

                        // ✅ Caregiver ke Firestore document mein patientId save karo
                        db.collection("caregivers").document(caregiverId)
                            .update("linkedPatientId", patientId)
                            .addOnFailureListener {
                                // Document exist nahi — set karo
                                db.collection("caregivers").document(caregiverId)
                                    .set(mapOf("linkedPatientId" to patientId), com.google.firebase.firestore.SetOptions.merge())
                            }

                        txtStatus.text = if (isUrdu) "✅ کامیابی سے جڑ گئے!" else "✅ Successfully linked!"
                        Toast.makeText(this,
                            if (isUrdu) "✅ $caregiverName سے جڑ گئے!" else "✅ Linked to $caregiverName!",
                            Toast.LENGTH_LONG).show()

                        android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed({
                                startActivity(Intent(this, MainActivity::class.java).putExtra("isUrdu", isUrdu))
                                finish()
                            }, 1500)
                    }
                }
                .addOnFailureListener { e ->
                    btnLink.isEnabled = true
                    btnLink.text      = if (isUrdu) "جوڑیں" else "Link Now"
                    txtStatus.text    = ""
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}