package fr.roadly.core.map.junction

import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.junction.RoadClass
import kotlin.math.cos
import kotlin.math.sin

/** Couleurs de la scène (ARGB), dérivées des jetons Roadly (jour / nuit) par l'écran. */
data class JunctionPalette(
    val skyTop: Int,
    val skyHorizon: Int,
    val ground: Int,
    val groundGrid: Int,
    val motorway: Int,
    val road: Int,
    val minorRoad: Int,
    val marking: Int,
    val barrier: Int,
    val deck: Int,
    val grass: Int,
    val building: Int,
    val path: Int,
    val pathGlow: Int,
    val laneActive: Int,
    val fog: Int,
    val night: Boolean,
) {
    companion object {
        val NIGHT = JunctionPalette(
            skyTop = 0xFF07060C.toInt(), skyHorizon = 0xFF1E1830.toInt(), ground = 0xFF15121D.toInt(), groundGrid = 0xFF231E30.toInt(),
            motorway = 0xFF3A3646.toInt(), road = 0xFF34303F.toInt(), minorRoad = 0xFF2C2936.toInt(), marking = 0xFFE9E6F2.toInt(),
            barrier = 0xFF8A8598.toInt(), deck = 0xFF4A4558.toInt(), grass = 0xFF1C2A22.toInt(), building = 0xFF2A2236.toInt(),
            path = 0xFFB794FF.toInt(), pathGlow = 0xFF8B5CF6.toInt(), laneActive = 0xFFB794FF.toInt(), fog = 0xFF1E1830.toInt(), night = true,
        )
        val DAY = JunctionPalette(
            skyTop = 0xFF8FB8E8.toInt(), skyHorizon = 0xFFE6EEF7.toInt(), ground = 0xFFDDE3D6.toInt(), groundGrid = 0xFFCDD5C4.toInt(),
            motorway = 0xFF6E7380.toInt(), road = 0xFF7C808B.toInt(), minorRoad = 0xFF8E929C.toInt(), marking = 0xFFFFFFFF.toInt(),
            barrier = 0xFFB9BCC4.toInt(), deck = 0xFF9EA2AB.toInt(), grass = 0xFF9DBB86.toInt(), building = 0xFFE4DFEA.toInt(),
            path = 0xFF7C3AED.toInt(), pathGlow = 0xFF8B5CF6.toInt(), laneActive = 0xFF7C3AED.toInt(), fog = 0xFFE6EEF7.toInt(), night = false,
        )
    }
}

/** Matériaux interprétés par le fragment shader (dernier attribut du sommet). */
object JunctionMaterial {
    const val LIT = 0f
    const val PATH = 1f
    const val GORE = 2f
    const val DASH = 3f
    const val GLOW = 4f
    const val LANE = 5f
    const val GROUND = 6f
}

/** Tampon de sommets : x y z, nx ny nz, r g b a, u v, matériau (13 flottants). Coordonnées GL : X = est, Y = haut, Z = −nord. */
class VertexBuffer(initial: Int = 4096) {
    var data = FloatArray(initial * STRIDE); private set
    var count = 0; private set

    fun clear() { count = 0 }

    fun add(x: Double, y: Double, z: Double, nx: Float, ny: Float, nz: Float, color: Int, alpha: Float, u: Double, v: Double, material: Float) {
        if ((count + 1) * STRIDE > data.size) data = data.copyOf(data.size * 2)
        var o = count * STRIDE
        data[o++] = x.toFloat(); data[o++] = y.toFloat(); data[o++] = z.toFloat()
        data[o++] = nx; data[o++] = ny; data[o++] = nz
        data[o++] = ((color shr 16) and 0xFF) / 255f; data[o++] = ((color shr 8) and 0xFF) / 255f; data[o++] = (color and 0xFF) / 255f
        data[o++] = alpha * (((color ushr 24) and 0xFF) / 255f)
        data[o++] = u.toFloat(); data[o++] = v.toFloat(); data[o] = material
        count++
    }

    fun trimmed(): FloatArray = data.copyOf(count * STRIDE)

    companion object { const val STRIDE = 13 }
}

/** Quadrilatère texturé d'un panneau (4 coins dans l'ordre bas-gauche, bas-droit, haut-droit, haut-gauche) et son contenu. */
data class SignQuad(val corners: List<FloatArray>, val sign: SceneSign, val widthM: Double, val heightM: Double)

/** Maillages d'une scène : opaques, transparents (trajectoire, halo), panneaux et étiquettes. */
class JunctionMeshes(
    val opaque: FloatArray,
    val transparent: FloatArray,
    val signs: List<SignQuad>,
    val labels: List<SceneLabel>,
    /** Trajectoire échantillonnée tous les 2 m pour la caméra et le véhicule : x, y(haut), z. */
    val pathSamples: FloatArray,
    val pathStep: Double,
    val maneuverS: Double,
    val endS: Double,
    val alongOffsetM: Double,
    val roundaboutCenter: FloatArray?,
) {
    val opaqueVertices get() = opaque.size / VertexBuffer.STRIDE
    val transparentVertices get() = transparent.size / VertexBuffer.STRIDE
}

/** Construit les maillages d'une [JunctionScene] (Kotlin pur, testé sur JVM). */
object JunctionMeshBuilder {
    const val PATH_WIDTH = 2.6
    const val PATH_GLOW_WIDTH = 7.0
    const val BARRIER_H = 0.9
    const val DECK_THICKNESS = 1.3
    const val GANTRY_H = 6.2
    const val PATH_STEP = 2.0
    private const val ROAD_LEVEL = 0.03
    private const val MARK_UP = 0.045
    private const val PATH_UP = 0.45

    fun build(scene: JunctionScene, palette: JunctionPalette): JunctionMeshes {
        val o = VertexBuffer(16_384)
        val t = VertexBuffer(4_096)
        ground(o, scene.radiusM + 900.0, palette)
        for (b in scene.buildings) building(o, b, palette)
        val levels = scene.roads.map { it.order }.distinct().sorted()
        for (r in scene.roads) {
            val lift = ROAD_LEVEL * (levels.indexOf(r.order) + 1)
            road(o, r, lift, palette)
        }
        scene.roundabout?.let { roundabout(o, it, ROAD_LEVEL * (levels.size + 1), palette) }
        for (g in scene.gores) gore(o, g, palette)
        for ((at, h) in scene.pillars) box(o, at, 1.6, 1.6, 0.0, h - DECK_THICKNESS * 0.6, palette.deck)
        for (a in scene.laneArrows) laneArrow(o, a, palette)
        val quads = scene.signs.map { gantry(o, it, palette) }
        path(t, scene.path, palette)
        val samples = samplePath(scene.path)
        return JunctionMeshes(
            o.trimmed(), t.trimmed(), quads, scene.labels, samples, PATH_STEP, scene.path.maneuverS, scene.path.endS, scene.path.alongOffsetM,
            scene.roundabout?.let { floatArrayOf(it.center.x.toFloat(), 0f, (-it.center.y).toFloat()) },
        )
    }

    private fun asphalt(c: RoadClass, p: JunctionPalette) = when {
        c.isHighway -> p.motorway
        c.rank <= RoadClass.TERTIARY.rank -> p.road
        else -> p.minorRoad
    }

    private fun ground(o: VertexBuffer, half: Double, p: JunctionPalette) {
        val y = -0.05
        val corners = listOf(-half to -half, half to -half, half to half, -half to half)
        val idx = intArrayOf(0, 1, 2, 0, 2, 3)
        for (i in idx) { val (x, z) = corners[i]; o.add(x, y, z, 0f, 1f, 0f, p.ground, 1f, x, z, JunctionMaterial.GROUND) }
    }

    /** Normales latérales par sommet (bissectrice, onglet borné). */
    internal fun sideNormals(pts: List<V2>): List<V2> = pts.indices.map { i ->
        val a = if (i > 0) (pts[i] - pts[i - 1]).normalized() else (pts[1] - pts[0]).normalized()
        val b = if (i < pts.lastIndex) (pts[i + 1] - pts[i]).normalized() else a
        val n = (a.rightNormal() + b.rightNormal()).normalized().let { if (it.length() < 0.5) a.rightNormal() else it }
        val c = n.dot(a.rightNormal()).coerceAtLeast(0.5)
        n * (1.0 / c)
    }

    /** Ruban plat de [pts] décalé latéralement de [offset] (m, > 0 = droite), largeur par sommet. */
    private fun ribbon(
        buf: VertexBuffer, pts: List<V2>, widths: (Int) -> Double, elev: (Int) -> Double, offset: (Int) -> Double,
        color: (Int) -> Int, alpha: Float, material: Float, vStart: Double = 0.0,
    ) {
        if (pts.size < 2) return
        val n = sideNormals(pts)
        var s = vStart
        for (i in 0 until pts.size - 1) {
            val j = i + 1
            val segLen = pts[i].dist(pts[j])
            fun edge(k: Int, side: Double): V2 = pts[k] + n[k] * (offset(k) + side * widths(k) / 2)
            val l0 = edge(i, -1.0); val r0 = edge(i, 1.0); val l1 = edge(j, -1.0); val r1 = edge(j, 1.0)
            val y0 = elev(i); val y1 = elev(j)
            val c0 = color(i); val c1 = color(j)
            val s1 = s + segLen
            buf.add(l0.x, y0, -l0.y, 0f, 1f, 0f, c0, alpha, 0.0, s, material)
            buf.add(r0.x, y0, -r0.y, 0f, 1f, 0f, c0, alpha, 1.0, s, material)
            buf.add(r1.x, y1, -r1.y, 0f, 1f, 0f, c1, alpha, 1.0, s1, material)
            buf.add(l0.x, y0, -l0.y, 0f, 1f, 0f, c0, alpha, 0.0, s, material)
            buf.add(r1.x, y1, -r1.y, 0f, 1f, 0f, c1, alpha, 1.0, s1, material)
            buf.add(l1.x, y1, -l1.y, 0f, 1f, 0f, c1, alpha, 0.0, s1, material)
            s = s1
        }
    }

    /** Mur vertical le long d'un bord (glissière, parapet, flanc de tablier) : de [bottom] à [top] relatifs à l'élévation. */
    private fun wall(buf: VertexBuffer, pts: List<V2>, side: Double, widths: (Int) -> Double, elev: (Int) -> Double, bottom: Double, top: Double, color: Int, only: (Int) -> Boolean = { true }) {
        if (pts.size < 2) return
        val n = sideNormals(pts)
        for (i in 0 until pts.size - 1) {
            val j = i + 1
            if (!only(i) || !only(j)) continue
            val a = pts[i] + n[i] * (side * widths(i) / 2); val b = pts[j] + n[j] * (side * widths(j) / 2)
            val nn = (n[i] * side).normalized()
            val nx = nn.x.toFloat(); val nz = (-nn.y).toFloat()
            val ya0 = elev(i) + bottom; val ya1 = elev(i) + top; val yb0 = elev(j) + bottom; val yb1 = elev(j) + top
            buf.add(a.x, ya0, -a.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            buf.add(b.x, yb0, -b.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            buf.add(b.x, yb1, -b.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            buf.add(a.x, ya0, -a.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            buf.add(b.x, yb1, -b.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            buf.add(a.x, ya1, -a.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
        }
    }

    private fun road(o: VertexBuffer, r: SceneRoad, lift: Double, p: JunctionPalette) {
        val pts = r.pts
        if (pts.size < 2) return
        val base = asphalt(r.roadClass, p)
        val tunnelTint = { i: Int -> if (r.tunnel.getOrElse(i) { false }) darken(base, 0.55f) else base }
        val elev = { i: Int -> r.elev[i] + lift }
        ribbon(o, pts, { r.widths[it] }, elev, { 0.0 }, tunnelTint, 1f, JunctionMaterial.LIT)
        // Flancs du tablier sur les parties surélevées.
        val raised = { i: Int -> r.elev[i] > 0.5 }
        for (side in listOf(-1.0, 1.0)) {
            wall(o, pts, side, { r.widths[it] }, { r.elev[it] }, -DECK_THICKNESS, lift, p.deck, raised)
            if (r.barriers || r.elev.any { it > 0.5 }) wall(o, pts, side, { r.widths[it] + 0.3 }, elev, 0.0, BARRIER_H, p.barrier) { r.barriers || raised(it) }
        }
        // Lignes de rive (continues) et séparations de voies (pointillés) seulement quand le nombre de voies est connu.
        val markY = { i: Int -> r.elev[i] + lift + MARK_UP }
        val edgeW = 0.18
        for (side in listOf(-1.0, 1.0)) {
            ribbon(o, pts, { edgeW }, markY, { side * (r.widths[it] / 2 - 0.55) }, { p.marking }, if (r.onRoute) 0.95f else 0.6f, JunctionMaterial.GLOW)
        }
        var i = 0
        while (i < pts.size - 1) {
            val n = r.laneCounts[i]
            var j = i
            while (j < pts.size - 1 && r.laneCounts[j + 1] == n) j++
            if (n != null && n >= 2 && j > i) {
                val run = pts.subList(i, j + 1)
                val total = n * JunctionSceneBuilder.LANE_WIDTH_M
                for (k in 1 until n) {
                    val off = -total / 2 + k * JunctionSceneBuilder.LANE_WIDTH_M
                    ribbon(o, run, { 0.15 }, { markY(i + it) }, { off }, { p.marking }, 0.85f, JunctionMaterial.DASH)
                }
            }
            i = maxOf(j, i + 1)
        }
    }

    private fun roundabout(o: VertexBuffer, rb: SceneRoundabout, lift: Double, p: JunctionPalette) {
        val seg = 72
        val inner = (rb.radius - rb.width / 2).coerceAtLeast(1.4); val outer = inner + rb.width
        fun at(r: Double, a: Double) = V2(rb.center.x + r * cos(a), rb.center.y + r * sin(a))
        for (k in 0 until seg) {
            val a0 = TAU * k / seg; val a1 = TAU * (k + 1) / seg
            val i0 = at(inner, a0); val o0 = at(outer, a0); val i1 = at(inner, a1); val o1 = at(outer, a1)
            for (v in listOf(i0, o0, o1, i0, o1, i1)) o.add(v.x, lift, -v.y, 0f, 1f, 0f, p.road, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            // Îlot central (pelouse surélevée) et bordure.
            val island = (inner - 0.6).coerceAtLeast(0.8)
            val c = rb.center; val e0 = at(island, a0); val e1 = at(island, a1)
            for (v in listOf(c, e0, e1)) o.add(v.x, lift + 0.3, -v.y, 0f, 1f, 0f, p.grass, 1f, 0.0, 0.0, JunctionMaterial.LIT)
            val nx = cos((a0 + a1) / 2).toFloat(); val nz = (-sin((a0 + a1) / 2)).toFloat()
            for ((v, h) in listOf(e0 to 0.0, e1 to 0.0, e1 to 0.3, e0 to 0.0, e1 to 0.3, e0 to 0.3)) o.add(v.x, lift + h, -v.y, nx, 0f, nz, p.barrier, 1f, 0.0, 0.0, JunctionMaterial.LIT)
        }
        // Rives et, si l'anneau a plusieurs voies, séparation pointillée.
        val ringPts = (0..seg).map { at(1.0, TAU * it / seg) }
        val circle = { r: Double -> (0..seg).map { at(r, TAU * it / seg) } }
        ribbon(o, circle(outer - 0.5), { 0.18 }, { lift + MARK_UP }, { 0.0 }, { p.marking }, 0.9f, JunctionMaterial.GLOW)
        ribbon(o, circle(inner + 0.5), { 0.18 }, { lift + MARK_UP }, { 0.0 }, { p.marking }, 0.9f, JunctionMaterial.GLOW)
        if ((rb.lanes ?: 1) >= 2) ribbon(o, circle(rb.radius), { 0.15 }, { lift + MARK_UP }, { 0.0 }, { p.marking }, 0.85f, JunctionMaterial.DASH)
        check(ringPts.isNotEmpty())
    }

    private fun gore(o: VertexBuffer, g: SceneGore, p: JunctionPalette) {
        val tris = triangulate(g.polygon)
        val y = g.elev + ROAD_LEVEL * 12 + MARK_UP
        for (v in tris) o.add(v.x, y, -v.y, 0f, 1f, 0f, p.marking, 0.9f, v.x, v.y, JunctionMaterial.GORE)
    }

    private fun building(o: VertexBuffer, b: SceneBuilding, p: JunctionPalette) {
        val ring = if (signedArea(b.ring) < 0) b.ring.reversed() else b.ring
        val top = b.height; val bottom = b.minHeight
        val shade = { k: Float -> darken(p.building, k) }
        for (i in ring.indices) {
            val a = ring[i]; val c = ring[(i + 1) % ring.size]
            val d = (c - a).normalized(); val nrm = d.rightNormal()
            val nx = nrm.x.toFloat(); val nz = (-nrm.y).toFloat()
            val col = shade(0.92f)
            for ((v, h) in listOf(a to bottom, c to bottom, c to top, a to bottom, c to top, a to top)) o.add(v.x, h, -v.y, nx, 0f, nz, col, 1f, 0.0, h, JunctionMaterial.LIT)
        }
        for (v in triangulate(ring)) o.add(v.x, top, -v.y, 0f, 1f, 0f, p.building, 1f, 0.0, 0.0, JunctionMaterial.LIT)
    }

    internal fun box(o: VertexBuffer, at: V2, sx: Double, sz: Double, y0: Double, y1: Double, color: Int, dir: V2 = V2(0.0, 1.0)) {
        val f = dir.normalized(); val r = f.rightNormal()
        val hx = sx / 2; val hz = sz / 2
        val c = listOf(at - r * hx - f * hz, at + r * hx - f * hz, at + r * hx + f * hz, at - r * hx + f * hz)
        for (i in 0 until 4) {
            val a = c[i]; val b = c[(i + 1) % 4]
            val nrm = ((a + b) * 0.5 - at).normalized()
            val nx = nrm.x.toFloat(); val nz = (-nrm.y).toFloat()
            for ((v, h) in listOf(a to y0, b to y0, b to y1, a to y0, b to y1, a to y1)) o.add(v.x, h, -v.y, nx, 0f, nz, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
        }
        for (v in listOf(c[0], c[1], c[2], c[0], c[2], c[3])) o.add(v.x, y1, -v.y, 0f, 1f, 0f, color, 1f, 0.0, 0.0, JunctionMaterial.LIT)
    }

    /** Portique (ou mât en accotement) et position du panneau texturé, face au conducteur. */
    private fun gantry(o: VertexBuffer, s: SceneSign, p: JunctionPalette): SignQuad {
        val f = s.dir.normalized(); val r = f.rightNormal()
        val half = s.roadWidth / 2 + 1.4
        val panelW = (s.roadWidth * 0.55).coerceIn(4.5, 9.0)
        val panelH = (1.1 + 0.75 * maxOf(1, s.lines.size)).coerceAtMost(4.0)
        val bottom = s.elev + GANTRY_H
        val metal = p.barrier
        val center: V2
        if (s.gantry) {
            box(o, s.at - r * half, 0.45, 0.45, s.elev, bottom + panelH + 0.3, metal, f)
            box(o, s.at + r * half, 0.45, 0.45, s.elev, bottom + panelH + 0.3, metal, f)
            // Poutre du portique : boîte allongée en travers de la chaussée.
            box(o, s.at, half * 2, 0.35, bottom + panelH * 0.55, bottom + panelH * 0.55 + 0.35, metal, f)
            val lateral = when (s.side) { 1 -> half - panelW / 2 - 0.6; -1 -> -(half - panelW / 2 - 0.6); else -> 0.0 }
            center = s.at + r * lateral
        } else {
            val post = s.at + r * (s.side.coerceIn(-1, 1).let { if (it == 0) 1 else it } * (half + 0.4))
            box(o, post, 0.25, 0.25, s.elev, s.elev + 2.2 + panelH, metal, f)
            center = post
        }
        val baseY = if (s.gantry) bottom else s.elev + 2.2
        // Dos du panneau (épaisseur) juste derrière la face texturée.
        box(o, center + f * 0.12, panelW, 0.18, baseY, baseY + panelH, darken(metal, 0.7f), f)
        val bl = center - r * (panelW / 2); val br = center + r * (panelW / 2)
        val corners = listOf(
            floatArrayOf(bl.x.toFloat(), baseY.toFloat(), (-bl.y).toFloat()),
            floatArrayOf(br.x.toFloat(), baseY.toFloat(), (-br.y).toFloat()),
            floatArrayOf(br.x.toFloat(), (baseY + panelH).toFloat(), (-br.y).toFloat()),
            floatArrayOf(bl.x.toFloat(), (baseY + panelH).toFloat(), (-bl.y).toFloat()),
        )
        return SignQuad(corners, s, panelW, panelH)
    }

    private fun laneArrow(o: VertexBuffer, a: SceneLaneArrow, p: JunctionPalette) {
        val color = if (a.active) p.laneActive else p.marking
        val alpha = when { a.active -> 1f; a.valid -> 0.75f; else -> 0.3f }
        val f = a.dir.normalized(); val r = f.rightNormal()
        val turn = when (a.direction) {
            LaneDirection.LEFT, LaneDirection.UTURN -> -1.0
            LaneDirection.SLIGHT_LEFT, LaneDirection.MERGE_LEFT -> -0.5
            LaneDirection.STRAIGHT -> 0.0
            LaneDirection.SLIGHT_RIGHT, LaneDirection.MERGE_RIGHT -> 0.5
            LaneDirection.RIGHT -> 1.0
        }
        val y = a.elev + ROAD_LEVEL * 12 + MARK_UP + 0.01
        // Fût de 4 m puis pointe orientée selon la direction de la voie.
        val shaft0 = a.at - f * 3.0; val shaft1 = a.at + f * 1.0
        val w = 0.22
        val quad = listOf(shaft0 - r * w, shaft0 + r * w, shaft1 + r * w, shaft0 - r * w, shaft1 + r * w, shaft1 - r * w)
        val mat = if (a.active) JunctionMaterial.LANE else JunctionMaterial.GLOW
        for (v in quad) o.add(v.x, y, -v.y, 0f, 1f, 0f, color, alpha, 0.0, 0.0, mat)
        val hd = (f + r * turn).normalized(); val hr = hd.rightNormal()
        val tip = shaft1 + hd * 1.6
        for (v in listOf(shaft1 - hr * 0.75, shaft1 + hr * 0.75, tip)) o.add(v.x, y, -v.y, 0f, 1f, 0f, color, alpha, 0.0, 0.0, mat)
    }

    /** Trajectoire : halo large puis ruban à chevrons animés (v = abscisse locale), et pointe de flèche en fin de trajectoire. */
    private fun path(t: VertexBuffer, path: ScenePath, p: JunctionPalette) {
        val line = path.line
        val endIdx = line.s.indexOfFirst { it >= path.endS }.let { if (it < 0) line.pts.lastIndex else it }
        val pts = line.pts.subList(0, endIdx + 1).toMutableList()
        val lat = path.lateral.subList(0, endIdx + 1).toMutableList()
        val el = path.elev.subList(0, endIdx + 1).toMutableList()
        // Dernier point exactement à endS.
        pts[pts.lastIndex] = line.pointAt(path.endS)
        if (pts.size < 2) return
        val dense = Polyline2(pts)
        // Rééchantillonné tous les 3 m : ruban lisse dans les bretelles courbes.
        val n = (dense.length / 3.0).toInt().coerceAtLeast(2)
        val sp = (0..n).map { dense.length * it / n }
        val rp = sp.map { dense.pointAt(it) }
        val interp = { arr: List<Double>, s: Double ->
            val i = dense.s.indexOfFirst { it >= s }.let { if (it <= 0) 1 else it }.coerceAtMost(arr.lastIndex)
            val a = dense.s[i - 1]; val b = dense.s[i]
            val k = ((s - a) / (b - a).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
            arr[i - 1] + (arr[i] - arr[i - 1]) * k
        }
        val rl = sp.map { interp(lat, it) }; val re = sp.map { interp(el, it) + PATH_UP }
        ribbon(t, rp, { PATH_GLOW_WIDTH }, { re[it] - 0.04 }, { rl[it] }, { p.pathGlow }, 0.28f, JunctionMaterial.GLOW)
        ribbon(t, rp, { PATH_WIDTH }, { re[it] }, { rl[it] }, { p.path }, 1f, JunctionMaterial.PATH)
        // Pointe.
        val tipBase = rp.last(); val d = (rp.last() - rp[rp.lastIndex - 1]).normalized(); val r = d.rightNormal()
        val shift = r * rl.last()
        val b0 = tipBase + shift - r * 3.2; val b1 = tipBase + shift + r * 3.2; val tip = tipBase + shift + d * 6.5
        val y = re.last()
        for (v in listOf(b0, b1, tip)) t.add(v.x, y, -v.y, 0f, 1f, 0f, p.path, 1f, 0.5, path.endS, JunctionMaterial.GLOW)
    }

    /** Points de la trajectoire (avec décalage de voie) tous les [PATH_STEP] m, de 0 à la fin de la fenêtre. */
    private fun samplePath(path: ScenePath): FloatArray {
        val line = path.line
        val n = (line.length / PATH_STEP).toInt() + 1
        val out = FloatArray(n * 3)
        for (k in 0 until n) {
            val s = k * PATH_STEP
            val c = line.pointAt(s); val r = line.tangentAt(s).rightNormal()
            val lat = JunctionSceneBuilder.elevAt(path, path.lateral, s)
            val p = c + r * lat
            out[k * 3] = p.x.toFloat(); out[k * 3 + 1] = JunctionSceneBuilder.elevAt(path, path.elev, s).toFloat(); out[k * 3 + 2] = (-p.y).toFloat()
        }
        return out
    }

    internal fun signedArea(ring: List<V2>): Double {
        var a = 0.0
        for (i in ring.indices) { val p = ring[i]; val q = ring[(i + 1) % ring.size]; a += p.x * q.y - q.x * p.y }
        return a / 2
    }

    /** Triangulation par oreilles d'un polygone simple (anneau ouvert) ; renvoie les sommets des triangles. */
    internal fun triangulate(poly: List<V2>): List<V2> {
        val ring = (if (poly.size > 3 && poly.first().dist(poly.last()) < 1e-6) poly.dropLast(1) else poly).let { if (signedArea(it) < 0) it.reversed() else it }
        if (ring.size < 3) return emptyList()
        val idx = ring.indices.toMutableList()
        val out = ArrayList<V2>()
        var guard = 0
        while (idx.size > 3 && guard++ < 10_000) {
            var cut = false
            for (k in idx.indices) {
                val a = ring[idx[(k + idx.size - 1) % idx.size]]; val b = ring[idx[k]]; val c = ring[idx[(k + 1) % idx.size]]
                val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
                if (cross <= 1e-9) continue
                val inside = idx.any { j -> val q = ring[j]; q != a && q != b && q != c && inTriangle(q, a, b, c) }
                if (inside) continue
                out += a; out += b; out += c
                idx.removeAt(k)
                cut = true
                break
            }
            if (!cut) break
        }
        if (idx.size == 3) { out += ring[idx[0]]; out += ring[idx[1]]; out += ring[idx[2]] }
        return out
    }

    private fun inTriangle(p: V2, a: V2, b: V2, c: V2): Boolean {
        fun s(p1: V2, p2: V2, p3: V2) = (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
        val d1 = s(p, a, b); val d2 = s(p, b, c); val d3 = s(p, c, a)
        val neg = d1 < 0 || d2 < 0 || d3 < 0; val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    internal fun darken(c: Int, k: Float): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * k).toInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    /** Chevron du véhicule (repère local : avant = −Z), 4,6 m de long. */
    fun vehicle(palette: JunctionPalette): FloatArray {
        val b = VertexBuffer(64)
        val body = if (palette.night) 0xFFFFFFFF.toInt() else 0xFF1B1530.toInt()
        val pts = listOf(V2(0.0, 2.6), V2(1.7, -2.0), V2(0.0, -1.0), V2(-1.7, -2.0))
        val y = 0.35
        for (v in listOf(pts[0], pts[1], pts[2], pts[0], pts[2], pts[3])) b.add(v.x, y, -v.y, 0f, 1f, 0f, body, 1f, 0.0, 0.0, JunctionMaterial.GLOW)
        // Liseré à la teinte de la trajectoire, légèrement plus large et dessous.
        val big = pts.map { it * 1.25 }
        for (v in listOf(big[0], big[1], big[2], big[0], big[2], big[3])) b.add(v.x, y - 0.05, -v.y, 0f, 1f, 0f, palette.pathGlow, 1f, 0.0, 0.0, JunctionMaterial.GLOW)
        return b.trimmed()
    }
}
