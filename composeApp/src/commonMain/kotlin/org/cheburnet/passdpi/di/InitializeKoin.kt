package org.cheburnet.passdpi.di

import org.cheburnet.passdpi.lib.ServiceLauncherModule
import org.cheburnet.passdpi.presentation.mainScreen.MainViewModel
import org.cheburnet.passdpi.presentation.settingsScreen.SettingsViewModel
import org.cheburnet.passdpi.store.PassDpiStoreModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

@Suppress("Unused") // used in Swift
fun initializeKoin() {
    startKoin {
        declareAllModules()
    }
}

fun KoinApplication.declareAllModules() {
    declareNativeModules()
    declareCommonModules()
}

fun KoinApplication.declareNativeModules() {
    modules(ServiceLauncherModule)
}

fun KoinApplication.declareCommonModules() {
    modules(
        PassDpiStoreModule,
        PresentationModule
    )
}

private val PresentationModule = module {
    viewModel { MainViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
}