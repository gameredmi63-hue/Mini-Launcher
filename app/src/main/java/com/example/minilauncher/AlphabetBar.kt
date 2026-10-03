package com.example.minilauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.exp

/** Vertical A–Z strip. Letters near the finger bulge out in a smooth "wave". */
class AlphabetBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onLetter(letter: String)
        fun onTouch(active: Boolean)
    }

    var letters: List<String> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private var touchY = 0f
    private var wave = 0f
    private var waveAnim: ValueAnimator? = null
    private var lastIndex = -1

    override fun onDraw(canvas: Canvas) {
        val n = letters.size
        if (n == 0) return
        val usable = (height - paddingTop - paddingBottom).toFloat()
        val step = usable / n
        val base = minOf(13f * density, step * 0.72f)
        val spread = step * 3.2f
        val cx = width - 20f * density

        for (i in 0 until n) {
            val cy = paddingTop + step * (i + 0.5f)
            val d = (cy - touchY) / spread
            val influence = if (wave > 0f) exp(-d * d) * wave else 0f
            paint.textSize = base * (1f + influence * 1.1f)
            paint.alpha = (255 * (0.5f + 0.5f * influence)).toInt()
            val x = cx - influence * 30f * density
            val y = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(letters[i], x, y, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touchY = e.y
                animateWave(1f)
                listener?.onTouch(true)
                select(e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                touchY = e.y
                select(e.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateWave(0f)
                listener?.onTouch(false)
                lastIndex = -1
            }
        }
        return true
    }

    private fun select(y: Float) {
        val n = letters.size
        if (n == 0) return
        val usable = (height - paddingTop - paddingBottom).toFloat()
        val idx = (((y - paddingTop) / usable) * n).toInt().coerceIn(0, n - 1)
        if (idx != lastIndex) {
            lastIndex = idx
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            listener?.onLetter(letters[idx])
        }
    }

    private fun animateWave(target: Float) {
        waveAnim?.cancel()
        waveAnim = ValueAnimator.ofFloat(wave, target).apply {
            duration = if (target > 0f) 180L else 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                wave = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
