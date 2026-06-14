package com.example.dementiaassistance

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ExerciseSettingsActivity : AppCompatActivity() {

    private var isUrdu = false

    companion object {
        const val PREFS_NAME = "ExerciseSettings"
        const val KEY_DIFFICULTY = "difficulty"
        const val DIFFICULTY_LOW = "low"
        const val DIFFICULTY_MEDIUM = "medium"
        const val DIFFICULTY_HIGH = "high"

        fun getDifficulty(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DIFFICULTY, DIFFICULTY_MEDIUM) ?: DIFFICULTY_MEDIUM
        }

        fun getRounds(context: Context): Int {
            return when (getDifficulty(context)) {
                DIFFICULTY_LOW -> 3
                DIFFICULTY_HIGH -> 7
                else -> 5 // medium default
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_settings)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle = findViewById<TextView>(R.id.txtTitle)
        val txtSubtitle = findViewById<TextView>(R.id.txtSubtitle)
        val txtCurrent = findViewById<TextView>(R.id.txtCurrentDifficulty)
        val btnLow = findViewById<Button>(R.id.btnLow)
        val btnMedium = findViewById<Button>(R.id.btnMedium)
        val btnHigh = findViewById<Button>(R.id.btnHigh)
        val btnBack = findViewById<Button>(R.id.btnBack)

        if (isUrdu) {
            txtTitle.text = "ورزش کی ترتیبات"
            txtSubtitle.text = "مریض کے لیے مشکل کی سطح منتخب کریں"
            btnLow.text = "آسان — 3 سوالات"
            btnMedium.text = "درمیانہ — 5 سوالات"
            btnHigh.text = "مشکل — 7 سوالات"
            btnBack.text = "← واپس"
        } else {
            txtTitle.text = "Exercise Settings"
            txtSubtitle.text = "Set difficulty level for patient"
            btnLow.text = "Easy — 3 Questions"
            btnMedium.text = "Medium — 5 Questions"
            btnHigh.text = "Hard — 7 Questions"
            btnBack.text = "← Back"
        }

        // ✅ Current difficulty dikhao
        updateCurrentLabel(txtCurrent)

        btnLow.setOnClickListener {
            saveDifficulty(DIFFICULTY_LOW)
            updateCurrentLabel(txtCurrent)
            Toast.makeText(this,
                if (isUrdu) "آسان سطح منتخب ہوئی ✅" else "Easy difficulty set ✅",
                Toast.LENGTH_SHORT).show()
        }

        btnMedium.setOnClickListener {
            saveDifficulty(DIFFICULTY_MEDIUM)
            updateCurrentLabel(txtCurrent)
            Toast.makeText(this,
                if (isUrdu) "درمیانہ سطح منتخب ہوئی ✅" else "Medium difficulty set ✅",
                Toast.LENGTH_SHORT).show()
        }

        btnHigh.setOnClickListener {
            saveDifficulty(DIFFICULTY_HIGH)
            updateCurrentLabel(txtCurrent)
            Toast.makeText(this,
                if (isUrdu) "مشکل سطح منتخب ہوئی ✅" else "Hard difficulty set ✅",
                Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun saveDifficulty(difficulty: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DIFFICULTY, difficulty)
            .apply()
    }

    private fun updateCurrentLabel(txtCurrent: TextView) {
        val current = getDifficulty(this)
        val label = when (current) {
            DIFFICULTY_LOW -> if (isUrdu) "موجودہ: آسان (3 سوالات)" else "Current: Easy (3 questions)"
            DIFFICULTY_HIGH -> if (isUrdu) "موجودہ: مشکل (7 سوالات)" else "Current: Hard (7 questions)"
            else -> if (isUrdu) "موجودہ: درمیانہ (5 سوالات)" else "Current: Medium (5 questions)"
        }
        txtCurrent.text = label
    }
}