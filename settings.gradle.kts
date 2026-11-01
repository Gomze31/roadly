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
    ":core:location", ":core:map", ":core:geocoding", ":core:routing", ":core:navigation", ":core:scooter",
    ":feature:home", ":feature:search", ":feature:route", ":feature:settings", ":feature:navigation", ":feature:trips",
)
