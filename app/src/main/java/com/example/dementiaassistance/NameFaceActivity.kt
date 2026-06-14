package com.example.dementiaassistance

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.util.Locale
import android.content.res.ColorStateList
import android.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class NameFaceActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isUrdu = false
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentDifficulty: String = "medium"
    private var maxQuestions: Int = 5

    private lateinit var txtTitle: TextView
    private lateinit var txtInstruction: TextView
    private lateinit var imgFace: ImageView
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var btnOption3: Button
    private lateinit var txtResult: TextView
    private lateinit var btnNext: Button
    private lateinit var btnBack: Button

    private var currentIndex = 0
    private var score = 0
    private var realMembers = JSONArray()
    private var questionList = mutableListOf<QuestionItem>()

    data class QuestionItem(
        val imagePath: String,        // ← ab randomly photo1/2/3 mein se ek
        val correctName: String,
        val correctNameUrdu: String,
        val options: List<String>,
        val optionsUrdu: List<String>,
        val correctIndex: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_name_face)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        // Safe Difficulty Loading
        val prefs = getSharedPreferences("ExerciseSettings", MODE_PRIVATE)
        currentDifficulty = prefs.getString("difficulty", "medium") ?: "medium"

        maxQuestions = when (currentDifficulty) {
            "low" -> 3
            "high" -> 7
            else -> 5
        }

        tts = TextToSpeech(this, this)

        initUI()
        loadRealMembers()
    }

    private fun initUI() {
        txtTitle = findViewById(R.id.txtTitle)
        txtInstruction = findViewById(R.id.txtInstruction)
        imgFace = findViewById(R.id.imgFace)
        btnOption1 = findViewById(R.id.btnOption1)
        btnOption2 = findViewById(R.id.btnOption2)
        btnOption3 = findViewById(R.id.btnOption3)
        txtResult = findViewById(R.id.txtResult)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)

        val levelLabel = if (isUrdu) {
            when (currentDifficulty) {
                "low" -> "آسان"
                "high" -> "مشکل"
                else -> "درمیانہ"
            }
        } else {
            when (currentDifficulty) {
                "low" -> "Easy"
                "high" -> "Hard"
                else -> "Medium"
            }
        }

        txtTitle.text = if (isUrdu) "👤 نام یاد کریں ($levelLabel)" else "👤 Name & Face ($levelLabel)"
        if (isUrdu) btnBack.text = "← واپسی"

        btnBack.setOnClickListener { finish() }
        btnNext.setOnClickListener {
            currentIndex++
            if (currentIndex < questionList.size) showQuestion()
            else showFinalScore()
        }
    }

    private fun loadRealMembers() {
        try {
            // FamilyPhotosActivity ka static function call krna
            val allMembers = FamilyPhotosActivity.loadMembers(this)
            realMembers = JSONArray()

            for (i in 0 until allMembers.length()) {
                val m = allMembers.getJSONObject(i)
                if (!m.getString("imagePath").startsWith("default")) {
                    realMembers.put(m)
                }
            }

            if (realMembers.length() < 2) {
                txtInstruction.text = if (isUrdu) "⚠️ کم از کم 2 تصاویر شامل کریں" else "⚠️ Add 2 photos first"
                imgFace.visibility = View.GONE
                return
            }

            buildQuestions()

        } catch (e: Exception) {
            txtInstruction.text = "Error loading photos"
        }
    }

    private fun buildQuestions() {
        questionList.clear()
        val total  = realMembers.length()
        val rounds = minOf(maxQuestions, total)

        // Members ko shuffle karo taake random order mein aayein
        val shuffledIndices = (0 until total).toMutableList().also { it.shuffle() }

        for (loopIdx in 0 until rounds) {
            val i       = shuffledIndices[loopIdx]
            val correct = realMembers.getJSONObject(i)
            val cName      = correct.getString("name")
            val cNameUrdu  = correct.optString("nameUrdu", cName)

            // ✅ 3 photos mein se randomly ek chuno
            val availablePaths = mutableListOf<String>()
            val p1 = correct.optString("imagePath",  "")
            val p2 = correct.optString("imagePath2", "")
            val p3 = correct.optString("imagePath3", "")
            if (p1.isNotEmpty()) availablePaths.add(p1)
            if (p2.isNotEmpty()) availablePaths.add(p2)
            if (p3.isNotEmpty()) availablePaths.add(p3)

            val chosenPath = if (availablePaths.isNotEmpty())
                availablePaths.random()   // ← har baar nai photo!
            else continue                 // koi photo nahi — skip

            // Wrong options
            val wrongNames      = mutableListOf<String>()
            val wrongNamesUrdu  = mutableListOf<String>()
            for (j in 0 until total) {
                if (j != i) {
                    wrongNames.add(realMembers.getJSONObject(j).getString("name"))
                    wrongNamesUrdu.add(realMembers.getJSONObject(j).optString("nameUrdu", ""))
                    if (wrongNames.size == 2) break
                }
            }
            while (wrongNames.size < 2) { wrongNames.add("Unknown"); wrongNamesUrdu.add("نامعلوم") }

            // Correct answer random position pe
            val correctIdx = (0..2).random()
            val names      = mutableListOf("", "", "")
            val namesUrdu  = mutableListOf("", "", "")
            names[correctIdx]      = cName
            namesUrdu[correctIdx]  = cNameUrdu
            var wIdx = 0
            for (k in 0..2) {
                if (k != correctIdx) {
                    names[k]     = wrongNames[wIdx]
                    namesUrdu[k] = wrongNamesUrdu[wIdx]
                    wIdx++
                }
            }

            questionList.add(
                QuestionItem(chosenPath, cName, cNameUrdu, names, namesUrdu, correctIdx)
            )
        }
        questionList.shuffle()
    }

    private fun showQuestion() {
        if (currentIndex >= questionList.size) return
        val q = questionList[currentIndex]

        txtResult.text = ""
        btnNext.visibility = View.GONE
        txtInstruction.text = if (isUrdu) "یہ کون ہیں؟" else "Who is this?"

        val bitmap = BitmapFactory.decodeFile(q.imagePath)
        imgFace.setImageBitmap(bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.ic_person))

        val buttons = listOf(btnOption1, btnOption2, btnOption3)
        val opts = if (isUrdu) q.optionsUrdu else q.options

        buttons.forEachIndexed { i, btn ->
            btn.text = opts[i]
            btn.isEnabled = true
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D8C"))
            btn.setOnClickListener { checkAnswer(i, q, buttons) }
        }

        if (isTtsReady) {
            handler.postDelayed({ triggerAudio(q) }, 1000)
        }
    }

    private fun triggerAudio(q: QuestionItem) {
        // اردو اور انگلش دونوں کے لیے صحیح سوال سیٹ کریں
        val mainQuestion = if (isUrdu) "تصویر میں یہ کون ہیں؟" else "Who is this person?"
        speakOut(mainQuestion, mainQuestion)

        val opts = if (isUrdu) q.optionsUrdu else q.options
        handler.postDelayed({
            val p = if (isUrdu) "آپشن نمبر 1: ${opts[0]}۔۔۔ آپشن نمبر 2: ${opts[1]}۔۔۔ آپشن نمبر 3: ${opts[2]}"
            else "Option 1: ${opts[0]}, Option 2: ${opts[1]}, Option 3: ${opts[2]}"

            // یہاں QUEUE_ADD استعمال کریں تاکہ سوال کے بعد آپشنز بجیں
            tts.speak(p, TextToSpeech.QUEUE_ADD, null, "opts")
        }, 2000)
    }

    private fun checkAnswer(sel: Int, q: QuestionItem, btns: List<Button>) {
        btns.forEach { it.isEnabled = false }
        tts.stop()
        handler.removeCallbacksAndMessages(null)

        if (sel == q.correctIndex) {
            score++
            btns[sel].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            txtResult.text = if (isUrdu) "✅ صحیح!" else "✅ Correct!"
            txtResult.setTextColor(Color.parseColor("#4CAF50"))
            speakOut("Correct", "صحیح جواب")
        } else {
            btns[sel].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
            btns[q.correctIndex].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            txtResult.text = if (isUrdu) "❌ غلط" else "❌ Wrong"
            txtResult.setTextColor(Color.parseColor("#F44336"))
            speakOut("Wrong", "غلط جواب")
        }

        btnNext.visibility = View.VISIBLE
        btnNext.text = if (currentIndex < questionList.size - 1) (if (isUrdu) "اگلا" else "Next") else (if (isUrdu) "مکمل" else "Finish")
    }

    private fun showFinalScore() {
        saveNameFaceResult()
        val intent = Intent(this, ExerciseResultActivity::class.java)
        intent.putExtra("score", score)
        intent.putExtra("totalQuestions", questionList.size)
        intent.putExtra("exerciseClass", "NameFace")
        intent.putExtra("isUrdu", isUrdu)
        startActivity(intent)
        finish()
    }
    private fun saveNameFaceResult() {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val resultData = hashMapOf(
            "exercise" to "NameFace", // یہ نام رپورٹ والے نام سے میچ ہونا چاہیے
            "score" to score,
            "total" to questionList.size,
            "date" to sdfDate.format(Date()), // آج کی صحیح تاریخ
            "time" to sdfTime.format(Date()),
            "timestamp" to System.currentTimeMillis()
        )

        // کیئرگیور کے 'exerciseResults' کلکشن میں ڈیٹا سیو کرنا
        db.collection("caregivers").document(uid)
            .collection("exerciseResults")
            .add(resultData)
            .addOnSuccessListener {
                android.util.Log.d("Firebase", "NameFace result saved!")
            }
    }
    private fun speakOut(en: String, ur: String) {
        if (isTtsReady) {
            tts.setLanguage(if (isUrdu) Locale("ur", "PK") else Locale.US)
            tts.speak(if (isUrdu) ur else en, TextToSpeech.QUEUE_FLUSH, null, "msg")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts.setSpeechRate(0.8f)

            // ✅ اب یہاں سے پہلا سوال شروع ہوگا، جب آواز ریڈی ہو چکی ہوگی
            handler.postDelayed({
                if (questionList.isNotEmpty()) {
                    showQuestion()
                }
            }, 1000) // 1 سیکنڈ کا وقفہ تاکہ انجن مکمل طور پر بیدار ہو جائے
        }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }
}