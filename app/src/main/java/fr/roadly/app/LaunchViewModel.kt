package fr.roadly.app

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.model.Place
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Résout les demandes externes qui dépendent des données locales (domicile, travail). */
@HiltViewModel
class LaunchViewModel @Inject constructor(private val favorites: FavoritesRepository) : ViewModel() {
    suspend fun home(): Place? = favorites.observeHome().first()
    suspend fun work(): Place? = favorites.observeWork().first()
}
