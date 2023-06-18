plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") version "7.4.0" apply false
    id("org.jetbrains.dokka") version "1.4.32" apply false
    id("com.github.ben-manes.versions")
}
group = "org.macrofocus"
version = "0.1.0"

repositories {
    mavenCentral()
}
