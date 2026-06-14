package com.example.dementiaassistance

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class FamilyPhotosActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var isUrdu = false
    private var isCaregiverMode = false
    private lateinit var familyContainer: LinearLayout
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_photos)
        supportActionBar?.hide()

        isUrdu          = intent.getBooleanExtra("isUrdu", false)
        isCaregiverMode = intent.getBooleanExtra("isCaregiverMode", false)

        tts = TextToSpeech(this, this)
        familyContainer = findViewById(R.id.familyContainer)

        val btnAddMember = findViewById<Button>(R.id.btnAddMember)
        val txtTitle     = findViewById<TextView>(R.id.txtTitle)
        val btnBack      = findViewById<Button>(R.id.btnBack)

        if (isCaregiverMode) {
            txtTitle.text           = if (isUrdu) "👨‍👩‍👧‍👦 خاندان (نگران)" else "👨‍👩‍👧‍👦 Family (Caregiver)"
            btnAddMember.visibility = View.VISIBLE
            btnAddMember.text       = if (isUrdu) "+ خاندان کا فرد شامل کریں" else "+ Add Family Member"
        } else {
            txtTitle.text           = if (isUrdu) "👨‍👩‍👧‍👦 خاندان" else "👨‍👩‍👧‍👦 Family"
            btnAddMember.visibility = View.GONE
        }

        btnBack.text = if (isUrdu) "← واپس" else "← Back"
        btnBack.setOnClickListener { finish() }

        btnAddMember.setOnClickListener {
            startActivity(
                Intent(this, AddFamilyMemberActivity::class.java)
                    .putExtra("isUrdu", isUrdu)
            )
        }

        loadAndDisplayMembers()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts.setSpeechRate(0.85f)
            tts.setPitch(1.0f)
        }
    }

    private fun speakText(english: String, urdu: String) {
        if (!isTtsReady) return
        tts.setLanguage(if (isUrdu) Locale("ur", "PK") else Locale.US)
        tts.speak(if (isUrdu) urdu else english, TextToSpeech.QUEUE_FLUSH, null, "family_tts")
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayMembers()
    }

    private fun loadAndDisplayMembers() {
        familyContainer.removeAllViews()
        // ✅ Sirf local load — no Firebase Storage
        displayMembers(loadMembers(this))
    }

    private fun displayMembers(members: JSONArray) {
        familyContainer.removeAllViews()

        if (members.length() == 0) {
            familyContainer.addView(TextView(this).apply {
                text     = if (isUrdu) "کوئی فرد نہیں — اوپر + بٹن سے شامل کریں"
                else "No members yet — tap + above to add"
                textSize = 16f
                gravity  = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 0)
                setTextColor(0xFF888888.toInt())
            })
            return
        }

        for (i in 0 until members.length()) {
            val member     = members.getJSONObject(i)
            val nameEn     = member.optString("name", "")
            val nameUr     = member.optString("nameUrdu", nameEn)
            val relationEn = member.optString("relation", "")
            val relationUr = member.optString("relationUrdu", relationEn)

            val path1 = member.optString("imagePath",  "")
            val path2 = member.optString("imagePath2", "")
            val path3 = member.optString("imagePath3", "")

            val displayName     = if (isUrdu) nameUr else nameEn
            val displayRelation = if (isUrdu) relationUr else relationEn

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                setBackgroundColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, 24) }
                elevation   = 6f
                isClickable = true
                isFocusable = true
            }

            card.setOnClickListener {
                if (!isCaregiverMode) {
                    speakText(
                        "This is $nameEn. $nameEn is your $relationEn.",
                        "یہ $nameUr ہیں۔ $nameUr آپ کے $relationUr ہیں۔"
                    )
                }
            }

            card.addView(TextView(this).apply {
                text     = displayName
                textSize = 24f
                setTextColor(0xFF000000.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity  = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, 4) }
            })

            card.addView(TextView(this).apply {
                text     = displayRelation
                textSize = 17f
                setTextColor(0xFF2E7D8C.toInt())
                gravity  = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, 14) }
            })

            val photoRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, 0, 12) }
            }

            val photoPaths  = listOf(path1, path2, path3)
            val photoLabels = if (isUrdu)
                listOf("تصویر ۱", "تصویر ۲", "تصویر ۳")
            else
                listOf("Photo 1", "Photo 2", "Photo 3")

            val imgSizePx = (105 * resources.displayMetrics.density).toInt()

            for (j in photoPaths.indices) {
                val path = photoPaths[j]

                val slotLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity     = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.setMargins(4, 0, 4, 0) }
                }

                val imgView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(imgSizePx, imgSizePx).also {
                        it.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFFEEEEEE.toInt())
                }

                when {
                    path == "default1" -> imgView.setImageResource(R.drawable.family_member1)
                    path == "default2" -> imgView.setImageResource(R.drawable.family_member2)
                    path == "default3" -> imgView.setImageResource(R.drawable.family_member3)
                    path.isNotEmpty()  -> {
                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp != null) imgView.setImageBitmap(bmp)
                        else imgView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    else -> imgView.setImageResource(android.R.drawable.ic_menu_gallery)
                }

                val lblPhoto = TextView(this).apply {
                    text     = photoLabels[j]
                    textSize = 11f
                    setTextColor(0xFF888888.toInt())
                    gravity  = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 4, 0, 0) }
                }

                slotLayout.addView(imgView)
                slotLayout.addView(lblPhoto)
                photoRow.addView(slotLayout)
            }

            card.addView(photoRow)

            if (isCaregiverMode) {
                val btnRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity     = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 8, 0, 0) }
                }

                val btnEdit = Button(this).apply {
                    text     = if (isUrdu) "✏️ ترمیم" else "✏️ Edit"
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.setMargins(0, 0, 8, 0) }
                    setOnClickListener { editMember(i) }
                }

                val btnDelete = Button(this).apply {
                    text     = if (isUrdu) "🗑️ ڈیلیٹ" else "🗑️ Delete"
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.setMargins(8, 0, 0, 0) }
                    setBackgroundColor(0xFFE53935.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setOnClickListener { deleteMember(i) }
                }

                btnRow.addView(btnEdit)
                btnRow.addView(btnDelete)
                card.addView(btnRow)
            }

            familyContainer.addView(card)
        }
    }

    private fun editMember(position: Int) {
        val member = loadMembers(this).getJSONObject(position)
        startActivity(
            Intent(this, AddFamilyMemberActivity::class.java)
                .putExtra("isUrdu", isUrdu)
                .putExtra("editIndex", position)
                .putExtra("editName",         member.optString("name"))
                .putExtra("editNameUrdu",     member.optString("nameUrdu"))
                .putExtra("editRelation",     member.optString("relation"))
                .putExtra("editRelationUrdu", member.optString("relationUrdu"))
        )
    }

    private fun deleteMember(position: Int) {
        AlertDialog.Builder(this)
            .setTitle(if (isUrdu) "ڈیلیٹ کریں؟" else "Delete?")
            .setMessage(
                if (isUrdu) "کیا آپ واقعی ڈیلیٹ کرنا چاہتے ہیں؟"
                else "Are you sure you want to delete?"
            )
            .setPositiveButton(if (isUrdu) "ہاں" else "Yes") { _, _ ->
                val members    = loadMembers(this)
                val deleted    = members.getJSONObject(position)
                val memberName = deleted.optString("name", "member_$position")

                getSharedPreferences(FaceRecognitionActivity.PREFS_LANDMARKS, MODE_PRIVATE)
                    .edit()
                    .remove("sig_$memberName")
                    .remove("sig_$position")
                    .remove("emb_$memberName")
                    .apply()

                val newList = JSONArray()
                for (i in 0 until members.length()) {
                    if (i != position) newList.put(members.get(i))
                }
                saveMembers(this, newList)

                Toast.makeText(this,
                    if (isUrdu) "ڈیلیٹ ہو گیا ✅" else "Deleted ✅",
                    Toast.LENGTH_SHORT).show()
                loadAndDisplayMembers()
            }
            .setNegativeButton(if (isUrdu) "نہیں" else "No", null)
            .show()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        super.onDestroy()
    }

    companion object {
        fun loadMembers(context: android.content.Context): JSONArray {
            val prefs = context.getSharedPreferences("family_members", android.content.Context.MODE_PRIVATE)
            return JSONArray(prefs.getString("members", "[]") ?: "[]")
        }

        fun saveMembers(context: android.content.Context, members: JSONArray) {
            context.getSharedPreferences("family_members", android.content.Context.MODE_PRIVATE)
                .edit().putString("members", members.toString()).apply()
        }
    }
}