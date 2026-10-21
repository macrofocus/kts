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
val ktsTargetAndroid = (project.findProperty("ktsTargetAndroid") as String?)?.toBoolean() ?: false
val ktsTargetiOS = (project.findProperty("ktsTargetiOS") as String?)?.toBoolean() ?: false
repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}
group = "org.macrofocus"
version = "0.20.0"

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
    js {
        binaries.library()
        useEsModules()
        browser {

        }
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
        browser()
        nodejs()
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

    // Share the pure-Kotlin actual declarations between the native and wasm targets
    applyDefaultHierarchyTemplate {
        common {
            group("nativeAndWasm") {
                group("native")
                withWasmJs()
            }
        }
    }

    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest = getByName("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain = getByName("jvmMain") {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest = getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain = getByName("jsMain") {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jsTest = getByName("jsTest") {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

//        val macosMain = getByName("macosMain")
//        val macosTest = getByName("macosTest")

//        val nativeMain = getByName("nativeMain")
//        val nativeTest = getByName("nativeTest")

        nativeMain {
//            dependsOn(commonMain)
        }

        nativeTest {
//            dependsOn(commonTest)
        }

        if(ktsTargetAndroid) {
            val androidMain = getByName("androidMain") {
                dependencies {
                    implementation(kotlin("stdlib-common"))
                }
            }
            val androidTest = getByName("androidTest") {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation("junit:junit:4.13.2")
                }
            }
        }

        if(ktsTargetiOS) {
            val iosMain = getByName("iosMain")
            val iosTest = getByName("iosTest")
        }

//        iosX64Main { dependsOn(nativeMain) }
//        iosArm64Main { dependsOn(nativeMain) }
//        iosSimulatorArm64Main { dependsOn(nativeMain) }
        linuxX64Main {
//            dependsOn(nativeMain)
        }
        linuxArm64Main {
//            dependsOn(nativeMain)
        }
        macosX64Main {
//            dependsOn(nativeMain)
        }
        macosArm64Main {
//            dependsOn(nativeMain)
        }
        mingwX64Main {
//            dependsOn(nativeMain)
        }

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
//val packForXcode = tasks.create("packForXcode")(Sync::class) {
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
tasks.named<Test>("jvmTest") {
    // JUnit 3 base classes without test methods (from the JTS test suite)
    exclude("**/OverlayNGTestCase.class")
    // Known JTS failures and performance/robustness stress tests: they also fail with jts-core
    exclude("test/jts/fail/**", "test/jts/perf/**")
}

// Binary compatibility check against JTS: the JTS Java tests are compiled against the official
// jts-core jar and the resulting classes are run once against jts-core and once against kts-core.
// Any test that fails only with kts-core (typically NoSuchMethodError, IncompatibleClassChangeError,
// IllegalAccessError, ...) is reported. Run with: ./gradlew :kts-core:jtsCompat
val jtsVersion = "1.20.0"
val jtsCompatJts = configurations.create("jtsCompatJts")
val jtsCompatJunit = configurations.create("jtsCompatJunit")
dependencies {
    jtsCompatJts("org.locationtech.jts:jts-core:$jtsVersion")
    jtsCompatJunit("junit:junit:4.13.2")
}
val jtsCompatDir = layout.buildDirectory.dir("jtsCompat")

val compileJtsCompatTests = tasks.register<JavaCompile>("compileJtsCompatTests") {
    description = "Compiles the Java tests against jts-core $jtsVersion"
    // AffineTransformationTest has been adapted to KTS and no longer compiles against JTS
    source(fileTree("src/jvmTest/java") { exclude("**/AffineTransformationTest.java") }, "src/jtsCompat/java")
    classpath = jtsCompatJts + jtsCompatJunit
    destinationDirectory.set(jtsCompatDir.map { it.dir("classes") })
    options.encoding = "UTF-8"
    options.compilerArgs.add("-nowarn")
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
}

fun registerJtsCompatRun(name: String, implementation: FileCollection) = tasks.register<JavaExec>(name) {
    description = "Runs the JTS-compiled Java tests against $name"
    val classesDir = compileJtsCompatTests.flatMap { it.destinationDirectory }
    val results = jtsCompatDir.map { it.file("$name.txt") }
    val log = jtsCompatDir.map { it.file("$name.log") }
    inputs.files(classesDir, implementation)
    outputs.files(results, log)
    classpath = files(classesDir, "src/jvmTest/resources") + jtsCompatJunit + implementation
    mainClass.set("JtsCompatRunner")
    workingDir = projectDir
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(classesDir.get().asFile.path, results.get().asFile.path, log.get().asFile.path)
    })
}
val jtsCompatRunJts = registerJtsCompatRun("jts", jtsCompatJts)
val jtsCompatRunKts = registerJtsCompatRun("kts", files(tasks.named("jvmJar")) + configurations["jvmRuntimeClasspath"])

tasks.register("jtsCompat") {
    group = "verification"
    description = "Checks that kts-core can replace jts-core $jtsVersion for code compiled against JTS"
    dependsOn(jtsCompatRunJts, jtsCompatRunKts)
    val jtsResults = jtsCompatDir.map { it.file("jts.txt").asFile }
    val ktsResults = jtsCompatDir.map { it.file("kts.txt").asFile }
    doLast {
        val jts = jtsResults.get().readLines()
        val kts = ktsResults.get().readLines()
        val onlyKts = kts.filter { !it.startsWith("TOTAL") } - jts.toSet()
        val onlyJts = jts.filter { !it.startsWith("TOTAL") } - kts.toSet()
        logger.lifecycle("jts-core: ${jts.first()}, ${jts.size - 1} failures; kts-core: ${kts.first()}, ${kts.size - 1} failures")
        if (onlyJts.isNotEmpty()) {
            logger.lifecycle("Failing only with jts-core:\n" + onlyJts.joinToString("\n"))
        }
        if (onlyKts.isNotEmpty() || jts.first() != kts.first()) {
            throw GradleException("Failing only with kts-core (see build/jtsCompat/kts.log):\n" + onlyKts.joinToString("\n"))
        }
    }
}
