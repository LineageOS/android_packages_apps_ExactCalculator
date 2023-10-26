/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.application") version "8.1.2"
    id("org.jetbrains.kotlin.android") version "1.7.10"
}

android {
    compileSdk = 34
    namespace = "com.android.calculator2"

    defaultConfig {
        applicationId = "com.android.calculator2"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard.flags"
                )
            )
        }
        getByName("debug") {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
            java.srcDirs("src")
            manifest.srcFile("AndroidManifest.xml")
            res.srcDirs("res")
        }
    }
}

dependencies {
    implementation(files("cr.jar"))

    implementation("androidx.webkit:webkit:1.7.0")
    implementation("com.google.android.material:material:1.9.0")
}
