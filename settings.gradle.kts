pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Roadly"
include(
    ":app",
    ":core:model", ":core:common", ":core:designsystem", ":core:data",
    ":core:location", ":core:map", ":core:geocoding", ":core:routing", ":core:navigation", ":core:scooter", ":core:assistant", ":core:radar",
    ":feature:home", ":feature:search", ":feature:route", ":feature:settings", ":feature:navigation", ":feature:assistant", ":feature:trips", ":feature:car", ":feature:onboarding",
)
