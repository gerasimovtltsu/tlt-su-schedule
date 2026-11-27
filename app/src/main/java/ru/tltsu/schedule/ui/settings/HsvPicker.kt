package ru.tltsu.schedule.ui.settings

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

internal class HsvPicker(context: Context) : View(context) {
    private val hsv = floatArrayOf(0f, 0f, 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradient: Shader? = null
    var onColor: ((Int) -> Unit)? = null
    val hue: Float get() = hsv[0]
    init { contentDescription = "Насыщенность и яркость цвета"; isClickable = true }
    fun color(): Int = Color.HSVToColor(hsv)
    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        rebuild()
    }
    fun setHue(value: Float) {
        hsv[0] = value.coerceIn(0f, 359f)
        rebuild()
    }
    private fun rebuild() {
        if (width > 0 && height > 0) {
            val full = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
            val horizontal = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, full, Shader.TileMode.CLAMP)
            val vertical = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            gradient = ComposeShader(horizontal, vertical, PorterDuff.Mode.SRC_OVER)
        }
        invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w,h,oldw,oldh); rebuild() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val x = hsv[1] * width
        val y = (1f - hsv[2]) * height
        val r = 8f * resources.displayMetrics.density
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * resources.displayMetrics.density
        paint.color = Color.BLACK
        canvas.drawCircle(x.coerceIn(r, maxOf(r, width-r)), y.coerceIn(r, maxOf(r, height-r)), r, paint)
        paint.strokeWidth = 1.5f * resources.displayMetrics.density
        paint.color = Color.WHITE
        canvas.drawCircle(x.coerceIn(r, maxOf(r, width-r)), y.coerceIn(r, maxOf(r, height-r)), r, paint)
        paint.style = Paint.Style.FILL
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (width <= 0 || height <= 0) return false
                parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP)
                hsv[1] = (event.x / width).coerceIn(0f, 1f)
                hsv[2] = (1f - event.y / height).coerceIn(0f, 1f)
                invalidate()
                onColor?.invoke(color())
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
