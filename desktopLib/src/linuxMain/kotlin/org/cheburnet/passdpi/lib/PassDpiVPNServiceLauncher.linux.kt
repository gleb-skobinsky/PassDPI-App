package org.cheburnet.passdpi.lib

class PassDpiVPNServiceLauncherLinux : PassDpiVPNServiceLauncher {
    override fun startService(args: String): Boolean {
        // TODO: Not implemented
        return false
    }

    override fun stopService(): Boolean {
        // TODO: Not implemented
        return false
    }
}

actual fun PassDpiVPNServiceLauncher(): PassDpiVPNServiceLauncher = PassDpiVPNServiceLauncherLinux()