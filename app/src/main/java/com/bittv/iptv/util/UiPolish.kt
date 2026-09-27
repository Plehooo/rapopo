package com.bittv.iptv.util

import android.app.Activity
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

/** Shared, lightweight UI polish for every programmatic Game/Social screen. */
object UiPolish {
    fun setupEdgeToEdge(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT

        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(
                left = max(baseLeft, baseLeft + bars.left),
                top = baseTop + bars.top,
                right = max(baseRight, baseRight + bars.right),
                bottom = baseBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun polish(root: View) {
        SoundFxManager.init(root.context.applicationContext)
        polishRecursive(root)
    }

    private fun polishRecursive(view: View) {
        when {
            view is Button -> attachPressMotion(view)
            view.isClickable && view is ViewGroup -> attachPressMotion(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) polishRecursive(view.getChildAt(i))
        }
    }

    private fun attachPressMotion(view: View) {
        if (view.getTag(com.bittv.iptv.R.id.ui_polished) == true) return
        view.setTag(com.bittv.iptv.R.id.ui_polished, true)
        view.isSoundEffectsEnabled = true
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate()
                    .scaleX(0.985f).scaleY(0.985f).setDuration(70L).start()
                MotionEvent.ACTION_UP -> {
                    SoundFxManager.play(v.context, SoundFxManager.Fx.TAP, 0.55f)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                }
                MotionEvent.ACTION_CANCEL -> v.animate()
                    .scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            false
        }
    }

}
