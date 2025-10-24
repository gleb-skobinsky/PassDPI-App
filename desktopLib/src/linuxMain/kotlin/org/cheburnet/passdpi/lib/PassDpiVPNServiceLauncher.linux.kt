package org.cheburnet.passdpi.lib

class PassDpiVPNServiceLauncherLinux : PassDpiVPNServiceLauncher {
    override suspend fun startService(args: String): Boolean {
        // TODO: Not implemented
        return false
    }

    override suspend fun stopService(): Boolean {
        // TODO: Not implemented
        return false
    }
}

actual fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherLinux()