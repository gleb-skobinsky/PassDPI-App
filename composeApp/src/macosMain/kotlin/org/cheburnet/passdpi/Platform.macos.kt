package org.cheburnet.passdpi

actual fun getPlatform(): Platform = MacosPlatform

object MacosPlatform : Platform {
    override val name: String= "MacOS"
}

