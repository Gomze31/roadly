package fr.roadly.core.designsystem.component

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Saisie vocale par la reconnaissance vocale du système (aucune clé d'API, aucun service Roadly) :
 * renvoie une fonction qui ouvre l'écoute ; [onText] reçoit la meilleure transcription, [onUnavailable] est
 * appelé si aucun service de reconnaissance n'est installé.
 */
@Composable
fun rememberVoiceInput(prompt: String, onText: (String) -> Unit, onUnavailable: () -> Unit = {}): () -> Unit {
    val currentOnText by rememberUpdatedState(onText)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) currentOnText(text)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try { launcher.launch(intent) } catch (e: ActivityNotFoundException) { currentOnUnavailable() }
    }
}
