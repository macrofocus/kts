plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") version "8.3.1" apply false
    id("org.jetbrains.dokka") apply false
    id("com.github.ben-manes.versions")
}
group = "org.macrofocus"
version = "0.1.0"

repositories {
    mavenCentral()
}
