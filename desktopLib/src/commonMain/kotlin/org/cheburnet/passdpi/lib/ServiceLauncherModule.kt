package org.cheburnet.passdpi.lib

import org.koin.dsl.module

val ServiceLauncherModule = module {
    single<PassDpiVPNServiceLauncher> {
        PassDpiVPNServiceLauncher(get())
    }
}