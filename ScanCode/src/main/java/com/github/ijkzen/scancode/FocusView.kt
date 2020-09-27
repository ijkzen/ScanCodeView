package com.github.ijkzen.scancode

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.github.ijkzen.scancode.util.convertDp2Px

class FocusView : View {

    private val INSIDE_MAX_RADIUS = convertDp2Px(20, context)

    private val OUTSIDE_MAX_RADIUS = convertDp2Px(25, context)
    private val OUTSIDE_MIN_RADIUS = INSIDE_MAX_RADIUS

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    private var focusAnimationValue: Int = 0
    private var valueAnimator = ValueAnimator.ofInt(1, 100).apply {
        duration = 1000
        addUpdateListener {
            focusAnimationValue = it.animatedValue as Int
            postInvalidate()
        }
    }

    private val insidePaint = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(context, R.color.internal_color)
        style = Paint.Style.FILL
    }

    private val outSidePaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2F
    }

    private var center: Float = 0F

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val dimension = convertDp2Px(60, context)
        setMeasuredDimension(dimension, dimension)
        center = dimension / 2F
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        when (focusAnimationValue) {
            in 1..85 -> {
                val outsideRadius =
                    OUTSIDE_MAX_RADIUS + (OUTSIDE_MIN_RADIUS - OUTSIDE_MAX_RADIUS) * (focusAnimationValue - 10) / 75F
                canvas?.drawCircle(center, center, outsideRadius, outSidePaint)

                if (focusAnimationValue > 10) {
                    val radius = INSIDE_MAX_RADIUS * focusAnimationValue / 85F
                    canvas?.drawCircle(center, center, radius, insidePaint)
                }
            }
            else -> {
                if (focusAnimationValue == 0) {
                    return
                }
                canvas?.drawCircle(center, center, OUTSIDE_MIN_RADIUS.toFloat(), outSidePaint)

                val alpha = 125 - (focusAnimationValue - 85) / 15F * 125
                insidePaint.color = Color.argb(alpha.toInt(), 0xB9, 0xBC, 0xE5)
                canvas?.drawCircle(center, center, INSIDE_MAX_RADIUS.toFloat(), insidePaint)
                if (focusAnimationValue == 100) {
                    insidePaint.color = Color.argb(125, 0xB9, 0xBC, 0xE5)
                }
            }
        }
    }

    fun startAnimation() {
        valueAnimator.start()
    }
}