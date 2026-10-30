package fr.roadly.core.designsystem.component

import fr.roadly.core.common.AppError

fun AppError.userMessage(): String = when (this) {
    AppError.Network -> "Pas de connexion réseau."
    AppError.Timeout -> "Le service met trop de temps à répondre."
    AppError.RateLimited -> "Service temporairement limité, réessayez dans quelques secondes."
    is AppError.ProviderUnavailable -> "Le service « $providerId » est indisponible."
    is AppError.Http -> "Erreur du service ($code)."
    AppError.PermissionDenied -> "Autorisation de localisation refusée."
    AppError.NoResult -> "Aucun résultat."
    is AppError.Parse -> "Réponse du service illisible."
    is AppError.Unknown -> "Erreur inattendue."
}
