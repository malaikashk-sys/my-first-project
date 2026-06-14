package com.example.dementiaassistance

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceRecognitionActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isUrdu = false
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceNetHelper: FaceNetHelper

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var txtStatus:     TextView
    private lateinit var txtResult:     TextView
    private lateinit var txtRelation:   TextView
    private lateinit var txtConfidence: TextView
    private lateinit var txtActivity:   TextView
    private lateinit var btnScan:       Button
    private lateinit var progressBar:   ProgressBar
    private lateinit var cameraPreview: PreviewView

    private var isProcessing = false

    private val eyeOpennessHistory  = mutableListOf<Float>()
    private val activityHandler     = Handler(Looper.getMainLooper())
    private var lastActivityUpdate  = 0L
    private val ACTIVITY_UPDATE_INTERVAL = 500L

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.10f)
                .enableTracking()
                .build()
        )
    }

    companion object {
        private const val TAG = "FaceRecognition"
        private const val CAMERA_PERMISSION_CODE = 200
        const val PREFS_LANDMARKS = "FaceLandmarkPrefs"

        // Threshold: cosine similarity 0..1 (FaceNet embeddings)
        // 0.70 = good balance; raise to 0.75 if false positives
        private const val MATCH_THRESHOLD = 0.75f

        data class MatchResult(
            val index:        Int,
            val score:        Float,
            val name:         String,
            val nameUrdu:     String,
            val relation:     String,
            val relationUrdu: String
        )

        enum class MatchQuality { PERFECT, GOOD, POSSIBLE, UNKNOWN }

        fun getMatchQuality(score: Float): MatchQuality = when {
            score >= 0.85f -> MatchQuality.PERFECT
            score >= 0.78f -> MatchQuality.GOOD
            score >= 0.70f -> MatchQuality.POSSIBLE
            else           -> MatchQuality.UNKNOWN
        }
    }

    // ── YUV → Bitmap (live preview) ───────────────────────────────────────
    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize   = yBuffer.remaining()
            val uSize   = uBuffer.remaining()
            val vSize   = vBuffer.remaining()
            val nv21    = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = android.graphics.YuvImage(
                nv21, android.graphics.ImageFormat.NV21, width, height, null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion failed", e); null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_recognition)
        supportActionBar?.hide()

        isUrdu        = intent.getBooleanExtra("isUrdu", false)
        tts           = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        faceNetHelper  = FaceNetHelper(this)

        initUI()
        requestCameraPermission()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(if (isUrdu) Locale("ur", "PK") else Locale.US)
        }
    }

    private fun initUI() {
        txtStatus     = findViewById(R.id.txtStatus)
        txtResult     = findViewById(R.id.txtResult)
        txtRelation   = findViewById(R.id.txtRelation)
        txtConfidence = findViewById(R.id.txtConfidence)
        txtActivity   = findViewById(R.id.txtActivity)
        btnScan       = findViewById(R.id.btnScan)
        progressBar   = findViewById(R.id.progressBar)
        cameraPreview = findViewById(R.id.cameraPreview)

        val btnClose  = findViewById<Button>(R.id.btnClose)
        val txtTitle  = findViewById<TextView>(R.id.txtFaceTitle)

        findViewById<Button>(R.id.btnCameraSwitch).visibility = View.GONE
        findViewById<TextView>(R.id.txtCameraMode).visibility = View.GONE

        if (isUrdu) {
            txtTitle.text   = "👁️ چہرہ پہچان"
            txtStatus.text  = "کیمرہ کسی کی طرف کریں"
            txtActivity.text= "سرگرمی: چیک ہو رہی..."
            btnScan.text    = "📷 اسکین کریں"
            btnClose.text   = "✕"
        } else {
            txtTitle.text   = "👁️ Face Recognition"
            txtStatus.text  = "Point camera at person"
            txtActivity.text= "Activity: Analyzing..."
            btnScan.text    = "📷 Scan"
            btnClose.text   = "✕"
        }

        btnClose.setOnClickListener { finish() }
        btnScan.setOnClickListener  { if (!isProcessing) captureAndRecognize() }
    }

    private fun resetUI() {
        runOnUiThread {
            isProcessing      = false
            btnScan.isEnabled = true
            btnScan.text      = if (isUrdu) "📷 اسکین کریں" else "📷 Scan"
            progressBar.visibility = View.GONE
            txtStatus.text    = if (isUrdu) "کیمرہ کسی کی طرف کریں" else "Point camera at person"
            findViewById<View>(R.id.resultCard).visibility = View.GONE
            txtResult.text = ""; txtRelation.text = ""; txtConfidence.text = ""
            txtActivity.text = if (isUrdu) "سرگرمی: چیک ہو رہی..." else "Activity: Analyzing..."
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startCamera()
            else { Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show(); finish() }
        }
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener({
                try {
                    cameraProvider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(cameraPreview.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        .also { it.setAnalyzer(cameraExecutor, LiveFaceAnalyzer()) }

                    cameraProvider!!.unbindAll()
                    cameraProvider!!.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, analysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Camera start failed", e)
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    // ── Live analyzer — only for blink/activity detection ─────────────────
    private inner class LiveFaceAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (isProcessing) { imageProxy.close(); return }
            val bitmap = imageProxy.toBitmap()
            bitmap?.let { bmp ->
                faceDetector.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { faces ->
                        val now = System.currentTimeMillis()
                        if (now - lastActivityUpdate >= ACTIVITY_UPDATE_INTERVAL) {
                            lastActivityUpdate = now
                            updateLiveActivity(if (faces.isNotEmpty()) faces[0] else null)
                        }
                    }
                    .addOnCompleteListener { bmp.recycle(); imageProxy.close() }
            } ?: imageProxy.close()
        }
    }

    // ── Capture & Recognize ───────────────────────────────────────────────
    private fun captureAndRecognize() {
        val cap = imageCapture ?: run {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show(); return
        }
        isProcessing      = true
        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE
        btnScan.text      = if (isUrdu) "🔄 اسکین ہو رہا..." else "🔄 Scanning..."
        txtStatus.text    = if (isUrdu) "چہرہ تلاش..." else "Detecting face..."

        val outputFile = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        cap.takePicture(
            ImageCapture.OutputFileOptions.Builder(outputFile).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    detectFaceAndMatch(outputFile)
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", e)
                    runOnUiThread { resetUI() }
                }
            }
        )
    }

    private fun detectFaceAndMatch(file: File) {
        val fullBitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (fullBitmap == null) { file.delete(); runOnUiThread { resetUI() }; return }

        faceDetector.process(InputImage.fromBitmap(fullBitmap, 0))
            .addOnSuccessListener { faces ->
                file.delete()
                if (faces.isEmpty()) {
                    fullBitmap.recycle()
                    runOnUiThread {
                        txtStatus.text = if (isUrdu) "❌ چہرہ نہیں ملا" else "❌ No face found"
                        speakBoth("No face detected. Move closer.", "چہرہ نہیں ملا۔ قریب آئیں۔")
                        resetUI()
                    }
                    return@addOnSuccessListener
                }

                // Crop face using bounding box
                val box    = faces[0].boundingBox
                val left   = box.left.coerceAtLeast(0)
                val top    = box.top.coerceAtLeast(0)
                val width  = box.width().coerceAtMost(fullBitmap.width  - left)
                val height = box.height().coerceAtMost(fullBitmap.height - top)

                if (width <= 0 || height <= 0) {
                    fullBitmap.recycle()
                    runOnUiThread { resetUI() }
                    return@addOnSuccessListener
                }

                val faceCrop  = Bitmap.createBitmap(fullBitmap, left, top, width, height)
                fullBitmap.recycle()

                // Get FaceNet embedding for scanned face
                val scannedEmbedding = faceNetHelper.getEmbedding(faceCrop)
                faceCrop.recycle()

                if (scannedEmbedding == null) {
                    runOnUiThread {
                        txtStatus.text = if (isUrdu) "⚠️ روشنی بہتر کریں" else "⚠️ Better lighting needed"
                        resetUI()
                    }
                    return@addOnSuccessListener
                }

                matchWithStoredEmbeddings(scannedEmbedding)
            }
            .addOnFailureListener { e ->
                file.delete(); fullBitmap.recycle()
                Log.e(TAG, "Detection failed", e)
                runOnUiThread { resetUI() }
            }
    }

    // ── FaceNet matching ──────────────────────────────────────────────────
    private fun matchWithStoredEmbeddings(scannedEmbedding: FloatArray) {
        try {
            val members = FamilyPhotosActivity.loadMembers(this)
            val prefs   = getSharedPreferences(PREFS_LANDMARKS, MODE_PRIVATE)
            var bestMatch: MatchResult? = null

            for (i in 0 until members.length()) {
                val member     = members.getJSONObject(i)
                val memberName = member.optString("name", "member_$i")
                val allStoredEmbs = prefs.getString("emb_$memberName", null) ?: continue

                val embList = allStoredEmbs.split(";")
                val scores = mutableListOf<Float>()
                for (embStr in embList) {
                    if (embStr.isEmpty()) continue
                    val storedEmb = embStr.split(",").map { it.toFloatOrNull() ?: 0f }.toFloatArray()
                    if (storedEmb.size == scannedEmbedding.size) {
                        scores.add(faceNetHelper.cosineSimilarity(scannedEmbedding, storedEmb))
                    }
                }
                val highestScore = if (scores.isEmpty()) 0f
                else scores.sortedDescending().take(3).average().toFloat()

                if (bestMatch == null || highestScore > bestMatch!!.score) {
                    bestMatch = MatchResult(
                        index        = i,
                        score        = highestScore,
                        name         = memberName,
                        nameUrdu     = member.optString("nameUrdu", memberName),
                        relation     = member.optString("relation", ""),
                        relationUrdu = member.optString("relationUrdu", "")
                    )
                }
            }

            runOnUiThread {
                // ✅ اگر میچ مل گیا اور اسکور اچھا ہے
                if (bestMatch != null && bestMatch!!.score >= MATCH_THRESHOLD) {
                    showMatchResult(bestMatch!!, getMatchQuality(bestMatch!!.score))
                }
                // ❌ اگر چہرہ ڈیٹیکٹ ہوا لیکن پہچانا نہیں گیا (Unknown)
                else {
                    findViewById<View>(R.id.resultCard).visibility = View.VISIBLE // کارڈ دکھائیں
                    txtStatus.text = if (isUrdu) "❌ انجان چہرہ" else "❌ Unknown Person"
                    txtResult.text = if (isUrdu) "غیر معروف" else "Unknown"
                    txtRelation.text = if (isUrdu) "کوئی ریکارڈ نہیں ملا" else "No record found"
                    txtConfidence.text = "Match: ${(bestMatch?.score?.times(100))?.toInt() ?: 0}%"
                    txtConfidence.setTextColor(Color.RED)

                    speakBoth("Unknown person detected.", "انجان شخص سامنے ہے۔")

                    activityHandler.postDelayed({ resetUI() }, 5000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Matching failed", e)
            runOnUiThread { resetUI() }
        }
    }
    // ── Show result ───────────────────────────────────────────────────────
    private fun showMatchResult(match: MatchResult, quality: MatchQuality) {
        findViewById<View>(R.id.resultCard).visibility = View.VISIBLE

        val displayName     = if (isUrdu) match.nameUrdu     else match.name
        val displayRelation = if (isUrdu) match.relationUrdu else match.relation

        txtResult.text   = displayName
        txtRelation.text = "${if (isUrdu) "رشتہ" else "Relation"}: $displayRelation"
        txtConfidence.text = "Match: ${(match.score * 100).toInt()}% ${quality.name}"
        txtConfidence.setTextColor(when (quality) {
            MatchQuality.PERFECT  -> 0xFF4CAF50.toInt()
            MatchQuality.GOOD     -> 0xFF2196F3.toInt()
            MatchQuality.POSSIBLE -> 0xFFFF9800.toInt()
            else                  -> 0xFFF44336.toInt()
        })
        txtStatus.text = if (isUrdu) "✅ پہچان لیا گیا!" else "✅ Recognized!"

        val speech = if (isUrdu) "${match.nameUrdu} ہے، ${match.relationUrdu}۔"
        else "This is ${match.name}, your ${match.relation}."
        speakBoth(speech, speech)

        activityHandler.postDelayed({ resetUI() }, 5000)
    }

    // ── Live activity (blink detection) ───────────────────────────────────
    private fun updateLiveActivity(face: Face?) {
        runOnUiThread {
            if (face != null) {
                val a = detectBlinkActivity(face)
                txtActivity.text = if (isUrdu) "سرگرمی: $a" else "Activity: $a"
                txtActivity.setTextColor(when {
                    a.contains("Alert") || a.contains("چوکنا") -> 0xFF4CAF50.toInt()
                    a.contains("Drowsy") || a.contains("ہلکا") -> 0xFFFF9800.toInt()
                    else -> 0xFFF44336.toInt()
                })
            } else {
                txtActivity.text = if (isUrdu) "سرگرمی: کوئی چہرہ نہیں" else "Activity: No face"
                txtActivity.setTextColor(0xFFFF9800.toInt())
            }
        }
    }

    private fun detectBlinkActivity(face: Face): String {
        val prob   = ((face.leftEyeOpenProbability ?: 0.5f) + (face.rightEyeOpenProbability ?: 0.5f)) / 2
        if (eyeOpennessHistory.size >= 10) eyeOpennessHistory.removeAt(0)
        eyeOpennessHistory.add(prob)
        val recent  = eyeOpennessHistory.takeLast(5).average().toFloat()
        val overall = eyeOpennessHistory.average().toFloat()
        return when {
            recent > 0.75f && overall > 0.7f -> if (isUrdu) "👀 چوکنا"      else "👀 Alert"
            prob   < 0.3f                    -> if (isUrdu) "😴 سو رہا"     else "😴 Sleeping"
            else                             -> if (isUrdu) "😴 ہلکا سو رہا" else "😴 Drowsy"
        }
    }

    private fun speakBoth(english: String, urdu: String) {
        try { tts.speak(if (isUrdu) urdu else english, TextToSpeech.QUEUE_FLUSH, null, null) }
        catch (e: Exception) { Log.e(TAG, "TTS failed", e) }
    }

    override fun onPause() {
        super.onPause()
        activityHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            faceDetector.close()
            faceNetHelper.close()
            if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        } catch (e: Exception) { Log.e(TAG, "Cleanup failed", e) }
    }
}