package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ApiClient @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun get(url: HttpUrl): Outcome<String> = execute(Request.Builder().url(url).get().build())

    suspend fun postJson(url: HttpUrl, body: String): Outcome<String> = execute(
        Request.Builder().url(url).post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build(),
    )

    private suspend fun execute(request: Request): Outcome<String> = withContext(dispatchers.io) {
        try {
            client.newCall(request).await().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> Outcome.Success(text)
                    resp.code == 429 -> Outcome.Failure(AppError.RateLimited)
                    resp.code in 500..599 -> Outcome.Failure(AppError.ProviderUnavailable(request.url.host, resp.code))
                    else -> Outcome.Failure(AppError.Http(resp.code, text))
                }
            }
        } catch (e: SocketTimeoutException) {
            Outcome.Failure(AppError.Timeout)
        } catch (e: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { cancel() }
}
