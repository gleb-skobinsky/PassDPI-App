plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    val nativeTargets = listOf(
        macosArm64(),
        macosX64(),
        iosArm64(),
        iosSimulatorArm64()
    )

    nativeTargets.forEach { nativeTarget ->
        nativeTarget.apply {
            compilations.getByName("main") {
                cinterops {
                    val libFdRetriever by creating {
                        definitionFile.set(
                            project.file("src/nativeInterop/cinterop/tunfd.def")
                        )
                    }
                }
            }
        }
    }
}