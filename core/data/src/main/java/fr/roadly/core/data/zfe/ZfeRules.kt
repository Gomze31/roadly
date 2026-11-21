package fr.roadly.core.data.zfe

import fr.roadly.core.model.CritAir
import fr.roadly.core.model.GeoPoint
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Règles ZFE : verdict d'un véhicule, horaires, zones traversées par un itinéraire. Logique pure (testée en JVM). */
object ZfeRules {

    /**
     * Verdict pour [critAir] (null = non renseigné) dans [rule] à [at].
     * Une restriction est en vigueur si la réglementation est active à cette date et que la catégorie a une vignette interdite.
     */
    fun verdict(rule: ZfeRule, vehicle: ZfeVehicleClass, critAir: CritAir?, at: LocalDateTime): ZfeVerdict {
        if (!rule.activeOn(at.toLocalDate())) return ZfeVerdict.NO_RESTRICTION
        val limit = restrictionRank(rule.restriction[vehicle]) ?: return ZfeVerdict.NO_RESTRICTION
        val rank = critAir?.rank() ?: return ZfeVerdict.CRITAIR_UNKNOWN
        if (rank < limit) return ZfeVerdict.ALLOWED
        return if (Hours.applies(rule.hours[vehicle], at) == false) ZfeVerdict.FORBIDDEN_OTHER_HOURS else ZfeVerdict.FORBIDDEN
    }

    /** Ordre de gravité, du plus grave au moins grave (le plus grave l'emporte quand plusieurs règles couvrent la même zone). */
    private val severity = listOf(ZfeVerdict.FORBIDDEN, ZfeVerdict.CRITAIR_UNKNOWN, ZfeVerdict.FORBIDDEN_OTHER_HOURS, ZfeVerdict.ALLOWED, ZfeVerdict.NO_RESTRICTION)

    /**
     * ZFE traversées par [route] (points échantillonnés, préfiltre par emprise), une par nom d'agglomération, en ne gardant
     * que les réglementations actives à [at] qui restreignent la catégorie ; triées par ordre de traversée.
     */
    fun crossings(route: List<GeoPoint>, rules: List<ZfeRule>, vehicle: ZfeVehicleClass, critAir: CritAir?, at: LocalDateTime): List<ZfeCrossing> {
        if (route.isEmpty()) return emptyList()
        val active = rules.filter { it.activeOn(at.toLocalDate()) && restrictionRank(it.restriction[vehicle]) != null }
        if (active.isEmpty()) return emptyList()
        val sampled = sample(route)
        val firstHit = HashMap<ZfeRule, Int>()
        for (r in active) {
            val idx = sampled.indexOfFirst { p -> inBox(p, r) && r.polygons.any { contains(it, p) } }
            if (idx >= 0) firstHit[r] = idx
        }
        return firstHit.entries.groupBy { it.key.name }.map { (_, hits) ->
            val best = hits.minBy { severity.indexOf(verdict(it.key, vehicle, critAir, at)) }
            val r = best.key
            hits.minOf { it.value } to ZfeCrossing(r, verdict(r, vehicle, critAir, at), r.restriction[vehicle], r.hours[vehicle])
        }.sortedBy { it.first }.map { it.second }
    }

    /** Un point tous les ~50 m au plus (sans perdre l'entrée d'une petite zone). */
    internal fun sample(route: List<GeoPoint>, stepM: Double = 50.0): List<GeoPoint> {
        if (route.size < 2) return route
        val out = ArrayList<GeoPoint>(route.size)
        out += route.first()
        for (i in 1 until route.size) {
            val a = route[i - 1]; val b = route[i]
            val d = a.distanceTo(b)
            val n = (d / stepM).toInt()
            for (k in 1..n) { val t = k / (n + 1.0); out += GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) }
            out += b
        }
        return out
    }

    private fun inBox(p: GeoPoint, r: ZfeRule) = p.lat in r.minLat..r.maxLat && p.lon in r.minLon..r.maxLon

    internal fun contains(poly: ZfePolygon, p: GeoPoint): Boolean = inRing(poly.outer, p) && poly.holes.none { inRing(it, p) }

    /** Lancer de rayon (pair-impair) en coordonnées lon/lat : suffisant à l'échelle d'une agglomération. */
    private fun inRing(ring: List<GeoPoint>, p: GeoPoint): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]; val b = ring[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) && p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Sous-ensemble d'`opening_hours` (OSM) rencontré dans la base : « 24/7 », « Mo-Fr 08:00-20:00 », plusieurs plages
     * « 07:00-9:00,16:00-19:00 », plusieurs règles séparées par « ; », « : » facultatif. Les exceptions (« PH off »,
     * « May 1 off ») sont ignorées. Valeur absente ou non comprise → null (le verdict reste « interdit », par prudence).
     */
    object Hours {
        private val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

        /** true = restriction en vigueur à [at] ; false = hors horaires ; null = horaires inconnus ou non interprétés. */
        fun applies(hours: String?, at: LocalDateTime): Boolean? {
            val h = hours?.trim()?.ifEmpty { null } ?: return null
            if (h == "24/7") return true
            val rules = h.split(';').map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith(" off", ignoreCase = true) }
            if (rules.isEmpty()) return null
            var understood = false
            for (rule in rules) {
                val parsed = parseRule(rule) ?: continue
                understood = true
                val (dayset, ranges) = parsed
                if (at.dayOfWeek !in dayset) continue
                val t = at.toLocalTime()
                if (ranges.any { (from, to) -> inRange(t, from, to) }) return true
            }
            return if (understood) false else null
        }

        private fun parseRule(rule: String): Pair<Set<DayOfWeek>, List<Pair<LocalTime, LocalTime?>>>? {
            val cleaned = rule.replace(" : ", " ").replace(Regex("\\s+"), " ").trim()
            val space = cleaned.indexOf(' ')
            val (dayPart, timePart) = if (space > 0 && cleaned[0].isLetter()) cleaned.substring(0, space) to cleaned.substring(space + 1) else "Mo-Su" to cleaned
            val dayset = parseDays(dayPart) ?: return null
            val ranges = timePart.split(',').map { it.trim() }.map { r ->
                val (a, b) = r.split('-').takeIf { it.size == 2 } ?: return null
                val from = time(a) ?: return null
                val to = if (b.trim() == "24:00") null else (time(b) ?: return null)
                from to to
            }
            return dayset to ranges
        }

        private fun parseDays(s: String): Set<DayOfWeek>? {
            val out = LinkedHashSet<DayOfWeek>()
            for (part in s.split(',')) {
                val ends = part.split('-')
                val a = days.indexOf(ends[0].trim()).takeIf { it >= 0 } ?: return null
                val b = if (ends.size == 2) days.indexOf(ends[1].trim()).takeIf { it >= 0 } ?: return null else a
                var i = a
                while (true) { out += DayOfWeek.of(i + 1); if (i == b) break; i = (i + 1) % 7 }
            }
            return out
        }

        private fun time(s: String): LocalTime? {
            val (h, m) = s.trim().split(':').takeIf { it.size == 2 } ?: return null
            return runCatching { LocalTime.of(h.toInt() % 24, m.toInt()) }.getOrNull()
        }

        /** [to] null = jusqu'à minuit ; une plage « 00:00-00:00 » couvre toute la journée. */
        private fun inRange(t: LocalTime, from: LocalTime, to: LocalTime?): Boolean = when {
            to == null -> !t.isBefore(from)
            to == from -> true
            to.isAfter(from) -> !t.isBefore(from) && t.isBefore(to)
            else -> !t.isBefore(from) || t.isBefore(to)
        }
    }
}
