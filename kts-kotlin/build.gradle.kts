/*
 * Copyright (c) 2026 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
// Kotlin-specific extensions to kts-core: a type-safe builder DSL for geometries, operators and collection-style
// extensions, allocation-free fast paths (a packed Hilbert R-tree, point and region locators, path emission), geography,
// GeoJSON, spatial joins, and coroutine/Flow variants of the bulk operations
plugins {
    kotlin("multiplatform")
    // For the @Serializable classes of the tests (as auth and molap, with the Kotlin version)
    kotlin("plugin.serialization") version "2.4.21"
    id("maven-publish")
}
repositories {
    mavenCentral()
}
group = "org.macrofocus"
version = "0.22.0"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }
    js {
        binaries.library()
        useEsModules()
        browser()
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        binaries.library()
        browser()
        nodejs()
    }
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":kts-core"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${project.property("coroutinesVersion")}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${project.property("serializationJsonVersion")}")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${project.property("coroutinesVersion")}")
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    // The benchmarks only print their timings: run with -Pkts.benchmark=true
    systemProperty("kts.benchmark", providers.gradleProperty("kts.benchmark").getOrElse("false"))
}
