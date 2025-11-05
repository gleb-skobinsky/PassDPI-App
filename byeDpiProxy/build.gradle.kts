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
    val nativeTargets = listOf(
        macosArm64(),
        macosX64(),
        linuxArm64(),
        linuxX64(),
        mingwX64(),
        iosArm64(),
        iosSimulatorArm64()
    )

    nativeTargets.forEach { nativeTarget ->
        nativeTarget.apply {
            compilations.getByName("main") {
                cinterops {
                    val libByeDpiProxy by creating {
                        definitionFile.set(
                            project.file(
                                when (nativeTarget.konanTarget) {
                                    KonanTarget.MACOS_ARM64 -> "src/nativeInterop/cinterop/byedpi_macosarm.def"
                                    KonanTarget.MACOS_X64 -> "src/nativeInterop/cinterop/byedpi_macos_x86_64.def"
                                    KonanTarget.IOS_ARM64 -> "src/nativeInterop/cinterop/byedpi_iphoneosarm.def"
                                    KonanTarget.IOS_SIMULATOR_ARM64 -> "src/nativeInterop/cinterop/byedpi_iphonesimulatorarm.def"
                                    KonanTarget.LINUX_ARM64 -> "src/nativeInterop/cinterop/byedpi_linuxarm.def"
                                    KonanTarget.LINUX_X64 -> "src/nativeInterop/cinterop/byedpi_linux_x86_64.def"
                                    KonanTarget.MINGW_X64 -> "src/nativeInterop/cinterop/byedpi_mingw.def"
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