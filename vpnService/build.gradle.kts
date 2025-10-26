plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {

    // Windows and Linux are JVM for now.
    jvm()

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
            }
        }

        // MacOS variants share macosMain
        val macosX64Main by getting { dependsOn(appleMain) }
        val macosArm64Main by getting { dependsOn(appleMain) }
        val iosSimulatorArm64Main by getting { dependsOn(appleMain) }
        val iosArm64Main by getting { dependsOn(appleMain) }
    }
}

tasks.register<Exec>("buildHevSocks5Tunnel") {
    group = "build"
    description = "Build the hev-socks5-tunnel native library"

    // Path to the submodule
    val submoduleDir = project.file("gitsubmodules/hevsockstunnel")

    // Output directory (optional)
    val buildDir = File(submoduleDir, "build")

    workingDir = submoduleDir

    // Clean + build
    commandLine("make", "clean")
    commandLine("make")

    doFirst {
        println("🛠️  Building hev-socks5-tunnel C library in: $submoduleDir")
    }

    doLast {
        if (!File(buildDir, "libhev_socks5_tunnel.a").exists()) {
            throw GradleException("❌ libhev_socks5_tunnel.a was not built! Check your Makefile.")
        } else {
            println("✅ hev-socks5-tunnel build completed successfully.")
        }
    }
}