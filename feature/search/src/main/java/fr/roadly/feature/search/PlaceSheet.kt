package fr.roadly.feature.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.Place
import java.util.Locale

@Composable
fun PlaceSheet(
    place: Place,
    isFavorite: Boolean,
    station: FuelStation? = null,
    distanceMeters: Double?,
    onDismiss: () -> Unit,
    onRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetHome: () -> Unit,
    onSetWork: () -> Unit,
) {
    val c = LocalRoadlyColors.current
    val context = LocalContext.current
    val shareText = placeShareText(place)
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            // En-tête : tuile + nom, adresse, distance.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(if (station != null) Icons.Default.LocalGasStation else Icons.Default.Place, style = IconTileStyle.BrandFilled, size = 44.dp)
                Spacer(Modifier.width(RoadlySpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (place.address != null) Text(place.address!!, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (distanceMeters != null) Text(stringResource(R.string.place_distance, Formatters.distance(distanceMeters)), style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                }
            }
            if (station != null) { Spacer(Modifier.height(RoadlySpacing.sm)); FuelPrices(station) }
            Spacer(Modifier.height(RoadlySpacing.m))
            GlassButton(stringResource(R.string.place_route), onClick = onRoute, icon = Icons.Default.Directions, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(RoadlySpacing.sm))
            // Trois tuiles d'action à largeur égale : pas de débordement sur écran étroit.
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                ActionTile(
                    label = stringResource(if (isFavorite) R.string.place_favorite_remove else R.string.place_favorite_add),
                    icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    style = if (isFavorite) IconTileStyle.Accent else IconTileStyle.Neutral,
                    onClick = onToggleFavorite, modifier = Modifier.weight(1f),
                )
                ActionTile(stringResource(R.string.place_set_home), Icons.Default.Home, IconTileStyle.Neutral, onSetHome, Modifier.weight(1f))
                ActionTile(stringResource(R.string.place_set_work), Icons.Default.Work, IconTileStyle.Neutral, onSetWork, Modifier.weight(1f))
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassChip(
                    stringResource(R.string.place_copy_address), icon = Icons.Default.ContentCopy, compact = true,
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(place.name, shareText))
                    },
                )
                val shareTitle = stringResource(R.string.place_share_title)
                GlassChip(
                    stringResource(R.string.place_share), icon = Icons.Default.Share, compact = true,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                )
            }
        }
    }
}

/** Prix publiés (jamais inventés) avec leur date, 24h/24 et services ; source affichée. */
@Composable
private fun FuelPrices(station: FuelStation) {
    val c = LocalRoadlyColors.current
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxWidth().clip(RoadlyShapes.Tile).background(c.glassBorder).padding(RoadlySpacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (station.prices.isEmpty()) {
            Text(stringResource(R.string.place_fuel_no_price), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                station.prices.forEach { p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.fuel.shortLabel(), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        Text(String.format(Locale.FRANCE, "%.3f", p.eurPerLitre), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(p.updatedAtEpochMs?.let { ageText(now - it) } ?: "", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
                    }
                }
            }
        }
        val extras = listOfNotNull(if (station.open24h) stringResource(R.string.place_fuel_24h) else null) + station.services.take(3)
        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.place_fuel_source), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
}

/** « il y a 3 h », « il y a 2 j » : fraîcheur du prix telle que publiée par la source. */
private fun ageText(ageMs: Long): String {
    val min = ageMs / 60_000
    return when {
        min < 60 -> "il y a ${min.coerceAtLeast(1)} min"
        min < 48 * 60 -> "il y a ${min / 60} h"
        else -> "il y a ${min / (24 * 60)} j"
    }
}

/** Texte copié/partagé : nom + adresse, ou coordonnées si le lieu n'a pas d'adresse. */
private fun placeShareText(place: Place): String {
    val where = place.address ?: String.format(java.util.Locale.ROOT, "%.5f, %.5f", place.point.lat, place.point.lon)
    return if (where == place.name) where else "${place.name}\n$where"
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, style: IconTileStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(
        modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoadlyShapes.Tile)
            .background(c.glassBorder)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = RoadlySpacing.s, horizontal = RoadlySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(icon, style = style, size = 40.dp)
        Spacer(Modifier.height(RoadlySpacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
