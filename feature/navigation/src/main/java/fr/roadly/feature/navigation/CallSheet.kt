package fr.roadly.feature.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.repository.CallContact
import fr.roadly.core.data.repository.CallContacts
import fr.roadly.core.data.repository.CallContactsRepository
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Proches à appeler depuis le guidage (liste locale au téléphone). */
@HiltViewModel
class CallViewModel @Inject constructor(private val repo: CallContactsRepository) : ViewModel() {
    val contacts: StateFlow<List<CallContact>> = repo.contacts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Écriture impossible (stockage plein) : la liste reste telle quelle.
    fun add(contact: CallContact) { viewModelScope.launch { runCatching { repo.add(contact) } } }
    fun remove(contact: CallContact) { viewModelScope.launch { runCatching { repo.remove(contact.number) } } }
}

/**
 * Feuille « Appeler un proche » : jusqu'à [CallContacts.MAX] proches, choisis dans les contacts du téléphone. Toucher un nom
 * ouvre le téléphone avec le numéro composé ; c'est le téléphone qui passe l'appel (kit mains libres, Bluetooth de la voiture),
 * et le guidage continue dans sa petite fenêtre. Roadly ne demande ni l'accès aux contacts ni le droit d'appeler.
 */
@Composable
fun CallSheet(onDismiss: () -> Unit, vm: CallViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var problem by remember { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val picked = readPickedContact(context, uri)
            if (picked != null) { problem = null; vm.add(picked) } else problem = R.string.call_pick_failed
        }
    }
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.call_overline))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.call_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(RoadlySpacing.s))
            Text(stringResource(R.string.call_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(RoadlySpacing.m))
            if (contacts.isEmpty()) {
                Text(stringResource(R.string.call_empty), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(RoadlySpacing.m))
            }
            contacts.forEachIndexed { i, contact ->
                ListRow(
                    contact.name, subtitle = contact.number, icon = Icons.Default.Call, iconStyle = IconTileStyle.BrandFilled,
                    onClick = { if (dial(context, contact.number)) onDismiss() else problem = R.string.call_no_dialer },
                    chevron = false, divider = i < contacts.lastIndex,
                    trailing = {
                        IconButton(onClick = { vm.remove(contact) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.call_remove, contact.name), tint = c.textTertiary)
                        }
                    },
                )
            }
            problem?.let {
                Spacer(Modifier.height(RoadlySpacing.s))
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = c.danger)
            }
            if (contacts.size < CallContacts.MAX) {
                Spacer(Modifier.height(RoadlySpacing.m))
                GlassButton(
                    stringResource(R.string.call_add), icon = Icons.Default.PersonAdd, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Sélecteur de contacts d'Android : seule la fiche choisie est lisible, sans permission d'accès au carnet.
                        val pick = Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
                        try { picker.launch(pick) } catch (e: ActivityNotFoundException) { problem = R.string.call_no_contacts_app }
                    },
                )
            } else {
                Spacer(Modifier.height(RoadlySpacing.s))
                Text(stringResource(R.string.call_full, CallContacts.MAX), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
        }
    }
}

/** Nom et numéro de la fiche choisie dans le sélecteur ; null si elle est illisible ou sans numéro composable. */
private fun readPickedContact(context: Context, uri: Uri): CallContact? = runCatching {
    val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
    context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        CallContacts.sanitize(CallContact(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty()))
    }
}.getOrNull()

/** Ouvre le téléphone avec [number] composé ; false si aucune application ne sait le faire (tablette sans téléphonie). */
private fun dial(context: Context, number: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    true
} catch (e: ActivityNotFoundException) {
    false
}
