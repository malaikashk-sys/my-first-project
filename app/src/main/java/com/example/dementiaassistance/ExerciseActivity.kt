package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class ExerciseActivity : AppCompatActivity() {
    private var isUrdu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle = findViewById<TextView>(R.id.txtExerciseTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)

        val cardReality = findViewById<MaterialCardView>(R.id.cardReality)
        val cardMemory = findViewById<MaterialCardView>(R.id.cardMemory)
        val cardNameFace = findViewById<MaterialCardView>(R.id.cardNameFace)
        val cardBreathing = findViewById<MaterialCardView>(R.id.cardBreathing)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // ✅ Correct: Title IDs
        val tvRealityTitle    = findViewById<TextView>(R.id.tvRealityTitle)
        val tvMemoryTitle     = findViewById<TextView>(R.id.tvMemoryTitle)
        val tvNameFaceTitle   = findViewById<TextView>(R.id.tvNameFaceTitle)
        val tvBreathingTitle  = findViewById<TextView>(R.id.tvBreathingTitle)

        // ✅ Correct: Subtitle IDs
        val tvRealitySubtitle   = findViewById<TextView>(R.id.tvRealitySubtitle)
        val tvMemorySubtitle    = findViewById<TextView>(R.id.tvMemorySubtitle)
        val tvNameFaceSubtitle  = findViewById<TextView>(R.id.tvNameFaceSubtitle)
        val tvBreathingSubtitle = findViewById<TextView>(R.id.tvBreathingSubtitle)

        if (isUrdu) {
            txtTitle.text = "روزانہ ورزش"
            txtSubtitle.text = "ورزش منتخب کریں"

            // Titles
            tvRealityTitle.text   = "حقیقت پسندی"
            tvMemoryTitle.text    = "یادداشت"
            tvNameFaceTitle.text  = "نام و چہرہ"
            tvBreathingTitle.text = "سانس لیں"

            // Subtitles
            tvRealitySubtitle.text   = "تاریخ، وقت اور جگہ کی پہچان"
            tvMemorySubtitle.text    = "دیکھیں، یاد کریں اور جواب دیں"
            tvNameFaceSubtitle.text  = "خاندان اور دوستوں کو پہچانیں"
            tvBreathingSubtitle.text = "ذہن اور جسم کو سکون دیں"
        }

        cardReality.setOnClickListener {
            startActivity(Intent(this, RealityOrientationActivity::class.java).putExtra("isUrdu", isUrdu))
        }
        cardMemory.setOnClickListener {
            startActivity(Intent(this, MemoryRecallActivity::class.java).putExtra("isUrdu", isUrdu))
        }
        cardNameFace.setOnClickListener {
            startActivity(Intent(this, NameFaceActivity::class.java).putExtra("isUrdu", isUrdu))
        }
        cardBreathing.setOnClickListener {
            startActivity(Intent(this, BreathingActivity::class.java).putExtra("isUrdu", isUrdu))
        }
        btnBack.setOnClickListener {
            finish()
        }
    }
}