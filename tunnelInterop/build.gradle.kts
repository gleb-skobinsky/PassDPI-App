import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "com.github.gleb-skobinsky"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val includedSystems = mutableSetOf<KonanTarget>()
    val nativeTargets = when {
        hostOs == "Mac OS X" -> {
            listOf(
                macosArm64().also { includedSystems.add(it.konanTarget) },
                macosX64().also { includedSystems.add(it.konanTarget) }
            )
        }

        hostOs == "Linux" && isArm64 -> {
            listOf(linuxArm64().also { includedSystems.add(it.konanTarget) })
        }

        hostOs == "Linux" && !isArm64 -> {
            listOf(linuxX64().also { includedSystems.add(it.konanTarget) })
        }

        isMingwX64 -> {
            listOf(mingwX64().also { includedSystems.add(it.konanTarget) })
        }

        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    // Even if the host OS is different from the possible C interop target, we declare it
    // in order that consuming modules can import the interop successfully
    if (KonanTarget.MACOS_ARM64 !in includedSystems) {
        macosArm64()
    }
    if (KonanTarget.MACOS_X64 !in includedSystems) {
        macosX64()
    }
    if (KonanTarget.LINUX_ARM64 !in includedSystems) {
        linuxArm64()
    }
    if (KonanTarget.LINUX_X64 !in includedSystems) {
        linuxX64()
    }
    if (KonanTarget.MINGW_X64 !in includedSystems) {
        mingwX64()
    }

    nativeTargets.forEach { nativeTarget ->
        nativeTarget.apply {
            compilations.getByName("main") {
                cinterops {
                    val libTun5socks by creating {
                        definitionFile.set(
                            project.file(
                                when (nativeTarget.konanTarget) {
                                    KonanTarget.MACOS_ARM64 -> "src/nativeInterop/cinterop/hevsocks_macosarm.def"
                                    KonanTarget.MACOS_X64 -> "src/nativeInterop/cinterop/hevsocks_macos_x86_64.def"
                                    KonanTarget.LINUX_ARM64 -> "" // TODO
                                    KonanTarget.LINUX_X64 -> "" // TODO
                                    KonanTarget.MINGW_X64 -> "" // TODO
                                    else -> error("Unsupported target")
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

tasks.register<Exec>("buildHevSocks5TunnelUniversal") {
    group = "build"
    description = "Build universal macOS binary for hev-socks5-tunnel"

    val submoduleDir = project.file("hev-socks5-tunnel")

    doFirst {
        println("⚙️ Building macOS universal binary...")
    }

    commandLine(
        "bash", "-c",
        """
        cd $submoduleDir
        make clean
        ./build-apple-raw.sh
        """.trimIndent()
    )
}