package com.example.dementiaassistance

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

class BreathingActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isUrdu = false
    private var isTtsReady = false

    private lateinit var txtTitle: TextView
    private lateinit var txtInstruction: TextView
    private lateinit var txtCircle: TextView
    private lateinit var txtPhase: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtRounds: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnBack: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var currentRound = 1
    private var totalRounds = 5
    private var difficultyLabel = ""

    private val inhaleCount = 4
    private val holdCount = 4
    private val exhaleCount = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breathing)
        supportActionBar?.hide()

        isUrdu = intent.getBooleanExtra("isUrdu", false)

        // Difficulty Settings
        val prefs = getSharedPreferences("ExerciseSettings", MODE_PRIVATE)
        val difficulty = prefs.getString("difficulty", "medium") ?: "medium"

        when (difficulty) {
            "low" -> {
                totalRounds = 3
                difficultyLabel = if (isUrdu) "آسان" else "Easy"
            }
            "high" -> {
                totalRounds = 7
                difficultyLabel = if (isUrdu) "مشکل" else "Hard"
            }
            else -> {
                totalRounds = 5
                difficultyLabel = if (isUrdu) "درمیانہ" else "Medium"
            }
        }

        tts = TextToSpeech(this, this)

        txtTitle = findViewById(R.id.txtTitle)
        txtInstruction = findViewById(R.id.txtInstruction)
        txtCircle = findViewById(R.id.txtCircle)
        txtPhase = findViewById(R.id.txtPhase)
        txtCount = findViewById(R.id.txtCount)
        txtRounds = findViewById(R.id.txtRounds)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnBack = findViewById(R.id.btnBack)

        if (isUrdu) {
            txtTitle.text = "🌬️ سانس کی ورزش"
            txtInstruction.text = " آہستہ سانس لیں اور دائرے کے ساتھ چلیں ($difficultyLabel)"
            txtPhase.text = "تیار ہیں..."
            btnStartStop.text = "▶ شروع کریں"
            btnBack.text = "🏠 گھر"
        } else {
            txtInstruction.text = " Breathe slowly and follow the circle ($difficultyLabel)"
        }

        updateRoundsText()

        btnStartStop.setOnClickListener {
            if (isRunning) stopBreathing()
            else startBreathing()
        }

        btnBack.setOnClickListener {
            stopBreathing()
            finish()
        }
    }

    private fun speakOut(english: String, urdu: String) {
        if (!isTtsReady) return
        if (isUrdu) {
            tts?.setLanguage(Locale("ur", "PK"))
            tts?.speak(urdu, TextToSpeech.QUEUE_FLUSH, null, "tts")
        } else {
            tts?.setLanguage(Locale.US)
            tts?.speak(english, TextToSpeech.QUEUE_FLUSH, null, "tts")
        }
    }

    private fun startBreathing() {
        isRunning = true
        currentRound = 1
        btnStartStop.text = if (isUrdu) "⏹ روکیں" else "⏹ Stop"
        btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))

        speakOut(
            "Let us begin. Breathe slowly and follow the circle.",
            "شروع کرتے ہیں۔ آہستہ سانس لیں اور دائرے کے ساتھ چلیں۔"
        )

        handler.postDelayed({ runInhale() }, 4000)
    }

    private fun runInhale() {
        if (!isRunning) return

        txtPhase.text = if (isUrdu) "سانس اندر لیں" else "Inhale"
        txtPhase.setTextColor(Color.parseColor("#1565C0"))
        txtCircle.setTextColor(Color.parseColor("#2196F3"))

        val scaleX = ObjectAnimator.ofFloat(txtCircle, "scaleX", 1f, 1.8f)
        val scaleY = ObjectAnimator.ofFloat(txtCircle, "scaleY", 1f, 1.8f)
        val anim = AnimatorSet()
        anim.playTogether(scaleX, scaleY)
        anim.duration = (inhaleCount * 1000).toLong()
        anim.start()

        speakOut("Inhale", "سانس اندر لیں")

        var count = 1
        val countRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (count <= inhaleCount) {
                    txtCount.text = count.toString()
                    count++
                    handler.postDelayed(this, 1000)
                } else {
                    runHold()
                }
            }
        }
        handler.post(countRunnable)
    }

    private fun runHold() {
        if (!isRunning) return

        txtPhase.text = if (isUrdu) "سانس روکیں" else "Hold"
        txtPhase.setTextColor(Color.parseColor("#E65100"))
        txtCircle.setTextColor(Color.parseColor("#FF9800"))

        speakOut("Hold", "سانس روکیں")

        var count = 1
        val countRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (count <= holdCount) {
                    txtCount.text = count.toString()
                    count++
                    handler.postDelayed(this, 1000)
                } else {
                    runExhale()
                }
            }
        }
        handler.post(countRunnable)
    }

    private fun runExhale() {
        if (!isRunning) return

        txtPhase.text = if (isUrdu) "سانس باہر چھوڑیں" else "Exhale"
        txtPhase.setTextColor(Color.parseColor("#2E7D32"))
        txtCircle.setTextColor(Color.parseColor("#4CAF50"))

        val scaleX = ObjectAnimator.ofFloat(txtCircle, "scaleX", 1.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(txtCircle, "scaleY", 1.8f, 1f)
        val anim = AnimatorSet()
        anim.playTogether(scaleX, scaleY)
        anim.duration = (exhaleCount * 1000).toLong()
        anim.start()

        speakOut("Exhale", "سانس باہر چھوڑیں")

        var count = 1
        val countRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (count <= exhaleCount) {
                    txtCount.text = count.toString()
                    count++
                    handler.postDelayed(this, 1000)
                } else {
                    if (currentRound < totalRounds) {
                        currentRound++
                        updateRoundsText()
                        handler.postDelayed({ runInhale() }, 1000)
                    } else {
                        finishExercise()
                    }
                }
            }
        }
        handler.post(countRunnable)
    }

    private fun finishExercise() {
        isRunning = false
        saveBreathingResult()
        txtPhase.text = if (isUrdu) "شاباش! 🌟" else "Well done! 🌟"
        txtPhase.setTextColor(Color.parseColor("#2E7D32"))
        txtCount.text = ""
        txtCircle.scaleX = 1f
        txtCircle.scaleY = 1f

        speakOut(
            "Excellent! You have completed the breathing exercise.",
            "شاباش! سانس کی ورزش مکمل ہوئی۔"
        )

        handler.postDelayed({
            val intent = Intent(this, ExerciseResultActivity::class.java)
            intent.putExtra("score", totalRounds)
            intent.putExtra("totalQuestions", totalRounds)
            intent.putExtra("exerciseClass", "BreathingExercise")
            intent.putExtra("isUrdu", isUrdu)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }, 4000)
    }
    private fun saveBreathingResult() {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val resultData = hashMapOf(
            "exercise" to "BreathingExercise", // رپورٹ کے نام سے میچ کرنا ضروری ہے
            "score" to totalRounds,
            "total" to totalRounds,
            "date" to sdfDate.format(Date()), // آج کی تاریخ
            "time" to sdfTime.format(Date()),
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("caregivers").document(uid)
            .collection("exerciseResults")
            .add(resultData)
            .addOnSuccessListener {
                android.util.Log.d("Firebase", "Breathing result saved!")
            }
    }
    private fun stopBreathing() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        txtPhase.text = if (isUrdu) "رکا ہوا" else "Stopped"
        txtCount.text = ""
        txtCircle.scaleX = 1f
        txtCircle.scaleY = 1f
        btnStartStop.text = if (isUrdu) "▶ شروع کریں" else "▶ Start"
        btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
    }

    private fun updateRoundsText() {
        txtRounds.text = if (isUrdu) "راؤنڈ $currentRound / $totalRounds" else "Round $currentRound of $totalRounds"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.setSpeechRate(0.8f)
        }
    }

    override fun onDestroy() {
        stopBreathing()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}