buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0")
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.38.0")
    }
}
apply(plugin = "com.github.ben-manes.versions")

plugins {
    kotlin("multiplatform") version "1.5.0" apply false
    id("org.jetbrains.dokka") version "1.4.32" apply false
}
group = "org.macrofocus"
version = "0.1.0"

repositories {
    mavenCentral()
}
