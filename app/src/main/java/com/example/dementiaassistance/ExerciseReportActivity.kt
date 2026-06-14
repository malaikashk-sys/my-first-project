package com.example.dementiaassistance

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ExerciseReportActivity : AppCompatActivity() {

    private var isUrdu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_report)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val txtTitle = findViewById<TextView>(R.id.txtTitle)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val container = findViewById<LinearLayout>(R.id.reportContainer)

        if (isUrdu) {
            txtTitle.text = "ورزش کی رپورٹ"
            btnBack.text = "واپس"
        }

        btnBack.setOnClickListener { finish() }

        val progressBar = ProgressBar(this)
        container.addView(progressBar)

        loadReportFromFirestore(container, progressBar)
    }

    private fun loadReportFromFirestore(container: LinearLayout, progressBar: ProgressBar) {
        val db = FirebaseFirestore.getInstance()
        val currentCaregiverUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentCaregiverUid == null) {
            container.removeView(progressBar)
            showEmpty(container)
            return
        }

        // ✅ Ab hum direct caregiver ke andar mojud exerciseResults ko check kar rahe hain
        db.collection("caregivers")
            .document(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            .collection("exerciseResults")
            .get() // Yahan se orderBy hata diya taaki agar timestamp na bhi ho toh data aa jaye
            .addOnSuccessListener { snapshot ->


                container.removeView(progressBar)

                if (snapshot.isEmpty) {
                    android.util.Log.d("ExerciseReport", "No data found in exerciseResults")
                    showEmpty(container)
                    return@addOnSuccessListener
                }

                val results = JSONArray()
                for (doc in snapshot.documents) {
                    val obj = JSONObject().apply {
                        put("exercise", doc.getString("exercise") ?: "Exercise")
                        put("score", doc.getLong("score")?.toInt() ?: 0)
                        put("total", doc.getLong("total")?.toInt() ?: 0)
                        put("date", doc.getString("date") ?: "")
                        put("time", doc.getString("time") ?: "")
                    }
                    results.put(obj)
                }
                renderReport(container, results)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ExerciseReport", "Error: ${e.message}")
                container.removeView(progressBar)
                showEmpty(container)
            }
    }
    private fun renderReport(container: LinearLayout, results: JSONArray) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val last7Days = mutableListOf<String>()
        for (i in 0 until 7) {
            last7Days.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        if (results.length() == 0) {
            showEmpty(container)
            return
        }

        for (day in last7Days) {
            val dayResults = mutableListOf<Triple<String, Int, Int>>()

            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                if (result.getString("date") == day) {
                    dayResults.add(Triple(
                        result.getString("exercise"),
                        result.getInt("score"),
                        result.getInt("total")
                    ))
                }
            }

            val dayCard = LinearLayout(this)
            dayCard.orientation = LinearLayout.VERTICAL
            dayCard.setBackgroundResource(R.drawable.btn_rounded)
            dayCard.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            dayCard.setPadding(40, 30, 40, 30)
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardParams.setMargins(0, 0, 0, 30)
            dayCard.layoutParams = cardParams
            dayCard.elevation = 8f

            val txtDate = TextView(this, null, 0, R.style.TitleText).apply {
                textSize = 20f
                val parsedDate = dateFormat.parse(day)
                text = if (day == dateFormat.format(Date()))
                    (if (isUrdu) "آج" else "Today")
                else displayFormat.format(parsedDate!!)
                setPadding(0, 0, 0, 15)
                gravity = Gravity.START
            }
            dayCard.addView(txtDate)

            if (dayResults.isEmpty()) {
                val txtNone = TextView(this, null, 0, R.style.SubtitleText)
                txtNone.text = if (isUrdu) "❌ کوئی ورزش نہیں کی" else "❌ No exercises done"
                txtNone.textSize = 15f
                txtNone.setTextColor(Color.parseColor("#999999"))
                dayCard.addView(txtNone)
            } else {
                for ((exercise, score, total) in dayResults) {
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPadding(0, 10, 0, 10)

                    val txtExercise = TextView(this, null, 0, R.style.SubtitleText).apply {
                        text = when (exercise) {
                            "RealityOrientation" -> if (isUrdu) "📅 ذہنی آگاہی" else "📅 Reality Orientation"
                            "NameFace"           -> if (isUrdu) "👤 نام اور چہرہ" else "👤 Name & Face"
                            "MemoryRecall"       -> if (isUrdu) "🧠 یادداشت کی مشق" else "🧠 Memory Recall"
                            "BreathingExercise"  -> if (isUrdu) "🌬️ سانس کی ورزش" else "🌬️ Breathing Exercise" // ✅ یہ لائن ایڈ کریں
                            else                 -> if (isUrdu) "🎮 ورزش" else "🎮 Exercise"
                        }
                        textSize = 17f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        gravity = Gravity.START
                    }
                    row.addView(txtExercise)

                    val txtScore = TextView(this, null, 0, R.style.TitleText).apply {
                        val percentage = (score * 100) / total
                        text = "$score/$total"
                        textSize = 17f
                        setTextColor(
                            when {
                                percentage >= 75 -> Color.parseColor("#4CAF50")
                                percentage >= 50 -> Color.parseColor("#FF9800")
                                else             -> Color.parseColor("#F44336")
                            }
                        )
                    }
                    row.addView(txtScore)
                    dayCard.addView(row)
                }
            }
            container.addView(dayCard)
        }
    }

    private fun showEmpty(container: LinearLayout) {
        val empty = TextView(this, null, 0, R.style.SubtitleText)
        empty.text = if (isUrdu) "کوئی ریکارڈ نہیں" else "No exercise records yet"
        empty.textSize = 18f
        empty.setPadding(0, 40, 0, 0)
        empty.gravity = Gravity.CENTER
        container.addView(empty)
    }
}