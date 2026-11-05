package fr.roadly.core.map.junction

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionPhase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

/** État dynamique de la vue, mis à jour à chaque position (thread UI → thread GL). */
data class JunctionDynamics(
    val phase: JunctionPhase,
    val kind: JunctionKind,
    val complexity: Int,
    val vehicleAlongM: Double,
    val speedKmh: Double?,
    val reveal: Float,
    val degraded: Boolean,
)

/**
 * Rendu OpenGL ES 3.0 de la vue de carrefour : ciel en dégradé, sol quadrillé, routes, marquages, ponts, bâtiments, portiques,
 * trajectoire à chevrons animés, flèches de voies, numéros de sortie (panneaux toujours face caméra) et véhicule.
 * Deux appels de dessin pour toute la géométrie statique (opaque puis transparente) ; textures de panneaux faites une fois par scène.
 * Toutes les méthodes s'exécutent sur le thread GL.
 */
internal class JunctionRenderer {
    private var program = 0
    private var skyProgram = 0
    private var texProgram = 0
    private var opaqueVbo = 0
    private var opaqueCount = 0
    private var transparentVbo = 0
    private var transparentCount = 0
    private var vehicleVbo = 0
    private var vehicleCount = 0
    private var quadVbo = 0
    private val signTextures = ArrayList<Pair<Int, SignQuad>>()
    private val labelTextures = ArrayList<Pair<Int, SceneLabel>>()
    private var meshes: JunctionMeshes? = null
    private var camera: JunctionCamera? = null
    private val interpolator = VehicleInterpolator()
    private var palette = JunctionPalette.NIGHT
    private var width = 1
    private var height = 1
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val model = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(4 * 5 * 6).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var startNs = System.nanoTime()
    private var lastFrameNs = 0L
    private var dynamics: JunctionDynamics? = null
    private var lastAlong = Double.NaN

    /** Caméra encore en mouvement (rendu continu nécessaire). */
    val animating: Boolean get() = (camera?.eyeSpeed ?: 0.0) > 0.05

    fun init() {
        program = link(VERTEX, FRAGMENT)
        skyProgram = link(SKY_VERTEX, SKY_FRAGMENT)
        texProgram = link(TEX_VERTEX, TEX_FRAGMENT)
        val ids = IntArray(4)
        GLES30.glGenBuffers(4, ids, 0)
        opaqueVbo = ids[0]; transparentVbo = ids[1]; vehicleVbo = ids[2]; quadVbo = ids[3]
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        startNs = System.nanoTime()
    }

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1); height = h.coerceAtLeast(1)
        GLES30.glViewport(0, 0, width, height)
    }

    fun setScene(m: JunctionMeshes, p: JunctionPalette) {
        palette = p
        meshes = m
        upload(opaqueVbo, m.opaque); opaqueCount = m.opaqueVertices
        upload(transparentVbo, m.transparent); transparentCount = m.transparentVertices
        val v = JunctionMeshBuilder.vehicle(p)
        upload(vehicleVbo, v); vehicleCount = v.size / VertexBuffer.STRIDE
        deleteTextures()
        for (q in m.signs) signTextures += texture(drawSign(q)) to q
        for (l in m.labels) labelTextures += texture(drawLabel(l, p)) to l
        camera = JunctionCamera(m.pathSamples, m.pathStep, m.maneuverS, m.roundaboutCenter)
        lastAlong = Double.NaN
    }

    fun setDynamics(d: JunctionDynamics) {
        dynamics = d
        val m = meshes ?: return
        if (d.vehicleAlongM != lastAlong) {
            lastAlong = d.vehicleAlongM
            interpolator.onFix(d.vehicleAlongM - m.alongOffsetM, d.speedKmh, (System.nanoTime() - startNs) / 1e9)
        }
    }

    fun release() {
        deleteTextures()
        GLES30.glDeleteBuffers(4, intArrayOf(opaqueVbo, transparentVbo, vehicleVbo, quadVbo), 0)
        GLES30.glDeleteProgram(program); GLES30.glDeleteProgram(skyProgram); GLES30.glDeleteProgram(texProgram)
    }

    private fun deleteTextures() {
        val all = (signTextures.map { it.first } + labelTextures.map { it.first }).toIntArray()
        if (all.isNotEmpty()) GLES30.glDeleteTextures(all.size, all, 0)
        signTextures.clear(); labelTextures.clear()
    }

    fun draw() {
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 1.0 / 60 else ((now - lastFrameNs) / 1e9).coerceIn(0.0, 0.25)
        lastFrameNs = now
        val time = ((now - startNs) / 1e9).toFloat()
        GLES30.glClearColor(Color.red(palette.skyHorizon) / 255f, Color.green(palette.skyHorizon) / 255f, Color.blue(palette.skyHorizon) / 255f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawSky()
        val m = meshes ?: return
        val cam = camera ?: return
        val d = dynamics ?: return
        val s = interpolator.current(time.toDouble(), dt)
        val pose = cam.update(JunctionCameraInput(d.phase, d.kind, d.complexity, s, d.speedKmh, d.reveal, dt))
        Matrix.perspectiveM(proj, 0, pose.fovDeg, width.toFloat() / height, 2f, 2600f)
        Matrix.setLookAtM(view, 0, pose.eye[0], pose.eye[1], pose.eye[2], pose.target[0], pose.target[1], pose.target[2], 0f, 1f, 0f)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

        GLES30.glUseProgram(program)
        uniforms(pose, time, s, d)
        // Opaques.
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        uniform1f("uClipBehind", 0f)
        drawVbo(opaqueVbo, opaqueCount, identity)
        // Panneaux texturés (opaques).
        drawSigns(pose)
        // Transparents : trajectoire et halo, sans écrire la profondeur.
        GLES30.glUseProgram(program)
        uniforms(pose, time, s, d)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        uniform1f("uClipBehind", 1f)
        drawVbo(transparentVbo, transparentCount, identity)
        uniform1f("uClipBehind", 0f)
        // Véhicule.
        val pos = FloatArray(3); val dir = FloatArray(3)
        cam.vehicleAt(s, pos, dir)
        val yaw = Math.toDegrees(kotlin.math.atan2(-dir[0].toDouble(), -dir[2].toDouble())).toFloat()
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, pos[0], pos[1] + 0.5f, pos[2])
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        GLES30.glDepthMask(true)
        drawVbo(vehicleVbo, vehicleCount, model)
        // Numéros de sortie, toujours lisibles : dessinés par-dessus la scène.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        drawLabels(pose)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun uniforms(pose: CameraPose, time: Float, s: Double, d: JunctionDynamics) {
        GLES30.glUniformMatrix4fv(loc("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform3f(loc("uEye"), pose.eye[0], pose.eye[1], pose.eye[2])
        val l = floatArrayOf(-0.35f, -0.85f, -0.4f); val n = sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2])
        GLES30.glUniform3f(loc("uLightDir"), l[0] / n, l[1] / n, l[2] / n)
        GLES30.glUniform3f(loc("uFog"), Color.red(palette.fog) / 255f, Color.green(palette.fog) / 255f, Color.blue(palette.fog) / 255f)
        GLES30.glUniform3f(loc("uGrid"), Color.red(palette.groundGrid) / 255f, Color.green(palette.groundGrid) / 255f, Color.blue(palette.groundGrid) / 255f)
        GLES30.glUniform1f(loc("uFogDensity"), if (palette.night) 1f / 900f else 1f / 1100f)
        GLES30.glUniform1f(loc("uTime"), time)
        GLES30.glUniform1f(loc("uVehicleS"), s.toFloat())
        // Mise en évidence : chevrons plus vifs et plus rapides à l'approche ; atténuée si la position est imprécise.
        val emphasis = when (d.phase) {
            JunctionPhase.PRECISE, JunctionPhase.MANEUVER -> 1f
            JunctionPhase.REINFORCE -> 0.75f
            else -> 0.5f
        } * (if (d.degraded) 0.5f else 1f)
        GLES30.glUniform1f(loc("uEmphasis"), emphasis)
        GLES30.glUniform1f(loc("uScroll"), if (d.phase >= JunctionPhase.PRECISE) 0.9f else 0.55f)
        GLES30.glUniform1f(loc("uNight"), if (palette.night) 1f else 0f)
    }

    private val locations = HashMap<String, Int>()
    private fun loc(name: String): Int = locations.getOrPut("$program/$name") { GLES30.glGetUniformLocation(program, name) }
    private fun uniform1f(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)

    private fun drawVbo(vbo: Int, count: Int, modelMatrix: FloatArray) {
        if (count == 0) return
        GLES30.glUniformMatrix4fv(loc("uModel"), 1, false, modelMatrix, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val stride = VertexBuffer.STRIDE * 4
        attrib(0, 3, stride, 0); attrib(1, 3, stride, 12); attrib(2, 4, stride, 24); attrib(3, 2, stride, 40); attrib(4, 1, stride, 48)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
        for (i in 0..4) GLES30.glDisableVertexAttribArray(i)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun attrib(index: Int, size: Int, stride: Int, offset: Int) {
        GLES30.glEnableVertexAttribArray(index)
        GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, stride, offset)
    }

    private fun drawSky() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(skyProgram)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uTop"), Color.red(palette.skyTop) / 255f, Color.green(palette.skyTop) / 255f, Color.blue(palette.skyTop) / 255f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uHorizon"), Color.red(palette.skyHorizon) / 255f, Color.green(palette.skyHorizon) / 255f, Color.blue(palette.skyHorizon) / 255f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawSigns(pose: CameraPose) {
        if (signTextures.isEmpty()) return
        GLES30.glUseProgram(texProgram)
        texUniforms(pose)
        for ((tex, q) in signTextures) {
            val c = q.corners
            quad(floatArrayOf(
                c[0][0], c[0][1], c[0][2], 0f, 1f, c[1][0], c[1][1], c[1][2], 1f, 1f, c[2][0], c[2][1], c[2][2], 1f, 0f,
                c[0][0], c[0][1], c[0][2], 0f, 1f, c[2][0], c[2][1], c[2][2], 1f, 0f, c[3][0], c[3][1], c[3][2], 0f, 0f,
            ), tex)
        }
    }

    private fun drawLabels(pose: CameraPose) {
        if (labelTextures.isEmpty()) return
        GLES30.glUseProgram(texProgram)
        texUniforms(pose)
        // Vecteurs droite / haut de la caméra (lignes de la matrice de vue).
        val rx = view[0]; val ry = view[4]; val rz = view[8]
        val ux = view[1]; val uy = view[5]; val uz = view[9]
        for ((tex, l) in labelTextures) {
            val cx = l.at.x.toFloat(); val cy = l.elev.toFloat(); val cz = (-l.at.y).toFloat()
            val dx = cx - pose.eye[0]; val dy = cy - pose.eye[1]; val dz = cz - pose.eye[2]
            // Taille constante à l'écran (≈ 6 % de la hauteur), un peu plus grande pour la sortie à prendre.
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            val h = dist * (if (l.target) 0.075f else 0.055f)
            fun p(a: Float, b: Float) = floatArrayOf(cx + (rx * a + ux * b) * h, cy + (ry * a + uy * b) * h, cz + (rz * a + uz * b) * h)
            val bl = p(-0.5f, -0.5f); val br = p(0.5f, -0.5f); val tr = p(0.5f, 0.5f); val tl = p(-0.5f, 0.5f)
            quad(floatArrayOf(
                bl[0], bl[1], bl[2], 0f, 1f, br[0], br[1], br[2], 1f, 1f, tr[0], tr[1], tr[2], 1f, 0f,
                bl[0], bl[1], bl[2], 0f, 1f, tr[0], tr[1], tr[2], 1f, 0f, tl[0], tl[1], tl[2], 0f, 0f,
            ), tex)
        }
    }

    private fun texUniforms(pose: CameraPose) {
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(texProgram, "uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(texProgram, "uEye"), pose.eye[0], pose.eye[1], pose.eye[2])
        GLES30.glUniform3f(GLES30.glGetUniformLocation(texProgram, "uFog"), Color.red(palette.fog) / 255f, Color.green(palette.fog) / 255f, Color.blue(palette.fog) / 255f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(texProgram, "uFogDensity"), if (palette.night) 1f / 1400f else 1f / 1600f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(texProgram, "uTex"), 0)
    }

    private fun quad(v: FloatArray, tex: Int) {
        quadBuffer.clear(); quadBuffer.put(v); quadBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.size * 4, quadBuffer, GLES30.GL_STREAM_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glDisableVertexAttribArray(0); GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun upload(vbo: Int, data: FloatArray) {
        val fb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun texture(bmp: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bmp.recycle()
        return ids[0]
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
            val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + GLES30.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES30.glCreateProgram()
        val v = compile(GLES30.GL_VERTEX_SHADER, vs); val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link: " + GLES30.glGetProgramInfoLog(p) }
        GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
        return p
    }

    /** Panneau de direction façon signalisation française : fond bleu (autoroute) ou vert, numéro de sortie, directions, cartouche. */
    private fun drawSign(q: SignQuad): Bitmap {
        val ppm = 64.0
        val w = (q.widthM * ppm).toInt().coerceIn(128, 1024)
        val h = (q.heightM * ppm).toInt().coerceIn(64, 512)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = q.sign
        val bg = when (s.style) { SignStyle.MOTORWAY -> 0xFF1D4FA3.toInt(); SignStyle.PRIMARY -> 0xFF1F7A47.toInt(); SignStyle.LOCAL -> Color.WHITE }
        val fg = if (s.style == SignStyle.LOCAL) Color.BLACK else Color.WHITE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = h * 0.08f
        paint.color = bg; c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = h * 0.035f; paint.color = fg
        val inset = h * 0.04f
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r * 0.8f, r * 0.8f, paint)
        paint.style = Paint.Style.FILL
        var x = h * 0.12f
        // Flèche de direction (orientée selon la manœuvre).
        val arrowSize = h * 0.42f
        val ax = if (s.side >= 0) w - h * 0.12f - arrowSize else x
        drawArrow(c, ax + arrowSize / 2, h * 0.5f, arrowSize, s.arrowDeg.toFloat(), fg)
        if (s.side < 0) x += arrowSize + h * 0.08f
        s.exitNumber?.let { num ->
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; typeface = Typeface.DEFAULT_BOLD; textSize = h * 0.22f }
            val label = "SORTIE $num"
            val tw = tp.measureText(label)
            val pill = RectF(x, h * 0.1f, x + tw + h * 0.16f, h * 0.38f)
            paint.color = fg; c.drawRoundRect(pill, h * 0.05f, h * 0.05f, paint)
            c.drawText(label, pill.left + h * 0.08f, pill.bottom - h * 0.07f, tp)
        }
        val textRight = if (s.side >= 0) ax - h * 0.08f else w - h * 0.12f
        var y = if (s.exitNumber != null) h * 0.58f else h * 0.36f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = h * (if (s.lines.size > 2) 0.16f else 0.2f) }
        for (line in s.lines) {
            var text = line
            while (tp.measureText(text) > textRight - x && text.length > 4) text = text.dropLast(2) + "…"
            c.drawText(text, x, y, tp)
            y += tp.textSize * 1.15f
        }
        s.branch?.let { ref ->
            val red = ref.trim().let { it.startsWith("A") || it.startsWith("E") || it.startsWith("N") }
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (red) 0xFFD6262F.toInt() else 0xFFF5C518.toInt() }
            val tpp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (red) Color.WHITE else Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = h * 0.17f }
            val tw = tpp.measureText(ref)
            val left = if (s.exitNumber != null) x + h * 0.02f + Paint().apply { textSize = h * 0.22f; typeface = Typeface.DEFAULT_BOLD }.measureText("SORTIE ${s.exitNumber}") + h * 0.3f else x
            val box = RectF(left, h * 0.12f, left + tw + h * 0.14f, h * 0.36f)
            if (box.right < textRight) {
                c.drawRoundRect(box, h * 0.03f, h * 0.03f, cp)
                c.drawText(ref, box.left + h * 0.07f, box.bottom - h * 0.06f, tpp)
            }
        }
        return bmp
    }

    private fun drawArrow(c: Canvas, cx: Float, cy: Float, size: Float, angleDeg: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        c.save()
        c.translate(cx, cy)
        c.rotate(angleDeg.coerceIn(-90f, 90f) * 0.8f)
        val s = size / 2
        val path = Path().apply {
            moveTo(0f, -s); lineTo(s * 0.7f, -s * 0.2f); lineTo(s * 0.22f, -s * 0.2f); lineTo(s * 0.22f, s)
            lineTo(-s * 0.22f, s); lineTo(-s * 0.22f, -s * 0.2f); lineTo(-s * 0.7f, -s * 0.2f); close()
        }
        c.drawPath(path, p)
        c.restore()
    }

    /** Pastille de numéro de sortie : la sortie à prendre à la teinte de la trajectoire, les autres en gris. */
    private fun drawLabel(l: SceneLabel, p: JunctionPalette): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (l.target) p.path else 0xFF5B5568.toInt()
        c.drawCircle(size / 2f, size / 2f, size * 0.46f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.06f; paint.color = Color.WHITE
        c.drawCircle(size / 2f, size / 2f, size * 0.42f, paint)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = size * 0.52f; textAlign = Paint.Align.CENTER }
        c.drawText(l.text, size / 2f, size / 2f - (tp.ascent() + tp.descent()) / 2, tp)
        return bmp
    }

    private companion object {
        const val VERTEX = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec4 aColor;
layout(location=3) in vec2 aUv;
layout(location=4) in float aMat;
uniform mat4 uViewProj;
uniform mat4 uModel;
out vec3 vWorld; out vec3 vNormal; out vec4 vColor; out vec2 vUv; out float vMat;
void main() {
  vec4 w = uModel * vec4(aPos, 1.0);
  vWorld = w.xyz; vNormal = mat3(uModel) * aNormal; vColor = aColor; vUv = aUv; vMat = aMat;
  gl_Position = uViewProj * w;
}"""
        const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vWorld; in vec3 vNormal; in vec4 vColor; in vec2 vUv; in float vMat;
uniform vec3 uEye; uniform vec3 uLightDir; uniform vec3 uFog; uniform vec3 uGrid;
uniform float uFogDensity; uniform float uTime; uniform float uVehicleS; uniform float uEmphasis; uniform float uScroll;
uniform float uClipBehind; uniform float uNight;
out vec4 frag;
void main() {
  int m = int(vMat + 0.5);
  vec3 c = vColor.rgb; float a = vColor.a;
  if (uClipBehind > 0.5 && vUv.y < uVehicleS - 3.0) discard;
  if (m == 0) {
    vec3 n = normalize(vNormal);
    float diff = max(dot(n, -uLightDir), 0.0);
    float hemi = 0.5 + 0.5 * n.y;
    c *= 0.42 + 0.33 * hemi + 0.42 * diff;
  } else if (m == 1) {
    float across = abs(vUv.x - 0.5) * 2.0;
    float chev = fract((vUv.y - across * 1.8) / 9.0 - uTime * uScroll);
    float band = smoothstep(0.0, 0.06, chev) * (1.0 - smoothstep(0.32, 0.4, chev));
    c = mix(c, vec3(1.0), band * 0.6 * uEmphasis);
    a *= 1.0 - smoothstep(0.8, 1.0, across);
    float ahead = clamp((vUv.y - uVehicleS) / 6.0, 0.0, 1.0);
    a *= ahead;
  } else if (m == 2) {
    if (fract((vUv.x + vUv.y) * 0.3) > 0.45) discard;
  } else if (m == 3) {
    if (fract(vUv.y / 12.0) > 0.38) discard;
  } else if (m == 5) {
    c *= 0.85 + 0.25 * sin(uTime * 5.0);
  } else if (m == 6) {
    vec2 g = abs(fract(vWorld.xz / 30.0) - 0.5);
    float line = 1.0 - smoothstep(0.0, 0.015, min(g.x, g.y));
    c = mix(c, uGrid, line * 0.7);
  }
  float dist = length(vWorld - uEye);
  float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
  c = mix(c, uFog, clamp(fog, 0.0, 1.0));
  frag = vec4(c, a);
}"""
        const val SKY_VERTEX = """#version 300 es
out float vY;
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
  vY = p.y * 0.5 + 0.5;
  gl_Position = vec4(p, 0.999, 1.0);
}"""
        const val SKY_FRAGMENT = """#version 300 es
precision mediump float;
in float vY; uniform vec3 uTop; uniform vec3 uHorizon; out vec4 frag;
void main() { frag = vec4(mix(uHorizon, uTop, smoothstep(0.35, 1.0, vY)), 1.0); }"""
        const val TEX_VERTEX = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec2 aUv;
uniform mat4 uViewProj;
out vec2 vUv; out vec3 vWorld;
void main() { vUv = aUv; vWorld = aPos; gl_Position = uViewProj * vec4(aPos, 1.0); }"""
        const val TEX_FRAGMENT = """#version 300 es
precision mediump float;
in vec2 vUv; in vec3 vWorld;
uniform sampler2D uTex; uniform vec3 uEye; uniform vec3 uFog; uniform float uFogDensity;
out vec4 frag;
void main() {
  vec4 t = texture(uTex, vUv);
  if (t.a < 0.02) discard;
  float dist = length(vWorld - uEye);
  float fog = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
  frag = vec4(mix(t.rgb, uFog, clamp(fog, 0.0, 1.0)), t.a);
}"""
    }
}
