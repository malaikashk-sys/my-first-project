package com.example.dementiaassistance

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()

        val mainLayout = findViewById<FrameLayout>(R.id.splashMainLayout)
        val imgLogo = findViewById<ImageView>(R.id.ivLogo)
        val txtAppName = findViewById<TextView>(R.id.tvAppName)
        val txtSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        // ✅ Background Color Animation (Optional but Professional)
        // Ye background ko Teal se thora dark ya light shade mein animate karega
        val colorFrom = Color.parseColor("#1a5f6e")
        val colorTo = Color.parseColor("#12434d")
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = 2500 // 2.5 seconds
        colorAnimation.addUpdateListener { animator ->
            mainLayout.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()

        // ✅ Logo — scale up + fade in
        val logoAnim = AnimationSet(true)
        val scaleAnim = ScaleAnimation(
            0.5f, 1f, 0.5f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnim.duration = 1000
        val logoFade = AlphaAnimation(0f, 1f)
        logoFade.duration = 1000
        logoAnim.addAnimation(scaleAnim)
        logoAnim.addAnimation(logoFade)
        logoAnim.fillAfter = true
        imgLogo.startAnimation(logoAnim)

        // ✅ Text — fade in after logo
        val textFade = AlphaAnimation(0f, 1f)
        textFade.duration = 800
        textFade.startOffset = 900
        textFade.fillAfter = true
        txtAppName.startAnimation(textFade)
        txtSubtitle.startAnimation(textFade)

        // ✅ Go to RoleSelection after 3.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3500)
    }
}