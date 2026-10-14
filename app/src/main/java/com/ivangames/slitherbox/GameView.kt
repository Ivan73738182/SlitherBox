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

class GameView(
    context: Context,
    val mode: String = "OFFLINE",
    val net: NetworkManager? = null
) : View(context) {

    // ===== МИР =====
    private val worldSize = 5000f
    private val foodRadius = 8f
    private val segmentRadius = 12f
    private val segmentSpacing = 14f
    private var snakeLength = 5
    private var score = 0

    // ===== ТРЕЙЛ =====
    private val trail = mutableListOf<PointF>()
    private val maxTrailLength = 8000
    private var headAngle = 0f
    private val baseSpeed = 4.5f

    // ===== УСКОРЕНИЕ =====
    private var boosting = false
    private var boostButtonX = 0f
    private var boostButtonY = 0f
    private var boostButtonR = 0f
    private var boostCooldown = 0f
    private val boostSpeedMul = 2.2f

    // ===== СОСТОЯНИЕ =====
    private var isGameOver = false

    // ===== РЕКОРД / СКИН / НИК =====
    private lateinit var prefs: SharedPreferences
    private var bestScore = 0
    private var skinColor: Int = 0xFF80DC78.toInt()
    private var playerNick: String = "IVAN"

    // ===== КАМЕРА =====
    private var camX = 0f
    private var camY = 0f

    // ===== ЕДА =====
    private data class Food(val x: Float, val y: Float, val color: Int, val big: Boolean = false)
    private val foods = mutableListOf<Food>()
    private val foodColors = intArrayOf(
        Color.rgb(80, 220, 120), Color.rgb(255, 100, 100),
        Color.rgb(255, 220, 100), Color.rgb(100, 180, 255),
        Color.rgb(200, 120, 255), Color.rgb(255, 160, 80),
        Color.rgb(255, 120, 200), Color.rgb(120, 255, 240)
    )

    // ===== БОТЫ =====
    private val bots = mutableListOf<BotSnake>()
    private val botNames = arrayOf(
        "Carolyn","Wendy","Tracey","Felicity","Andrea","mass thief",
        "eatmyfat","DNADNB","u8violin","Skilled","Ninja","Viper",
        "Cobra","Python","Anaconda","Mamba","Boa","Adder","Racer",
        "Garter","King","Grass","Tree","Sea","Corn","Milk",
        "Rat","Fox","Wolf","Lion","Tiger","Bear","Eagle"
    )
    private val botColors = intArrayOf(
        Color.rgb(255,100,100), Color.rgb(255,220,100), Color.rgb(180,120,255),
        Color.rgb(255,160,80), Color.rgb(100,200,255), Color.rgb(255,130,180),
        Color.rgb(120,255,200), Color.rgb(200,200,100), Color.rgb(180,255,120),
        Color.rgb(255,180,180)
    )

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
    private val paintGrid = Paint().apply { color = Color.rgb(30, 45, 70); strokeWidth = 1f }
    private val paintBorder = Paint().apply {
        color = Color.rgb(255, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val paintFood = Paint().apply { isAntiAlias = true }
    private val paintBigFood = Paint().apply { isAntiAlias = true }
    private val paintSnakeBody = Paint().apply { isAntiAlias = true }
    private val paintSnakeHead = Paint().apply { isAntiAlias = true }
    private val paintEyeWhite = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val paintEyeBlack = Paint().apply { color = Color.BLACK; isAntiAlias = true }
    private val paintJoyBase = Paint().apply { color = Color.argb(80, 255, 255, 255); isAntiAlias = true }
    private val paintJoyKnob = Paint().apply { color = Color.argb(180, 255, 255, 255); isAntiAlias = true }
    private val paintBoostBase = Paint().apply { color = Color.argb(80, 255, 220, 100); isAntiAlias = true }
    private val paintScore = Paint().apply {
        color = Color.WHITE; textSize = 42f; isAntiAlias = true; isFakeBoldText = true
    }
    private val paintNick = Paint().apply {
        color = Color.WHITE; textSize = 26f; isAntiAlias = true; isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val paintLeader = Paint().apply {
        color = Color.WHITE; textSize = 24f; isAntiAlias = true
    }
    private val paintLeaderBg = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val paintLeaderHighlight = Paint().apply { color = Color.argb(120, 255, 220, 100) }
    private val paintGameOverTitle = Paint().apply {
        color = Color.WHITE; textSize = 100f; isAntiAlias = true
        isFakeBoldText = true; textAlign = Paint.Align.CENTER
    }
    private val paintGameOverText = Paint().apply {
        color = Color.WHITE; textSize = 50f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val paintGameOverHint = Paint().apply {
        color = Color.rgb(200, 200, 200); textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    private val paintOverlayBg = Paint().apply { color = Color.argb(180, 0, 0, 0) }

    private var lastFrameTime = 0L

    init {
        prefs = context.getSharedPreferences("slitherbox", Context.MODE_PRIVATE)
        bestScore = prefs.getInt("best_score", 0)
        skinColor = prefs.getInt("skin_color", 0xFF80DC78.toInt())
        playerNick = prefs.getString("player_nick", "IVAN") ?: "IVAN"

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
        boosting = false
        boostCooldown = 0f

        skinColor = prefs.getInt("skin_color", 0xFF80DC78.toInt())
        playerNick = prefs.getString("player_nick", "IVAN") ?: "IVAN"

        repeat(600) { foods.add(spawnFood()) }

        val margin = 500f
        for (i in 0 until 30) {
            val bx = margin + Math.random().toFloat() * (worldSize - 2 * margin)
            val by = margin + Math.random().toFloat() * (worldSize - 2 * margin)
            val ba = (Math.random() * 2 * Math.PI).toFloat()
            val color = botColors[i % botColors.size]
            val headColor = lighten(color, 0.3f)
            val nick = botNames[i % botNames.size]
            bots.add(BotSnake(bx, by, ba, color, headColor, worldSize, nick))
        }
    }

    private fun lighten(color: Int, amount: Float): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val nr = (r + (255 - r) * amount).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * amount).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(nr, ng, nb)
    }

    private fun spawnFood(big: Boolean = false): Food {
        return Food(
            (50..(worldSize.toInt() - 50)).random().toFloat(),
            (50..(worldSize.toInt() - 50)).random().toFloat(),
            if (big) Color.rgb(255, 200, 60) else foodColors.random(),
            big
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        joyRadius = h * 0.18f
        joyCenterX = joyRadius + h * 0.05f
        joyCenterY = h - joyRadius - h * 0.05f
        joyKnobX = joyCenterX
        joyKnobY = joyCenterY

        boostButtonR = h * 0.12f
        boostButtonX = w - boostButtonR - h * 0.05f
        boostButtonY = h - boostButtonR - h * 0.05f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameTime).coerceAtMost(33L)
        lastFrameTime = now

        if (!isGameOver) update(dt)

        drawWorld(canvas)

        if (isGameOver) drawGameOver(canvas)

        invalidate()
    }

    private fun update(dt: Long) {
        val speed = baseSpeed * (if (boosting) boostSpeedMul else 1f) * (dt / 16.67f)

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

        if (boosting) {
            boostCooldown += dt
            if (boostCooldown >= 500f) {
                boostCooldown = 0f
                if (snakeLength > 5) snakeLength -= 1
            }
        }

        for (bot in bots) {
            if (!bot.alive) continue
            val botSegments = bot.getSegments()
            for (i in 1 until botSegments.size) {
                val seg = botSegments[i]
                val dx = newHeadX - seg.x
                val dy = newHeadY - seg.y
                if (dx * dx + dy * dy < (segmentRadius * 1.4f) * (segmentRadius * 1.4f)) {
                    gameOver(); return
                }
            }
            if (botSegments.isNotEmpty()) {
                val botHead = botSegments[0]
                val dx = newHeadX - botHead.x
                val dy = newHeadY - botHead.y
                if (dx * dx + dy * dy < (segmentRadius * 1.4f) * (segmentRadius * 1.4f)) {
                    gameOver(); return
                }
            }
        }

        checkFoodCollision(newHeadX, newHeadY)

        val foodPositions = foods.map { PointF(it.x, it.y) }
        for (bot in bots) {
            if (!bot.alive) continue
            val enemies = mutableListOf<PointF>()
            enemies.addAll(getSnakeSegments())
            for (other in bots) {
                if (other !== bot && other.alive) {
                    enemies.addAll(other.getSegments())
                }
            }
            bot.update(dt, foodPositions, enemies)
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

        val deadBots = bots.filter { !it.alive }
        for (bot in deadBots) {
            val head2 = bot.trail.lastOrNull() ?: continue
            val dropCount = (bot.length / 3).coerceAtLeast(2)
            for (i in 0 until dropCount) {
                foods.add(Food(
                    head2.x + (Math.random().toFloat() - 0.5f) * 100f,
                    head2.y + (Math.random().toFloat() - 0.5f) * 100f,
                    Color.rgb(255, 200, 60),
                    true
                ))
            }
        }
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
                val points = if (food.big) 10 else 1
                score += points
                while (score >= snakeLength * 10) {
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

    // ===== ПРОДОЛЖЕНИЕ — ЧАСТЬ 2B =====
    // Сюда вставим: drawWorld, drawLeaderboard, drawGameOver, onTouchEvent, updateJoy
}
    private fun drawWorld(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 30, 50))

        // Сетка
        val gridSize = 200f
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
        canvas.drawRect(-camX, -camY, worldSize - camX, worldSize - camY, paintBorder)

        // Еда (culling)
        for (food in foods) {
            val sx = food.x - camX
            val sy = food.y - camY
            if (sx > -20 && sx < width + 20 && sy > -20 && sy < height + 20) {
                if (food.big) {
                    paintBigFood.color = food.color
                    canvas.drawCircle(sx, sy, foodRadius * 2.2f, paintBigFood)
                } else {
                    paintFood.color = food.color
                    canvas.drawCircle(sx, sy, foodRadius, paintFood)
                }
            }
        }

        // Боты
        for (bot in bots) {
            bot.draw(canvas, camX, camY, paintSnakeBody, paintSnakeHead)
        }

        // Игрок
        if (!isGameOver) {
            val segments = getSnakeSegments()
            val baseR = (skinColor shr 16) and 0xFF
            val baseG = (skinColor shr 8) and 0xFF
            val baseB = skinColor and 0xFF

            for (i in segments.size - 1 downTo 1) {
                val seg = segments[i]
                val sx = seg.x - camX
                val sy = seg.y - camY
                if (sx < -50 || sx > width + 50 || sy < -50 || sy > height + 50) continue

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

            val hr = (baseR + (255 - baseR) * 0.3f).toInt().coerceIn(0, 255)
            val hg = (baseG + (255 - baseG) * 0.3f).toInt().coerceIn(0, 255)
            val hb = (baseB + (255 - baseB) * 0.3f).toInt().coerceIn(0, 255)
            paintSnakeHead.color = Color.rgb(hr, hg, hb)
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
            canvas.drawCircle(eye1X + fwdX * 3f, eye1Y + fwdY * 3f, segmentRadius * 0.18f, paintEyeBlack)
            canvas.drawCircle(eye2X + fwdX * 3f, eye2Y + fwdY * 3f, segmentRadius * 0.18f, paintEyeBlack)

            canvas.drawText(playerNick, hx, hy - segmentRadius * 2f, paintNick)

            // Джойстик
            canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintJoyBase)
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.45f, paintJoyKnob)

            // Кнопка ускорения
            canvas.drawCircle(boostButtonX, boostButtonY, boostButtonR, paintBoostBase)
            val arrowPaint = Paint().apply {
                color = Color.argb(200, 255, 255, 255)
                strokeWidth = 5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val ax = boostButtonX
            val ay = boostButtonY
            canvas.drawLine(ax, ay + 15f, ax, ay - 15f, arrowPaint)
            canvas.drawLine(ax - 12f, ay - 3f, ax, ay - 15f, arrowPaint)
            canvas.drawLine(ax + 12f, ay - 3f, ax, ay - 15f, arrowPaint)
        }

        if (!isGameOver) {
            canvas.drawText("Score: $score", 40f, 60f, paintScore)
            canvas.drawText("Length: $snakeLength", 40f, 110f, paintScore)
            drawLeaderboard(canvas)
        }
    }

    private fun drawLeaderboard(canvas: Canvas) {
        val entries = mutableListOf<Pair<String, Int>>()
        entries.add(playerNick to score)
        for (bot in bots) {
            if (bot.alive) entries.add(bot.nick to bot.score)
        }
        entries.sortByDescending { it.second }
        val top = entries.take(10)

        val lbX = width - 300f
        val lbY = 30f
        val lbW = 280f
        val rowH = 34f
        val lbH = rowH * (top.size + 1) + 20f

        canvas.drawRect(lbX, lbY, lbX + lbW, lbY + lbH, paintLeaderBg)

        canvas.drawText("🏆 ЛИДЕРЫ", lbX + 15f, lbY + 30f, paintLeader)

        for ((i, entry) in top.withIndex()) {
            val y = lbY + 30f + rowH * (i + 1)
            if (entry.first == playerNick) {
                canvas.drawRect(lbX + 5f, y - 25f, lbX + lbW - 5f, y + 5f, paintLeaderHighlight)
            }
            canvas.drawText("${i + 1}.", lbX + 15f, y, paintLeader)
            canvas.drawText(entry.first, lbX + 55f, y, paintLeader)
            val numText = entry.second.toString()
            val numWidth = paintLeader.measureText(numText)
            canvas.drawText(numText, lbX + lbW - 15f - numWidth, y, paintLeader)
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
                // Кнопка ускорения?
                val dxB = x - boostButtonX
                val dyB = y - boostButtonY
                if (dxB * dxB + dyB * dyB < boostButtonR * boostButtonR) {
                    boosting = true
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
                boosting = false
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
