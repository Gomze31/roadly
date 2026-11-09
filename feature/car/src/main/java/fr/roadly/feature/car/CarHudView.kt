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

    /** Police arrondie de l'app (Nunito Black), repli sur la police système en gras. */
    private val display: Typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, fr.roadly.core.designsystem.R.font.nunito_black) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1 * dp; color = PANEL_BORDER }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = LIMIT_RED; strokeWidth = 6 * dp }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 18 * sp; typeface = display }
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 15 * sp }
    private val limitText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 20 * sp; typeface = display; textAlign = Paint.Align.CENTER }
    private val speedText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28 * sp; typeface = display; textAlign = Paint.Align.CENTER }
    private val unitText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 12 * sp; typeface = display; textAlign = Paint.Align.CENTER }
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
        val margin = MARGIN_DP * dp
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

    /**
     * Bulle ronde (vitesse, « km/h ») en bas à gauche ; le panneau de limitation la chevauche en haut à droite, comme sur le téléphone.
     * Sans vitesse, le panneau seul prend la place de la bulle.
     */
    private fun drawSpeed(canvas: Canvas, a: SurfaceArea, margin: Float) {
        val bubble = BUBBLE_DP * dp
        val sign = SIGN_DP * dp
        val left = a.left + margin
        val bottom = a.bottom - margin
        val speed = model.speedKmh
        if (speed != null) {
            val cx = left + bubble / 2
            val cy = bottom - bubble / 2
            fill.color = PANEL
            canvas.drawCircle(cx, cy, bubble / 2, fill)
            canvas.drawCircle(cx, cy, bubble / 2 - stroke.strokeWidth / 2, stroke)
            speedText.color = if (model.overLimit) OVER_RED else WHITE
            val numberBase = cy + 4 * dp
            canvas.drawText(speed.toString(), cx, numberBase, speedText)
            canvas.drawText(context.getString(R.string.car_kmh), cx, numberBase + unitText.textSize + 3 * dp, unitText)
        }
        model.limitKmh?.let { limit ->
            // Centre du panneau sur la diagonale haut-droite de la bulle, assez loin pour ne jamais couvrir les chiffres.
            val off = bubble / 2 * SIGN_OFFSET
            val sx = if (speed != null) left + bubble / 2 + off else left + sign / 2
            val sy = if (speed != null) bottom - bubble / 2 - off else bottom - sign / 2
            fill.color = WHITE
            canvas.drawCircle(sx, sy, sign / 2, fill)
            canvas.drawCircle(sx, sy, sign / 2 - ring.strokeWidth / 2, ring)
            canvas.drawText(limit.toString(), sx, sy - (limitText.ascent() + limitText.descent()) / 2, limitText)
        }
    }

    companion object {
        private const val MARGIN_DP = 12
        /** Diamètre de la bulle de vitesse et du panneau de limitation (dp). */
        private const val BUBBLE_DP = 76
        private const val SIGN_DP = 46
        /** Décalage du centre du panneau par rapport au centre de la bulle, en rayons de bulle (sur chaque axe). */
        private const val SIGN_OFFSET = 0.95f
        /** Dépassement du panneau au-dessus de la bulle (dp), arrondi au supérieur (voir [drawSpeed]). */
        private val SIGN_OVERHANG_DP = kotlin.math.ceil(SIGN_DP / 2f + BUBBLE_DP / 2f * SIGN_OFFSET - BUBBLE_DP / 2f).toInt()

        /** Hauteur réservée en bas à gauche de la zone libre (limitation, vitesse et marge) : l'attribution de la carte passe au-dessus. */
        fun bottomStartHeightPx(context: Context): Int = ((BUBBLE_DP + SIGN_OVERHANG_DP + MARGIN_DP) * context.resources.displayMetrics.density).toInt()

        private const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF111111.toInt()
        const val MUTED = 0xFFB4A8BE.toInt()
        /** Mêmes teintes que l'app : violet très sombre, liseré violet discret. */
        const val PANEL = 0xF21C1024.toInt()
        const val PANEL_BORDER = 0x66B04FC0
        const val LIMIT_RED = 0xFFE0263C.toInt()
        const val OVER_RED = 0xFFFF5252.toInt()
        const val ZONE_INSIDE = 0xF2C62828.toInt()
        const val ZONE_AHEAD = 0xF2B45309.toInt()
    }
}
