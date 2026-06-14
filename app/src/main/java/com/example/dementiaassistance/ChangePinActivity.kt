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

class ChangePinActivity : AppCompatActivity() {

    private var isUrdu = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    companion object {
        const val PREFS_NAME = "PinPrefs"
        const val KEY_PIN = "caregiverPin"
        const val DEFAULT_PIN = "1234"

        fun getSavedPin(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        }

        fun savePin(context: Context, pin: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PIN, pin).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_pin)
        supportActionBar?.hide()

        // ✅ Firebase initialize
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)
        val etCurrentPin = findViewById<EditText>(R.id.etCurrentPin)
        val etNewPin = findViewById<EditText>(R.id.etNewPin)
        val etConfirmPin = findViewById<EditText>(R.id.etConfirmPin)
        val btnChangePin = findViewById<Button>(R.id.btnChangePin)
        val btnBack = findViewById<Button>(R.id.btnBack)

        if (isUrdu) {
            txtTitle.text = "پن تبدیل کریں"
            txtSubtitle.text = "موجودہ پن درج کریں اور نیا پن مقرر کریں"
            etCurrentPin.hint = "موجودہ پن"
            etNewPin.hint = "نیا پن (4 ہندسے)"
            etConfirmPin.hint = "نیا پن دوبارہ درج کریں"
            btnChangePin.text = "🔑 پن تبدیل کریں"
            btnBack.text = "واپس"
        }

        btnChangePin.setOnClickListener {
            val currentPin = etCurrentPin.text.toString().trim()
            val newPin = etNewPin.text.toString().trim()
            val confirmPin = etConfirmPin.text.toString().trim()

            // ✅ Current PIN verify karo
            if (currentPin != getSavedPin(this)) {
                Toast.makeText(
                    this,
                    if (isUrdu) "موجودہ پن غلط ہے" else "Current PIN is incorrect",
                    Toast.LENGTH_SHORT
                ).show()
                etCurrentPin.text.clear()
                return@setOnClickListener
            }

            // ✅ Sirf 4 digits allowed
            if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
                Toast.makeText(
                    this,
                    if (isUrdu) "نیا پن 4 ہندسوں کا ہونا چاہیے" else "New PIN must be 4 digits",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // ✅ Confirm match check
            if (newPin != confirmPin) {
                Toast.makeText(
                    this,
                    if (isUrdu) "نیا پن مماثل نہیں" else "New PINs do not match",
                    Toast.LENGTH_SHORT
                ).show()
                etConfirmPin.text.clear()
                return@setOnClickListener
            }

            // ✅ Same PIN check
            if (newPin == currentPin) {
                Toast.makeText(
                    this,
                    if (isUrdu) "نیا پن پرانے پن سے مختلف ہونا چاہیے"
                    else "New PIN must be different from current PIN",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // ✅ Step 1: SharedPreferences mein save karo (fast local access)
            savePin(this, newPin)

            // ✅ Step 2: Firestore mein bhi sync karo (Firebase connected proof)
            val uid = auth.currentUser?.uid
            if (uid != null) {
                db.collection("caregivers").document(uid)
                    .update("caregiverPin", newPin)
                    .addOnSuccessListener {
                        // Firestore sync successful — silent
                    }
                    .addOnFailureListener {
                        // Local mein save ho gaya — Firestore fail hone pe bhi app kaam karta hai
                    }
            }

            Toast.makeText(
                this,
                if (isUrdu) "پن کامیابی سے تبدیل ہو گیا!" else "PIN changed successfully!",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }

        btnBack.setOnClickListener { finish() }
    }
}