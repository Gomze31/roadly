package fr.roadly.core.assistant.settings

import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Plafond quotidien en minutes de micro ; les tokens sont affichés à titre indicatif. */
@Singleton
class UsageMeter @Inject constructor(private val repo: AssistantSettingsRepository) {
    suspend fun canStart(nowMs: Long, zone: ZoneId): Boolean {
        val capSeconds = repo.settings.first().dailyCapMinutes * 60L
        val u = repo.usage.first()
        val usedToday = if (u.day == day(nowMs, zone)) u.micSeconds else 0L
        return usedToday < capSeconds
    }

    suspend fun record(nowMs: Long, zone: ZoneId, tokens: Long, micSeconds: Long) =
        repo.addUsage(day(nowMs, zone), tokens, micSeconds)

    companion object {
        fun day(nowMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
    }
}
