// AGP нужен только Android-модулю. Кладём его в общий classpath корня, чтобы
// Kotlin-плагины видели его классы, но не требуем, если собираем без Android.
buildscript {
    if (providers.gradleProperty("withAndroid").getOrElse("true").toBoolean()) {
        repositories { google(); mavenCentral() }
        dependencies { classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}") }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
