package org.cheburnet.passdpi

import android.app.Application
import org.cheburnet.passdpi.di.declareNativeModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@MainApplication)
            declareNativeModules()
        }
    }
}