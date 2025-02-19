//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

group = "io.zenoh"
version = "0.11.0-dev"

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.adarshr.test-logger")
    id("org.jetbrains.dokka")
    id("org.mozilla.rust-android-gradle.rust-android")
    `maven-publish`
}

android {
    namespace = "io.zenoh"
    compileSdk = 30

    ndkVersion = "26.0.10792818"

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

cargo {
    pythonCommand = "python3"
    module = "../zenoh-jni"
    libname = "zenoh-jni"
    targetIncludes = arrayOf("libzenoh_jni.so")
    targetDirectory = "../zenoh-jni/target/"
    profile = "release"
    targets = arrayListOf(
        "arm",
        "arm64",
        "x86",
        "x86_64",
    )
}

kotlin {
    jvmToolchain(11)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            val zenohPaths = "../zenoh-jni/target/debug"
            jvmArgs("-Djava.library.path=$zenohPaths")
        }
    }
    androidTarget {
        publishLibraryVariants("release")
    }

    @Suppress("Unused")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("commons-net:commons-net:3.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jvmMain by getting {
            resources.srcDir("../zenoh-jni/target/release").include(arrayListOf("*.dylib", "*.so", "*.dll"))

            // The line below is intended to load the native libraries that are crosscompiled on GitHub actions when publishing a JVM package.
            resources.srcDir("../jni-libs").include("*/**")
        }
        val jvmTest by getting {
            resources.srcDir("../zenoh-jni/target/debug").include(arrayListOf("*.dylib", "*.so", "*.dll"))
        }
    }

    publishing {
        repositories {
            maven {
                name = "GithubPackages"
                url = uri("https://maven.pkg.github.com/eclipse-zenoh/zenoh-java")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

tasks.withType<Test> {
    doFirst {
        // The line below is added for the Android Unit tests which are equivalent to the JVM tests.
        // For them to work we need to specify the path to the native library as a system property and not as a jvmArg.
        systemProperty("java.library.path", "../zenoh-jni/target/debug")
    }
}

tasks.whenObjectAdded {
    if ((this.name == "mergeDebugJniLibFolders" || this.name == "mergeReleaseJniLibFolders")) {
        this.dependsOn("cargoBuild")
        this.inputs.dir(buildDir.resolve("rustJniLibs/android"))
    }
}
