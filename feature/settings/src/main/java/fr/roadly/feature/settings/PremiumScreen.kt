package fr.roadly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.data.premium.StoreState
import fr.roadly.core.data.premium.StoreUnavailableReason
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.PremiumTag
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.RoadlyTopBar
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Feature
import fr.roadly.core.model.Tier

/**
 * Roadly+ : ce qui est gratuit, ce qui relève du palier Roadly+, et l'état réel de la boutique.
 * Sans boutique intégrée, toutes les fonctions restent incluses et aucun bouton d'achat n'est affiché.
 */
@Composable
fun PremiumScreen(onBack: () -> Unit, vm: PremiumViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val e = state.entitlements
    RoadlyScreen {
        RoadlyTopBar(stringResource(R.string.premium_title), onBack = onBack, backDescription = stringResource(R.string.settings_back))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(null) {
                Row(Modifier.fillMaxWidth().padding(RoadlySpacing.m), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.Star, style = IconTileStyle.BrandFilled, size = 56.dp)
                    Spacer(Modifier.width(RoadlySpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (e.tier == Tier.PLUS) R.string.premium_tier_plus else R.string.premium_tier_free), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                            if (e.tier == Tier.PLUS) { Spacer(Modifier.width(8.dp)); PremiumTag() }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(if (!e.storeConfigured) R.string.premium_state_no_store else if (e.tier == Tier.PLUS) R.string.premium_state_plus else R.string.premium_state_free),
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                }
            }

            SettingsGroup(stringResource(R.string.premium_group_free), footer = stringResource(R.string.premium_group_free_footer)) {
                val free = Feature.entries.filter { !it.plus }
                free.forEachIndexed { i, f -> FeatureRow(f, included = true, divider = i < free.lastIndex) }
            }

            SettingsGroup(stringResource(R.string.premium_group_plus), footer = stringResource(if (e.storeConfigured) R.string.premium_group_plus_footer else R.string.premium_group_plus_footer_no_store)) {
                val plus = Feature.entries.filter { it.plus }
                plus.forEachIndexed { i, f -> FeatureRow(f, included = e.includes(f), divider = i < plus.lastIndex) }
            }

            SettingsGroup(stringResource(R.string.premium_group_store), footer = stringResource(R.string.premium_store_footer)) {
                when (val s = state.store) {
                    null -> Unit
                    is StoreState.Unavailable -> ListRow(
                        stringResource(R.string.premium_store_unavailable),
                        subtitle = stringResource(when (s.reason) {
                            StoreUnavailableReason.NOT_INTEGRATED -> R.string.premium_store_not_integrated
                            StoreUnavailableReason.NO_NETWORK -> R.string.premium_store_no_network
                            StoreUnavailableReason.STORE_ERROR -> R.string.premium_store_error
                        }),
                        icon = Icons.Default.Storefront, iconStyle = IconTileStyle.Neutral, chevron = false, divider = false,
                    )
                    is StoreState.Ready -> Column(Modifier.padding(RoadlySpacing.m), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        ListRow(s.productLabel, value = s.formattedPrice, icon = Icons.Default.Storefront, chevron = false, divider = false, modifier = Modifier.padding(0.dp))
                        if (e.tier != Tier.PLUS) GlassButton(stringResource(R.string.premium_buy), onClick = vm::purchase, style = GlassButtonStyle.Brand, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                        GlassButton(stringResource(R.string.premium_restore), onClick = vm::restore, style = GlassButtonStyle.Secondary, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                        state.lastError?.let { Text(it.userMessage(), style = MaterialTheme.typography.bodySmall, color = c.danger) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(f: Feature, included: Boolean, divider: Boolean) {
    val label = stringResource(when (f) {
        Feature.NAVIGATION -> R.string.premium_f_navigation; Feature.SPEED_ALERTS -> R.string.premium_f_speed
        Feature.VIGILANCE_ZONES -> R.string.premium_f_vigilance; Feature.FUEL_PRICES -> R.string.premium_f_fuel
        Feature.COMMUNITY -> R.string.premium_f_community; Feature.TROPHIES -> R.string.premium_f_trophies
        Feature.VEHICLE_ADVANCED -> R.string.premium_f_vehicle; Feature.TOUR_OPTIMIZATION -> R.string.premium_f_tour
        Feature.TRIP_HISTORY -> R.string.premium_f_history; Feature.REPLAY -> R.string.premium_f_replay
        Feature.COCKPIT_TINT -> R.string.premium_f_tint
    })
    ListRow(
        label,
        subtitle = stringResource(if (included) R.string.premium_included else R.string.premium_locked),
        icon = if (included) Icons.Default.CheckCircle else Icons.Default.Lock,
        iconStyle = if (included) IconTileStyle.Accent else IconTileStyle.Neutral,
        chevron = false, divider = divider,
    )
}
