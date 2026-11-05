package fr.roadly.core.data.testing

import fr.roadly.core.data.repository.DriveMemoryRepository
import fr.roadly.core.data.repository.ParkedVehicle
import fr.roadly.core.data.repository.TollReminder
import kotlinx.coroutines.flow.MutableStateFlow

/** Double de test en mémoire de [DriveMemoryRepository]. */
class FakeDriveMemoryRepository : DriveMemoryRepository {
    val tolls = MutableStateFlow<List<TollReminder>>(emptyList())
    val parked = MutableStateFlow<ParkedVehicle?>(null)

    override val tollReminders = tolls
    override suspend fun addTollReminder(reminder: TollReminder) { tolls.value = listOf(reminder) + tolls.value.filterNot { it.id == reminder.id } }
    override suspend fun dismissTollReminder(id: Long) { tolls.value = tolls.value.filterNot { it.id == id } }
    override val parkedVehicle = parked
    override suspend fun setParkedVehicle(value: ParkedVehicle?) { parked.value = value }
}
