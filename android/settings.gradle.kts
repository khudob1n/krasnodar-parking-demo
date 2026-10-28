pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "krasnodar-parking"

// Общая логика: модели, разбор каталога, поиск, форматирование, политика
// офлайн-кэша. Платформенно-независима, чтобы позже подключить iOS.
include(":shared")

// Android-приложение требует Android SDK и Android Gradle Plugin. Без них
// (например, в CI только для общей логики) запускайте с -PwithAndroid=false.
if (providers.gradleProperty("withAndroid").getOrElse("true").toBoolean()) {
    include(":app")
}
