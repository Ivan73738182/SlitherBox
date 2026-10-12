package com.ivangames.slitherbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Фон меню: гексагональная сетка + ползающие декоративные змейки.
 */
class MenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ===== ДЕКОРАТИВНЫЕ ЗМЕЙКИ =====
    private data class DecoSnake(
        val trail: MutableList<PointF>,
        var headAngle: Float,
        val color: Int,
        val headColor: Int,
        val speed: Float
    )

    private val decoSnakes = mutableListOf<DecoSnake>()
    private val segmentRadius = 14f
    private val segmentSpacing = 16f
    private val trailLength = 40

    // ===== КРАСКИ =====
    private val paintBg = Paint().apply {
        color = Color.rgb(15, 22, 40)
    }
    private val paintHex = Paint().apply {
        color = Color.rgb(35, 55, 85)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private var lastFrameTime = 0L
    private var initialized = false

    init {
        lastFrameTime = SystemClock.uptimeMillis()
    }

    private fun initIfNeeded() {
        if (initialized) return
        if (width <= 0 || height <= 0) return
        initialized = true

        val colors = listOf(
            Color.rgb(80, 220, 120) to Color.rgb(140, 255, 170),
            Color.rgb(255, 180, 80) to Color.rgb(255, 220, 130),
            Color.rgb(120, 180, 255) to Color.rgb(170, 220, 255)
        )

        for ((body, head) in colors) {
            val startX = (0..width).random().toFloat()
            val startY = (0..height).random().toFloat()
            val angle = (Math.random() * 2 * Math.PI).toFloat()

            val trail = mutableListOf<PointF>()
            for (i in 0 until 400) {
                trail.add(PointF(
                    startX - cos(angle) * i * 2f,
                    startY - sin(angle) * i * 2f
                ))
            }

            decoSnakes.add(DecoSnake(
                trail = trail,
                headAngle = angle,
                color = body,
                headColor = head,
                speed = 2f + Math.random().toFloat() * 2f
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        initIfNeeded()

        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameTime).coerceAtMost(33L)
        lastFrameTime = now

        update(dt)
        drawBg(canvas)
        drawDecoSnakes(canvas)

        invalidate()
    }

    private fun update(dt: Long) {
        val speedFactor = dt / 16.67f

        for (snake in decoSnakes) {
            snake.headAngle += (Math.random().toFloat() - 0.5f) * 0.05f * speedFactor

            val head = snake.trail.last()
            if (head.x < 50f) snake.headAngle = 0f
            if (head.x > width - 50f) snake.headAngle = Math.PI.toFloat()
            if (head.y < 50f) snake.headAngle = (Math.PI / 2).toFloat()
            if (head.y > height - 50f) snake.headAngle = -(Math.PI / 2).toFloat()

            val newX = head.x + cos(snake.headAngle) * snake.speed * speedFactor
            val newY = head.y + sin(snake.headAngle) * snake.speed * speedFactor

            snake.trail.add(PointF(newX, newY))

            while (snake.trail.size > 500) {
                snake.trail.removeAt(0)
            }
        }
    }

    private fun drawBg(canvas: Canvas) {
        canvas.drawColor(paintBg.color)

        // Гексагональная сетка
        val hexSize = 50f
        val hexWidth = hexSize * 1.732f
        val hexHeight = hexSize * 2f
        val vertSpacing = hexHeight * 0.75f

        var row = 0
        var y = -hexHeight
        while (y < height + hexHeight) {
            var x = -hexWidth
            if (row % 2 == 1) x -= hexWidth / 2f
            while (x < width + hexWidth) {
                drawHexagon(canvas, x + hexWidth / 2f, y + hexHeight / 2f, hexSize)
                x += hexWidth
            }
            y += vertSpacing
            row++
        }
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = (Math.PI / 3 * i).toFloat() - (Math.PI / 6).toFloat()
            val px = cx + cos(angle) * size
            val py = cy + sin(angle) * size
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paintHex)
    }

    private fun drawDecoSnakes(canvas: Canvas) {
        val bodyPaint = Paint().apply { isAntiAlias = true }
        val headPaint = Paint().apply { isAntiAlias = true }

        for (snake in decoSnakes) {
            val segments = mutableListOf<PointF>()
            if (snake.trail.isEmpty()) continue
            segments.add(snake.trail.last())

            var needDist = segmentSpacing.toDouble()
            var i = snake.trail.size - 2
            var accumulated = 0.0

            while (i > 0 && segments.size < trailLength) {
                val a = snake.trail[i + 1]
                val b = snake.trail[i]
                val dx = (b.x - a.x).toDouble()
                val dy = (b.y - a.y).toDouble()
                val d = sqrt(dx * dx + dy * dy)

                if (accumulated + d >= needDist) {
                    val t = (needDist - accumulated) / d
                    val px = (a.x + (b.x - a.x) * t).toFloat()
                    val py = (a.y + (b.y - a.y) * t).toFloat()
                    segments.add(PointF(px, py))
                    needDist += segmentSpacing
                } else {
                    accumulated += d
                }
                i--
            }

            // Тело
            bodyPaint.color = snake.color
            for (j in segments.size - 1 downTo 1) {
                val seg = segments[j]
                canvas.drawCircle(seg.x, seg.y, segmentRadius, bodyPaint)
            }

            // Голова
            if (segments.isNotEmpty()) {
                headPaint.color = snake.headColor
                canvas.drawCircle(segments[0].x, segments[0].y, segmentRadius * 1.15f, headPaint)
            }
        }
    }
}
