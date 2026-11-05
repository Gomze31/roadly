package fr.roadly.feature.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import fr.roadly.core.common.Formatters
import fr.roadly.core.map.SurfaceArea

/**
 * Indications dessinées sur la carte de la voiture, dans la zone laissée libre par les gabarits de l'hôte :
 * pastille « Zone de danger » en haut, limitation et vitesse en bas à gauche. Aucune interaction (l'hôte gère le toucher).
 */
@SuppressLint("ViewConstructor")
internal class CarHudView(context: Context) : View(context) {
    private var model: CarHudModel = CarHudModel.EMPTY
    private var area: SurfaceArea? = null
    private val dp = resources.displayMetrics.density
    private val sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = LIMIT_RED; strokeWidth = 6 * dp }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 18 * sp; typeface = Typeface.DEFAULT_BOLD }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 15 * sp }
    private val limitText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 24 * sp; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val speedText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26 * sp; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val unitText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 11 * sp; textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    fun update(model: CarHudModel) {
        if (model == this.model) return
        this.model = model
        invalidate()
    }

    fun setArea(area: SurfaceArea) {
        if (area == this.area) return
        this.area = area
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val a = area ?: SurfaceArea(0, 0, width, height)
        if (a.width <= 0 || a.height <= 0) return
        val margin = 12 * dp
        model.zone?.let { drawZone(canvas, it, a, margin) }
        drawSpeed(canvas, a, margin)
    }

    private fun drawZone(canvas: Canvas, zone: CarZoneModel, a: SurfaceArea, margin: Float) {
        val res = context.resources
        val head = res.getString(R.string.car_zone_title)
        val parts = buildList {
            add(res.getString(if (zone.inside) R.string.car_zone_remaining else R.string.car_zone_in, Formatters.distance(zone.distanceM)))
            zone.limitKmh?.let { add("$it ${res.getString(R.string.car_kmh)}") }
            if (!zone.reliable) add(res.getString(R.string.car_zone_low_confidence))
        }
        val sub = parts.joinToString(" · ")
        val padH = 16 * dp
        val padV = 10 * dp
        val gap = 2 * dp
        val textW = maxOf(title.measureText(head), detail.measureText(sub))
        val w = (textW + 2 * padH).coerceAtMost(a.width - 2 * margin)
        val h = padV * 2 + title.textSize + gap + detail.textSize
        val left = a.centerX - w / 2
        val top = a.top + margin
        rect.set(left, top, left + w, top + h)
        fill.color = if (zone.inside) ZONE_INSIDE else ZONE_AHEAD
        canvas.drawRoundRect(rect, 14 * dp, 14 * dp, fill)
        val baseTitle = top + padV - title.ascent()
        canvas.drawText(head, left + padH, baseTitle, title)
        canvas.drawText(sub, left + padH, baseTitle + title.descent() + gap - detail.ascent(), detail)
    }

    private fun drawSpeed(canvas: Canvas, a: SurfaceArea, margin: Float) {
        val size = 64 * dp
        var x = a.left + margin
        val bottom = a.bottom - margin
        val top = bottom - size
        model.limitKmh?.let { limit ->
            val cx = x + size / 2
            val cy = top + size / 2
            fill.color = WHITE
            canvas.drawCircle(cx, cy, size / 2, fill)
            canvas.drawCircle(cx, cy, size / 2 - ring.strokeWidth / 2, ring)
            canvas.drawText(limit.toString(), cx, cy - (limitText.ascent() + limitText.descent()) / 2, limitText)
            x += size + 8 * dp
        }
        val speed = model.speedKmh ?: return
        rect.set(x, top, x + size * 1.1f, bottom)
        fill.color = PANEL
        canvas.drawRoundRect(rect, 12 * dp, 12 * dp, fill)
        speedText.color = if (model.overLimit) OVER_RED else WHITE
        val cx = rect.centerX()
        val numberBase = rect.centerY() + 2 * dp
        canvas.drawText(speed.toString(), cx, numberBase, speedText)
        canvas.drawText(context.getString(R.string.car_kmh), cx, numberBase + unitText.textSize + 2 * dp, unitText)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF111111.toInt()
        const val MUTED = 0xFFB8C0CC.toInt()
        const val PANEL = 0xE60B0F14.toInt()
        const val LIMIT_RED = 0xFFD32F2F.toInt()
        const val OVER_RED = 0xFFFF5252.toInt()
        const val ZONE_INSIDE = 0xF2C62828.toInt()
        const val ZONE_AHEAD = 0xF2B45309.toInt()
    }
}
