package fr.roadly.feature.settings

/** Ce que le téléphone permet de constater sur Android Auto (sans accès aux réglages internes d'Android Auto). */
data class AndroidAutoFacts(
    /** Le service CarAppService de Roadly est présent dans la version installée. */
    val carServiceInstalled: Boolean,
    /** Version de l'application Android Auto, null si elle est absente. */
    val androidAutoVersion: String?,
    /** Paquet qui a installé Roadly (com.android.vending = Play Store), null si inconnu. */
    val installerPackage: String?,
    val roadlyVersion: String,
)

enum class CheckStatus { OK, ACTION, BLOCKING }

data class AutoCheck(val title: String, val detail: String, val status: CheckStatus)

/** Diagnostic « Roadly n'apparaît pas dans Android Auto » : vérifie ce qui est vérifiable, explique le reste. */
object AndroidAutoReadiness {
    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
    const val PLAY_STORE = "com.android.vending"

    fun checks(f: AndroidAutoFacts): List<AutoCheck> = buildList {
        add(
            if (f.carServiceInstalled) AutoCheck("Roadly ${f.roadlyVersion} contient Android Auto", "Le service Android Auto de Roadly est installé sur ce téléphone.", CheckStatus.OK)
            else AutoCheck("Cette version de Roadly n'a pas Android Auto", "Installez la dernière version (Réglages › Mises à jour, ou la release GitHub 0.1.174 ou plus récente).", CheckStatus.BLOCKING),
        )
        add(
            if (f.androidAutoVersion != null) AutoCheck("Android Auto ${f.androidAutoVersion} installé", "L'application Android Auto est présente.", CheckStatus.OK)
            else AutoCheck("Android Auto introuvable", "Installez ou mettez à jour « Android Auto » depuis le Play Store.", CheckStatus.BLOCKING),
        )
        if (f.installerPackage != PLAY_STORE) {
            add(AutoCheck(
                "« Sources inconnues » à activer dans Android Auto",
                "Roadly ne vient pas du Play Store : Android Auto le masque tant que cette option de développeur n'est pas activée. Roadly ne peut pas vérifier ce réglage lui-même.",
                CheckStatus.ACTION,
            ))
        }
    }

    /** Étapes à suivre sur le téléphone (Android Auto n'offre aucun accès direct à ses options de développeur). */
    val steps = listOf(
        "Ouvrez Android Auto : bouton ci-dessous, ou Paramètres › Plus de connexions (ou Appareils connectés) › Android Auto.",
        "Faites défiler jusqu'à « Version » et touchez-la 10 fois, puis acceptez : le mode développeur est activé.",
        "Menu ⋮ en haut à droite › Paramètres pour les développeurs › cochez « Sources inconnues ».",
        "Débranchez puis rebranchez le téléphone (ou relancez Android Auto) : Roadly apparaît dans le lanceur. S'il n'y est pas, Android Auto › Personnaliser le lanceur › affichez Roadly.",
    )
}
