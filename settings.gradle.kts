pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }

    val kotlinVersion: String by settings
    val dokkaVersion: String by settings
    val versionsVersion: String by settings

    plugins {
        kotlin("multiplatform") version kotlinVersion apply false
        id("org.jetbrains.dokka") version dokkaVersion apply false
        id("io.github.ben-manes.versions") version versionsVersion apply false
    }
}

rootProject.name = "kts"
include("kts-core")
