package fr.roadly.feature.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.charging.Connector
import fr.roadly.core.designsystem.component.EmptyState
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.navigation.ChargerAhead
import java.util.Locale
import kotlin.math.roundToInt

/** Capacité utile de la batterie et charge actuelle, gardées dans les réglages pour les prochains trajets. */
@Composable
internal fun EvBatteryDialog(capacityKwh: Double?, chargePct: Int?, onSave: (Double?, Int) -> Unit, onDismiss: () -> Unit, onChargers: (() -> Unit)? = null) {
    val c = LocalRoadlyColors.current
    var capacity by remember { mutableStateOf(capacityKwh?.let { String.format(Locale.FRANCE, "%.0f", it) } ?: "") }
    var charge by remember { mutableFloatStateOf((chargePct ?: 80).toFloat()) }
    val parsed = capacity.replace(',', '.').toDoubleOrNull()?.takeIf { it in 5.0..250.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = { Text(stringResource(R.string.route_ev_dialog_title), color = c.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    capacity, { capacity = it.take(6) }, label = { Text(stringResource(R.string.route_ev_capacity)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = capacity.isNotBlank() && parsed == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(RoadlySpacing.m))
                Text(stringResource(R.string.route_ev_charge, charge.roundToInt()), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Slider(charge, { charge = it }, valueRange = 0f..100f, steps = 19)
                Text(stringResource(R.string.route_ev_dialog_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                if (onChargers != null) TextButton(onClick = onChargers) { Text(stringResource(R.string.route_ev_chargers)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parsed, charge.roundToInt()) }, enabled = parsed != null) { Text(stringResource(R.string.route_ev_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.route_ev_cancel)) } },
    )
}

/** Bornes de 50 kW et plus à moins de 2 km du trajet, dans l'ordre du trajet ; un appui ajoute la borne comme arrêt. */
@Composable
internal fun ChargersSheet(ui: ChargersUi, onPick: (ChargerAhead) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.route_ev_dialog_title))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.route_chargers_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(RoadlySpacing.s))
            when (ui) {
                ChargersUi.Hidden -> Unit
                ChargersUi.Loading -> LoadingRow(stringResource(R.string.route_chargers_loading))
                is ChargersUi.Error -> ErrorState(ui.error.userMessage(), onRetry = onRetry)
                is ChargersUi.Content -> if (ui.chargers.isEmpty()) {
                    EmptyState(Icons.Default.EvStation, stringResource(R.string.route_chargers_empty))
                } else {
                    Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                        ui.chargers.forEachIndexed { i, ch ->
                            if (i > 0) RowDivider(inset = 0.dp)
                            ChargerRow(ch, onClick = { onPick(ch) })
                        }
                    }
                    Spacer(Modifier.height(RoadlySpacing.s))
                    Text(stringResource(R.string.route_chargers_source), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
internal fun ChargerRow(ch: ChargerAhead, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val s = ch.station
    val connectors = s.connectors.joinToString(" · ") { when (it) { Connector.CCS -> "CCS"; Connector.CHADEMO -> "CHAdeMO"; Connector.TYPE2 -> "Type 2" } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = RoadlySpacing.touchTargetDriving)
            .clickable(onClickLabel = stringResource(R.string.route_chargers_add), role = Role.Button, onClick = onClick).padding(vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Default.EvStation, style = if (s.maxKw >= 150) IconTileStyle.Brand else IconTileStyle.Neutral, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(stringResource(R.string.route_chargers_where, Formatters.distance(ch.aheadM), Formatters.distance(ch.detourM)), connectors.ifEmpty { null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Dernier état publié (base dynamique nationale) : hors service en évidence, sinon libres si le relevé est récent.
            ch.status?.describe(System.currentTimeMillis())?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = if ((ch.status?.outOfService ?: 0) > 0) c.warning else c.success, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            (s.price ?: s.operator)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(8.dp))
        Text("${s.maxKw} kW", style = MaterialTheme.typography.titleMedium, color = if (s.maxKw >= 150) c.brandBright else c.textPrimary)
    }
}
