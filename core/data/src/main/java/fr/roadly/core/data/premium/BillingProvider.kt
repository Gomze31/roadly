package fr.roadly.core.data.premium

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.model.Entitlements
import fr.roadly.core.model.Tier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Pourquoi la boutique n'est pas utilisable. */
enum class StoreUnavailableReason {
    /** Aucune intégration de paiement (Google Play Billing) : produit et clé à configurer par l'exploitant. */
    NOT_INTEGRATED,
    NO_NETWORK,
    STORE_ERROR,
}

sealed interface StoreState {
    data class Unavailable(val reason: StoreUnavailableReason) : StoreState
    /** Offre réelle lue depuis la boutique : libellé et prix formatés par la boutique, jamais inventés. */
    data class Ready(val productLabel: String, val formattedPrice: String) : StoreState
}

/**
 * Porte d'entrée vers une boutique (Google Play Billing). Une implémentation Play lit l'offre, lance l'achat et
 * reconnaît les achats existants ; [NoBillingProvider] est la version livrée tant que l'exploitant n'a pas configuré de produit.
 */
interface BillingProvider {
    val store: Flow<StoreState>
    val tier: Flow<Tier>
    suspend fun purchase(): Outcome<Tier>
    suspend fun restore(): Outcome<Tier>
}

@Singleton
class NoBillingProvider @Inject constructor() : BillingProvider {
    override val store: Flow<StoreState> = flowOf(StoreState.Unavailable(StoreUnavailableReason.NOT_INTEGRATED))
    override val tier: Flow<Tier> = flowOf(Tier.FREE)
    override suspend fun purchase(): Outcome<Tier> = Outcome.Failure(AppError.ProviderUnavailable(SERVICE))
    override suspend fun restore(): Outcome<Tier> = Outcome.Failure(AppError.ProviderUnavailable(SERVICE))

    private companion object { const val SERVICE = "boutique" }
}

/** Droits courants de l'appareil, dérivés de la boutique. */
interface EntitlementsRepository {
    val entitlements: Flow<Entitlements>
}

@Singleton
class DefaultEntitlementsRepository @Inject constructor(billing: BillingProvider) : EntitlementsRepository {
    override val entitlements: Flow<Entitlements> = combine(billing.store, billing.tier) { store, tier ->
        Entitlements(tier = tier, storeConfigured = store is StoreState.Ready)
    }
}
