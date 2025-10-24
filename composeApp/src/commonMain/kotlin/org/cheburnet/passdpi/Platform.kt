package org.cheburnet.passdpi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform