package fr.roadly.feature.route

import fr.roadly.core.designsystem.theme.LocalUiScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.theme.ButtonLabel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.StatValue
import fr.roadly.core.designsystem.theme.softShadow
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.routing.RouteOption
import java.time.ZoneId
import kotlin.math.roundToInt

/** Pilule de l'en-tête : marque, destination, étoile, horloge, fermer. */
@Composable
internal fun RouteTopBar(
    destinationName: String,
    favorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onPickTime: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoadlyShapes.Panel, contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoadlyShapes.Pill).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = c.onBrandAuto, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(RoadlySpacing.sm))
            Text(destinationName, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (favorite != null) {
                HeaderAction(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    stringResource(if (favorite) R.string.route_favorite_remove else R.string.route_favorite_add),
                    tint = if (favorite) c.warning else c.textSecondary, onClick = onToggleFavorite,
                )
            }
            HeaderAction(Icons.Default.Schedule, stringResource(R.string.route_pick_time), tint = c.textSecondary, onClick = onPickTime)
            Box(
                Modifier.size(36.dp).clip(RoadlyShapes.Pill).background(c.textSecondary.copy(alpha = 0.35f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.route_back), tint = c.textPrimary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoadlyShapes.Pill).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** Pilule « PARTIR DE · Ma position » avec le bouton d'inversion. */
@Composable
internal fun RouteFromBar(originLabel: String, onSwap: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoadlyShapes.Panel, contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = c.brand, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(RoadlySpacing.sm))
            Column(Modifier.weight(1f)) {
                OverlineText(stringResource(R.string.route_from), color = c.brand, dash = false)
                Text(originLabel, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onSwap != null) {
                Box(
                    Modifier.size(44.dp).clip(RoadlyShapes.Pill).background(c.surfaceElevated).clickable(role = Role.Button, onClick = onSwap),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.route_swap), tint = c.brand, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

/** Écart de durée avec l'itinéraire choisi, en minutes arrondies (« +2 min », « −3 min ») ; null si moins d'une minute. */
internal fun durationDeltaMinutes(option: RouteOption, active: RouteOption): Int? =
    ((option.route.durationSeconds - active.route.durationSeconds) / 60.0).roundToInt().takeIf { it != 0 }

/** Panneau « CHOIX ROADLY » : durée en grand, arrivée, distance, pastilles, alternatives avec écart, arrêts. */
@Composable
internal fun RouteChoicePanel(
    s: RouteUiState.Content,
    detailsOpen: Boolean,
    onToggleDetails: () -> Unit,
    onSelect: (String) -> Unit,
    onPickTime: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreferences: (RoutePreferences) -> Unit,
    onAddStop: () -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onRemoveStop: (Int) -> Unit,
    onOptimize: () -> Unit,
    modifier: Modifier = Modifier,
    onZfe: (fr.roadly.core.data.zfe.ZfeCrossing) -> Unit = {},
    onTraffic: (List<fr.roadly.core.data.traffic.TrafficHit>) -> Unit = {},
) {
    val c = LocalRoadlyColors.current
    val active = s.options.firstOrNull { it.route.id == s.activeId } ?: s.options.first()
    val zone = ZoneId.systemDefault()
    GlassPanel(modifier.fillMaxWidth(), shape = RoadlyShapes.Panel, contentPadding = 0.dp) {
        Column {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(c.brand.copy(alpha = if (c.isDark) 0.14f else 0.08f), Color.Transparent)))
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = c.brand, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    OverlineText(stringResource(R.string.route_choice_title), color = c.brandBright, dash = false, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clip(RoadlyShapes.Pill).clickable(role = Role.Button, onClick = onToggleDetails), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.route_details), tint = if (detailsOpen) c.brand else c.textSecondary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Formatters.duration(active.route.durationSeconds), style = MaterialTheme.typography.displayLarge, color = c.textPrimary)
                    Spacer(Modifier.width(18.dp))
                    HeadStat(stringResource(R.string.route_arrival_label), Formatters.clockTime(active.arrivalEpochMs, zone), Modifier.weight(1f))
                    HeadStat(stringResource(R.string.route_distance_label), Formatters.distance(active.route.distanceMeters), alignEnd = true)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Incidents officiels en premier (comme « Incident détecté » de la référence), puis la ZFE.
                    s.traffic[active.route.id]?.filter { it.event.kind.notable }?.takeIf { it.isNotEmpty() }?.let { hits ->
                        StatusPill(
                            if (hits.size == 1) stringResource(R.string.route_incident_one) else stringResource(R.string.route_incident_n, hits.size),
                            Icons.Default.Warning, c.accent, onClick = { onTraffic(hits) },
                        )
                    }
                    // Route fermée (Bison Futé) contournée automatiquement : dit pourquoi le tracé peut sembler plus long.
                    if (active.route.avoidedClosures > 0) {
                        StatusPill(
                            pluralStringResource(R.plurals.route_closures_avoided, active.route.avoidedClosures, active.route.avoidedClosures),
                            Icons.Default.Block, c.success,
                        )
                    }
                    // Rappel des options d'évitement actives (pour ne jamais rouler sans savoir qu'on évite les péages, par exemple).
                    if (s.preferences.any) {
                        StatusPill(avoidSummary(s.preferences), Icons.Default.Tune, c.brandBright, onClick = onToggleDetails)
                    }
                    // ZFE : une interdiction doit se voir sans faire défiler.
                    s.zfe[active.route.id]?.firstOrNull { it.verdict != fr.roadly.core.data.zfe.ZfeVerdict.NO_RESTRICTION }?.let { z ->
                        val (text, color) = zfePill(z)
                        StatusPill(text, Icons.Default.Eco, color, onClick = { onZfe(z) })
                    }
                    val arrive = s.arriveAtEpochMs
                    StatusPill(
                        when {
                            arrive != null -> stringResource(R.string.route_arrive_at, Formatters.clockTime(arrive, zone), Formatters.clockTime(s.departAtEpochMs, zone))
                            s.departAtEpochMs - System.currentTimeMillis() < 60_000 -> stringResource(R.string.route_depart_now)
                            else -> stringResource(R.string.route_depart_at, Formatters.clockTime(s.departAtEpochMs, zone))
                        },
                        Icons.Default.Schedule, c.brandBright, onClick = onPickTime,
                    )
                    if (active.route.hasToll) StatusPill(stringResource(R.string.route_toll_yes), Icons.Default.Euro, c.accent)
                    s.dangerZones[active.route.id]?.takeIf { it > 0 }?.let { n -> StatusPill(pluralStringResource(R.plurals.route_danger_zones, n, n), Icons.Default.Shield, c.accent) }
                    val cost = active.cost
                    if (cost != null) StatusPill(stringResource(R.string.route_cost_chip, Formatters.euros(cost.fuelEur)), Icons.Default.LocalGasStation, c.brandBright, onClick = onOpenSettings)
                    else StatusPill(stringResource(R.string.route_vehicle_cta), Icons.Default.DirectionsCar, c.textSecondary, onClick = onOpenSettings)
                }
                AnimatedVisibility(detailsOpen) {
                    Column(Modifier.padding(top = 12.dp)) {
                        s.identities[active.route.id]?.let { identity -> RouteIdentityCard(identity, s.dangerZones[active.route.id]); Spacer(Modifier.height(10.dp)) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                            val p = s.preferences
                            GlassChip(stringResource(R.string.route_opt_no_tolls), onClick = { onPreferences(p.copy(avoidTolls = !p.avoidTolls)) }, icon = Icons.Default.Euro, compact = true, selected = p.avoidTolls)
                            GlassChip(stringResource(R.string.route_opt_no_highways), onClick = { onPreferences(p.copy(avoidHighways = !p.avoidHighways)) }, icon = Icons.Default.AltRoute, compact = true, selected = p.avoidHighways)
                            GlassChip(stringResource(R.string.route_opt_no_tunnels), onClick = { onPreferences(p.copy(avoidTunnels = !p.avoidTunnels)) }, icon = Icons.Default.Subway, compact = true, selected = p.avoidTunnels)
                            GlassChip(stringResource(R.string.route_opt_no_ferries), onClick = { onPreferences(p.copy(avoidFerries = !p.avoidFerries)) }, icon = Icons.Default.DirectionsBoat, compact = true, selected = p.avoidFerries)
                            GlassChip(stringResource(R.string.route_opt_no_unpaved), onClick = { onPreferences(p.copy(avoidUnpaved = !p.avoidUnpaved)) }, icon = Icons.Default.Terrain, compact = true, selected = p.avoidUnpaved)
                        }
                    }
                }
            }
            // Alternatives : durée, écart avec le choix, distance, icônes (péage, zones).
            s.options.filter { it.route.id != active.route.id }.forEach { o ->
                RowDivider(inset = 0.dp)
                AlternativeRow(
                    o, active, s.dangerZones[o.route.id] ?: 0, onClick = { onSelect(o.route.id) }, zfe = s.zfe[o.route.id]?.firstOrNull()?.verdict,
                    incidents = s.traffic[o.route.id]?.count { it.event.kind.notable } ?: 0,
                )
            }
            RowDivider(inset = 0.dp)
            if (s.stops.isNotEmpty() || s.optimizing) {
                StopsList(s.stops, s.optimizing, onAdd = onAddStop, onMove = onMoveStop, onRemove = onRemoveStop, onOptimize = onOptimize, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), showAdd = false)
            }
            AddStopButton(enabled = s.stops.size < fr.roadly.core.routing.StopOrdering.MAX_STOPS, onClick = onAddStop, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun HeadStat(label: String, value: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val c = LocalRoadlyColors.current
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        OverlineText(label, color = c.textSecondary, dash = false)
        Spacer(Modifier.height(2.dp))
        Text(value, style = StatValue.copy(fontSize = 20.sp * LocalUiScale.current, lineHeight = 24.sp * LocalUiScale.current), color = c.textPrimary)
    }
}

/** Pastille cerclée de couleur (« Avec péage », « Départ maintenant »…), comme les pastilles d'état de la référence. */
@Composable
internal fun StatusPill(text: String, icon: ImageVector, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(RoadlyShapes.Pill).background(color.copy(alpha = 0.12f)).border(1.5.dp, color.copy(alpha = 0.75f), RoadlyShapes.Pill)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = ButtonLabel.copy(fontSize = 14.sp), color = color, maxLines = 1)
    }
}

@Composable
private fun AlternativeRow(o: RouteOption, active: RouteOption, dangerZones: Int, onClick: () -> Unit, zfe: fr.roadly.core.data.zfe.ZfeVerdict? = null, incidents: Int = 0) {
    val c = LocalRoadlyColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Formatters.duration(o.route.durationSeconds), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        durationDeltaMinutes(o, active)?.let { d ->
            Spacer(Modifier.width(10.dp))
            val slower = d > 0
            val col = if (slower) c.danger else c.success
            Text(
                if (slower) "+$d min" else "−${-d} min",
                style = ButtonLabel.copy(fontSize = 14.sp), color = col,
                modifier = Modifier.clip(RoadlyShapes.Pill).background(col.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        // Place réduite : au plus deux icônes (les plus importantes d'abord), pour ne jamais rogner la ligne.
        val maxIcons = if (fr.roadly.core.designsystem.theme.isCompactSpace()) 2 else 4
        var shown = 0
        fun room(): Boolean = (shown < maxIcons).also { if (it) shown++ }
        val showIncidents = incidents > 0 && room()
        val showZfe = zfe != null && zfe != fr.roadly.core.data.zfe.ZfeVerdict.NO_RESTRICTION && room()
        val showToll = o.route.hasToll && room()
        val showZones = dangerZones > 0 && room()
        Text(Formatters.distance(o.route.distanceMeters), style = StatValue.copy(fontSize = 17.sp * LocalUiScale.current, fontWeight = FontWeight.ExtraBold), color = c.textPrimary)
        if (showToll) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Euro, contentDescription = stringResource(R.string.route_toll_yes), tint = c.accent, modifier = Modifier.size(20.dp)) }
        if (showIncidents) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.CarCrash, contentDescription = pluralStringResource(R.plurals.route_incidents, incidents, incidents), tint = c.accent, modifier = Modifier.size(20.dp))
        }
        if (showZfe) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Eco, contentDescription = stringResource(R.string.route_zfe_short), tint = zfeColor(zfe), modifier = Modifier.size(20.dp))
        }
        if (showZones) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Warning, contentDescription = pluralStringResource(R.plurals.route_danger_zones, dangerZones, dangerZones), tint = c.warning, modifier = Modifier.size(20.dp)) }
    }
}

/** « ⊕ Ajouter un arrêt » : pilule cerclée de marque. */
@Composable
internal fun AddStopButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoadlyShapes.Pill).border(1.5.dp, c.brand.copy(alpha = 0.55f), RoadlyShapes.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.AddCircle, contentDescription = null, tint = c.brand, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.route_add_stop), style = ButtonLabel, color = c.brandBright)
    }
}

/** Gros bouton « ROULER → ». */
@Composable
internal fun RollButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).softShadow(RoadlyShapes.Pill, c.brand, 12.dp).clip(RoadlyShapes.Pill)
            .background(Brush.verticalGradient(listOf(c.brandBright, c.brand))).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.route_start).uppercase(), style = ButtonLabel.copy(fontSize = 20.sp, letterSpacing = 2.sp), color = c.onBrandAuto)
        Spacer(Modifier.width(12.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = c.onBrandAuto, modifier = Modifier.size(24.dp))
    }
}

/** Étiquette « ✓ CHOISIR » posée sur un tracé alternatif. */
@Composable
internal fun ChooseLabel(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.softShadow(RoadlyShapes.Pill, Color.Black, 8.dp).clip(RoadlyShapes.Pill).background(c.surface).border(1.dp, c.glassBorder, RoadlyShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = c.brand, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.route_choose).uppercase(), style = ButtonLabel.copy(letterSpacing = 1.5.sp), color = c.textPrimary)
    }
}

/** Bouton rond latéral (carburant…). */
@Composable
internal fun SideRoundButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit, iconModifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Box(
        Modifier.size(52.dp).softShadow(RoadlyShapes.Pill, Color.Black, 8.dp).clip(RoadlyShapes.Pill).background(c.surface).border(1.dp, c.glassBorder, RoadlyShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = iconModifier.size(24.dp)) }
}

@Composable
internal fun zfeColor(v: fr.roadly.core.data.zfe.ZfeVerdict): Color {
    val c = LocalRoadlyColors.current
    return when (v) {
        fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN -> c.danger
        fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN_OTHER_HOURS, fr.roadly.core.data.zfe.ZfeVerdict.CRITAIR_UNKNOWN -> c.accent
        else -> c.success
    }
}

/** Texte et couleur de la pastille ZFE de l'itinéraire choisi. */
@Composable
internal fun zfePill(z: fr.roadly.core.data.zfe.ZfeCrossing): Pair<String, Color> {
    val text = when (z.verdict) {
        fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN -> stringResource(R.string.route_zfe_forbidden)
        fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN_OTHER_HOURS -> stringResource(R.string.route_zfe_hours)
        fr.roadly.core.data.zfe.ZfeVerdict.CRITAIR_UNKNOWN -> stringResource(R.string.route_zfe_unknown)
        else -> stringResource(R.string.route_zfe_allowed)
    }
    return text to zfeColor(z.verdict)
}

/** Détail d'une ZFE traversée : réglementation, vignettes interdites, horaires, liens officiels. */
@Composable
internal fun ZfeDialog(z: fr.roadly.core.data.zfe.ZfeCrossing, onOpenVehicle: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val (headline, color) = zfePill(z)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        icon = { Icon(Icons.Default.Eco, contentDescription = null, tint = color) },
        title = { Text(z.rule.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(headline, style = MaterialTheme.typography.titleSmall, color = color)
                fr.roadly.core.data.zfe.zfeRestrictionLabel(z.restriction)?.let {
                    Text(stringResource(R.string.route_zfe_rule, it), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
                Text(stringResource(R.string.route_zfe_hours_label, z.hours ?: stringResource(R.string.route_zfe_hours_unknown)), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Text(stringResource(R.string.route_zfe_source), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                z.rule.decreeUrl?.let { url -> Text(stringResource(R.string.route_zfe_decree), style = MaterialTheme.typography.labelLarge, color = c.brandBright, modifier = Modifier.clickable { runCatching { uri.openUri(url) } }.padding(vertical = 4.dp)) }
                z.rule.infoUrl?.let { url -> Text(stringResource(R.string.route_zfe_info), style = MaterialTheme.typography.labelLarge, color = c.brandBright, modifier = Modifier.clickable { runCatching { uri.openUri(url) } }.padding(vertical = 4.dp)) }
            }
        },
        confirmButton = {
            if (z.verdict == fr.roadly.core.data.zfe.ZfeVerdict.CRITAIR_UNKNOWN) fr.roadly.core.designsystem.component.GlassButton(stringResource(R.string.route_zfe_set_critair), onClick = { onDismiss(); onOpenVehicle() })
            else fr.roadly.core.designsystem.component.GlassButton(stringResource(R.string.route_time_confirm_ok), onClick = onDismiss)
        },
    )
}

/** Périmètres à dessiner : chaque agglomération une fois, avec l'état le plus strict parmi les itinéraires affichés. */
internal fun zfeShapes(byRoute: Map<String, List<fr.roadly.core.data.zfe.ZfeCrossing>>): List<fr.roadly.core.map.ZfeShape> {
    fun state(v: fr.roadly.core.data.zfe.ZfeVerdict) = when (v) {
        fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN -> "forbidden"
        fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN_OTHER_HOURS, fr.roadly.core.data.zfe.ZfeVerdict.CRITAIR_UNKNOWN -> "caution"
        else -> "ok"
    }
    val rank = listOf("forbidden", "caution", "ok")
    return byRoute.values.flatten().groupBy { it.rule.name }.map { (_, list) ->
        val worst = list.minBy { rank.indexOf(state(it.verdict)) }
        fr.roadly.core.map.ZfeShape(state(worst.verdict), worst.rule.polygons.map { p -> listOf(p.outer) + p.holes })
    }
}

@Composable
private fun trafficLabel(k: fr.roadly.core.data.traffic.TrafficKind): String = stringResource(when (k) {
    fr.roadly.core.data.traffic.TrafficKind.ACCIDENT -> R.string.route_traffic_accident
    fr.roadly.core.data.traffic.TrafficKind.JAM -> R.string.route_traffic_jam
    fr.roadly.core.data.traffic.TrafficKind.SLOW -> R.string.route_traffic_slow
    fr.roadly.core.data.traffic.TrafficKind.BROKEN_DOWN -> R.string.route_traffic_broken
    fr.roadly.core.data.traffic.TrafficKind.CLOSURE -> R.string.route_traffic_closure
    fr.roadly.core.data.traffic.TrafficKind.OBSTACLE -> R.string.route_traffic_obstacle
    fr.roadly.core.data.traffic.TrafficKind.WEATHER -> R.string.route_traffic_weather
    fr.roadly.core.data.traffic.TrafficKind.LANE_CLOSURE -> R.string.route_traffic_lanes
    fr.roadly.core.data.traffic.TrafficKind.ROADWORK -> R.string.route_traffic_works
})

/** Liste des incidents officiels de l'itinéraire choisi : nature, route, sens, distance depuis le départ, longueur de bouchon. */
@Composable
internal fun TrafficDialog(hits: List<fr.roadly.core.data.traffic.TrafficHit>, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = c.accent) },
        title = { Text(stringResource(R.string.route_traffic_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                hits.take(12).forEach { h ->
                    val e = h.event
                    Column {
                        Text(
                            listOfNotNull(trafficLabel(e.kind), e.road).joinToString(" · ") +
                                (e.queueLengthM?.let { " · " + Formatters.distance(it.toDouble()) } ?: ""),
                            style = MaterialTheme.typography.titleSmall, color = c.textPrimary,
                        )
                        Text(
                            listOfNotNull(stringResource(R.string.route_traffic_at, Formatters.distance(h.alongM)), e.direction, e.description).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                }
                Text(stringResource(R.string.route_traffic_source), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
        },
        confirmButton = { fr.roadly.core.designsystem.component.GlassButton(stringResource(R.string.route_time_confirm_ok), onClick = onDismiss) },
    )
}

/** « Sans péage · sans tunnel » : options d'évitement actives, en toutes lettres. */
@Composable
internal fun avoidSummary(p: RoutePreferences): String = p.active.map { a ->
    stringResource(when (a) {
        fr.roadly.core.model.RouteAvoid.TOLLS -> R.string.route_avoid_tolls_short
        fr.roadly.core.model.RouteAvoid.HIGHWAYS -> R.string.route_avoid_highways_short
        fr.roadly.core.model.RouteAvoid.TUNNELS -> R.string.route_avoid_tunnels_short
        fr.roadly.core.model.RouteAvoid.FERRIES -> R.string.route_avoid_ferries_short
        fr.roadly.core.model.RouteAvoid.UNPAVED -> R.string.route_avoid_unpaved_short
    })
}.joinToString(" · ").replaceFirstChar { it.uppercase() }
