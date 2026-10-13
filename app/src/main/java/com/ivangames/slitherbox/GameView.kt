package com.ivangames.slitherbox

import android.content.Context
import android.content.SharedPreferences
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

class GameView(context: Context, val mode: String = "OFFLINE") : View(context) {

    // ===== ИГРОВОЙ МИР =====
    private val worldSize = 2000f
    private val foodRadius = 8f
    private val segmentRadius = 12f
    private val segmentSpacing = 14f
    private var snakeLength = 5
    private var score = 0

    // ===== ЗМЕЙКА (трейл) =====
    private val trail = mutableListOf<PointF>()
    private val maxTrailLength = 5000
    private var headAngle = 0f
    private val baseSpeed = 4.5f

    // ===== СОСТОЯНИЕ ИГРЫ =====
    private var isGameOver = false

    // ===== РЕКОРД + СКИН =====
    private lateinit var prefs: SharedPreferences
    private var bestScore = 0
    private var skinColor: Int = 0xFF80DC78.toInt()

    // ===== КАМЕРА =====
    private var camX = 0f
    private var camY = 0f

    // ===== ЕДА =====
    private data class Food(val x: Float, val y: Float, val color: Int)
    private val foods = mutableListOf<Food>()

    // ===== ПАЛИТРА ЕДЫ =====
    private val foodColors = intArrayOf(
        Color.rgb(80, 220, 120),
        Color.rgb(255, 100, 100),
        Color.rgb(255, 220, 100),
        Color.rgb(100, 180, 255),
        Color.rgb(200, 120, 255),
        Color.rgb(255, 160, 80),
        Color.rgb(255, 120, 200),
        Color.rgb(120, 255, 240)
    )

    // ===== БОТЫ =====
    private val bots = mutableListOf<BotSnake>()

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
    private val paintFood = Paint().apply { isAntiAlias = true }
    private val paintSnakeBody = Paint().apply { isAntiAlias = true }
    private val paintSnakeHead = Paint().apply { isAntiAlias = true }
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
    private val paintBotBody = Paint().apply { isAntiAlias = true }
    private val paintBotHead = Paint().apply { isAntiAlias = true }

    // Game Over
    private val paintGameOverTitle = Paint().apply {
        color = Color.WHITE
        textSize = 110f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val paintGameOverText = Paint().apply {
        color = Color.WHITE
        textSize = 55f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val paintGameOverHint = Paint().apply {
        color = Color.rgb(200, 200, 200)
        textSize = 45f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val paintOverlayBg = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
    }

    private var lastFrameTime = 0L

    init {
        prefs = context.getSharedPreferences("slitherbox", Context.MODE_PRIVATE)
        bestScore = prefs.getInt("best_score", 0)
        skinColor = prefs.getInt("skin_color", 0xFF80DC78.toInt())

        startNewGame()

        lastFrameTime = SystemClock.uptimeMillis()
    }

    private fun startNewGame() {
        trail.clear()
        foods.clear()
        bots.clear()

        val startX = worldSize / 2f
        val startY = worldSize / 2f

        for (i in 0 until 200) {
            trail.add(PointF(startX - i * 2f, startY))
        }

        headAngle = 0f
        snakeLength = 5
        score = 0
        isGameOver = false

        // Обновляем скин (мог поменяться в меню)
        skinColor = prefs.getInt("skin_color", 0xFF80DC78.toInt())

        repeat(200) {
            foods.add(spawnFood())
        }

        val botColors = listOf(
            Color.rgb(255, 100, 100) to Color.rgb(255, 150, 150),
            Color.rgb(255, 220, 100) to Color.rgb(255, 240, 160),
            Color.rgb(180, 120, 255) to Color.rgb(210, 170, 255),
            Color.rgb(255, 160, 80)  to Color.rgb(255, 200, 130),
            Color.rgb(100, 200, 255) to Color.rgb(160, 230, 255)
        )

        val margin = 300f
        for (colors in botColors) {
            val bx = margin + Math.random().toFloat() * (worldSize - 2 * margin)
            val by = margin + Math.random().toFloat() * (worldSize - 2 * margin)
            val ba = (Math.random() * 2 * Math.PI).toFloat()
            bots.add(BotSnake(bx, by, ba, colors.first, colors.second, worldSize))
        }

        joyKnobX = joyCenterX
        joyKnobY = joyCenterY
        joyVectorX = 0f
        joyVectorY = 0f
        joyActive = false
    }

    private fun randomFoodColor(): Int = foodColors.random()

    private fun spawnFood(): Food {
        return Food(
            (50..(worldSize.toInt() - 50)).random().toFloat(),
            (50..(worldSize.toInt() - 50)).random().toFloat(),
            randomFoodColor()
        )
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

        if (!isGameOver) {
            update(dt)
        }

        drawWorld(canvas)

        if (isGameOver) {
            drawGameOver(canvas)
        }

        invalidate()
    }

    private fun update(dt: Long) {
        val speed = baseSpeed * (dt / 16.67f)

        if (joyActive) {
            val targetAngle = atan2(joyVectorY, joyVectorX)
            var diff = targetAngle - headAngle
            while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
            while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

            val turnSpeed = 0.12f * (dt / 16.67f)
            headAngle += diff.coerceIn(-turnSpeed, turnSpeed)
        }

        val head = trail.last()
        val newHeadX = head.x + cos(headAngle) * speed
        val newHeadY = head.y + sin(headAngle) * speed

        // Стены
        if (newHeadX < segmentRadius || newHeadX > worldSize - segmentRadius ||
            newHeadY < segmentRadius || newHeadY > worldSize - segmentRadius
        ) {
            gameOver()
            return
        }

        trail.add(PointF(newHeadX, newHeadY))

        while (trail.size > maxTrailLength) {
            trail.removeAt(0)
        }

        // Столкновения с ботами
        for (bot in bots) {
            if (!bot.alive) continue
            val botSegments = bot.getSegments()
            for (i in 1 until botSegments.size) {
                val seg = botSegments[i]
                val dx = newHeadX - seg.x
                val dy = newHeadY - seg.y
                if (dx * dx + dy * dy < (segmentRadius * 1.4f) * (segmentRadius * 1.4f)) {
                    gameOver()
                    return
                }
            }
            if (botSegments.isNotEmpty()) {
                val botHead = botSegments[0]
                val dx = newHeadX - botHead.x
                val dy = newHeadY - botHead.y
                if (dx * dx + dy * dy < (segmentRadius * 1.4f) * (segmentRadius * 1.4f)) {
                    gameOver()
                    return
                }
            }
        }

        checkFoodCollision(newHeadX, newHeadY)

        // Боты
        val foodPositions = foods.map { PointF(it.x, it.y) }
        for (bot in bots) {
            if (!bot.alive) continue
            val enemiesForThisBot = mutableListOf<PointF>()
            enemiesForThisBot.addAll(getSnakeSegments())
            for (otherBot in bots) {
                if (otherBot !== bot && otherBot.alive) {
                    enemiesForThisBot.addAll(otherBot.getSegments())
                }
            }
            bot.update(dt, foodPositions, enemiesForThisBot)
        }

        val eatenFoods = mutableListOf<Food>()
        for (bot in bots) {
            if (!bot.alive) continue
            for (food in foods) {
                if (bot.tryEat(food.x, food.y)) {
                    eatenFoods.add(food)
                    foods.add(spawnFood())
                    break
                }
            }
        }
        foods.removeAll(eatenFoods)

        bots.removeAll { !it.alive }

        camX = newHeadX - width / 2f
        camY = newHeadY - height / 2f
    }

    private fun gameOver() {
        isGameOver = true

        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", bestScore).apply()
        }
    }

    private fun checkFoodCollision(hx: Float, hy: Float) {
        val newFoods = mutableListOf<Food>()
        val iter = foods.iterator()
        while (iter.hasNext()) {
            val food = iter.next()
            val dx = food.x - hx
            val dy = food.y - hy
            if (sqrt(dx * dx + dy * dy) < segmentRadius + foodRadius) {
                iter.remove()
                score += 1
                if (score % 10 == 0) {
                    snakeLength += 1
                }
                newFoods.add(spawnFood())
            }
        }
        foods.addAll(newFoods)
    }

    private fun getSnakeSegments(): List<PointF> {
        val result = mutableListOf<PointF>()
        if (trail.isEmpty()) return result

        result.add(trail.last())

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

        // Границы
        canvas.drawRect(
            -camX, -camY,
            worldSize - camX, worldSize - camY,
            paintBorder
        )

        // Еда
        for (food in foods) {
            val sx = food.x - camX
            val sy = food.y - camY
            if (sx > -20 && sx < width + 20 && sy > -20 && sy < height + 20) {
                paintFood.color = food.color
                canvas.drawCircle(sx, sy, foodRadius, paintFood)
            }
        }

        // Боты
        for (bot in bots) {
            if (bot.alive) {
                bot.draw(canvas, camX, camY, paintBotBody, paintBotHead)
            }
        }

        // Игрок
        if (!isGameOver) {
            val segments = getSnakeSegments()

            // Базовые компоненты скина
            val baseR = (skinColor shr 16) and 0xFF
            val baseG = (skinColor shr 8) and 0xFF
            val baseB = skinColor and 0xFF

            for (i in segments.size - 1 downTo 1) {
                val seg = segments[i]
                val sx = seg.x - camX
                val sy = seg.y - camY

                // Затемняем от головы к хвосту
                val t = 1f - (i.toFloat() / segments.size)
                val factor = 0.55f + 0.45f * t
                val r = (baseR * factor).toInt().coerceIn(0, 255)
                val g = (baseG * factor).toInt().coerceIn(0, 255)
                val b = (baseB * factor).toInt().coerceIn(0, 255)
                paintSnakeBody.color = Color.rgb(r, g, b)

                canvas.drawCircle(sx, sy, segmentRadius, paintSnakeBody)
            }

            val head = segments[0]
            val hx = head.x - camX
            val hy = head.y - camY

            // Голова — ярче скина
            val headR = (baseR + (255 - baseR) * 0.3f).toInt().coerceIn(0, 255)
            val headG = (baseG + (255 - baseG) * 0.3f).toInt().coerceIn(0, 255)
            val headB = (baseB + (255 - baseB) * 0.3f).toInt().coerceIn(0, 255)
            paintSnakeHead.color = Color.rgb(headR, headG, headB)

            canvas.drawCircle(hx, hy, segmentRadius * 1.15f, paintSnakeHead)

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
        }

        // UI
        if (!isGameOver) {
            canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintJoyBase)
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintJoyKnob)

            canvas.drawText("Score: $score", 40f, 70f, paintScore)
            canvas.drawText("Length: $snakeLength", 40f, 130f, paintScore)
            canvas.drawText("Best: $bestScore", 40f, 190f, paintScore)
        }
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintOverlayBg)

        val cx = width / 2f
        val cy = height / 2f

        canvas.drawText("Game Over", cx, cy - 100f, paintGameOverTitle)
        canvas.drawText("Score: $score", cx, cy + 20f, paintGameOverText)
        canvas.drawText("Best: $bestScore", cx, cy + 90f, paintGameOverText)

        val blink = (SystemClock.uptimeMillis() / 500) % 2 == 0L
        if (blink) {
            canvas.drawText("Tap to restart", cx, cy + 200f, paintGameOverHint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isGameOver) {
                    startNewGame()
                    lastFrameTime = SystemClock.uptimeMillis()
                    return true
                }
                if (x < width / 2f) {
                    joyActive = true
                    updateJoy(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (joyActive && !isGameOver) updateJoy(x, y)
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
