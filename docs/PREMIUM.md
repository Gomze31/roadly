# Roadly+ — architecture gratuit / premium (LOT 11)

Roadly est livré **sans boutique**. L'architecture premium existe, est testée, et respecte une règle simple : **on ne verrouille jamais une fonction derrière un achat impossible**. Tant qu'aucune boutique n'est intégrée, toutes les fonctions sont incluses et l'écran *Réglages → Roadly+* le dit.

## Modèle (`core:model`)

- `Feature` : les 11 fonctions couvertes ; `plus = true` pour celles prévues au palier Roadly+ (profils véhicules avancés, optimisation de tournée, historique, Replay, teinte du cockpit).
- `Tier` : `FREE` / `PLUS`.
- `Entitlements(tier, storeConfigured)` : `includes(feature)` vaut vrai si `tier == PLUS`, si la fonction n'est pas `plus`, **ou si aucune boutique n'est configurée**. `Entitlements.NO_STORE` est l'état livré.

## Boutique (`core:data`, package `premium`)

- `BillingProvider` : `store: Flow<StoreState>`, `tier: Flow<Tier>`, `purchase()`, `restore()`.
- `StoreState.Unavailable(reason)` (`NOT_INTEGRATED`, `NO_NETWORK`, `STORE_ERROR`) ou `StoreState.Ready(productLabel, formattedPrice)` : libellé et prix **lus depuis la boutique**, jamais écrits dans l'application.
- `NoBillingProvider` (lié par Hilt) : boutique indisponible (`NOT_INTEGRATED`), palier `FREE`, achat et restauration en échec `ProviderUnavailable`.
- `DefaultEntitlementsRepository` combine les deux flux : `storeConfigured = store is Ready`.

## Écran Roadly+ (`feature:settings`)

Palier et explication, fonctions « Inclus dans Roadly », fonctions « Roadly+ » avec état inclus / verrouillé, section Boutique. Les boutons **S'abonner** et **Restaurer mes achats** ne sont rendus que si la boutique est `Ready` : aucun bouton inactif.

## Intégrer Google Play Billing (à faire par l'exploitant)

1. Play Console : créer l'application, puis un abonnement (par exemple `roadly_plus_monthly`) avec son offre et, si souhaité, un essai gratuit. Les prix se fixent dans la console.
2. Ajouter `com.android.billingclient:billing-ktx` au module `core:data` (hors ligne : le dépôt Gradle local doit contenir l'artefact).
3. Écrire `PlayBillingProvider : BillingProvider` :
   - connexion `BillingClient`, `queryProductDetailsAsync` → `StoreState.Ready(label, formattedPrice)` ou `Unavailable(STORE_ERROR / NO_NETWORK)` ;
   - `queryPurchasesAsync` au démarrage et après achat → `tier` (`PLUS` si un abonnement actif et reconnu existe) ;
   - `purchase()` lance `launchBillingFlow` (nécessite une `Activity` : passer par un `ActivityProvider` ou un canal d'événements vers `MainActivity`) ; `acknowledgePurchase` après succès ;
   - `restore()` = nouvelle `queryPurchasesAsync`.
4. Remplacer le binding Hilt dans `RepositoryModule` : `@Binds fun billing(impl: PlayBillingProvider): BillingProvider`.
5. Vérifier côté serveur si un backend existe (Supabase Edge Function + Google Play Developer API) pour éviter la fraude ; sinon la vérification locale suffit pour une première version.

Rien d'autre ne change : les écrans lisent `EntitlementsRepository.entitlements` et les fonctions `plus` se verrouillent d'elles-mêmes pour le palier `FREE` dès que `storeConfigured` passe à vrai.

## Points de verrouillage prévus (non encore appliqués)

Tant qu'aucune boutique n'existe, aucun écran ne verrouille. Quand `PlayBillingProvider` sera livré, brancher `Entitlements.locked(feature)` sur : réglage véhicule (types moto / taxi / PL, gabarit), bouton « Optimiser l'ordre », entrées « Mes trajets » et Replay, sélecteur de teinte. Chaque point affiche alors `PremiumTag` et ouvre l'écran Roadly+.
