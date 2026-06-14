package com.example.dementiaassistance

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import android.content.res.ColorStateList
import android.graphics.Color


class MemoryRecallActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isUrdu = false
    private var isTtsReady = false

    private lateinit var txtTitle: TextView
    private lateinit var txtInstruction: TextView
    private lateinit var imgMemory: ImageView
    private lateinit var txtCountdown: TextView
    private lateinit var txtQuestion: TextView
    private lateinit var layoutOptions: LinearLayout
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var btnOption3: Button
    private lateinit var btnOption4: Button
    private lateinit var txtResult: TextView
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button

    private var currentRound = 0
    private var score = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFirstSpeak = false
    private var difficultyLabel = ""

    data class MemoryRound(
        val imageRes: Int,
        val questionEn: String,
        val questionUr: String,
        val options: List<String>,
        val optionsUr: List<String>,
        val correctIndex: Int
    )

    private val allRounds = listOf(
        MemoryRound(R.drawable.mem_apple, "What fruit did you just see?", "آپ نے ابھی کون سا پھل دیکھا؟", listOf("Mango", "Apple", "Grapes", "Orange"), listOf("آم", "سیب", "انگور", "مالٹا"), 1),
        MemoryRound(R.drawable.mem_clock, "What was the object in the image?", "تصویر میں کیا چیز تھی؟", listOf("Clock", "Lamp", "Phone", "Mirror"), listOf("گھڑی", "لیمپ", "فون", "آئینہ"), 0),
        MemoryRound(R.drawable.mem_flower, "What was shown in the image?", "تصویر میں کیا دکھایا گیا تھا؟", listOf("Bird", "Tree", "Flower", "Butterfly"), listOf("پرندہ", "درخت", "پھول", "تتلی"), 2),
        MemoryRound(R.drawable.mem_key, "Which object did you see?", "آپ نے کون سی چیز دیکھی؟", listOf("Pen", "Key", "Coin", "Ring"), listOf("قلم", "چابی", "سکہ", "انگوٹھی"), 1),
        MemoryRound(R.drawable.mem_book, "What was the item in the picture?", "تصویر میں کون سی چیز تھی؟", listOf("Bag", "Bottle", "Book", "Box"), listOf("بیگ", "بوتل", "کتاب", "ڈبہ"), 2),
        MemoryRound(R.drawable.mem_phone, "What was the object in the image?", "تصویر میں کیا چیز تھی؟", listOf("Laptop", "Phone", "Radio", "Camera"), listOf("لیپ ٹاپ", "فون", "ریڈیو", "کیمرہ"), 1),
        MemoryRound(R.drawable.mem_bottle, "What was shown in the picture?", "تصویر میں کیا دکھایا گیا تھا؟", listOf("Glass", "Plate", "Bottle", "Spoon"), listOf("گلاس", "پلیٹ", "بوتل", "چمچ"), 2)
    )

    private var rounds: List<MemoryRound> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory_recall)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)
        tts = TextToSpeech(this, this)

        val prefs = getSharedPreferences("ExerciseSettings", MODE_PRIVATE)
        val difficulty = prefs.getString("difficulty", "medium") ?: "medium"

        val roundsToTake = when (difficulty) {
            "low" -> {
                difficultyLabel = if (isUrdu) "آسان" else "Easy"
                3
            }
            "high" -> {
                difficultyLabel = if (isUrdu) "مشکل" else "Hard"
                7
            }
            else -> {
                difficultyLabel = if (isUrdu) "درمیانہ" else "Medium"
                5
            }
        }

        rounds = allRounds.shuffled().take(roundsToTake)
        initUI()
        pendingFirstSpeak = true
        startRound()
    }

    private fun initUI() {
        txtTitle       = findViewById(R.id.txtTitle)
        txtInstruction = findViewById(R.id.txtInstruction)
        imgMemory      = findViewById(R.id.imgMemory)
        txtCountdown   = findViewById(R.id.txtCountdown)
        txtQuestion    = findViewById(R.id.txtQuestion)
        layoutOptions  = findViewById(R.id.layoutOptions)
        btnOption1     = findViewById(R.id.btnOption1)
        btnOption2     = findViewById(R.id.btnOption2)
        btnOption3     = findViewById(R.id.btnOption3)
        btnOption4     = findViewById(R.id.btnOption4)
        txtResult      = findViewById(R.id.txtResult)
        btnNext        = findViewById(R.id.btnNext)
        btnBack        = findViewById(R.id.btnBack)

        if (isUrdu) {
            txtTitle.text = "🧠 یادداشت"
            btnBack.text  = "← واپسی"
        }

        btnBack.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            currentRound++
            if (currentRound < rounds.size) startRound()
            else showFinalScore()
        }
    }

    private fun startRound() {
        if (rounds.isEmpty()) return
        val round = rounds[currentRound]

        txtResult.text = ""
        txtQuestion.text = ""
        layoutOptions.visibility = View.GONE
        btnNext.visibility = View.GONE
        imgMemory.visibility = View.VISIBLE

        // ✅ ایرر سے بچنے کے لیے سٹرنگ کو اس طرح ترتیب دیا ہے
        val roundDisplay = "${currentRound + 1} / ${rounds.size}"

        txtInstruction.text = if (isUrdu) {
            "لیول: ($difficultyLabel) — راؤنڈ $roundDisplay"
        } else {
            "Level: ($difficultyLabel) — Round $roundDisplay"
        }

        imgMemory.setImageResource(round.imageRes)

        if (isTtsReady) {
            speakOut("Round ${currentRound + 1}. Look carefully.", "راؤنڈ ${currentRound + 1}۔ غور سے دیکھیں۔")
        }

        var seconds = 8
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (seconds > 0) {
                    txtCountdown.text = "⏱ $seconds"
                    if (seconds <= 3) txtCountdown.setTextColor(Color.RED)
                    else txtCountdown.setTextColor(Color.BLACK)
                    seconds--
                    handler.postDelayed(this, 1000)
                } else {
                    txtCountdown.text = ""
                    imgMemory.visibility = View.GONE
                    showQuestion(round)
                }
            }
        }
        handler.post(countdownRunnable)
    }

    private fun showQuestion(round: MemoryRound) {
        txtQuestion.text = if (isUrdu) round.questionUr else round.questionEn
        layoutOptions.visibility = View.VISIBLE
        val options = if (isUrdu) round.optionsUr else round.options
        val buttons = listOf(btnOption1, btnOption2, btnOption3, btnOption4)

        buttons.forEachIndexed { i, btn ->
            btn.text = options[i]
            btn.isEnabled = true
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D8C"))
            btn.setOnClickListener { checkAnswer(i, round.correctIndex, buttons) }
        }

        speakOut(txtQuestion.text.toString(), txtQuestion.text.toString())
        handler.postDelayed({ speakOptionsSequence(options) }, 2200)
    }

    private fun speakOptionsSequence(options: List<String>) {
        options.forEachIndexed { i, opt ->
            handler.postDelayed({
                if (isTtsReady && isRunningActivity()) {
                    val prefix = if (isUrdu) "آپشن ${i + 1}" else "Option ${i + 1}"
                    tts.speak("$prefix: $opt", TextToSpeech.QUEUE_ADD, null, "opt$i")
                }
            }, i * 1900L)
        }
    }

    private fun checkAnswer(selected: Int, correct: Int, buttons: List<Button>) {
        buttons.forEach { it.isEnabled = false }
        tts.stop()
        handler.removeCallbacksAndMessages(null)

        if (selected == correct) {
            score++
            buttons[selected].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            txtResult.text = if (isUrdu) "✅ صحیح جواب!" else "✅ Correct!"
            txtResult.setTextColor(Color.parseColor("#4CAF50"))
            speakOut("Correct", "صحیح جواب")
        } else {
            buttons[selected].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
            buttons[correct].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            txtResult.text = if (isUrdu) "❌ غلط جواب" else "❌ Wrong"
            txtResult.setTextColor(Color.parseColor("#F44336"))
            speakOut("Wrong", "غلط جواب")
        }

        // ✅ Answer ke baad correct image wapas dikhao (reinforcement)
        val round = rounds[currentRound]
        imgMemory.setImageResource(round.imageRes)
        imgMemory.visibility = View.VISIBLE

        btnNext.visibility = View.VISIBLE
        btnNext.text = if (currentRound < rounds.size - 1)
            (if (isUrdu) "اگلا راؤنڈ" else "Next")
        else
            (if (isUrdu) "مکمل" else "Finish")
    }
    private fun showFinalScore() {
        saveResultToFirestore() // ڈیٹا سیو کرنے والا نیا فنکشن

        try {
            val intent = Intent(this, ExerciseResultActivity::class.java)
            intent.putExtra("score", score)
            intent.putExtra("totalQuestions", rounds.size)
            intent.putExtra("exerciseClass", "MemoryRecall")
            intent.putExtra("isUrdu", isUrdu)
            startActivity(intent)
            finish()
        } catch (e: Exception) { finish() }
    }

    private fun saveResultToFirestore() {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val resultData = hashMapOf(
            "exercise" to "MemoryRecall", // یہ نام رپورٹ والے نام سے میچ ہونا چاہیے
            "score" to score,
            "total" to rounds.size,
            "date" to sdfDate.format(Date()), // یہ آج کی صحیح تاریخ سیو کرے گا
            "time" to sdfTime.format(Date()),
            "timestamp" to System.currentTimeMillis()
        )

        // ڈیٹا کو کیئرگیور کے فولڈر میں سیو کرنا (جیسا کہ آپ کی رپورٹ میں ہے)
        db.collection("caregivers").document(uid)
            .collection("exerciseResults")
            .add(resultData)
            .addOnSuccessListener {
                android.util.Log.d("Firebase", "Result saved successfully!")
            }
    }

    private fun speakOut(eng: String, urdu: String) {
        if (isTtsReady) {
            tts.setLanguage(if (isUrdu) Locale("ur", "PK") else Locale.US)
            tts.speak(if (isUrdu) urdu else eng, TextToSpeech.QUEUE_FLUSH, null, "msg")
        }
    }

    private fun isRunningActivity(): Boolean = !isFinishing && !isDestroyed

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts.setSpeechRate(0.8f)
            if (pendingFirstSpeak) {
                pendingFirstSpeak = false
                speakOut("Look carefully and remember.", "غور سے دیکھیں اور یاد رکھیں۔")
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}