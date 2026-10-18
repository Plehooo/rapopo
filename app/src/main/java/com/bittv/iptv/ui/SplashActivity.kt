package com.bittv.iptv.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bittv.iptv.R

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val openApp = Runnable {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        playEntranceAnimation()

        // Short branded splash: replace the launch-time black gap with
        // a clear "by DITZYA" screen while MainActivity is prepared.
        handler.postDelayed(openApp, 700L)
    }

    private fun playEntranceAnimation() {
        val logo = findViewById<FrameLayout>(R.id.splashLogoFrame)
        val title = findViewById<TextView>(R.id.splashTitle)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)
        val progress = findViewById<ProgressBar>(R.id.splashProgress)

        val logoPop = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.6f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.6f, 1f)
            )
            duration = 380L
            interpolator = OvershootInterpolator(2.2f)
        }

        val textsFadeUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 14f, 0f),
                ObjectAnimator.ofFloat(subtitle, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(subtitle, View.TRANSLATION_Y, 14f, 0f)
            )
            duration = 300L
            startDelay = 160L
            interpolator = DecelerateInterpolator()
        }

        val progressFadeIn = ObjectAnimator.ofFloat(progress, View.ALPHA, 0f, 1f).apply {
            duration = 250L
            startDelay = 320L
        }

        AnimatorSet().apply {
            playTogether(logoPop, textsFadeUp, progressFadeIn)
            start()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
