package fr.roadly.core.common

sealed class AppError {
    data object Network : AppError()
    data object Timeout : AppError()
    data object RateLimited : AppError()
    data class ProviderUnavailable(val providerId: String, val httpCode: Int? = null) : AppError()
    data class Http(val code: Int, val body: String) : AppError()
    data object PermissionDenied : AppError()
    data object NoResult : AppError()
    data class Parse(val detail: String) : AppError()
    data class Unknown(val detail: String?) : AppError()
}
