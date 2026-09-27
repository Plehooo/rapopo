package com.bittv.iptv.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Lightweight pseudo-3D/2.5D arena; GPU-friendly, no network or model downloads. */
class GameWorld3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = Array(24) { Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }
    private val heroPath = Path()
    private val bossPath = Path()
    private val rect = RectF()
    private var heroPower = 100
    private var bossPower = 130
    private var tick = 0L
    private var impact = 0f
    private var running = true

    init {
        isFocusable = false
        grid.strokeWidth = dp(1f)
    }

    fun setStats(heroPower: Int, bossPower: Int) {
        this.heroPower = heroPower.coerceAtLeast(1)
        this.bossPower = bossPower.coerceAtLeast(1)
        invalidate()
    }

    fun impact() {
        impact = 1f
        invalidate()
    }

    fun setAnimationEnabled(enabled: Boolean) {
        running = enabled
        if (enabled) postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Dark space background.
        canvas.drawColor(0xFF070A10.toInt())

        // Stars / particles.
        p.style = Paint.Style.FILL
        particles.forEachIndexed { i, s ->
            val px = (s.x * w + ((tick * (8 + i % 5)) % w)) % w
            val py = s.y * h
            val a = (70 + 110 * (0.5 + 0.5 * sin(tick * 0.035 + i))).toInt().coerceIn(30, 180)
            p.color = (a shl 24) or 0xD6E6FF
            canvas.drawCircle(px, py, dp(0.8f + s.z * 1.5f), p)
        }

        // Perspective horizon + floor grid.
        val horizon = h * 0.46f
        grid.color = 0x6638BDF8
        grid.strokeWidth = dp(1f)
        for (i in -7..7) {
            val bottomX = w / 2f + i * w * 0.095f
            canvas.drawLine(w / 2f, horizon, bottomX, h, grid)
        }
        for (j in 1..9) {
            val t = j / 9f
            val y = horizon + (h - horizon) * (t * t)
            canvas.drawLine(0f, y, w, y, grid)
        }
        p.style = Paint.Style.FILL

        // Orbiting energy core.
        val cx = w * 0.5f
        val cy = h * 0.35f + sin(tick * 0.035) * dp(6f)
        val pulse = 1f + 0.09f * sin(tick * 0.08)
        p.color = 0x2038BDF8
        canvas.drawCircle(cx, cy, dp(34f) * pulse, p)
        p.color = 0x8838BDF8
        canvas.drawCircle(cx, cy, dp(18f) * pulse, p)
        p.color = 0xFFECF7FF.toInt()
        canvas.drawCircle(cx, cy, dp(6f) * pulse, p)

        // Floating hero + boss, rotated in 3D-like perspective.
        val heroX = w * 0.27f
        val bossX = w * 0.73f
        val floatY = sin(tick * 0.045) * dp(7f)
        drawCharacter(canvas, heroX, h * 0.61f + floatY, dp(34f), heroPath, true, tick * 0.02f)
        drawCharacter(canvas, bossX, h * 0.61f - floatY, dp(44f), bossPath, false, -tick * 0.015f)

        // Power rails.
        drawPower(canvas, heroX, h * 0.81f, heroPower, "HERO")
        drawPower(canvas, bossX, h * 0.81f, bossPower, "BOSS")

        if (impact > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2f)
            p.color = 0xB0FFFFFF.toInt()
            canvas.drawCircle(cx, cy, dp(28f) + dp(46f) * (1f - impact), p)
            p.style = Paint.Style.FILL
            impact = (impact - 0.08f).coerceAtLeast(0f)
        }

        tick++
        if (running && visibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun drawCharacter(canvas: Canvas, x: Float, y: Float, size: Float, path: Path, hero: Boolean, rot: Float) {
        canvas.save()
        canvas.translate(x, y)
        val scaleX = 0.82f + 0.18f * cos(rot)
        canvas.scale(scaleX, 1f)
        path.reset()
        path.moveTo(0f, -size)
        path.lineTo(size * 0.72f, -size * 0.2f)
        path.lineTo(size * 0.45f, size)
        path.lineTo(-size * 0.45f, size)
        path.lineTo(-size * 0.72f, -size * 0.2f)
        path.close()
        p.color = if (hero) 0xFF4FD8FF.toInt() else 0xFFFF5F78.toInt()
        canvas.drawPath(path, p)
        p.color = 0xFF10151D
        canvas.drawCircle(0f, -size * 0.15f, size * 0.34f, p)
        p.color = 0xFFF7FBFF.toInt()
        canvas.drawCircle(-size * 0.12f, -size * 0.2f, size * 0.055f, p)
        canvas.drawCircle(size * 0.12f, -size * 0.2f, size * 0.055f, p)
        canvas.restore()
    }

    private fun drawPower(canvas: Canvas, x: Float, y: Float, value: Int, label: String) {
        val barW = dp(78f)
        val left = x - barW / 2f
        p.color = 0x55333B4A
        canvas.drawRoundRect(RectF(left, y, left + barW, y + dp(7f)), dp(5f), dp(5f), p)
        val maxP = maxOf(heroPower, bossPower, 1)
        p.color = if (label == "HERO") 0xFF4FD8FF.toInt() else 0xFFFF5F78.toInt()
        canvas.drawRoundRect(RectF(left, y, left + barW * (value.toFloat() / maxP).coerceIn(0f, 1f), y + dp(7f)), dp(5f), dp(5f), p)
        p.textSize = dp(9f)
        p.color = 0xFFCCD6E5.toInt()
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("$label  $value", x, y + dp(22f), p)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private data class Particle(val x: Float, val y: Float, val z: Float)
}
