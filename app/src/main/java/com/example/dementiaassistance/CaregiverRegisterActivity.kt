package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CaregiverRegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isUrdu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_register)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)
        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val txtLogin = findViewById<TextView>(R.id.txtLogin)

        // ✅ Fixed: Kotlin mein findViewById ke andar 'style' nahi likhte
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnBack = findViewById<Button>(R.id.btnBack)

        if (isUrdu) {
            txtTitle.text = "اکاؤنٹ بنائیں"
            txtSubtitle.text = "کیئرگیور کے طور پر رجسٹر کریں"
            btnRegister.text = "اکاؤنٹ بنائیں"
            txtLogin.text = "پہلے سے اکاؤنٹ ہے؟ لاگ ان کریں"
            btnBack.text = "واپس"
            etName.hint = "اپنا پورا نام درج کریں"
            etEmail.hint = "ای میل درج کریں"
            etPassword.hint = "کم از کم 6 حروف"
            etConfirmPassword.hint = "پاس ورڈ دوبارہ درج کریں"
        }

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = if (isUrdu) "نام درج کریں" else "Enter name"
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                etEmail.error = if (isUrdu) "ای میل درج کریں" else "Enter email"
                return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = if (isUrdu) "کم از کم 6 حروف" else "Min 6 characters"
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                etConfirmPassword.error = if (isUrdu) "پاس ورڈ مماثل نہیں" else "Passwords don't match"
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = if (isUrdu) "بن رہا ہے..." else "Creating..."

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user!!.uid
                    val patientCode = (100000..999999).random().toString()

                    val caregiverData = hashMapOf(
                        "name" to name,
                        "email" to email,
                        "patientCode" to patientCode,
                        "linkedPatientId" to "",
                        "role" to "caregiver",
                        "createdAt" to System.currentTimeMillis()
                    )

                    db.collection("caregivers").document(uid).set(caregiverData)
                        .addOnSuccessListener {
                            showPatientCode(patientCode, name)
                        }
                        .addOnFailureListener { e ->
                            btnRegister.isEnabled = true
                            Toast.makeText(this, "Firestore Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    btnRegister.isEnabled = true
                    btnRegister.text = if (isUrdu) "اکاؤنٹ بنائیں" else "Create Account"
                    Toast.makeText(this, "Auth Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        txtLogin.setOnClickListener { finish() }
        btnBack.setOnClickListener { finish() }
    }

    private fun showPatientCode(code: String, name: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (isUrdu) "✅ رجسٹریشن کامیاب!" else "✅ Registration Successful!")
            .setMessage(
                if (isUrdu) "خوش آمدید $name!\n\nمریض کا لنک کوڈ: $code\n\nیہ کوڈ مریض کو دیں تاکہ وہ آپ سے جڑ سکے۔"
                else "Welcome $name!\n\nPatient Link Code: $code\n\nShare this code with your patient."
            )
            .setCancelable(false)
            .setPositiveButton(if (isUrdu) "ڈیش بورڈ" else "Go to Dashboard") { _, _ ->
                // ✅ Registration ke baad currentUser available hota hai — seedha dashboard
                val uid = auth.currentUser?.uid
                android.util.Log.d("Register", "Going to dashboard, UID = $uid")

                val intent = Intent(this, CaregiverDashboardActivity::class.java)
                intent.putExtra("isUrdu", isUrdu)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }}