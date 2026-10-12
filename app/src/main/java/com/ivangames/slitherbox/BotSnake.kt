package com.ivangames.slitherbox

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BotSnake(
    startX: Float,
    startY: Float,
    startAngle: Float,
    private val bodyColor: Int,
    private val headColor: Int,
    private val worldSize: Float,
    private val segmentRadius: Float = 12f,
    private val segmentSpacing: Float = 14f,
    private val speed: Float = 3.8f
) {
    // Трейл (история позиций головы)
    val trail = mutableListOf<PointF>()
    var headAngle = startAngle
    var length = 5
    var alive = true

    // Время жизни (для будущего)
    var age = 0f

    // Кулдаун на смену цели (чтобы бот не дёргался)
    private var retargetCooldown = 0f
    private var targetFood: PointF? = null

    init {
        // Стартовый трейл — 200 точек назад
        for (i in 0 until 200) {
            trail.add(PointF(
                startX - cos(startAngle) * i * 2f,
                startY - sin(startAngle) * i * 2f
            ))
        }
    }

    fun update(dt: Long, foods: List<PointF>) {
        if (!alive) return

        val speedThisFrame = speed * (dt / 16.67f)
        age += dt

        // ===== AI: выбираем цель =====
        retargetCooldown -= dt
        if (retargetCooldown <= 0f || targetFood == null) {
            targetFood = findNearestFood(foods)
            retargetCooldown = 300f  // ищем новую цель раз в 0.3 сек
        }

        // Целевой угол
        var targetAngle = headAngle

        val head = trail.last()

        // 1. Избегаем границ мира (приоритет!)
        val margin = 100f
        val nearWall = when {
            head.x < margin -> true
            head.x > worldSize - margin -> true
            head.y < margin -> true
            head.y > worldSize - margin -> true
            else -> false
        }

        if (nearWall) {
            // Разворачиваемся к центру мира
            val toCenterX = worldSize / 2f - head.x
            val toCenterY = worldSize / 2f - head.y
            targetAngle = atan2(toCenterY, toCenterX)
        } else if (targetFood != null) {
            // 2. Идём к еде
            val dx = targetFood!!.x - head.x
            val dy = targetFood!!.y - head.y
            targetAngle = atan2(dy, dx)
        }

        // ===== Плавный поворот =====
        var diff = targetAngle - headAngle
        while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
        while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

        val turnSpeed = 0.08f * (dt / 16.67f)
        headAngle += diff.coerceIn(-turnSpeed, turnSpeed)

        // ===== Двигаем голову =====
        val newHeadX = head.x + cos(headAngle) * speedThisFrame
        val newHeadY = head.y + sin(headAngle) * speedThisFrame

        val clampedX = newHeadX.coerceIn(segmentRadius, worldSize - segmentRadius)
        val clampedY = newHeadY.coerceIn(segmentRadius, worldSize - segmentRadius)

        trail.add(PointF(clampedX, clampedY))

        // Ограничиваем память
        while (trail.size > 2000) {
            trail.removeAt(0)
        }

        // ===== Бот ест еду =====
        // Возвращаем индексы съеденной еды (родительский GameView удалит их)
        // Пока просто проверяем — рост
        // (упрощённо — GameView сам вызовет checkEat)
    }

    /**
     * Проверяет, съел ли бот еду по координатам.
     * Возвращает true, если еда съедена (бот растёт).
     */
    fun tryEat(foodX: Float, foodY: Float): Boolean {
        val head = trail.last()
        val dx = foodX - head.x
        val dy = foodY - head.y
        if (sqrt(dx * dx + dy * dy) < segmentRadius + 8f) {
            length += 1
            return true
        }
        return false
    }

    /**
     * Возвращает позиции сегментов змейки (от головы к хвосту) — по трейлу.
     */
    fun getSegments(): List<PointF> {
        val result = mutableListOf<PointF>()
        if (trail.isEmpty()) return result

        result.add(trail.last())

        var needDist = segmentSpacing.toDouble()
        var i = trail.size - 2
        var accumulated = 0.0

        while (i > 0 && result.size < length) {
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

        // Добиваем, если трейл короткий
        while (result.size < length) {
            result.add(PointF(trail[0].x, trail[0].y))
        }

        return result
    }

    private fun findNearestFood(foods: List<PointF>): PointF? {
        if (foods.isEmpty()) return null
        val head = trail.last()
        var best: PointF? = null
        var bestDist = Float.MAX_VALUE

        // Ищем не самое близкое, а случайное из ближайших 20 —
        // так боты не собираются в одну точку
        val candidates = mutableListOf<Pair<PointF, Float>>()
        for (f in foods) {
            val dx = f.x - head.x
            val dy = f.y - head.y
            val d = dx * dx + dy * dy
            candidates.add(f to d)
        }
        candidates.sortBy { it.second }

        val topN = candidates.take(20)
        if (topN.isNotEmpty()) {
            val pick = topN.random()
            best = pick.first
            bestDist = pick.second
        }

        return best
    }

    fun draw(canvas: Canvas, camX: Float, camY: Float, bodyPaint: Paint, headPaint: Paint) {
        if (!alive) return

        val segments = getSegments()
        if (segments.isEmpty()) return

        // Тело
        bodyPaint.color = bodyColor
        for (i in segments.size - 1 downTo 1) {
            val seg = segments[i]
            val sx = seg.x - camX
            val sy = seg.y - camY
            if (sx < -50 || sx > canvas.width + 50 || sy < -50 || sy > canvas.height + 50) continue
            canvas.drawCircle(sx, sy, segmentRadius, bodyPaint)
        }

        // Голова
        val head = segments[0]
        val hx = head.x - camX
        val hy = head.y - camY

        headPaint.color = headColor
        canvas.drawCircle(hx, hy, segmentRadius * 1.15f, headPaint)

        // Глаза
        val whitePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        val blackPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        val eyeDist = segmentRadius * 0.55f
        val perpX = cos(headAngle + Math.PI.toFloat() / 2)
        val perpY = sin(headAngle + Math.PI.toFloat() / 2)
        val fwdX = cos(headAngle)
        val fwdY = sin(headAngle)

        val eye1X = hx + fwdX * eyeDist + perpX * eyeDist * 0.5f
        val eye1Y = hy + fwdY * eyeDist + perpY * eyeDist * 0.5f
        val eye2X = hx + fwdX * eyeDist - perpX * eyeDist * 0.5f
        val eye2Y = hy + fwdY * eyeDist - perpY * eyeDist * 0.5f

        canvas.drawCircle(eye1X, eye1Y, segmentRadius * 0.35f, whitePaint)
        canvas.drawCircle(eye2X, eye2Y, segmentRadius * 0.35f, whitePaint)
        canvas.drawCircle(
            eye1X + fwdX * segmentRadius * 0.15f,
            eye1Y + fwdY * segmentRadius * 0.15f,
            segmentRadius * 0.18f, blackPaint
        )
        canvas.drawCircle(
            eye2X + fwdX * segmentRadius * 0.15f,
            eye2Y + fwdY * segmentRadius * 0.15f,
            segmentRadius * 0.18f, blackPaint
        )
    }
}
