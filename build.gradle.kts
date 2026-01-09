plugins {
    kotlin("multiplatform") apply false
    id("com.android.kotlin.multiplatform.library") version "8.13.2" apply false
    id("org.jetbrains.dokka") apply false
    id("com.github.ben-manes.versions")
}
group = "org.macrofocus"
version = "0.2.0"

repositories {
    mavenCentral()
}
