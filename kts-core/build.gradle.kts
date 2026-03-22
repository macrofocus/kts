/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
plugins {
    kotlin("multiplatform")
//    id("com.android.kotlin.multiplatform.library")
//    id("kotlin-android-extensions")
    id("maven-publish")
}
val ktsTargetAndroid = (project.properties["ktsTargetAndroid"] as String?)?.toBoolean() ?: false
val ktsTargetiOS = (project.properties["ktsTargetiOS"] as String?)?.toBoolean() ?: false
repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}
group = "org.macrofocus"
version = "0.2.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("17"))
    }
    jvm() {
        if(!ktsTargetAndroid) {
    //        withJava()
        }
    }
    js(IR) {
        binaries.library()
        useEsModules()
        browser {

        }
    }
    if(ktsTargetAndroid) {
        androidTarget()
    }
//    if(ktsTargetiOS) {
//        ios {
//            binaries {
//                framework {
//                    baseName = "shared"
//                }
//            }
//        }
//    }

    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64()

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

//        val nativeMain by getting
//        val nativeTest by getting

        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        if(ktsTargetAndroid) {
            val androidMain by getting {
                dependencies {
                    implementation(kotlin("stdlib-common"))
                }
            }
            val androidTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation("junit:junit:4.13.2")
                }
            }
        }

        if(ktsTargetiOS) {
            val iosMain by getting
            val iosTest by getting
        }

//        iosX64Main { dependsOn(nativeMain) }
//        iosArm64Main { dependsOn(nativeMain) }
//        iosSimulatorArm64Main { dependsOn(nativeMain) }
        linuxX64Main { dependsOn(nativeMain) }
        linuxArm64Main { dependsOn(nativeMain) }
        macosX64Main { dependsOn(nativeMain) }
        macosArm64Main { dependsOn(nativeMain) }
        mingwX64Main { dependsOn(nativeMain) }

        // Tests (optional)
//        iosX64Test { dependsOn(nativeTest) }
//        iosArm64Test { dependsOn(nativeTest) }
//        iosSimulatorArm64Test { dependsOn(nativeTest) }
//        linuxX64Test { dependsOn(nativeTest) }
//        linuxArm64Test { dependsOn(nativeTest) }
//        macosX64Test { dependsOn(nativeTest) }
//        macosArm64Test { dependsOn(nativeTest) }
//        mingwX64Test { dependsOn(nativeTest) }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries {
            // Static library (.a)
            staticLib {
                baseName = "kts"  // Name of your library
            }

            // Shared library (.so, .dylib, .dll)
            sharedLib {
                baseName = "kts"
            }

            // Optional: For Apple (iOS/macOS) — produces .framework
            if (name.startsWith("ios") || name.startsWith("macos")) {
                framework {
                    baseName = "kts"
                    // embedBitcode("bitcode") // for iOS
                }
            }
        }
    }
}
if(ktsTargetAndroid) {
//    android {
//        compileSdkVersion(35)
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

tasks.getByName("jvmTest") {
    val skipTestsProvider = project.hasProperty("isProduction")
    onlyIf("mySkipTests property is not set") {
        !skipTestsProvider
    }
}