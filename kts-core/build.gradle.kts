import java.util.Properties
/*
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
plugins {
    kotlin("multiplatform")
//    id("com.android.library")
//    id("kotlin-android-extensions")
    id("maven-publish")
}
val ktsTargetAndroid = (project.properties["ktsTargetAndroid"] as String).toBoolean()
val ktsTargetiOS = (project.properties["ktsTargetiOS"] as String).toBoolean()
repositories {
    gradlePluginPortal()
    google()
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlin")
    }
//    maven {
//        url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
//    }
}
group = "org.macrofocus.kts"
version = "0.1-SNAPSHOT"
kotlin {
    jvm() {
        if(!ktsTargetAndroid) {
            withJava()
        }
    }
    js {
        useCommonJs()
        browser {

        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("macos")
        hostOs == "Mac OS X" -> macosX64("native") {
            binaries {
                sharedLib {
                    baseName = "native"
                }
            }
        }
        hostOs == "Linux" -> linuxX64("native") {
            binaries {
                sharedLib {
                    baseName = "native"
                }
            }
        }
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    if(ktsTargetAndroid) {
        android()
    }
    if(ktsTargetiOS) {
        ios {
            binaries {
                framework {
                    baseName = "shared"
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

//        val macosMain by getting
//        val macosTest by getting

        val nativeMain by getting
        val nativeTest by getting

        if(ktsTargetAndroid) {
            val androidMain by getting {
                dependencies {
                    implementation(kotlin("stdlib-common"))
                }
            }
            val androidTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation("junit:junit:4.12")
                }
            }
        }

        if(ktsTargetiOS) {
            val iosMain by getting
            val iosTest by getting
        }
    }
}
val local = Properties()
val localProperties: File = rootProject.file("local.properties")
if (localProperties.exists()) {
    localProperties.inputStream().use { local.load(it) }
}
val archivaUser = local["archiva.user"] as String?
val archivaPassword = local["archiva.password"] as String?
publishing {
    repositories {
        maven("https://www.macrofocus.com/archiva/repository/snapshots/") {
            credentials {
                username = archivaUser
                password = archivaPassword
            }
        }
    }
}
if(ktsTargetAndroid) {
//    android {
//        compileSdkVersion(29)
//        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
//        defaultConfig {
//            minSdkVersion(24)
//            targetSdkVersion(29)
//            versionCode = 1
//            versionName = "1.0"
//        }
//        buildTypes {
//            getByName("release") {
//                isMinifyEnabled = false
//            }
//        }
//    }
}
//val packForXcode by tasks.creating(Sync::class) {
//    group = "build"
//    val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
//    val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
//    val targetName = "ios" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64"
//    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
//    inputs.property("mode", mode)
//    dependsOn(framework.linkTask)
//    val targetDir = File(buildDir, "xcode-frameworks")
//    from({ framework.outputDirectory })
//    into(targetDir)
//}
//tasks.getByName("build").dependsOn(packForXcode)