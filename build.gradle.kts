buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.35.0")
    }
}
apply(plugin = "com.github.ben-manes.versions")

plugins {
    kotlin("multiplatform") version "1.4.10" apply false
    id("org.jetbrains.dokka") version "1.4.10.2" apply false
}
group = "org.macrofocus"
version = "0.1.0"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlin")
    }
//    maven {
//        url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
//    }
}
