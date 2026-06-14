package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class CaregiverLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isUrdu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_login)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)
        val txtEmailLabel = findViewById<TextView>(R.id.txtEmailLabel)
        val txtPasswordLabel = findViewById<TextView>(R.id.txtPasswordLabel)
        val txtForgotPassword = findViewById<TextView>(R.id.txtForgotPassword)
        val txtRegister = findViewById<TextView>(R.id.txtRegister)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        // ✅ Fixed: Kotlin mein findViewById ke sath 'style' nahi likhte
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Apply language
        if (isUrdu) {
            txtTitle.text = "کیئرگیور لاگ ان"
            txtSubtitle.text = "مریض کی دیکھ بھال کے لیے سائن ان کریں"
            txtEmailLabel.text = "ای میل"
            txtPasswordLabel.text = "پاس ورڈ"
            etEmail.hint = "ای میل درج کریں"
            etPassword.hint = "پاس ورڈ درج کریں"
            txtForgotPassword.text = "پاس ورڈ بھول گئے؟"
            txtRegister.text = "نئے کیئرگیور؟ یہاں رجسٹر کریں"
            btnLogin.text = "لاگ ان"
            btnBack.text = "واپس"
        }

        if (auth.currentUser != null) {
            goToDashboard()
            return
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = if (isUrdu) "ای میل درج کریں" else "Enter email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = if (isUrdu) "پاس ورڈ درج کریں" else "Enter password"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = if (isUrdu) "لاگ ان ہو رہا ہے..." else "Logging in..."

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Toast.makeText(this, if (isUrdu) "لاگ ان کامیاب!" else "Login Successful!", Toast.LENGTH_SHORT).show()
                    goToDashboard()
                }
                .addOnFailureListener { e ->
                    btnLogin.isEnabled = true
                    btnLogin.text = if (isUrdu) "لاگ ان" else "Login"
                    Toast.makeText(this, if (isUrdu) "غلط ای میل یا پاس ورڈ" else "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        txtForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, if (isUrdu) "پہلے ای میل درج کریں" else "Enter email first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email).addOnSuccessListener {
                Toast.makeText(this, if (isUrdu) "ای میل بھیجی گئی" else "Reset email sent!", Toast.LENGTH_LONG).show()
            }
        }

        txtRegister.setOnClickListener {
            startActivity(Intent(this, CaregiverRegisterActivity::class.java).putExtra("isUrdu", isUrdu))
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun goToDashboard() {
        val intent = Intent(this, CaregiverDashboardActivity::class.java)
        intent.putExtra("isUrdu", isUrdu)
        startActivity(intent)
        finish()
    }
}