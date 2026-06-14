package com.example.dementiaassistance

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AddFamilyMemberActivity : AppCompatActivity() {

    private var isUrdu    = false
    private var editIndex = -1

    private val selectedImagePaths = arrayOfNulls<String>(3)
    private lateinit var imgSlots:   Array<ImageView>
    private lateinit var slotLabels: Array<TextView>
    private lateinit var btnSave:    Button
    private lateinit var faceNetHelper: FaceNetHelper

    companion object {
        private const val PICK_IMAGE_BASE = 300
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_family_member)
        supportActionBar?.hide()

        isUrdu    = intent.getBooleanExtra("isUrdu", false)
        editIndex = intent.getIntExtra("editIndex", -1)
        faceNetHelper = FaceNetHelper(this)

        val txtTitle       = findViewById<TextView>(R.id.txtTitle)
        val etName         = findViewById<EditText>(R.id.etMemberName)
        val etRelation     = findViewById<EditText>(R.id.etMemberRelation)
        val etNameUrdu     = findViewById<EditText>(R.id.etMemberNameUrdu)
        val etRelationUrdu = findViewById<EditText>(R.id.etMemberRelationUrdu)
        btnSave            = findViewById(R.id.btnSaveMember)
        val btnCancel      = findViewById<Button>(R.id.btnCancelMember)

        imgSlots   = arrayOf(
            findViewById(R.id.imgSlot1),
            findViewById(R.id.imgSlot2),
            findViewById(R.id.imgSlot3)
        )
        slotLabels = arrayOf(
            findViewById(R.id.lblSlot1),
            findViewById(R.id.lblSlot2),
            findViewById(R.id.lblSlot3)
        )

        val slotHints = if (isUrdu)
            arrayOf("📷 تصویر ۱", "📷 تصویر ۲", "📷 تصویر ۳")
        else
            arrayOf("📷 Photo 1", "📷 Photo 2", "📷 Photo 3")

        for (i in 0..2) {
            slotLabels[i].text = slotHints[i]
            imgSlots[i].setOnClickListener {
                val pick = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(pick, PICK_IMAGE_BASE + i)
            }
        }

        if (isUrdu) {
            txtTitle.text  = if (editIndex >= 0) "فرد تبدیل کریں" else "خاندان کا فرد شامل کریں"
            btnSave.text   = "💾 محفوظ کریں"
            btnCancel.text = "منسوخ"
        } else {
            txtTitle.text  = if (editIndex >= 0) "Edit Family Member" else "Add Family Member"
            btnSave.text   = "💾 Save"
            btnCancel.text = "Cancel"
        }

        if (editIndex >= 0) {
            etName.setText(intent.getStringExtra("editName") ?: "")
            etRelation.setText(intent.getStringExtra("editRelation") ?: "")
            etNameUrdu.setText(intent.getStringExtra("editNameUrdu") ?: "")
            etRelationUrdu.setText(intent.getStringExtra("editRelationUrdu") ?: "")
        }

        updateSaveButton()

        btnSave.setOnClickListener {
            val name         = etName.text.toString().trim()
            val relation     = etRelation.text.toString().trim()
            val nameUrdu     = etNameUrdu.text.toString().trim()
            val relationUrdu = etRelationUrdu.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this,
                    if (isUrdu) "انگریزی نام ضروری ہے" else "English name is required",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedImagePaths.filterNotNull().size < 3) {
                Toast.makeText(this,
                    if (isUrdu) "3 تصاویر ضروری ہیں" else "Please add all 3 photos",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val members = FamilyPhotosActivity.loadMembers(this)
            val member  = JSONObject().apply {
                put("name",         name)
                put("nameUrdu",     if (nameUrdu.isNotEmpty()) nameUrdu else name)
                put("relation",     relation)
                put("relationUrdu", if (relationUrdu.isNotEmpty()) relationUrdu else relation)
                put("imagePath",    selectedImagePaths[0] ?: "")
                put("imagePath2",   selectedImagePaths[1] ?: "")
                put("imagePath3",   selectedImagePaths[2] ?: "")
            }

            if (editIndex >= 0) members.put(editIndex, member)
            else members.put(member)
            FamilyPhotosActivity.saveMembers(this, members)

            val savedIndex = if (editIndex >= 0) editIndex else members.length() - 1

            // Clear old embeddings
            getSharedPreferences(FaceRecognitionActivity.PREFS_LANDMARKS, MODE_PRIVATE)
                .edit()
                .remove("emb_$name")
                .remove("sig_$name")
                .remove("sig_$savedIndex")
                .apply()

            extractEmbeddingsFromAllPhotos(
                paths     = selectedImagePaths.filterNotNull(),
                memberKey = name
            )
        }

        btnCancel.setOnClickListener { finish() }
    }

    private fun updateSaveButton() {
        val filled = selectedImagePaths.count { it != null }
        btnSave.isEnabled = filled == 3
        btnSave.alpha     = if (filled == 3) 1.0f else 0.5f
        findViewById<TextView>(R.id.txtPhotoProgress).text =
            if (isUrdu) "$filled/3 تصاویر منتخب" else "$filled/3 photos selected"
    }

    private fun extractEmbeddingsFromAllPhotos(paths: List<String>, memberKey: String) {
        Toast.makeText(this,
            if (isUrdu) "چہرے پہچانے جا رہے ہیں..." else "Extracting faces...",
            Toast.LENGTH_SHORT).show()

        val collectedEmbeddings = mutableListOf<String>()
        var processed = 0

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .build()
        )

        fun checkDone() {
            processed++
            if (processed == paths.size) {
                detector.close()
                if (collectedEmbeddings.isNotEmpty()) {
                    getSharedPreferences(FaceRecognitionActivity.PREFS_LANDMARKS, MODE_PRIVATE)
                        .edit()
                        .putString("emb_$memberKey", collectedEmbeddings.joinToString(";"))
                        .apply()
                    Toast.makeText(this,
                        if (isUrdu) "${collectedEmbeddings.size}/3 چہرے محفوظ ✅"
                        else "${collectedEmbeddings.size}/3 faces saved ✅",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this,
                        if (isUrdu) "کوئی چہرہ نہیں ملا ⚠️" else "No face found ⚠️ Use clear photos",
                        Toast.LENGTH_LONG).show()
                }
                faceNetHelper.close()
                finish()
            }
        }

        for (path in paths) {
            val fullBitmap = BitmapFactory.decodeFile(path)
            if (fullBitmap == null) { checkDone(); continue }

            val image = InputImage.fromBitmap(fullBitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val box    = faces[0].boundingBox
                        val left   = box.left.coerceAtLeast(0)
                        val top    = box.top.coerceAtLeast(0)
                        val width  = box.width().coerceAtMost(fullBitmap.width  - left)
                        val height = box.height().coerceAtMost(fullBitmap.height - top)

                        if (width > 0 && height > 0) {
                            val faceCrop  = Bitmap.createBitmap(fullBitmap, left, top, width, height)
                            val embedding = faceNetHelper.getEmbedding(faceCrop)
                            faceCrop.recycle()
                            if (embedding != null) {
                                collectedEmbeddings.add(embedding.joinToString(","))
                            }
                        }
                    }
                    fullBitmap.recycle()
                    checkDone()
                }
                .addOnFailureListener { fullBitmap.recycle(); checkDone() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val slotIndex = requestCode - PICK_IMAGE_BASE
        if (slotIndex in 0..2 && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data ?: return
            val savedPath = saveImageToStorage(uri)
            if (savedPath != null) {
                selectedImagePaths[slotIndex] = savedPath
                imgSlots[slotIndex].setImageBitmap(BitmapFactory.decodeFile(savedPath))
                val labels = if (isUrdu)
                    arrayOf("✅ تصویر ۱", "✅ تصویر ۲", "✅ تصویر ۳")
                else
                    arrayOf("✅ Photo 1", "✅ Photo 2", "✅ Photo 3")
                slotLabels[slotIndex].text = labels[slotIndex]
                slotLabels[slotIndex].setTextColor(0xFF2E7D8C.toInt())
                updateSaveButton()
            }
        }
    }

    private fun saveImageToStorage(uri: Uri): String? {
        return try {
            val inputStream  = contentResolver.openInputStream(uri)
            val file         = File(filesDir, "family_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close(); outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this,
                if (isUrdu) "تصویر محفوظ نہیں ہوئی" else "Failed to save image",
                Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { faceNetHelper.close() } catch (e: Exception) { }
    }
}