package org.cheburnet.passdpi.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun initializeKoin() {
    startKoin {
        declareNativeModules()
    }
}

expect fun KoinApplication.declareNativeModules()