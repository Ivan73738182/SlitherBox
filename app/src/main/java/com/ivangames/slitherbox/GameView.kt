package com.ivangames.slitherbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GameView(context: Context) : View(context) {

    // ===== ИГРОВОЙ МИР =====
    private val worldSize = 2000f
    private val foodRadius = 8f
    private val segmentRadius = 12f
    private val segmentSpacing = 14f       // расстояние между сегментами
    private var snakeLength = 5            // сколько сегментов у змейки
    private var score = 0                  // счёт

    // ===== ЗМЕЙКА (через трейл) =====
    private val trail = mutableListOf<PointF>()   // история позиций головы (последняя — в конце)
    private val maxTrailLength = 5000             // ограничение, чтобы не жрало память
    private var headAngle = 0f
    private val baseSpeed = 4.5f

    // ===== КАМЕРА =====
    private var camX = 0f
    private var camY = 0f

    // ===== ЕДА =====
    private data class Food(val x: Float, val y: Float)
    private val foods = mutableListOf<Food>()

    // ===== ДЖОЙСТИК =====
    private var joyCenterX = 0f
    private var joyCenterY = 0f
    private var joyRadius = 0f
    private var joyKnobX = 0f
    private var joyKnobY = 0f
    private var joyActive = false
    private var joyVectorX = 0f
    private var joyVectorY = 0f

    // ===== КРАСКИ =====
    private val paintGrid = Paint().apply {
        color = Color.rgb(30, 45, 70)
        strokeWidth = 1f
    }
    private val paintBorder = Paint().apply {
        color = Color.rgb(255, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val paintFood = Paint().apply {
        color = Color.rgb(80, 220, 120)
        isAntiAlias = true
    }
    private val paintSnakeBody = Paint().apply { isAntiAlias = true }
    private val paintSnakeHead = Paint().apply {
        color = Color.rgb(140, 255, 170)
        isAntiAlias = true
    }
    private val paintEyeWhite = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    private val paintEyeBlack = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
    }
    private val paintJoyBase = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        isAntiAlias = true
    }
    private val paintJoyKnob = Paint().apply {
        color = Color.argb(180, 255, 255, 255)
        isAntiAlias = true
    }
    private val paintScore = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private var lastFrameTime = 0L

    init {
        val startX = worldSize / 2f
        val startY = worldSize / 2f

        // Стартовый трейл — 5 точек влево от старта
        for (i in 0 until 200) {
            trail.add(PointF(startX - i * 2f, startY))
        }

        headAngle = 0f

        // Спавним еду
        repeat(200) {
            foods.add(Food(
                (50..(worldSize.toInt() - 50)).random().toFloat(),
                (50..(worldSize.toInt() - 50)).random().toFloat()
            ))
        }

        lastFrameTime = SystemClock.uptimeMillis()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        joyRadius = h * 0.18f
        joyCenterX = joyRadius + h * 0.05f
        joyCenterY = h - joyRadius - h * 0.05f
        joyKnobX = joyCenterX
        joyKnobY = joyCenterY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameTime).coerceAtMost(33L)
        lastFrameTime = now

        update(dt)
        drawWorld(canvas)

        invalidate()
    }

    private fun update(dt: Long) {
        val speed = baseSpeed * (dt / 16.67f)

        // Поворот головы по джойстику
        if (joyActive) {
            val targetAngle = atan2(joyVectorY, joyVectorX)
            var diff = targetAngle - headAngle
            while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

            val turnSpeed = 0.12f * (dt / 16.67f)
            headAngle += diff.coerceIn(-turnSpeed, turnSpeed)
        }

        // Двигаем голову
        val head = trail.last()
        val newHeadX = head.x + cos(headAngle) * speed
        val newHeadY = head.y + sin(headAngle) * speed

        // Границы мира — отталкивание
        val clampedX = newHeadX.coerceIn(segmentRadius, worldSize - segmentRadius)
        val clampedY = newHeadY.coerceIn(segmentRadius, worldSize - segmentRadius)

        trail.add(PointF(clampedX, clampedY))

        // Ограничиваем память
        while (trail.size > maxTrailLength) {
            trail.removeAt(0)
        }

        // Съедание еды
        checkFoodCollision(clampedX, clampedY)

        // Камера за головой
        camX = clampedX - width / 2f
        camY = clampedY - height / 2f
    }

    private fun checkFoodCollision(hx: Float, hy: Float) {
        val iter = foods.iterator()
        while (iter.hasNext()) {
            val food = iter.next()
            val dx = food.x - hx
            val dy = food.y - hy
            if (sqrt(dx * dx + dy * dy) < segmentRadius + foodRadius) {
                iter.remove()
                score += 1

                // Каждые 10 очков — +1 сегмент
                if (score % 10 == 0) {
                    snakeLength += 1
                }

                // Респавн еды в новом месте
                foods.add(Food(
                    (50..(worldSize.toInt() - 50)).random().toFloat(),
                    (50..(worldSize.toInt() - 50)).random().toFloat()
                ))
            }
        }
    }

    // Возвращает позиции сегментов змейки (от головы к хвосту)
    private fun getSnakeSegments(): List<PointF> {
        val result = mutableListOf<PointF>()

        // Голова — последняя точка трейла
        result.add(trail.last())

        // Идём по трейлу с конца к началу, набирая точки на нужном расстоянии
        var needDist = segmentSpacing.toDouble()
        var i = trail.size - 2
        var accumulated = 0.0

        while (i > 0 && result.size < snakeLength) {
            val a = trail[i + 1]
            val b = trail[i]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val d = sqrt(dx * dx + dy * dy)

            if (accumulated + d >= needDist) {
                // Интерполируем точку на нужном расстоянии
                val t = (needDist - accumulated) / d
                val px = (a.x + (b.x - a.x) * t).toFloat()
                val py = (a.y + (b.y - a.y) * t).toFloat()
                result.add(PointF(px, py))

                needDist += segmentSpacing
            } else {
                accumulated += d
            }
            i--
        }

        // Если трейл короткий — добиваем оставшиеся сегменты в конце
        while (result.size < snakeLength) {
            result.add(PointF(trail[0].x, trail[0].y))
        }

        return result
    }

    private fun drawWorld(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 30, 50))

        // Сетка
        val gridSize = 100f
        val gridStartX = -(camX % gridSize)
        val gridStartY = -(camY % gridSize)
        var x = gridStartX
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paintGrid)
            x += gridSize
        }
        var y = gridStartY
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paintGrid)
            y += gridSize
        }

        // Границы мира
        canvas.drawRect(
            -camX, -camY,
            worldSize - camX, worldSize - camY,
            paintBorder
        )

        // Еда (только то, что видно)
        for (food in foods) {
            val sx = food.x - camX
            val sy = food.y - camY
            if (sx > -20 && sx < width + 20 && sy > -20 && sy < height + 20) {
                canvas.drawCircle(sx, sy, foodRadius, paintFood)
            }
        }

        // Змейка
        val segments = getSnakeSegments()

        // Рисуем с хвоста к голове
        for (i in segments.size - 1 downTo 1) {
            val seg = segments[i]
            val sx = seg.x - camX
            val sy = seg.y - camY

            val t = 1f - (i.toFloat() / segments.size)
            val r = (50 + 80 * t).toInt()
            val g = (160 + 90 * t).toInt()
            val b = (80 + 60 * t).toInt()
            paintSnakeBody.color = Color.rgb(r, g, b)

            canvas.drawCircle(sx, sy, segmentRadius, paintSnakeBody)
        }

        // Голова
        val head = segments[0]
        val hx = head.x - camX
        val hy = head.y - camY
        canvas.drawCircle(hx, hy, segmentRadius * 1.15f, paintSnakeHead)

        // Глаза
        val eyeDist = segmentRadius * 0.55f
        val perpX = cos(headAngle + Math.PI.toFloat() / 2)
        val perpY = sin(headAngle + Math.PI.toFloat() / 2)
        val fwdX = cos(headAngle)
        val fwdY = sin(headAngle)

        val eye1X = hx + fwdX * eyeDist + perpX * eyeDist * 0.5f
        val eye1Y = hy + fwdY * eyeDist + perpY * eyeDist * 0.5f
        val eye2X = hx + fwdX * eyeDist - perpX * eyeDist * 0.5f
        val eye2Y = hy + fwdY * eyeDist - perpY * eyeDist * 0.5f

        canvas.drawCircle(eye1X, eye1Y, segmentRadius * 0.35f, paintEyeWhite)
        canvas.drawCircle(eye2X, eye2Y, segmentRadius * 0.35f, paintEyeWhite)
        canvas.drawCircle(
            eye1X + fwdX * segmentRadius * 0.15f,
            eye1Y + fwdY * segmentRadius * 0.15f,
            segmentRadius * 0.18f, paintEyeBlack
        )
        canvas.drawCircle(
            eye2X + fwdX * segmentRadius * 0.15f,
            eye2Y + fwdY * segmentRadius * 0.15f,
            segmentRadius * 0.18f, paintEyeBlack
        )

        // Джойстик
        canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintJoyBase)
        canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintJoyKnob)

        // Счёт
        canvas.drawText("Score: $score", 40f, 70f, paintScore)
        canvas.drawText("Length: $snakeLength", 40f, 130f, paintScore)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (x < width / 2f) {
                    joyActive = true
                    updateJoy(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (joyActive) updateJoy(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                joyActive = false
                joyKnobX = joyCenterX
                joyKnobY = joyCenterY
                joyVectorX = 0f
                joyVectorY = 0f
            }
        }
        return true
    }

    private fun updateJoy(x: Float, y: Float) {
        val dx = x - joyCenterX
        val dy = y - joyCenterY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist < 10f) {
            joyVectorX = 0f
            joyVectorY = 0f
            joyKnobX = joyCenterX
            joyKnobY = joyCenterY
            return
        }

        val clampedDist = dist.coerceAtMost(joyRadius * 0.7f)
        val nx = dx / dist
        val ny = dy / dist
        joyKnobX = joyCenterX + nx * clampedDist
        joyKnobY = joyCenterY + ny * clampedDist

        joyVectorX = nx
        joyVectorY = ny
    }
}
