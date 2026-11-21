package fr.roadly.core.data.network

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())

    companion object {
        /**
         * `Roadly/<version> (+<contact>)` : les services publics (FOSSGIS, Nominatim, Overpass…) exigent un User-Agent qui
         * identifie l'application et permet de joindre son responsable. [contact] vient de la compilation
         * (`BuildConfig.CONTACT`, propriété Gradle `roadly.contact`) : jamais d'adresse inventée dans le code.
         */
        fun userAgent(version: String, contact: String): String = "Roadly/$version (+$contact)"
    }
}
