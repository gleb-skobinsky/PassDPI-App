plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {

    jvm("windows")

    macosArm64()
    macosX64()

    linuxArm64()
    linuxX64()

    @Suppress("unused")
    sourceSets {
        val commonMain by getting
        // Windows
        val windowsMain by getting {
            dependsOn(commonMain)
        }

        // Linux (for linuxArm64 + linuxX64)
        val linuxMain by creating {
            dependsOn(commonMain)
        }

        // macOS (for both x64 and arm64)
        val macosMain by creating {
            dependsOn(commonMain)
        }

        // Linux variants share macosMain
        val macosX64Main by getting { dependsOn(macosMain) }
        val macosArm64Main by getting { dependsOn(macosMain) }

        // Linux variants share linuxMain
        val linuxX64Main by getting { dependsOn(linuxMain) }
        val linuxArm64Main by getting { dependsOn(linuxMain) }
    }
}