package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class RoleSelectionActivity : AppCompatActivity() {

    private var isUrdu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ Make sure this matches your XML file name exactly (activity_role_selection)
        setContentView(R.layout.activity_role_selection)
        supportActionBar?.hide()

        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        isUrdu = prefs.getBoolean("isUrdu", false)

        // UI References
        val txtTitle = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)
        val cardPatient = findViewById<MaterialCardView>(R.id.cardPatient)
        val cardCaregiver = findViewById<MaterialCardView>(R.id.cardCaregiver)
        val btnLanguage = findViewById<Button>(R.id.btnLanguage)

        // ✅ References for text inside the cards
        val tvPatientTitle = findViewById<TextView>(R.id.tvPatientTitle)
        val tvPatientDesc = findViewById<TextView>(R.id.tvPatientDesc)
        val tvCaregiverTitle = findViewById<TextView>(R.id.tvCaregiverTitle)
        val tvCaregiverDesc = findViewById<TextView>(R.id.tvCaregiverDesc)

        // Initial Language Load
        applyLanguage(txtTitle, txtSubtitle, btnLanguage, tvPatientTitle, tvPatientDesc, tvCaregiverTitle, tvCaregiverDesc)

        btnLanguage.setOnClickListener {
            isUrdu = !isUrdu
            prefs.edit().putBoolean("isUrdu", isUrdu).apply()
            applyLanguage(txtTitle, txtSubtitle, btnLanguage, tvPatientTitle, tvPatientDesc, tvCaregiverTitle, tvCaregiverDesc)
        }

        cardCaregiver.setOnClickListener {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val targetClass = if (auth.currentUser != null) {
                CaregiverDashboardActivity::class.java
            } else {
                CaregiverLoginActivity::class.java
            }
            startActivity(Intent(this, targetClass).putExtra("isUrdu", isUrdu))
        }

        cardPatient.setOnClickListener {
            val isLinked = prefs.getBoolean("isLinked", false)
            val targetClass = if (isLinked) {
                MainActivity::class.java
            } else {
                PatientLinkActivity::class.java
            }
            startActivity(Intent(this, targetClass).putExtra("isUrdu", isUrdu))
        }
    }

    private fun applyLanguage(
        txtTitle: TextView, txtSubtitle: TextView, btnLang: Button,
        pTitle: TextView, pDesc: TextView, cTitle: TextView, cDesc: TextView
    ) {
        if (isUrdu) {
            txtTitle.text = "خوش آمدید"
            txtSubtitle.text = "براہ کرم اپنا کردار منتخب کریں"
            btnLang.text = "English"
            pTitle.text = "میں مریض ہوں"
            pDesc.text = "اپنے روزمرہ کے کاموں تک رسائی حاصل کریں"
            cTitle.text = "میں کیئرگیور ہوں"
            cDesc.text = "نگرانی اور مدد کریں"
        } else {
            txtTitle.text = "Welcome"
            txtSubtitle.text = "Please select your role to continue"
            btnLang.text = "اردو"
            pTitle.text = "I am a Patient"
            pDesc.text = "Access your daily tasks"
            cTitle.text = "I am a Caregiver"
            cDesc.text = "Monitor and assist"
        }
    }
}