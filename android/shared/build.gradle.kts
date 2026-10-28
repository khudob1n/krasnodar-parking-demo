plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Android-приложение потребляет JVM-вариант модуля. Когда начнём iOS,
    // добавим iosArm64()/iosSimulatorArm64() — код commonMain к этому готов:
    // в нём нет java.* и android.*.
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Тест на настоящем снимке каталога из data/snapshot.
tasks.named<Test>("jvmTest") {
    systemProperty("catalog.snapshot", rootDir.resolve("../data/snapshot/parkings.json").absolutePath)
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
