pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    val kotlinVersion: String by settings

    plugins {
        kotlin("multiplatform") version kotlinVersion apply false
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:7.4.0")
            }
        }
    }
}
rootProject.name = "kts"
include("kts-core")
