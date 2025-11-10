import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {

    // Windows and Linux are JVM for now.
    jvm()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    macosArm64()
    macosX64()
    iosArm64()
    iosSimulatorArm64()

    @Suppress("unused")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(projects.optionsStorage)
                implementation(libs.koin.core)
                implementation(libs.logger)
            }
        }
        val jvmMain by getting {
            dependsOn(commonMain)
        }
        // Windows
        val windowsMain by creating {
            dependsOn(jvmMain)
        }

        // Linux (for linuxArm64 + linuxX64)
        val linuxMain by creating {
            dependsOn(jvmMain)
        }

        // macOS (for both x64 and arm64)
        val appleMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(projects.tunnelInterop)
                implementation(projects.byeDpiProxy)
            }
        }

        // MacOS variants share macosMain
        val macosX64Main by getting { dependsOn(appleMain) }
        val macosArm64Main by getting { dependsOn(appleMain) }
        val iosSimulatorArm64Main by getting { dependsOn(appleMain) }
        val iosArm64Main by getting { dependsOn(appleMain) }
    }
}

android {
    namespace = "org.cheburnet.passdpi.vpnService"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}