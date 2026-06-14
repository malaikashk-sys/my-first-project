package com.example.dementiaassistance

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class RealityOrientationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isUrdu = false
    private var isTtsReady = false
    private var totalRounds = 3
    private var difficultyLabel = ""

    private val handler = Handler(Looper.getMainLooper())
    private val timeHandler = Handler(Looper.getMainLooper())

    private lateinit var txtDate: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtSeason: TextView
    private lateinit var txtLocation: TextView
    private lateinit var txtWeather: TextView
    private lateinit var txtQuizQuestion: TextView
    private lateinit var layoutQuizOptions: LinearLayout
    private lateinit var infoCardContainer: LinearLayout
    private lateinit var quizCardContainer: LinearLayout
    private lateinit var btnQuiz1: Button
    private lateinit var btnQuiz2: Button
    private lateinit var btnQuiz3: Button
    private lateinit var txtQuizResult: TextView
    private lateinit var btnNextQuiz: Button

    private var quizIndex = 0
    private var quizScore = 0
    private var quizQuestions = listOf<QuizQuestion>()

    data class QuizQuestion(val question: String, val options: List<String>, val correctIndex: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reality_orientation)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        val prefs = getSharedPreferences("ExerciseSettings", MODE_PRIVATE)
        val difficulty = prefs.getString("difficulty", "medium") ?: "medium"

        when (difficulty) {
            "low" -> { totalRounds = 3; difficultyLabel = if (isUrdu) "آسان" else "Easy" }
            "high" -> { totalRounds = 7; difficultyLabel = if (isUrdu) "مشکل" else "Hard" }
            else -> { totalRounds = 5; difficultyLabel = if (isUrdu) "درمیانہ" else "Medium" }
        }

        tts = TextToSpeech(this, this)

        initUI()
        updateDateTime()
        updateSeason()
        fetchWeather()

        quizQuestions = buildQuizQuestions()
        timeHandler.post(timeRunnable)
    }

    private fun buildQuizQuestions(): List<QuizQuestion> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR).toString()
        val dayUr = getDayNameUrdu(cal.get(Calendar.DAY_OF_WEEK))
        val dayEn = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH) ?: ""
        val monthUr = getMonthNameUrdu(cal.get(Calendar.MONTH))
        val monthEn = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""
        val dateToday = cal.get(Calendar.DAY_OF_MONTH).toString()

        val questions = if (isUrdu) {
            listOf(
                QuizQuestion("آج کون سا دن ہے؟", listOf(dayUr, "منگل", "جمعہ"), 0),
                QuizQuestion("موجودہ سال کیا ہے؟", listOf(year, "2023", "2025"), 0),
                QuizQuestion("موجودہ مہینہ کون سا ہے؟", listOf(monthUr, "اپریل", "اگست"), 0),
                QuizQuestion("موسم کون سا چل رہا ہے؟", listOf(getSeasonUrdu(cal.get(Calendar.MONTH)), "خزاں", "سردی"), 0),
                QuizQuestion("آج تاریخ کیا ہے؟", listOf(dateToday, "15", "28"), 0),
                QuizQuestion("آپ کا ملک کون سا ہے؟", listOf("پاکستان", "بھارت", "سعودی عرب"), 0),
                QuizQuestion("آپ اس وقت کس شہر میں ہیں؟", listOf("فیصل آباد", "لاہور", "اسلام آباد"), 0) // ✅ نیو سوال
            )
        } else {
            listOf(
                QuizQuestion("What day is today?", listOf(dayEn, "Tuesday", "Friday"), 0),
                QuizQuestion("What is the current year?", listOf(year, "2023", "2025"), 0),
                QuizQuestion("What is the current month?", listOf(monthEn, "April", "August"), 0),
                QuizQuestion("What is the current season?", listOf(getSeasonEnglish(cal.get(Calendar.MONTH)), "Autumn", "Winter"), 0),
                QuizQuestion("What is today's date?", listOf(dateToday, "15", "28"), 0),
                QuizQuestion("Which country is this?", listOf("Pakistan", "India", "Turkey"), 0),
                QuizQuestion("In which city are you currently?", listOf("Faisalabad", "Lahore", "Islamabad"), 0) // ✅ New Question
            )
        }

        return questions.shuffled().take(totalRounds).map { q ->
            val correctVal = q.options[0]
            val shuffledOptions = q.options.shuffled()
            q.copy(options = shuffledOptions, correctIndex = shuffledOptions.indexOf(correctVal))
        }
    }

    private fun initUI() {
        infoCardContainer = findViewById(R.id.infoCardContainer)
        quizCardContainer = findViewById(R.id.quizCardContainer)
        txtDate = findViewById(R.id.txtDate)
        txtTime = findViewById(R.id.txtTime)
        txtSeason = findViewById(R.id.txtSeason)
        txtWeather = findViewById(R.id.txtWeather)
        txtLocation = findViewById(R.id.txtLocation)
        txtQuizQuestion = findViewById(R.id.txtQuizQuestion)
        layoutQuizOptions = findViewById(R.id.layoutQuizOptions)
        btnQuiz1 = findViewById(R.id.btnQuiz1)
        btnQuiz2 = findViewById(R.id.btnQuiz2)
        btnQuiz3 = findViewById(R.id.btnQuiz3)
        txtQuizResult = findViewById(R.id.txtQuizResult)
        btnNextQuiz = findViewById(R.id.btnNextQuiz)
        val btnBack = findViewById<Button>(R.id.btnBack)

        if (isUrdu) {
            findViewById<TextView>(R.id.txtExerciseTitle).text = "ذہنی آگاہی"
            findViewById<TextView>(R.id.txtSubtitle).text = "حالاتِ حاضرہ کی مشق ($difficultyLabel)"
            findViewById<TextView>(R.id.txtTodayLabel).text = "آج ہے:"
            findViewById<TextView>(R.id.txtTimeLabel).text = "موجودہ وقت:"
            findViewById<TextView>(R.id.txtLocationLabel).text = "آپ اس وقت یہاں ہیں:"
            findViewById<TextView>(R.id.quizLabel).text = "🧩 مختصر کوئز"
            btnBack.text = "← واپسی"
        } else {
            findViewById<TextView>(R.id.txtSubtitle).text = "Reality Orientation Exercise ($difficultyLabel)"
        }

        btnBack.setOnClickListener { finish() }
        btnNextQuiz.setOnClickListener {
            if (quizIndex < quizQuestions.size - 1) { quizIndex++; showQuiz() } else { finishExercise() }
        }
    }

    private fun showQuiz() {
        if (quizIndex >= quizQuestions.size) return
        val q = quizQuestions[quizIndex]
        txtQuizQuestion.text = q.question
        val buttons = listOf(btnQuiz1, btnQuiz2, btnQuiz3)

        buttons.forEachIndexed { i, btn ->
            btn.text = q.options[i]
            btn.isEnabled = true
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D8C"))
            btn.setOnClickListener { checkAnswer(i, q.correctIndex) }
        }

        btnNextQuiz.visibility = View.GONE
        txtQuizResult.text = ""

        // آپشنز کو نمبر کے ساتھ بولنے کی لاجک
        val optionsSpeech = if (isUrdu) {
            "آپشن ایک: ${q.options[0]}۔۔۔ آپشن دو: ${q.options[1]}۔۔۔ آپشن تین: ${q.options[2]}"
        } else {
            "Option 1: ${q.options[0]}... Option 2: ${q.options[1]}... Option 3: ${q.options[2]}"
        }

        val fullSpeech = "${q.question}. $optionsSpeech"
        speakOut(fullSpeech)
    }
    private fun checkAnswer(selected: Int, correct: Int) {
        val buttons = listOf(btnQuiz1, btnQuiz2, btnQuiz3)
        buttons.forEach { it.isEnabled = false }

        if (selected == correct) {
            quizScore++
            txtQuizResult.text = if (isUrdu) "صحیح! ✓" else "Correct! ✓"
            txtQuizResult.setTextColor(Color.parseColor("#4CAF50"))
            speakOut(if (isUrdu) "صحیح جواب" else "Correct")
        } else {
            txtQuizResult.text = if (isUrdu) "غلط!" else "Wrong!"
            txtQuizResult.setTextColor(Color.parseColor("#F44336"))
            buttons[selected].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
            speakOut(if (isUrdu) "غلط جواب" else "Wrong")
        }

        buttons[correct].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        btnNextQuiz.visibility = View.VISIBLE
        btnNextQuiz.text = if (quizIndex < quizQuestions.size - 1)
            (if (isUrdu) "اگلا سوال" else "Next") else (if (isUrdu) "مکمل" else "Finish")
    }

    private fun speakOrientation() {
        if (!isTtsReady) return
        val city = if (isUrdu) "فیصل آباد" else "Faisalabad"
        val country = if (isUrdu) "پاکستان" else "Pakistan"

        // اردو وقت کے لیے نئی لاجک
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR)
        val minutes = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "صبح" else "شام"

        val timeInUrdu = if (minutes == 0) "$hour بج رہے ہیں" else "$hour بج کر $minutes منٹ ہوئے ہیں"

        val infoText = if (isUrdu) {
            "آج ${txtDate.text} ہے۔ وقت $timeInUrdu ہے۔ آپ اس وقت $city $country میں ہیں۔ اب کوئز شروع کرتے ہیں۔"
        } else {
            "Today is ${txtDate.text}. The time is ${txtTime.text}. You are in $city, $country. Now answer some questions."
        }
        speakOut(infoText)
    }

    private fun speakOut(text: String) {
        if (isTtsReady) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id") }
    }

    private fun updateDateTime() {
        val cal = Calendar.getInstance()
        txtDate.text = if (isUrdu) "${getDayNameUrdu(cal.get(Calendar.DAY_OF_WEEK))}، ${cal.get(Calendar.DAY_OF_MONTH)} ${getMonthNameUrdu(cal.get(Calendar.MONTH))} ${cal.get(Calendar.YEAR)}"
        else SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.ENGLISH).format(cal.time)
        txtTime.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)
    }

    private fun updateSeason() {
        val month = Calendar.getInstance().get(Calendar.MONTH)
        val season = if (isUrdu) getSeasonUrdu(month) else getSeasonEnglish(month)
        txtSeason.text = if (isUrdu) "☀️ موسم: $season" else "☀️ Season: $season"
    }

    private fun fetchWeather() {
        Thread {
            try {
                val response = URL("https://api.open-meteo.com/v1/forecast?latitude=31.4187&longitude=73.0791&current_weather=true").readText()
                val temp = JSONObject(response).getJSONObject("current_weather").getDouble("temperature").toInt()
                runOnUiThread { updateLocationUI(temp) }
            } catch (e: Exception) {
                runOnUiThread { updateLocationUI(28) }
            }
        }.start()
    }

    private fun updateLocationUI(temp: Int) {
        val city = if (isUrdu) "فیصل آباد" else "Faisalabad"
        val country = if (isUrdu) "پاکستان" else "Pakistan"

        // 1. اوپر والی مین لوکیشن (سرخ مارک والی) - اسے کالا اور بولڈ رکھیں
        val locationLabel = if (isUrdu) "$city، $country" else "$city, $country"
        txtLocation.text = locationLabel
        txtLocation.setTextColor(Color.BLACK)
        txtLocation.setTypeface(null, android.graphics.Typeface.BOLD)

        // 2. نیچے والا موسم (txtWeather) - اسے واپس نیلا (Primary Color) کر دیا ہے
        txtWeather.text = if (isUrdu) "$city، $country: $temp°C" else "$city, $country: $temp°C"

        // یہاں ہم اسے واپس بلو (Primary Blue) کر رہے ہیں
        txtWeather.setTextColor(Color.parseColor("#2E7D8C"))
        txtWeather.setTypeface(null, android.graphics.Typeface.NORMAL) // اسے نارمل رہنے دیں

        if (isUrdu) {
            txtLocation.textAlignment = View.TEXT_ALIGNMENT_CENTER
            txtWeather.textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
    }
    private fun finishExercise() {
        saveRealityResult()
        try {
            handler.removeCallbacksAndMessages(null)
            timeHandler.removeCallbacksAndMessages(null)

            val intent = Intent(this, ExerciseResultActivity::class.java)
            intent.putExtra("score", quizScore)
            intent.putExtra("totalQuestions", quizQuestions.size)
            intent.putExtra("isUrdu", isUrdu)

            // ✅ یہ لائن ایڈ کریں، اس کے بغیر 'Play Again' کام نہیں کرے گا
            intent.putExtra("exerciseClass", "RealityOrientation")

            startActivity(intent)
            finish()
        } catch (e: Exception) {
            finish()
        }
    }
    private fun saveRealityResult() {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val resultData = hashMapOf(
            "exercise" to "RealityOrientation", // رپورٹ فائل میں یہی نام ہونا چاہیے
            "score" to quizScore,
            "total" to quizQuestions.size,
            "date" to sdfDate.format(Date()),
            "time" to sdfTime.format(Date()),
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("caregivers").document(uid)
            .collection("exerciseResults")
            .add(resultData)
            .addOnSuccessListener {
                android.util.Log.d("Firebase", "Reality Orientation result saved!")
            }
    }

    private val timeRunnable = object : Runnable {
        override fun run() { updateDateTime(); timeHandler.postDelayed(this, 60000) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts.setLanguage(if (isUrdu) Locale("ur", "PK") else Locale.ENGLISH)
            handler.postDelayed({ speakOrientation() }, 1000)

            // ✅ Gap increased to 14 seconds (Approx 9 sec for speech + 5 sec gap)
            handler.postDelayed({
                runOnUiThread {
                    infoCardContainer.visibility = View.GONE
                    quizCardContainer.visibility = View.VISIBLE
                    showQuiz()
                }
            }, 16000)
        }
    }

    private fun getDayNameUrdu(day: Int): String = when (day) {
        Calendar.SUNDAY -> "اتوار"
        Calendar.MONDAY -> "پیر"
        Calendar.TUESDAY -> "منگل"
        Calendar.WEDNESDAY -> "بدھ"
        Calendar.THURSDAY -> "جمعرات"
        Calendar.FRIDAY -> "جمعہ"
        else -> "ہفتہ"
    }

    private fun getMonthNameUrdu(month: Int): String = when (month) {
        0 -> "جنوری" 1 -> "فروری" 2 -> "مارچ" 3 -> "اپریل" 4 -> "مئی" 5 -> "جون"
        6 -> "جولائی" 7 -> "اگست" 8 -> "ستمبر" 9 -> "اکتوبر" 10 -> "نومبر" 11 -> "دسمبر"
        else -> ""
    }

    private fun getSeasonUrdu(month: Int): String = when (month) {
        in 2..4 -> "بہار"
        in 5..8 -> "گرمی"
        else -> "سردی"
    }

    private fun getSeasonEnglish(month: Int): String = when (month) {
        in 2..4 -> "Spring"
        in 5..8 -> "Summer"
        else -> "Winter"
    }

    override fun onDestroy() {
        timeHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}