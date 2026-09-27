package com.bittv.iptv.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bittv.iptv.R

/**
 * Bikin large icon notifikasi yang seragam di semua jenis notif app ini
 * (pengumuman, update channel, update aplikasi): badge bulat gradasi biru
 * dengan logo di tengah, plus warna aksen biru buat ikon kecil di status
 * bar. Sebelumnya tiap notif cuma pakai ikon default polos tanpa warna —
 * sekarang seragam dan kerasa "identitas app sendiri", bukan notif generik.
 */
object NotificationBranding {

    private var cachedLargeIcon: Bitmap? = null

    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.brand_blue)

    fun largeIcon(context: Context): Bitmap {
        cachedLargeIcon?.let { return it }

        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val brandBlueLight = ContextCompat.getColor(context, R.color.brand_blue_light)
        val brandBlueDark = ContextCompat.getColor(context, R.color.brand_blue_dark)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center * 0.85f, center * 0.8f, size * 0.75f,
                brandBlueLight, brandBlueDark,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(center, center, center, backgroundPaint)

        val logo = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_app_logo, context.theme)
        if (logo != null) {
            val inset = (size * 0.24f).toInt()
            logo.setBounds(inset, inset, size - inset, size - inset)
            logo.draw(canvas)
        }

        return bitmap.also { cachedLargeIcon = it }
    }
}
