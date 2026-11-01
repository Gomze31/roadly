package fr.roadly.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import fr.roadly.core.assistant.AssistantController
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.vigilance.VigilanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class RoadlyApp : Application() {
    @Inject lateinit var vigilance: VigilanceRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var assistant: AssistantController

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Zones de vigilance : base locale remplie au premier lancement puis rafraîchie tous les 30 jours ; un échec réseau est silencieux.
        appScope.launch { runCatching { vigilance.refreshIfStale(System.currentTimeMillis()) } }
        // Micro réservé au premier plan : Roadly AI se ferme quand l'application passe en arrière-plan.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = assistant.onAppBackground()
        })
    }
}
