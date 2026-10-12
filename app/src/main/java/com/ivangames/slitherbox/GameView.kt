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
    private val worldSize = 2000f          // размер мира (квадрат)
    private val foodRadius = 8f            // радиус еды
    private val segmentRadius = 12f        // радиус одного сегмента змейки
    private val segmentSpacing = 12f       // расстояние между сегментами (для плавности)
    private val startSegments = 5          // стартовая длина змейки

    // ===== ЗМЕЙКА =====
    private val segments = mutableListOf<PointF>()   // точки тела, [0] — голова
    private var headAngle = 0f              // угол поворота головы (радианы)
    private val baseSpeed = 3.5f            // базовая скорость (пикселей/кадр)

    // ===== КАМЕРА =====
    private var camX = 0f
    private var camY = 0f

    // ===== ЕДА =====
    private data class Food(val x: Float, val y: Float)
    private val foods = mutableListOf<Food>()

    // ===== УПРАВЛЕНИЕ (джойстик) =====
    private var joyCenterX = 0f
    private var joyCenterY = 0f
    private var joyRadius = 0f
    private var joyKnobX = 0f
    private var joyKnobY = 0f
    private var joyActive = false
    private var joyVectorX = 0f
    private var joyVectorY = 0f

    // ===== КРАСКИ =====
    private val paintBg = Paint().apply { color = Color.rgb(20, 30, 50) }
    private val paintGrid = Paint().apply {
        color = Color.rgb(30, 45, 70)
        strokeWidth = 1f
    }
    private val paintFood = Paint().apply {
        color = Color.rgb(80, 220, 120)
        isAntiAlias = true
    }
    private val paintSnakeHead = Paint().apply {
        color = Color.rgb(120, 255, 160)
        isAntiAlias = true
    }
    private val paintSnakeBody = Paint().apply {
        color = Color.rgb(60, 200, 100)
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

    // ===== ВРЕМЯ =====
    private var lastFrameTime = 0L

    init {
        // Стартовая позиция змейки — в центре мира
        val startX = worldSize / 2f
        val startY = worldSize / 2f
        for (i in 0 until startSegments) {
            segments.add(PointF(startX - i * segmentSpacing, startY))
        }
        headAngle = 0f   // смотрим вправо

        // Спавним еду
        repeat(150) {
            foods.add(Food(
                (50..(worldSize.toInt() - 50)).random().toFloat(),
                (50..(worldSize.toInt() - 50)).random().toFloat()
            ))
        }

        lastFrameTime = SystemClock.uptimeMillis()
    }

    // ===== РАЗМЕР ЭКРАНА =====
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        joyRadius = h * 0.18f
        joyCenterX = joyRadius + h * 0.05f
        joyCenterY = h - joyRadius - h * 0.05f
        joyKnobX = joyCenterX
        joyKnobY = joyCenterY
    }

    // ===== ИГРОВОЙ ЦИКЛ =====
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Ограничиваем FPS ~60
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameTime).coerceAtMost(33L)
        lastFrameTime = now

        update(dt)
        drawWorld(canvas)

        // Перерисовка
        invalidate()
    }

    private fun update(dt: Long) {
        val speed = baseSpeed * (dt / 16.67f)  // нормализация по dt

        // Поворот головы по джойстику (плавно)
        if (joyActive) {
            val targetAngle = atan2(joyVectorY, joyVectorX)
            var diff = targetAngle - headAngle
            while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

            // Плавный поворот (максимум 0.15 радиан за кадр)
            val turnSpeed = 0.15f * (dt / 16.67f)
            headAngle += diff.coerceIn(-turnSpeed, turnSpeed)
        }

        // Двигаем голову вперёд
        val head = segments[0]
        val newHeadX = head.x + cos(headAngle) * speed
        val newHeadY = head.y + sin(headAngle) * speed

        // Границы мира — отталкивание
        val clampedX = newHeadX.coerceIn(segmentRadius, worldSize - segmentRadius)
        val clampedY = newHeadY.coerceIn(segmentRadius, worldSize - segmentRadius)

        // Вставляем новую голову
        segments.add(0, PointF(clampedX, clampedY))

        // Убираем хвост (с сохранением нужной длины)
        while (segments.size > startSegments) {
            segments.removeAt(segments.size - 1)
        }

        // Проверяем съедание еды
        checkFoodCollision()

        // Камера следит за головой
        camX = clampedX - width / 2f
        camY = clampedY - height / 2f
    }

    private fun checkFoodCollision() {
        val head = segments[0]
        val iter = foods.iterator()
        while (iter.hasNext()) {
            val food = iter.next()
            val dx = food.x - head.x
            val dy = food.y - head.y
            if (sqrt(dx * dx + dy * dy) < segmentRadius + foodRadius) {
                iter.remove()
                // TODO: Здесь будет рост и счёт (Шаг 5)
            }
        }
    }

    private fun drawWorld(canvas: Canvas) {
        // Фон
        canvas.drawColor(Color.rgb(20, 30, 50))

        // Сетка (для ощущения движения мира)
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

        // Границы мира (красная рамка)
        val left = -camX
        val top = -camY
        val right = worldSize - camX
        val bottom = worldSize - camY
        val paintBorder = Paint().apply {
            color = Color.rgb(255, 80, 80)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(left, top, right, bottom, paintBorder)

        // Еда
        for (food in foods) {
            val sx = food.x - camX
            val sy = food.y - camY
            if (sx > -20 && sx < width + 20 && sy > -20 && sy < height + 20) {
                canvas.drawCircle(sx, sy, foodRadius, paintFood)
            }
        }

        // Змейка (рисуем с хвоста к голове)
        for (i in segments.size - 1 downTo 1) {
            val seg = segments[i]
            val sx = seg.x - camX
            val sy = seg.y - camY

            // Градиент: чем ближе к голове, тем ярче
            val t = 1f - (i.toFloat() / segments.size)
            val r = (60 + 60 * t).toInt()
            val g = (180 + 60 * t).toInt()
            val b = (100 + 40 * t).toInt()
            paintSnakeBody.color = Color.rgb(r, g, b)

            canvas.drawCircle(sx, sy, segmentRadius, paintSnakeBody)
        }

        // Голова
        val head = segments[0]
        val hx = head.x - camX
        val hy = head.y - camY
        canvas.drawCircle(hx, hy, segmentRadius * 1.15f, paintSnakeHead)

        // Глаза (смотрят по направлению движения)
        val eyeDist = segmentRadius * 0.55f
        val eyeOffsetAngle = 0.6f  // угол между глазами
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

        // Зрачки (смещены вперёд)
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
    }

    // ===== УПРАВЛЕНИЕ =====
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Джойстик активируется, если тап слева от середины экрана
                if (x < width / 2f) {
                    joyActive = true
                    updateJoy(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (joyActive) {
                    updateJoy(x, y)
                }
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

        // Ограничиваем ручку радиусом
        val clampedDist = dist.coerceAtMost(joyRadius * 0.7f)
        val nx = dx / dist
        val ny = dy / dist
        joyKnobX = joyCenterX + nx * clampedDist
        joyKnobY = joyCenterY + ny * clampedDist

        // Вектор направления
        joyVectorX = nx
        joyVectorY = ny
    }
}
