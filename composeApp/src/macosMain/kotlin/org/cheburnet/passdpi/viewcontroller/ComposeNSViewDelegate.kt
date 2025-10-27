package org.cheburnet.passdpi.viewcontroller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import platform.AppKit.NSEvent
import platform.AppKit.NSEventModifierFlagCommand
import platform.AppKit.NSEventModifierFlagControl
import platform.AppKit.NSEventModifierFlagOption
import platform.AppKit.NSEventModifierFlagShift
import platform.AppKit.NSKeyDown
import platform.AppKit.NSKeyUp
import platform.AppKit.NSTrackingActiveAlways
import platform.AppKit.NSTrackingActiveInKeyWindow
import platform.AppKit.NSTrackingArea
import platform.AppKit.NSTrackingAssumeInside
import platform.AppKit.NSTrackingInVisibleRect
import platform.AppKit.NSTrackingMouseEnteredAndExited
import platform.AppKit.NSTrackingMouseMoved
import platform.AppKit.NSView
import platform.AppKit.NSWindow


@OptIn(InternalComposeUiApi::class)
class ComposeNSViewDelegate(
    window: NSWindow,
    content: @Composable () -> Unit,
) : LifecycleOwner {
    private var isDisposed = false
    private val macosTextInputService = MacosTextInputService()
    private val _windowInfo = MacosWindowInfoImpl().apply {
        isWindowFocused = true
    }

    private val platformContext: PlatformContext =
        object : PlatformContext by PlatformContext.Empty {
            override val windowInfo get() = _windowInfo
            override val textInputService get() = macosTextInputService
            /*
            override fun setPointerIcon(pointerIcon: PointerIcon) {
                val cursor = (pointerIcon as? MacosCursor)?.cursor ?: NSCursor.arrowCursor
                cursor.set()
            }

             */
        }
    private val skiaLayer = SkiaLayer()
    private val scene = CanvasLayersComposeScene(
        coroutineContext = Dispatchers.Main,
        platformContext = platformContext,
        invalidate = skiaLayer::needRedraw,
    )
    private val renderDelegate = object : SkikoRenderDelegate {
        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            val sizeInPx = IntSize(width, height)
            _windowInfo.containerSize = sizeInPx
            scene.size = sizeInPx // TODO: Move it out from onRender to avoid extra invalidation
            scene.render(canvas.asComposeCanvas(), nanoTime)
        }
    }

    override val lifecycle = LifecycleRegistry(this)

    @OptIn(ExperimentalForeignApi::class)
    internal val view = object : NSView(window.frame) {
        private var trackingArea: NSTrackingArea? = null
        override fun wantsUpdateLayer() = true
        override fun acceptsFirstResponder() = true
        override fun viewWillMoveToWindow(newWindow: NSWindow?) {
            updateTrackingAreas()
        }

        @OptIn(ExperimentalForeignApi::class)
        override fun updateTrackingAreas() {
            trackingArea?.let { removeTrackingArea(it) }
            trackingArea = NSTrackingArea(
                rect = bounds,
                options = NSTrackingActiveAlways or
                        NSTrackingMouseEnteredAndExited or
                        NSTrackingMouseMoved or
                        NSTrackingActiveInKeyWindow or
                        NSTrackingAssumeInside or
                        NSTrackingInVisibleRect,
                owner = this, userInfo = null
            )
            addTrackingArea(trackingArea!!)
        }

        override fun mouseDown(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Press, PointerButton.Primary)
        }

        override fun mouseUp(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Release, PointerButton.Primary)
        }

        override fun rightMouseDown(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Press, PointerButton.Secondary)
        }

        override fun rightMouseUp(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Release, PointerButton.Secondary)
        }

        override fun otherMouseDown(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Press, PointerButton(event.buttonNumber.toInt()))
        }

        override fun otherMouseUp(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Release, PointerButton(event.buttonNumber.toInt()))
        }

        override fun mouseMoved(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Move)
        }

        override fun mouseDragged(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Move)
        }

        override fun scrollWheel(event: NSEvent) {
            onMouseEvent(event, PointerEventType.Scroll)
        }

        override fun keyDown(event: NSEvent) {
            val consumed = onKeyboardEvent(event.toComposeEvent())
            if (!consumed) {
                // Pass only unconsumed event to system handler.
                // It will trigger the system's "beep" sound for unconsumed events.
                super.keyDown(event)
            }
        }

        override fun keyUp(event: NSEvent) {
            onKeyboardEvent(event.toComposeEvent())
        }
    }

    init {
        window.contentView = view

        skiaLayer.renderDelegate = renderDelegate
        skiaLayer.attachTo(view) // Should be called after attaching to window

        scene.density = Density(window.backingScaleFactor.toFloat())
        scene.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides this
            ) {
                content()
            }
        }
    }

    @Suppress("Unused")
    fun destroy() {
        check(!isDisposed) { "ComposeWindow is already disposed" }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        skiaLayer.detach()
        scene.close()
        isDisposed = true
    }

    @Suppress("Unused")
    fun resume() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @Suppress("Unused")
    fun stop() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun start() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun pause() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    private fun onKeyboardEvent(event: KeyEvent): Boolean {
        if (isDisposed) return false
        return scene.sendKeyEvent(event)
    }

    private fun onMouseEvent(
        event: NSEvent,
        eventType: PointerEventType,
        button: PointerButton? = null,
    ) {
        if (isDisposed) return
        scene.sendPointerEvent(
            eventType = eventType,
            position = event.offset.toOffset(scene.density),
            scrollDelta = Offset(x = event.deltaX.toFloat(), y = event.deltaY.toFloat()),
            nativeEvent = event,
            button = button,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private val NSEvent.offset: DpOffset get() {
        val position = locationInWindow.useContents {
            DpOffset(x = x.dp, y = y.dp)
        }
        val height = view.frame.useContents { size.height.dp }
        return DpOffset(
            x = position.x,
            y = height - position.y,
        )
    }
}

internal class MacosTextInputService : PlatformTextInputService {

    data class CurrentInput(
        var value: TextFieldValue,
        val onEditCommand: ((List<EditCommand>) -> Unit),
    )

    private var currentInput: CurrentInput? = null

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        currentInput = CurrentInput(
            value,
            onEditCommand
        )
    }

    override fun stopInput() {
        currentInput = null
    }

    override fun showSoftwareKeyboard() {
        //do nothing
    }

    override fun hideSoftwareKeyboard() {
        //do nothing
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        currentInput?.let { input ->
            input.value = newValue
        }
    }
}

internal fun NSEvent.toComposeEvent(): KeyEvent {
    return KeyEvent(
        nativeKeyEvent = InternalKeyEvent(
            key = Key(keyCode.toLong()),
            type = when (type) {
                NSKeyDown -> KeyEventType.KeyDown
                NSKeyUp -> KeyEventType.KeyUp
                else -> KeyEventType.Unknown
            },
            codePoint = characters?.firstOrNull()?.code ?: 0, // TODO: Support multichar CodePoint
            modifiers = toInputModifiers(),
            nativeEvent = this
        )
    )
}

private fun NSEvent.toInputModifiers() = PointerKeyboardModifiers(
    isAltPressed = modifierFlags and NSEventModifierFlagOption != 0UL,
    isShiftPressed = modifierFlags and NSEventModifierFlagShift != 0UL,
    isCtrlPressed = modifierFlags and NSEventModifierFlagControl != 0UL,
    isMetaPressed = modifierFlags and NSEventModifierFlagCommand != 0UL
)

internal data class InternalKeyEvent(
    val key: Key,
    val type: KeyEventType,
    val codePoint: Int,
    val modifiers: PointerKeyboardModifiers, // Reuse pointer modifiers

    val nativeEvent: Any? = null
)

@Stable
internal fun DpOffset.toOffset(density: Density): Offset = with(density) {
    if (isSpecified) {
        Offset(x.toPx(), y.toPx())
    } else {
        Offset.Unspecified
    }
}

internal class MacosWindowInfoImpl : WindowInfo {
    private val _containerSize = mutableStateOf(IntSize.Zero)

    override var isWindowFocused: Boolean by mutableStateOf(false)

    override var keyboardModifiers: PointerKeyboardModifiers
        get() = GlobalKeyboardModifiers.value
        set(value) {
            GlobalKeyboardModifiers.value = value
        }

    override var containerSize: IntSize
        get() = _containerSize.value
        set(value) {
            _containerSize.value = value
        }

    companion object {
        // One instance across all windows makes sense, since the state of KeyboardModifiers is
        // common for all windows.
        internal val GlobalKeyboardModifiers = mutableStateOf(PointerKeyboardModifiers())
    }
}