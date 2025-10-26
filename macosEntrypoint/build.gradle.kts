plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    listOf(
        macosArm64(),
        macosX64()
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "ComposeAppMac"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
//            implementation(compose.foundation)
//            implementation(compose.material3)
//            implementation(compose.ui)
//            implementation(compose.components.resources)
//            implementation(compose.components.uiToolingPreview)
//            implementation(libs.androidx.lifecycle.viewmodelCompose)
//            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        macosMain.dependencies {}
    }
}