plugins {
    kotlin("multiplatform") apply false
    id("com.android.kotlin.multiplatform.library") version "9.1.0" apply false
    id("org.jetbrains.dokka") apply false
    id("io.github.ben-manes.versions")
}
group = "org.macrofocus"
version = "0.2.1"
repositories {
    mavenCentral()
}
