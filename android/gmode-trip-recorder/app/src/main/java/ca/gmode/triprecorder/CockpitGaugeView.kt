package ca.gmode.triprecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import ca.gmode.triprecorder.settings.DashboardPalette
import kotlin.math.min

class CockpitGaugeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var palette = ca.gmode.triprecorder.settings.AppearanceSettings.PRESETS.first()
    private var title = "SPEED"
    private var value = "--"
    private var unit = "km/h"
    private var subtitle = "WAITING"
    private var progress: Float? = null

    init {
        minimumHeight = dp(154)
        contentDescription = "Speed gauge waiting for telemetry"
    }

    fun setPalette(value: DashboardPalette) {
        palette = value
        invalidate()
    }

    fun setReading(title: String, value: String, unit: String, progress: Double?, subtitle: String = "") {
        this.title = title.uppercase()
        this.value = value
        this.unit = unit
        this.subtitle = subtitle.uppercase()
        this.progress = progress?.coerceIn(0.0, 1.0)?.toFloat()
        contentDescription = listOf(this.title, value, unit, subtitle).filter { it.isNotBlank() }.joinToString(" ")
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize(dp(154), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height * 0.55f
        val radius = min(width * 0.38f, height * 0.36f)
        val oval = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(5).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = palette.outline
        canvas.drawArc(oval, 145f, 250f, false, paint)
        progress?.let {
            paint.color = palette.accent
            canvas.drawArc(oval, 145f, 250f * it, false, paint)
        }

        paint.strokeWidth = dp(1).toFloat()
        paint.color = Color.argb(150, Color.red(palette.muted), Color.green(palette.muted), Color.blue(palette.muted))
        for (tick in 0..10) {
            val angle = Math.toRadians((145.0 + tick * 25.0))
            val inner = radius + dp(3)
            val outer = radius + dp(if (tick % 5 == 0) 11 else 7)
            canvas.drawLine(
                centerX + kotlin.math.cos(angle).toFloat() * inner,
                centerY + kotlin.math.sin(angle).toFloat() * inner,
                centerX + kotlin.math.cos(angle).toFloat() * outer,
                centerY + kotlin.math.sin(angle).toFloat() * outer,
                paint,
            )
        }

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = palette.muted
        paint.textSize = dp(10).toFloat()
        canvas.drawText(title, centerX, dp(19).toFloat(), paint)

        paint.color = Color.WHITE
        paint.textSize = dp(if (value.length > 9) 18 else 28).toFloat()
        canvas.drawText(value, centerX, centerY + dp(4), paint)
        paint.color = palette.muted
        paint.textSize = dp(9).toFloat()
        canvas.drawText(unit, centerX, centerY + dp(19), paint)
        paint.color = palette.accent
        paint.textSize = dp(8).toFloat()
        canvas.drawText(subtitle, centerX, height - dp(10).toFloat(), paint)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
