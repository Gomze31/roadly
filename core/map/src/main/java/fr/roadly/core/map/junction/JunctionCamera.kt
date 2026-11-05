package fr.roadly.core.map.junction

import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionPhase
import kotlin.math.exp
import kotlin.math.sqrt

/** Pose de caméra : œil, point visé (coordonnées GL), champ vertical (°). */
data class CameraPose(val eye: FloatArray, val target: FloatArray, val fovDeg: Float) {
    override fun equals(other: Any?) = other is CameraPose && eye.contentEquals(other.eye) && target.contentEquals(other.target) && fovDeg == other.fovDeg
    override fun hashCode() = 31 * eye.contentHashCode() + target.contentHashCode()
}

data class JunctionCameraInput(
    val phase: JunctionPhase,
    val kind: JunctionKind,
    val complexity: Int,
    /** Abscisse locale du véhicule sur la trajectoire de la scène. */
    val vehicleS: Double,
    val speedKmh: Double?,
    val reveal: Float,
    val dtS: Double,
)

/**
 * Caméra de la vue de carrefour (Kotlin pur) : posée derrière le véhicule sur la trajectoire, elle vise un point devant lui.
 * Loin du carrefour, haute et longue (vue d'ensemble, plus large pour un échangeur ou un rond-point) ; elle descend vers une
 * caméra de poursuite à l'approche, puis remonte doucement après la manœuvre. Œil et cible suivent des ressorts amortis
 * critiques : pas de rotation brusque, pas de saut de cadrage quand l'étape change.
 */
class JunctionCamera(
    private val samples: FloatArray,
    private val step: Double,
    private val maneuverS: Double,
    private val roundaboutCenter: FloatArray?,
) {
    private val eye = FloatArray(3)
    private val eyeV = FloatArray(3)
    private val target = FloatArray(3)
    private val targetV = FloatArray(3)
    private var fov = 52.0
    private var initialized = false

    /** Hauteur, recul et visée (m) d'une étape, avant multiplicateurs. */
    data class Rig(val back: Double, val height: Double, val ahead: Double)

    fun rigFor(input: JunctionCameraInput): Rig {
        val d = maneuverS - input.vehicleS
        var rig = when (input.phase) {
            JunctionPhase.PREPARE, JunctionPhase.REVEAL -> Rig(70.0, 62.0, (d * 0.75 + 50.0).coerceIn(120.0, 420.0))
            JunctionPhase.REINFORCE -> Rig(55.0, 44.0, (d * 0.7 + 50.0).coerceIn(100.0, 300.0))
            JunctionPhase.PRECISE -> Rig(42.0, 27.0, (d + 40.0).coerceIn(70.0, 180.0))
            JunctionPhase.MANEUVER -> Rig(32.0, 17.0, 75.0)
            JunctionPhase.RECOVER -> Rig(40.0, 25.0, 100.0)
        }
        // Rond-point : plus haut (toutes les branches visibles). Échangeur / complexité : vue plus large.
        if (input.kind == JunctionKind.ROUNDABOUT) rig = rig.copy(back = rig.back * 0.85, height = rig.height * 1.45)
        if (input.kind == JunctionKind.INTERCHANGE || input.kind == JunctionKind.MOTORWAY_FORK) rig = rig.copy(height = rig.height * 1.12)
        val c = input.complexity.coerceIn(0, 3)
        rig = rig.copy(back = rig.back * (1 + 0.08 * c), height = rig.height * (1 + 0.12 * c))
        // Vitesse : recul et visée allongés au-delà de 50 km/h.
        val v = ((input.speedKmh ?: 0.0) - 50.0).coerceAtLeast(0.0) / 250.0
        rig = rig.copy(back = rig.back * (1 + v), ahead = rig.ahead * (1 + v))
        // Apparition : la caméra arrive d'un peu plus haut et descend pendant l'étape REVEAL.
        if (input.phase == JunctionPhase.REVEAL || input.phase == JunctionPhase.PREPARE) rig = rig.copy(height = rig.height * (1 + 0.5 * (1 - input.reveal)))
        return rig
    }

    private fun sample(s: Double, out: FloatArray) {
        val n = samples.size / 3
        if (n == 0) { out.fill(0f); return }
        if (n == 1) { for (k in 0..2) out[k] = samples[k]; return }
        val f = s / step
        val i = f.toInt().coerceIn(0, n - 2)
        val t = (f - i).toFloat()
        // Au-delà des extrémités : prolongement selon la tangente du premier / dernier segment.
        for (k in 0..2) {
            val a = samples[i * 3 + k]; val b = samples[(i + 1) * 3 + k]
            out[k] = if (k == 1) a + (b - a) * t.coerceIn(0f, 1f) else a + (b - a) * t
        }
    }

    fun update(input: JunctionCameraInput): CameraPose {
        val rig = rigFor(input)
        val e = FloatArray(3); val tg = FloatArray(3)
        sample(input.vehicleS - rig.back, e)
        sample(input.vehicleS + rig.ahead, tg)
        val vehicle = FloatArray(3).also { sample(input.vehicleS, it) }
        e[1] = vehicle[1] + rig.height.toFloat()
        tg[1] = tg[1] + 1.0f
        // Rond-point : la visée glisse vers le centre de l'anneau tant que la sortie n'est pas prise.
        val rc = roundaboutCenter
        if (rc != null && input.kind == JunctionKind.ROUNDABOUT && input.phase != JunctionPhase.RECOVER) {
            val w = when (input.phase) { JunctionPhase.MANEUVER -> 0.35f; JunctionPhase.PRECISE -> 0.55f; else -> 0.7f }
            tg[0] += (rc[0] - tg[0]) * w; tg[2] += (rc[2] - tg[2]) * w
        }
        val targetFov = if (input.phase <= JunctionPhase.REINFORCE) 54.0 else 50.0
        if (!initialized) {
            e.copyInto(eye); tg.copyInto(target); eyeV.fill(0f); targetV.fill(0f); fov = targetFov
            initialized = true
        } else {
            val tau = when (input.phase) { JunctionPhase.MANEUVER -> 0.45; JunctionPhase.PRECISE -> 0.6; else -> 0.85 }
            var left = input.dtS.coerceIn(0.0, 0.5)
            // Sous-pas : stabilité du ressort même après une image lente.
            while (left > 1e-6) {
                val dt = minOf(left, 1.0 / 60.0)
                spring(eye, eyeV, e, tau, dt)
                spring(target, targetV, tg, tau * 0.8, dt)
                left -= dt
            }
            fov += (targetFov - fov) * (1 - exp(-input.dtS / 0.8))
        }
        return CameraPose(eye.copyOf(), target.copyOf(), fov.toFloat())
    }

    /** Ressort amorti critique (ω = 2 / τ), intégration semi-implicite. */
    private fun spring(x: FloatArray, v: FloatArray, goal: FloatArray, tau: Double, dt: Double) {
        val w = 2.0 / tau
        for (k in 0..2) {
            val a = -2 * w * v[k] - w * w * (x[k] - goal[k])
            v[k] = (v[k] + a * dt).toFloat()
            x[k] = (x[k] + v[k] * dt).toFloat()
        }
    }

    /** Vitesse de l'œil (m/s) : sert à savoir si la caméra est posée (rendu à la demande). */
    val eyeSpeed: Double get() = sqrt((eyeV[0] * eyeV[0] + eyeV[1] * eyeV[1] + eyeV[2] * eyeV[2]).toDouble())

    /** Position (coordonnées GL) et tangente du véhicule à l'abscisse [s]. */
    fun vehicleAt(s: Double, pos: FloatArray, dir: FloatArray) {
        sample(s, pos)
        val a = FloatArray(3); val b = FloatArray(3)
        sample(s - 3.0, a); sample(s + 3.0, b)
        val dx = b[0] - a[0]; val dz = b[2] - a[2]
        val l = sqrt((dx * dx + dz * dz).toDouble()).toFloat().coerceAtLeast(1e-4f)
        dir[0] = dx / l; dir[1] = 0f; dir[2] = dz / l
    }
}

/**
 * Position du véhicule pour le rendu : les positions arrivent ≈ 1 fois par seconde ; entre deux, l'abscisse est extrapolée à la
 * vitesse courante (au plus 1,5 s) puis rattrapée en douceur, sans jamais reculer visiblement.
 */
class VehicleInterpolator {
    private var shownS = Double.NaN
    private var fixS = 0.0
    private var fixAtS = 0.0
    private var mps = 0.0

    fun onFix(s: Double, speedKmh: Double?, nowS: Double) {
        fixS = s; fixAtS = nowS; mps = (speedKmh ?: 0.0).coerceAtLeast(0.0) / 3.6
        if (shownS.isNaN() || kotlin.math.abs(s - shownS) > 80.0) shownS = s
    }

    fun current(nowS: Double, dtS: Double): Double {
        if (shownS.isNaN()) return fixS
        val predicted = fixS + mps * (nowS - fixAtS).coerceIn(0.0, 1.5)
        val k = 1 - exp(-dtS / 0.35)
        val next = shownS + (predicted - shownS) * k
        // Pas de recul visible (un fix légèrement en arrière est absorbé), sauf grand écart traité dans onFix.
        shownS = if (next < shownS && shownS - predicted < 5.0) shownS else next
        return shownS
    }
}
