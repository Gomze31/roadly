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
        add(
            if (f.installerPackage == PLAY_STORE) AutoCheck(
                "Roadly est marqué « installé par le Play Store »",
                "Android Auto l'accepte. Pour garder cette marque, installez les mises à jour avec UnknownAuto, pas avec le bouton « Installer » de Roadly.",
                CheckStatus.OK,
            ) else AutoCheck(
                "Android Auto refuse Roadly installé par APK",
                "Depuis Android Auto 17.9, les applications de navigation installées hors Play Store sont masquées, même avec « Sources inconnues » " +
                    "(journal Android Auto : « Package DENIED »). Réinstallez Roadly avec UnknownAuto : l'installation est alors marquée comme venant du Play Store.",
                CheckStatus.BLOCKING,
            ),
        )
    }

    /** Lien de l'outil libre (MIT) qui installe un APK avec la marque « Play Store » via Shizuku, sans ordinateur ni root. */
    const val UNKNOWN_AUTO_URL = "https://github.com/GitMag/UnknownAuto/releases/latest"
    const val SHIZUKU_PLAY_URL = "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
    const val ROADLY_RELEASES_URL = "https://github.com/Gomze31/roadly/releases/latest"

    /**
     * Étapes à suivre sur le téléphone. Android Auto (17.9 et suivantes) n'affiche une application de navigation installée hors Play
     * Store que si Android la croit installée par le Play Store ; cette marque ne se pose qu'à l'installation (UnknownAuto + Shizuku).
     */
    val steps = listOf(
        "Installez Shizuku depuis le Play Store, puis démarrez-le par « Débogage sans fil » en suivant ses explications (le téléphone doit être en Wi-Fi ; aucun ordinateur).",
        "Installez UnknownAuto (application libre, GitHub GitMag/UnknownAuto) et autorisez-le dans Shizuku.",
        "Téléchargez l'APK de Roadly (GitHub, dernière version) et installez-le avec UnknownAuto : la mise à jour garde vos favoris et votre historique.",
        "Débranchez puis rebranchez le téléphone : Roadly apparaît dans la voiture (sinon Android Auto › Personnaliser le lanceur › cochez Roadly).",
        "Pour les mises à jour suivantes, passez aussi par UnknownAuto : le bouton « Installer » de Roadly retirerait la marque et Roadly disparaîtrait d'Android Auto.",
    )
}
