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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Текущий скин
    var skinColor: Int = 0xFF80DC78.toInt()
        set(value) {
            field = value
            invalidate()
        }

    // ===== ДЕКОРАТИВНЫЕ ЗМЕЙКИ (фон) =====
    private data class DecoSnake(
        val trail: MutableList<PointF>,
        var headAngle: Float,
        val color: Int,
        val headColor: Int,
        val speed: Float
    )

    // ===== ИГРОК-ЗМЕЙКА (большая, ползает) =====
    private data class PlayerSnake(
        val trail: MutableList<PointF>,
        var headAngle: Float,
        var targetAngle: Float,
        var retargetTime: Float
    )

    private val decoSnakes = mutableListOf<DecoSnake>()
    private var playerSnake: PlayerSnake? = null

    private val segmentRadius = 14f
    private val segmentSpacing = 16f
    private val trailLength = 40

    // Игрок — параметры
    private val playerRadius = 18f
    private val playerSpacing = 22f
    private val playerLength = 25
    private val playerSpeed = 3.5f

    // Краски
    private val paintBg = Paint().apply { color = Color.rgb(15, 22, 40) }
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

        // Декоративные змейки
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
            decoSnakes.add(DecoSnake(trail, angle, body, head, 2f + Math.random().toFloat() * 2f))
        }

        // Игрок — стартовая змейка
        val px = width / 2f
        val py = height / 2f
        val pAngle = 0f

        val pTrail = mutableListOf<PointF>()
        for (i in 0 until 400) {
            pTrail.add(PointF(
                px - cos(pAngle) * i * 2f,
                py - sin(pAngle) * i * 2f
            ))
        }
        playerSnake = PlayerSnake(pTrail, pAngle, pAngle, 0f)
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
        drawPlayerSnake(canvas)

        invalidate()
    }

    private fun update(dt: Long) {
        val speedFactor = dt / 16.67f

        // Декоративные
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
            while (snake.trail.size > 500) snake.trail.removeAt(0)
        }

        // Игрок — ползает, извивается
        playerSnake?.let { ps ->
            ps.retargetTime -= dt
            if (ps.retargetTime <= 0f) {
                // Новый случайный угол
                ps.targetAngle = (Math.random() * 2 * Math.PI).toFloat()
                ps.retargetTime = 2000f + Math.random().toFloat() * 2000f
            }

            val head = ps.trail.last()

            // Убегаем от краёв
            val margin = 100f
            var forced = false
            if (head.x < margin) { ps.targetAngle = 0f; forced = true }
            if (head.x > width - margin) { ps.targetAngle = Math.PI.toFloat(); forced = true }
            if (head.y < margin) { ps.targetAngle = (Math.PI / 2).toFloat(); forced = true }
            if (head.y > height - margin) { ps.targetAngle = -(Math.PI / 2).toFloat(); forced = true }

            // Плавный поворот
            var diff = ps.targetAngle - ps.headAngle
            while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

            val turnSpeed = (if (forced) 0.15f else 0.06f) * speedFactor
            ps.headAngle += diff.coerceIn(-turnSpeed, turnSpeed)

            // Движение
            val newX = head.x + cos(ps.headAngle) * playerSpeed * speedFactor
            val newY = head.y + sin(ps.headAngle) * playerSpeed * speedFactor

            ps.trail.add(PointF(newX, newY))
            while (ps.trail.size > 800) ps.trail.removeAt(0)
        }
    }

    private fun drawBg(canvas: Canvas) {
        canvas.drawColor(paintBg.color)

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
            val segments = getSegments(snake.trail, trailLength, segmentSpacing)
            bodyPaint.color = snake.color
            for (j in segments.size - 1 downTo 1) {
                val seg = segments[j]
                canvas.drawCircle(seg.x, seg.y, segmentRadius, bodyPaint)
            }
            if (segments.isNotEmpty()) {
                headPaint.color = snake.headColor
                canvas.drawCircle(segments[0].x, segments[0].y, segmentRadius * 1.15f, headPaint)
            }
        }
    }

    private fun drawPlayerSnake(canvas: Canvas) {
        val ps = playerSnake ?: return
        val segments = getSegments(ps.trail, playerLength, playerSpacing)
        if (segments.isEmpty()) return

        val baseR = (skinColor shr 16) and 0xFF
        val baseG = (skinColor shr 8) and 0xFF
        val baseB = skinColor and 0xFF

        val bodyPaint = Paint().apply { isAntiAlias = true }
        val headPaint = Paint().apply { isAntiAlias = true }
        val eyeWhite = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val eyeBlack = Paint().apply { color = Color.BLACK; isAntiAlias = true }

        // Тело
        for (i in segments.size - 1 downTo 1) {
            val seg = segments[i]
            val t = 1f - (i.toFloat() / segments.size)
            val factor = 0.55f + 0.45f * t
            val r = (baseR * factor).toInt().coerceIn(0, 255)
            val g = (baseG * factor).toInt().coerceIn(0, 255)
            val b = (baseB * factor).toInt().coerceIn(0, 255)
            bodyPaint.color = Color.rgb(r, g, b)
            canvas.drawCircle(seg.x, seg.y, playerRadius, bodyPaint)
        }

        // Голова
        val head = segments[0]
        val hr = (baseR + (255 - baseR) * 0.3f).toInt().coerceIn(0, 255)
        val hg = (baseG + (255 - baseG) * 0.3f).toInt().coerceIn(0, 255)
        val hb = (baseB + (255 - baseB) * 0.3f).toInt().coerceIn(0, 255)
        headPaint.color = Color.rgb(hr, hg, hb)
        canvas.drawCircle(head.x, head.y, playerRadius * 1.15f, headPaint)

        // Глаза
        val eyeDist = playerRadius * 0.55f
        val perpX = cos(ps.headAngle + Math.PI.toFloat() / 2)
        val perpY = sin(ps.headAngle + Math.PI.toFloat() / 2)
        val fwdX = cos(ps.headAngle)
        val fwdY = sin(ps.headAngle)

        val eye1X = head.x + fwdX * eyeDist + perpX * eyeDist * 0.5f
        val eye1Y = head.y + fwdY * eyeDist + perpY * eyeDist * 0.5f
        val eye2X = head.x + fwdX * eyeDist - perpX * eyeDist * 0.5f
        val eye2Y = head.y + fwdY * eyeDist - perpY * eyeDist * 0.5f

        canvas.drawCircle(eye1X, eye1Y, playerRadius * 0.35f, eyeWhite)
        canvas.drawCircle(eye2X, eye2Y, playerRadius * 0.35f, eyeWhite)
        canvas.drawCircle(eye1X + fwdX * 3f, eye1Y + fwdY * 3f, playerRadius * 0.18f, eyeBlack)
        canvas.drawCircle(eye2X + fwdX * 3f, eye2Y + fwdY * 3f, playerRadius * 0.18f, eyeBlack)
    }

    private fun getSegments(trail: List<PointF>, count: Int, spacing: Float): List<PointF> {
        val result = mutableListOf<PointF>()
        if (trail.isEmpty()) return result
        result.add(trail.last())

        var needDist = spacing.toDouble()
        var i = trail.size - 2
        var accumulated = 0.0

        while (i > 0 && result.size < count) {
            val a = trail[i + 1]
            val b = trail[i]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val d = sqrt(dx * dx + dy * dy)

            if (accumulated + d >= needDist) {
                val t = (needDist - accumulated) / d
                val px = (a.x + (b.x - a.x) * t).toFloat()
                val py = (a.y + (b.y - a.y) * t).toFloat()
                result.add(PointF(px, py))
                needDist += spacing
            } else {
                accumulated += d
            }
            i--
        }

        while (result.size < count) {
            result.add(PointF(trail[0].x, trail[0].y))
        }
        return result
    }
}
