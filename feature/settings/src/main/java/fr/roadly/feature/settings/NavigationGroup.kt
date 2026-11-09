package fr.roadly.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import kotlin.math.roundToInt

/** Retrait d'un séparateur sous une ligne avec tuile d'icône (même valeur que `RowContentInset` de `SettingsScreen`). */
private val NavRowInset = 64.dp

/** Vues proposées au départ : le nord en haut passe par « Rotation automatique ». */
private val START_VIEWS = listOf(CameraMode3D.FOLLOW_3D, CameraMode3D.FOLLOW_2D)

/** E2 : groupe « Navigation » des Réglages. Les libellés sont résolus ici, le sélecteur segmenté prend une fonction non composable. */
@Composable
internal fun NavigationGroup(s: NavigationSettings, vm: SettingsViewModel) {
    SettingsGroup(stringResource(R.string.settings_group_navigation), footer = stringResource(R.string.settings_nav_footer)) {
        val views = START_VIEWS.associateWith { stringResource(if (it == CameraMode3D.FOLLOW_3D) R.string.settings_nav_view_3d else R.string.settings_nav_view_2d) }
        ChoiceRow(stringResource(R.string.settings_nav_view), null, Icons.Default.ViewInAr, START_VIEWS, s.defaultCameraMode, views, vm::setDefaultCameraMode)
        ToggleRow(
            stringResource(R.string.settings_nav_rotate), checked = s.autoRotate, onCheckedChange = vm::setAutoRotate,
            subtitle = stringResource(R.string.settings_nav_rotate_subtitle), icon = Icons.Default.Explore,
        )
        val zooms = mapOf(
            -1 to stringResource(R.string.settings_nav_zoom_wide), 0 to stringResource(R.string.settings_nav_zoom_normal), 1 to stringResource(R.string.settings_nav_zoom_close),
        )
        ChoiceRow(stringResource(R.string.settings_nav_zoom), stringResource(R.string.settings_nav_zoom_subtitle), Icons.Default.ZoomIn, GuidanceDefaults.ALLOWED_ZOOM_OFFSETS, s.zoomOffset, zooms, vm::setZoomOffset)
        val pitches = PitchPreset.entries.associateWith { stringResource(R.string.settings_nav_pitch_value, it.degrees.roundToInt()) }
        ChoiceRow(stringResource(R.string.settings_nav_pitch), stringResource(R.string.settings_nav_pitch_subtitle), Icons.Default.Landscape, PitchPreset.entries, s.pitchPreset, pitches, vm::setPitchPreset)
        val details = MapDetail.entries.associateWith { it.label() }
        ChoiceRow(stringResource(R.string.settings_map_detail), stringResource(R.string.settings_map_detail_subtitle), Icons.Default.Layers, MapDetail.entries, s.mapDetail, details, vm::setMapDetail)
        ToggleRow(
            stringResource(R.string.settings_map_3d), checked = s.buildings3d, onCheckedChange = vm::setMap3d,
            subtitle = stringResource(R.string.settings_map_3d_subtitle), icon = Icons.Default.LocationCity,
        )
        val aerial = vm.mapAerial.collectAsStateWithLifecycle()
        ToggleRow(
            stringResource(R.string.settings_map_aerial), checked = aerial.value, onCheckedChange = vm::setMapAerial,
            subtitle = stringResource(R.string.settings_map_aerial_subtitle), icon = Icons.Default.Satellite,
        )
        val junction3d = vm.junction3d.collectAsStateWithLifecycle()
        ToggleRow(
            stringResource(R.string.settings_junction_3d), checked = junction3d.value, onCheckedChange = vm::setJunction3d,
            subtitle = stringResource(R.string.settings_junction_3d_subtitle), icon = Icons.Default.ViewInAr,
        )
        ToggleRow(
            stringResource(R.string.settings_nav_then), checked = s.showNextManeuverPreview, onCheckedChange = vm::setShowNextManeuverPreview,
            subtitle = stringResource(R.string.settings_nav_then_subtitle), icon = Icons.Default.TurnRight,
        )
        val powers = PowerSaveMode.entries.associateWith { it.label() }
        ChoiceRow(stringResource(R.string.settings_nav_power), stringResource(R.string.settings_nav_power_subtitle), Icons.Default.BatterySaver, PowerSaveMode.entries, s.powerSaveMode, powers, vm::setPowerSaveMode, last = true)
    }
}

/** Ligne titrée suivie d'un sélecteur segmenté, puis d'un séparateur (sauf la dernière ligne du groupe). */
@Composable
private fun <T> ChoiceRow(
    title: String, subtitle: String?, icon: ImageVector, options: List<T>, selected: T, labels: Map<T, String>, onSelect: (T) -> Unit, last: Boolean = false,
) {
    ListRow(title, subtitle = subtitle, icon = icon, divider = false)
    SegmentedControl(
        options = options, selected = selected, onSelect = onSelect, label = { labels.getValue(it) },
        modifier = Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m),
    )
    if (!last) RowDivider(inset = NavRowInset)
}

@Composable
private fun MapDetail.label(): String = stringResource(
    when (this) {
        MapDetail.NORMAL -> R.string.settings_map_detail_normal
        MapDetail.REDUCED -> R.string.settings_map_detail_reduced
    },
)

@Composable
private fun PowerSaveMode.label(): String = stringResource(
    when (this) {
        PowerSaveMode.AUTO -> R.string.settings_nav_power_auto
        PowerSaveMode.ON -> R.string.settings_nav_power_on
        PowerSaveMode.OFF -> R.string.settings_nav_power_off
    },
)
