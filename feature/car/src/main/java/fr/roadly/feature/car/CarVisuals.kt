package fr.roadly.feature.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Lane as CarLane
import androidx.car.app.navigation.model.LaneDirection as CarLaneDirection
import androidx.core.graphics.drawable.IconCompat
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import kotlin.math.cos
import kotlin.math.sin

/** Voies de l'itinéraire converties pour la bibliothèque Car App (formes `LaneDirection.SHAPE_*`). */
internal object CarLanes {
    fun shape(d: LaneDirection): Int = when (d) {
        LaneDirection.LEFT -> CarLaneDirection.SHAPE_NORMAL_LEFT
        LaneDirection.SLIGHT_LEFT, LaneDirection.MERGE_LEFT -> CarLaneDirection.SHAPE_SLIGHT_LEFT
        LaneDirection.STRAIGHT -> CarLaneDirection.SHAPE_STRAIGHT
        LaneDirection.SLIGHT_RIGHT, LaneDirection.MERGE_RIGHT -> CarLaneDirection.SHAPE_SLIGHT_RIGHT
        LaneDirection.RIGHT -> CarLaneDirection.SHAPE_NORMAL_RIGHT
        LaneDirection.UTURN -> CarLaneDirection.SHAPE_U_TURN_LEFT
    }

    /** Voie conseillée (ou, à défaut de conseil, toute voie valide) : ses directions sont « recommandées » pour l'hôte. */
    fun recommended(lane: Lane, anyActive: Boolean): Boolean = if (anyActive) lane.active else lane.valid

    /**
     * Voies à envoyer à l'hôte : toutes, ou aucune. Une voie sans direction connue (donnée absente d'OpenStreetMap) ne peut pas être
     * dessinée sans inventer une flèche, et un bloc dont il manquerait une voie serait faux : le bloc entier est retiré.
     */
    fun displayable(lanes: List<Lane>): List<Lane> = if (lanes.any { it.directions.isEmpty() }) emptyList() else lanes

    fun toCar(lanes: List<Lane>): List<CarLane> {
        val shown = displayable(lanes)
        val anyActive = shown.any { it.active }
        return shown.map { lane ->
            val b = CarLane.Builder()
            val rec = recommended(lane, anyActive)
            for (d in lane.directions) b.addDirection(CarLaneDirection.create(shape(d), rec))
            b.build()
        }
    }
}

/**
 * Images de la carte de guidage, dessinées en bitmaps (la bibliothèque Car App n'accepte ni vues ni animations dans ses gabarits) :
 * rond-point avec la sortie à prendre, panneau de sortie, voies. En cache par contenu : les gabarits sont reconstruits souvent.
 */
internal object CarVisuals {
    private const val JUNCTION_W = 600
    private const val JUNCTION_H = 300
    private const val LANE_H = 96
    private const val LANE_W = 84
    private const val MAX_LANES = 7

    private const val PANEL = 0xFF12182E.toInt()
    private const val ROAD = 0xFF46506A.toInt()
    private const val ROAD_EDGE = 0xFF262E45.toInt()
    private const val PATH = 0xFF4D9BFF.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DIM = 0x59FFFFFF
    private const val VALID = 0xB3FFFFFF.toInt()
    private const val SIGN_BLUE = 0xFF1F4FBF.toInt()
    private const val SIGN_BADGE = 0xFFFFFFFF.toInt()
    private const val SIGN_BADGE_TEXT = 0xFF14306F.toInt()

    private val cache = object : LinkedHashMap<Any, CarIcon>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, CarIcon>?): Boolean = size > 24
    }

    @Synchronized
    fun junction(v: CarJunctionVisual): CarIcon {
        // Angle arrondi à 5° : même image tant que la donnée ne change pas vraiment.
        val key: Any = if (v is CarJunctionVisual.Roundabout) v.copy(exitAngleDeg = Math.round(v.exitAngleDeg / 5.0) * 5.0) else v
        return cache.getOrPut(key) {
            val bmp = Bitmap.createBitmap(JUNCTION_W, JUNCTION_H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            when (v) {
                is CarJunctionVisual.Roundabout -> drawRoundabout(c, v)
                is CarJunctionVisual.ExitSign -> drawExitSign(c, v)
            }
            icon(bmp)
        }
    }

    @Synchronized
    fun lanes(lanes: List<Lane>): CarIcon = cache.getOrPut(lanes) {
        val shown = lanes.take(MAX_LANES)
        val bmp = Bitmap.createBitmap(LANE_W * shown.size.coerceAtLeast(1), LANE_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val anyActive = shown.any { it.active }
        val sep = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM; strokeWidth = 3f }
        shown.forEachIndexed { i, lane ->
            val left = i * LANE_W.toFloat()
            if (i > 0) c.drawLine(left, 14f, left, LANE_H - 14f, sep)
            val color = when {
                CarLanes.recommended(lane, anyActive) -> WHITE
                lane.valid -> VALID
                else -> DIM
            }
            // Aucune flèche inventée : une voie sans direction reste vide (l'appelant n'envoie pas un tel bloc, voir CarLanes.displayable).
            for (d in lane.directions) drawLaneArrow(c, left + LANE_W / 2f, d, color)
        }
        icon(bmp)
    }

    private fun icon(bmp: Bitmap): CarIcon = CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build()

    /** Angle « horloge » (0 = haut, sens horaire) → point à [r] du centre. */
    private fun at(cx: Float, cy: Float, r: Float, clockDeg: Double): Pair<Float, Float> {
        val a = Math.toRadians(clockDeg)
        return (cx + r * sin(a)).toFloat() to (cy - r * cos(a)).toFloat()
    }

    private fun drawRoundabout(c: Canvas, v: CarJunctionVisual.Roundabout) {
        c.drawColor(PANEL)
        val cx = JUNCTION_W / 2f
        val cy = JUNCTION_H / 2f - 6f
        val r = 78f
        val armLen = 150f
        // Demi-tour (sortie à l'opposé du sens d'arrivée) : tour complet de l'anneau.
        val exit = if (v.exitAngleDeg >= 170.0 || v.exitAngleDeg <= -170.0) -180.0 else v.exitAngleDeg
        val road = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 46f; color = ROAD_EDGE }
        val fill = Paint(road).apply { strokeWidth = 38f; color = ROAD }
        val (ex1, ey1) = at(cx, cy, r, exit)
        val (ex2, ey2) = at(cx, cy, armLen, exit)
        // Mode rond-point : branches à laisser passer, courtes ; celles déjà derrière soi sont éteintes.
        for (arm in v.otherExits) {
            val (ax1, ay1) = at(cx, cy, r, arm.angleDeg)
            val (ax2, ay2) = at(cx, cy, r + 46f, arm.angleDeg)
            val armPaint = Paint(road).apply { strokeWidth = 30f; color = if (arm.passed) ROAD_EDGE else ROAD }
            c.drawLine(ax1, ay1, ax2, ay2, armPaint)
        }
        // Chaussée : anneau, bras d'entrée (en bas), bras de sortie.
        for (p in listOf(road, fill)) {
            c.drawCircle(cx, cy, r, p)
            c.drawLine(cx, cy + r, cx, JUNCTION_H + 30f, p)
            c.drawLine(ex1, ey1, ex2, ey2, p)
        }
        // Trajet : entrée, arc dans le sens de circulation (antihoraire, conduite à droite), sortie avec sa flèche.
        val path = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 20f; color = PATH }
        val route = Path().apply {
            moveTo(cx, JUNCTION_H + 30f)
            lineTo(cx, cy + r)
            // drawArc : angles depuis l'est, sens horaire ; angle horloge a → a − 90. Départ au sud (180 → 90), balayage négatif.
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 90f, (exit - 180.0).toFloat(), false)
            lineTo(ex2, ey2)
        }
        c.drawPath(route, path)
        drawArrowHead(c, ex2, ey2, exit, 30f, PATH)
        // Mode rond-point : position du véhicule sur l'anneau.
        v.vehicleAngleDeg?.let { a ->
            val (vx, vy) = at(cx, cy, r, a)
            c.drawCircle(vx, vy, 17f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL })
            c.drawCircle(vx, vy, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
        }
        v.exitNumber?.let { n ->
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 62f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            c.drawText(n.toString(), cx, cy - (text.ascent() + text.descent()) / 2, text)
        }
    }

    /** Tête de flèche en ([x], [y]) pointant vers l'angle « horloge » [clockDeg]. */
    private fun drawArrowHead(c: Canvas, x: Float, y: Float, clockDeg: Double, size: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val (tx, ty) = at(x, y, size, clockDeg)
        val (lx, ly) = at(x, y, size * 0.85f, clockDeg - 110)
        val (rx, ry) = at(x, y, size * 0.85f, clockDeg + 110)
        c.drawPath(Path().apply { moveTo(tx, ty); lineTo(lx, ly); lineTo(rx, ry); close() }, p)
    }

    private fun drawExitSign(c: Canvas, v: CarJunctionVisual.ExitSign) {
        c.drawColor(PANEL)
        val m = 14f
        val panel = RectF(m, m, JUNCTION_W - m, JUNCTION_H - m)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SIGN_BLUE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; style = Paint.Style.STROKE; strokeWidth = 6f }
        c.drawRoundRect(panel, 26f, 26f, bg)
        c.drawRoundRect(RectF(panel.left + 8, panel.top + 8, panel.right - 8, panel.bottom - 8), 20f, 20f, border)
        val pad = 34f
        val arrowW = 120f
        val textLeft = if (v.left) panel.left + pad + arrowW else panel.left + pad
        val textRight = if (v.left) panel.right - pad else panel.right - pad - arrowW
        var y = panel.top + pad
        // Cartouche « SORTIE 16 » et route signalée (« A62 ») sur la même ligne.
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SIGN_BADGE_TEXT; textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
        val label = listOfNotNull("SORTIE".takeIf { v.number != null }, v.number).joinToString(" ")
        var x = textLeft
        if (label.isNotEmpty()) {
            val w = badgeText.measureText(label) + 36f
            c.drawRoundRect(RectF(x, y, x + w, y + 60f), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SIGN_BADGE })
            c.drawText(label, x + 18f, y + 30f - (badgeText.ascent() + badgeText.descent()) / 2, badgeText)
            x += w + 18f
        }
        v.branch?.let { b ->
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
            c.drawText(ellipsize(b, t, textRight - x), x, y + 30f - (t.ascent() + t.descent()) / 2, t)
        }
        if (label.isNotEmpty() || v.branch != null) y += 60f + 22f
        // Directions : une ligne par destination (« Toulouse-Centre · Blagnac » → deux lignes), en grand.
        val dest = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; textSize = 52f; typeface = Typeface.DEFAULT_BOLD }
        val lines = v.toward?.split(" · ", "·")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().take(2)
        for (line in lines) {
            if (y + dest.textSize > panel.bottom - pad + 12f) break
            c.drawText(ellipsize(line, dest, textRight - textLeft), textLeft, y - dest.ascent(), dest)
            y += dest.textSize + 12f
        }
        // Flèche de sortie : en biais vers le côté de la sortie.
        val ax = if (v.left) panel.left + pad + arrowW / 2 - 6f else panel.right - pad - arrowW / 2 + 6f
        val ay = panel.centerY() + 34f
        val angle = if (v.left) -40.0 else 40.0
        val (hx, hy) = at(ax, ay, 84f, angle)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE; style = Paint.Style.STROKE; strokeWidth = 18f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(ax, ay + 40f, ax, ay, stroke)
        c.drawLine(ax, ay, hx, hy, stroke)
        drawArrowHead(c, hx, hy, angle, 34f, WHITE)
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String =
        TextUtils.ellipsize(text, TextPaint(paint), width.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()

    /** Flèche d'une direction dans une voie centrée en [cx] : tige depuis le bas, puis branche et tête. */
    private fun drawLaneArrow(c: Canvas, cx: Float, d: LaneDirection, color: Int) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val bottom = LANE_H - 14f
        val mid = LANE_H * 0.52f
        val top = 22f
        val side = LANE_W * 0.30f
        val path = Path().apply { moveTo(cx, bottom) }
        val (endX, endY, angle) = when (d) {
            LaneDirection.STRAIGHT -> { path.lineTo(cx, top); Triple(cx, top, 0.0) }
            LaneDirection.SLIGHT_RIGHT, LaneDirection.MERGE_RIGHT -> { path.lineTo(cx, mid); path.lineTo(cx + side * 0.8f, top + 4f); Triple(cx + side * 0.8f, top + 4f, 35.0) }
            LaneDirection.SLIGHT_LEFT, LaneDirection.MERGE_LEFT -> { path.lineTo(cx, mid); path.lineTo(cx - side * 0.8f, top + 4f); Triple(cx - side * 0.8f, top + 4f, -35.0) }
            LaneDirection.RIGHT -> { path.lineTo(cx, mid - 8f); path.lineTo(cx + side, mid - 8f); Triple(cx + side, mid - 8f, 90.0) }
            LaneDirection.LEFT -> { path.lineTo(cx, mid - 8f); path.lineTo(cx - side, mid - 8f); Triple(cx - side, mid - 8f, -90.0) }
            LaneDirection.UTURN -> {
                path.lineTo(cx, top + 22f)
                path.arcTo(RectF(cx - side, top + 6f, cx, top + 38f), 0f, -180f, false)
                path.lineTo(cx - side, mid + 6f)
                Triple(cx - side, mid + 6f, 180.0)
            }
        }
        c.drawPath(path, stroke)
        drawArrowHead(c, endX, endY, angle, 13f, color)
    }
}
