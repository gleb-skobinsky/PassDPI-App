package org.cheburnet.passdpi

import org.cheburnet.passdpi.viewcontroller.ComposeNSViewDelegate
import platform.AppKit.NSWindow

fun AttachMainComposeView(
    window: NSWindow
): ComposeNSViewDelegate = ComposeNSViewDelegate(
    window = window,
    content = { App() }
)