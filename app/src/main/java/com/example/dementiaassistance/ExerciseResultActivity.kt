package com.example.dementiaassistance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ExerciseResultActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isUrdu = false

    companion object {
        const val PREFS_NAME = "ExerciseResultsPrefs"
        const val KEY_RESULTS = "exerciseResults"

        fun saveResult(context: Context, exerciseName: String, score: Int, total: Int) {
            // ✅ 1. Local SharedPreferences mein save karo (offline safe)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_RESULTS, "[]")
            val array = JSONArray(json)

            val result = JSONObject().apply {
                put("exercise", exerciseName)
                put("score", score)
                put("total", total)
                put("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                put("time", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))
            }
            array.put(result)

            val trimmed = JSONArray()
            val start = if (array.length() > 50) array.length() - 50 else 0
            for (i in start until array.length()) trimmed.put(array.getJSONObject(i))
            prefs.edit().putString(KEY_RESULTS, trimmed.toString()).apply()

            // ✅ 2. Firestore mein save karo — caregiver ke collection mein
            saveToFirestore(context, exerciseName, score, total)
        }

        private fun saveToFirestore(context: Context, exerciseName: String, score: Int, total: Int) {
            // ✅ Patient ke phone pe linkedCaregiverId se caregiver ka path use karo
            val appPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val linkedCaregiverId = appPrefs.getString("linkedCaregiverId", null)

            if (linkedCaregiverId.isNullOrEmpty()) {
                android.util.Log.w("ExerciseResult", "⚠️ No linkedCaregiverId — skipping Firestore save")
                return
            }

            val db = FirebaseFirestore.getInstance()
            val resultData = hashMapOf(
                "exercise"  to exerciseName,
                "score"     to score,
                "total"     to total,
                "date"      to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                "time"      to SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                "timestamp" to System.currentTimeMillis()
            )

            // ✅ Caregiver ke document ke andar exerciseResults collection
            // ExerciseReportActivity bhi yahi se fetch karta hai — match!
            db.collection("caregivers")
                .document(linkedCaregiverId)
                .collection("exerciseResults")
                .add(resultData)
                .addOnSuccessListener {
                    android.util.Log.d("ExerciseResult", "✅ Result saved to caregiver's exerciseResults!")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ExerciseResult", "❌ Firestore save failed: ${e.message}")
                }
        }

        fun getResults(context: Context): JSONArray {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return JSONArray(prefs.getString(KEY_RESULTS, "[]") ?: "[]")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_result)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)
        val score = intent.getIntExtra("score", 0)
        val totalQuestions = intent.getIntExtra("totalQuestions", 4)
        val exerciseClass = intent.getStringExtra("exerciseClass") ?: ""

        tts = TextToSpeech(this, this)

        val txtResult     = findViewById<TextView>(R.id.txtResult)
        val txtFinalScore = findViewById<TextView>(R.id.txtFinalScore)
        val txtMessage    = findViewById<TextView>(R.id.txtMessage)
        val btnPlayAgain  = findViewById<Button>(R.id.btnPlayAgain)
        val btnBack       = findViewById<Button>(R.id.btnBack)

        txtResult.text     = if (isUrdu) "ورزش مکمل!" else "Exercise Complete!"
        txtFinalScore.text = "$score / $totalQuestions"

        txtMessage.text = when {
            score == totalQuestions ->
                if (isUrdu) "بہت اچھے! کامل اسکور!" else "Excellent! Perfect score!"
            score >= totalQuestions / 2 ->
                if (isUrdu) "اچھا کام! جاری رکھیں!" else "Good job! Keep it up!"
            else ->
                if (isUrdu) "کوشش کریں! آپ کر سکتے ہیں!" else "Keep trying! You can do it!"
        }

        if (isUrdu) {
            btnPlayAgain.text = "دوبارہ کھیلیں"
            btnBack.text      = "واپس"
        }

        btnPlayAgain.setOnClickListener {
            val nextIntent = when (exerciseClass) {
                "MemoryRecall"       -> Intent(this, MemoryRecallActivity::class.java)
                "BreathingExercise"  -> Intent(this, BreathingActivity::class.java)
                "NameFace"           -> Intent(this, NameFaceActivity::class.java)
                "RealityOrientation" -> Intent(this, RealityOrientationActivity::class.java)
                else                 -> null
            }

            if (nextIntent != null) {
                nextIntent.putExtra("isUrdu", isUrdu)
                nextIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(nextIntent)
                finish()
            } else {
                android.util.Log.e("ExerciseResult", "❌ Exercise class '$exerciseClass' match nahi hui")
            }
        }

        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = if (isUrdu) Locale("ur", "PK") else Locale.US
            tts?.setLanguage(locale)
            tts?.setSpeechRate(0.8f)

            val score = intent.getIntExtra("score", 0)
            val total = intent.getIntExtra("totalQuestions", 4)

            Handler(Looper.getMainLooper()).postDelayed({
                val speechText = if (isUrdu) {
                    "آپ کا اسکور ہے $total میں سے $score ۔ ${getFeedbackUrdu(score, total)}"
                } else {
                    "Your score is $score out of $total. ${getFeedbackEn(score, total)}"
                }
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "RESULT")
            }, 2000)
        }
    }

    private fun getFeedbackUrdu(score: Int, total: Int): String {
        return when {
            score == total     -> "بہت خوب! آپ نے کمال کر دیا۔"
            score >= total / 2 -> "اچھا کام کیا! جاری رکھیں۔"
            else               -> "کوئی بات نہیں، اگلی بار آپ اور بھی اچھا کریں گے۔"
        }
    }

    private fun getFeedbackEn(score: Int, total: Int): String {
        return when {
            score == total     -> "Excellent! You did a great job."
            score >= total / 2 -> "Good job! Keep it up."
            else               -> "Don't worry, you can do better next time."
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}