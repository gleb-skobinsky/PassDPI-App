plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "com.github.gleb-skobinsky"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

enum class TargetOs {
    Windows,
    Linux,
    LinuxArm,
    Macos,
    MacosArm
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    var targetOs: TargetOs? = null
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> {
            targetOs = TargetOs.MacosArm
            macosArm64()
        }

        hostOs == "Mac OS X" && !isArm64 -> {
            targetOs = TargetOs.Macos
            macosX64()
        }

        hostOs == "Linux" && isArm64 -> {
            targetOs = TargetOs.LinuxArm
            linuxArm64()
        }

        hostOs == "Linux" && !isArm64 -> {
            targetOs = TargetOs.Linux
            linuxX64()
        }

        isMingwX64 -> {
            targetOs = TargetOs.Windows
            mingwX64()
        }

        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    if (targetOs != TargetOs.MacosArm) {
        macosArm64()
    }
    if (targetOs != TargetOs.Macos) {
        macosX64()
    }
    if (targetOs != TargetOs.LinuxArm) {
        linuxArm64()
    }
    if (targetOs != TargetOs.Linux) {
        linuxX64()
    }
    if (targetOs != TargetOs.Windows) {
        mingwX64()
    }

    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val libTun5socks by creating {
                    definitionFile.set(
                        project.file(
                            when (targetOs) {
                                TargetOs.MacosArm -> "src/nativeInterop/cinterop/hevsocks_macosarm.def"
                                TargetOs.Macos -> "src/nativeInterop/cinterop/hevsocks_macos_x64.def"
                                TargetOs.LinuxArm -> "" // TODO
                                TargetOs.Linux -> "" // TODO
                                TargetOs.Windows -> "" // TODO
                            }
                        )
                    )
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