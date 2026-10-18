package com.bittv.iptv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max

class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var aspectWidth: Int = 16
        set(value) {
            field = max(1, value)
            requestLayout()
        }

    var aspectHeight: Int = 9
        set(value) {
            field = max(1, value)
            requestLayout()
        }

    var useFullHeight: Boolean = false
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (useFullHeight) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        if (widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0) {
            val targetHeight =
                (widthSize.toFloat() * aspectHeight / aspectWidth).toInt()
                    .coerceAtLeast(1)

            val exactHeight =
                MeasureSpec.makeMeasureSpec(
                    targetHeight,
                    MeasureSpec.EXACTLY
                )

            super.onMeasure(
                widthMeasureSpec,
                exactHeight
            )
        } else {
            super.onMeasure(
                widthMeasureSpec,
                heightMeasureSpec
            )
        }
    }
}
