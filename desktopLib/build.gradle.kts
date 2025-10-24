plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Windows and Linux are JVM for now.
    jvm()

    macosArm64()
    macosX64()

    @Suppress("unused")
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(projects.optionsStorage)
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
        val macosMain by creating {
            dependsOn(commonMain)
        }

        // MacOS variants share macosMain
        val macosX64Main by getting { dependsOn(macosMain) }
        val macosArm64Main by getting { dependsOn(macosMain) }
    }
}