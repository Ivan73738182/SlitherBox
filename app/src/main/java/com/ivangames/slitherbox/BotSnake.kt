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
    val trail = mutableListOf<PointF>()
    var headAngle = startAngle
    var length = 5
    var score = 0
    var alive = true
    var age = 0f

    private var retargetCooldown = 0f
    private var targetFood: PointF? = null

    init {
        for (i in 0 until 200) {
            trail.add(PointF(
                startX - cos(startAngle) * i * 2f,
                startY - sin(startAngle) * i * 2f
            ))
        }
    }

    /**
     * Обновление бота.
     * @param enemies список чужих сегментов (все сегменты других змей + игрока)
     *                 — для избегания. Формат: список точек.
     */
    fun update(dt: Long, foods: List<PointF>, enemySegments: List<PointF>) {
        if (!alive) return

        val speedThisFrame = speed * (dt / 16.67f)
        age += dt

        val head = trail.last()

        // ===== ПРОВЕРКА СМЕРТИ ОТ СТЕН =====
        val wallMargin = segmentRadius
        if (head.x < wallMargin || head.x > worldSize - wallMargin ||
            head.y < wallMargin || head.y > worldSize - wallMargin
        ) {
            alive = false
            return
        }

        // ===== ПРОВЕРКА СТОЛКНОВЕНИЯ С ЧУЖИМИ СЕГМЕНТАМИ =====
        val dangerRadius = segmentRadius * 2.5f  // радиус обнаружения опасности
        for (seg in enemySegments) {
            val dx = seg.x - head.x
            val dy = seg.y - head.y
            if (dx * dx + dy * dy < (segmentRadius * 1.5f) * (segmentRadius * 1.5f)) {
                // Касание = смерть
                alive = false
                return
            }
        }

        // ===== AI =====
        // Ищем ближайшую опасность (сегмент врага) в радиусе
        var dangerSegment: PointF? = null
        var dangerDist = Float.MAX_VALUE
        for (seg in enemySegments) {
            val dx = seg.x - head.x
            val dy = seg.y - head.y
            val d = dx * dx + dy * dy
            if (d < dangerRadius * dangerRadius && d < dangerDist) {
                dangerDist = d
                dangerSegment = seg
            }
        }

        // Стена рядом?
        val nearWallDist = 150f
        val nearWall = head.x < nearWallDist || head.x > worldSize - nearWallDist ||
                head.y < nearWallDist || head.y > worldSize - nearWallDist

        var targetAngle = headAngle

        when {
            // 1. Опасность — уходим
            dangerSegment != null -> {
                val dx = head.x - dangerSegment.x
                val dy = head.y - dangerSegment.y
                targetAngle = atan2(dy, dx)
            }
            // 2. Стена близко — к центру
            nearWall -> {
                val toCenterX = worldSize / 2f - head.x
                val toCenterY = worldSize / 2f - head.y
                targetAngle = atan2(toCenterY, toCenterX)
            }
            // 3. Еда — идём
            else -> {
                retargetCooldown -= dt
                if (retargetCooldown <= 0f || targetFood == null) {
                    targetFood = findBestFood(foods, head)
                    retargetCooldown = 300f
                }
                if (targetFood != null) {
                    val dx = targetFood!!.x - head.x
                    val dy = targetFood!!.y - head.y
                    targetAngle = atan2(dy, dx)
                }
            }
        }

        // Плавный поворот
        var diff = targetAngle - headAngle
        while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
        while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()

        val turnSpeed = 0.09f * (dt / 16.67f)
        headAngle += diff.coerceIn(-turnSpeed, turnSpeed)

        // Двигаем
        val newHeadX = head.x + cos(headAngle) * speedThisFrame
        val newHeadY = head.y + sin(headAngle) * speedThisFrame

        trail.add(PointF(newHeadX, newHeadY))

        while (trail.size > 2000) {
            trail.removeAt(0)
        }
    }

    fun tryEat(foodX: Float, foodY: Float): Boolean {
        if (!alive) return false
        val head = trail.last()
        val dx = foodX - head.x
        val dy = foodY - head.y
        if (sqrt(dx * dx + dy * dy) < segmentRadius + 8f) {
            score += 1
            if (score % 10 == 0) {
                length += 1
            }
            return true
        }
        return false
    }

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

        while (result.size < length) {
            result.add(PointF(trail[0].x, trail[0].y))
        }

        return result
    }

    /**
     * Ищет лучшую еду: не ближайшую, а случайную из топ-15.
     * Так боты не собираются в одну точку.
     */
    private fun findBestFood(foods: List<PointF>, head: PointF): PointF? {
        if (foods.isEmpty()) return null

        val candidates = mutableListOf<Pair<PointF, Float>>()
        for (f in foods) {
            val dx = f.x - head.x
            val dy = f.y - head.y
            val d = dx * dx + dy * dy
            candidates.add(f to d)
        }
        candidates.sortBy { it.second }
        val topN = candidates.take(15)
        return if (topN.isNotEmpty()) topN.random().first else null
    }

    fun draw(canvas: Canvas, camX: Float, camY: Float, bodyPaint: Paint, headPaint: Paint) {
        if (!alive) return

        val segments = getSegments()
        if (segments.isEmpty()) return

        bodyPaint.color = bodyColor
        for (i in segments.size - 1 downTo 1) {
            val seg = segments[i]
            val sx = seg.x - camX
            val sy = seg.y - camY
            if (sx < -50 || sx > canvas.width + 50 || sy < -50 || sy > canvas.height + 50) continue
            canvas.drawCircle(sx, sy, segmentRadius, bodyPaint)
        }

        val head = segments[0]
        val hx = head.x - camX
        val hy = head.y - camY

        headPaint.color = headColor
        canvas.drawCircle(hx, hy, segmentRadius * 1.15f, headPaint)

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
