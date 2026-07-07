plugins {
    kotlin("multiplatform") apply false
    id("com.android.kotlin.multiplatform.library") version "9.0.0" apply false
    id("org.jetbrains.dokka") apply false
    id("com.github.ben-manes.versions")
}
group = "org.macrofocus"
version = "0.2.1"
repositories {
    mavenCentral()
}
