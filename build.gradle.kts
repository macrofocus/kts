buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.github.ben-manes:gradle-versions-plugin:0.33.0")
    }
}
apply(plugin = "com.github.ben-manes.versions")

plugins {
    kotlin("multiplatform") version "1.4.10" apply false
    id("org.jetbrains.dokka") version "1.4.0" apply false
}
group = "org.macrofocus"
version = "0.1-SNAPSHOT"

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
