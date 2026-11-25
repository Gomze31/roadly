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
    private val smallRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = LIMIT_RED }
    private val smallLimitText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 16 * sp; typeface = display; textAlign = Paint.Align.CENTER }
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
        val zoneBottom = model.zone?.let { drawZone(canvas, it, a, margin) }
        // Signal perdu : dit en haut de la zone libre, sous la pastille de zone s'il y en a une (la vitesse, elle, disparaît).
        if (model.gpsLost) drawGpsLost(canvas, a, (zoneBottom ?: a.top.toFloat()) + margin)
        drawSpeed(canvas, a, margin)
        model.roadName?.let { drawRoadName(canvas, it, a, margin) }
    }

    /** Pastille « Signal GPS perdu », centrée, dont le haut est à [top]. */
    private fun drawGpsLost(canvas: Canvas, a: SurfaceArea, top: Float) {
        val padH = 16 * dp
        val text = android.text.TextUtils.ellipsize(
            context.getString(R.string.car_gps_lost), android.text.TextPaint(title), (a.width - 2 * MARGIN_DP * dp - 2 * padH).coerceAtLeast(0f), android.text.TextUtils.TruncateAt.END,
        ).toString()
        val w = title.measureText(text) + 2 * padH
        val h = title.textSize + 18 * dp
        val left = a.centerX - w / 2
        rect.set(left, top, left + w, top + h)
        fill.color = GPS_LOST
        canvas.drawRoundRect(rect, h / 2, h / 2, fill)
        canvas.drawText(text, left + padH, rect.centerY() - (title.ascent() + title.descent()) / 2, title)
    }

    /** Dessine la pastille de zone et rend l'ordonnée de son bord bas. */
    private fun drawZone(canvas: Canvas, zone: CarZoneModel, a: SurfaceArea, margin: Float): Float {
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
        return top + h
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
        // Prochaine limitation : à droite de la bulle et de son panneau.
        drawNextLimit(canvas, left + bubble * (0.5f + SIGN_OFFSET / 2) + sign / 2 + 10 * dp, bottom)
    }

    /** Prochaine limitation différente : petit panneau et distance, à droite de la vitesse (« 70 · 300 m »). */
    private fun drawNextLimit(canvas: Canvas, left: Float, bottom: Float) {
        val kmh = model.nextLimitKmh ?: return
        val meters = model.nextLimitDistanceM ?: return
        val size = NEXT_LIMIT_SIZE_DP * dp
        val label = Formatters.maneuverDistance(meters.toDouble())
        val w = size + 8 * dp + detail.measureText(label) + 14 * dp
        rect.set(left, bottom - size - 8 * dp, left + w, bottom)
        fill.color = PANEL
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
        val cx = left + 4 * dp + size / 2
        val cy = rect.centerY()
        fill.color = WHITE
        canvas.drawCircle(cx, cy, size / 2, fill)
        smallRing.strokeWidth = 4 * dp
        canvas.drawCircle(cx, cy, size / 2 - smallRing.strokeWidth / 2, smallRing)
        canvas.drawText(kmh.toString(), cx, cy - (smallLimitText.ascent() + smallLimitText.descent()) / 2, smallLimitText)
        canvas.drawText(label, cx + size / 2 + 8 * dp, cy - (detail.ascent() + detail.descent()) / 2, detail)
    }

    /** Conduite libre : nom de la voie suivie, en bas au centre de la zone libre. */
    private fun drawRoadName(canvas: Canvas, name: String, a: SurfaceArea, margin: Float) {
        val padH = 18 * dp
        val maxW = a.width * 0.5f
        val text = android.text.TextUtils.ellipsize(name, android.text.TextPaint(title), maxW - 2 * padH, android.text.TextUtils.TruncateAt.END).toString()
        val w = title.measureText(text) + 2 * padH
        val h = title.textSize + 18 * dp
        val left = a.centerX - w / 2
        val top = a.bottom - margin - h
        rect.set(left, top, left + w, top + h)
        fill.color = ROAD_PILL
        canvas.drawRoundRect(rect, h / 2, h / 2, fill)
        canvas.drawText(text, left + padH, rect.centerY() - (title.ascent() + title.descent()) / 2, title)
    }

    companion object {
        private const val MARGIN_DP = 12
        private const val NEXT_LIMIT_SIZE_DP = 36
        const val ROAD_PILL = 0xF22B1A66.toInt()
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
        const val MUTED = 0xFFA9B4CC.toInt()
        /** Mêmes teintes que l'app : bleu nuit, liseré bleu discret. */
        const val PANEL = 0xF20F142B.toInt()
        const val PANEL_BORDER = 0x665F86D6
        const val LIMIT_RED = 0xFFE0263C.toInt()
        const val OVER_RED = 0xFFFF5252.toInt()
        const val ZONE_INSIDE = 0xF2C62828.toInt()
        const val ZONE_AHEAD = 0xF2B45309.toInt()
        /** Pastille « Signal GPS perdu » : gris sombre, distinct des pastilles de zone de danger. */
        const val GPS_LOST = 0xF23A3F4B.toInt()
    }
}
