package app.gamenative.ui.screen.xserver

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.ExternalController
import com.winlator.inputcontrols.ExternalControllerBinding
import com.winlator.inputcontrols.GamepadState
import com.winlator.math.Mathf
import com.winlator.winhandler.WinHandler
import com.winlator.xserver.XServer

/**
 * Standalone handler for physical controller input that works independently of view visibility.
 * Applies profile bindings to convert physical controller input into virtual gamepad state.
 */
class PhysicalControllerHandler(
    private var profile: ControlsProfile?,
    private val xServer: XServer?,
    private val onOpenNavigationMenu: (() -> Unit)? = null
) {
    private val TAG = "gncontrol"

    fun setProfile(profile: ControlsProfile?) {
        this.profile = profile
        Log.d(TAG, "PhysicalControllerHandler: Profile set to ${profile?.name}")
    }

    /**
     * Handle physical controller button events.
     * Extracted from InputControlsView.onKeyEvent()
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (profile != null && event.repeatCount == 0) {
            val controller = profile?.getController(event.deviceId)
            if (controller != null) {
                val controllerBinding = controller.getControllerBinding(event.keyCode)
                if (controllerBinding != null) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        handleInputEvent(controllerBinding.binding, true)
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        handleInputEvent(controllerBinding.binding, false)
                    }
                    return true
                }
            }
        }
        return false
    }

    /**
     * Handle physical controller analog stick and trigger events.
     * Extracted from InputControlsView.onGenericMotionEvent()
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (profile != null) {
            val controller = profile?.getController(event.deviceId)
            if (controller != null && controller.updateStateFromMotionEvent(event)) {
                // Process trigger buttons (L2/R2)
                var controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_L2)
                if (controllerBinding != null) {
                    handleInputEvent(
                        controllerBinding.binding,
                        controller.state.isPressed(ExternalController.IDX_BUTTON_L2.toInt())
                    )
                }

                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_R2)
                if (controllerBinding != null) {
                    handleInputEvent(
                        controllerBinding.binding,
                        controller.state.isPressed(ExternalController.IDX_BUTTON_R2.toInt())
                    )
                }

                // Process analog stick input
                processJoystickInput(controller)
                return true
            }
        }
        return false
    }

    /**
     * Process analog stick input and apply bindings.
     * Extracted from InputControlsView.processJoystickInput()
     */
    private fun processJoystickInput(controller: ExternalController) {
        val axes = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y
        )
        val values = floatArrayOf(
            controller.state.thumbLX,
            controller.state.thumbLY,
            controller.state.thumbRX,
            controller.state.thumbRY,
            controller.state.dPadX.toFloat(),
            controller.state.dPadY.toFloat()
        )

        for (i in axes.indices) {
            var controllerBinding: ExternalControllerBinding?
            if (Math.abs(values[i]) > ControlElement.STICK_DEAD_ZONE) {
                controllerBinding = controller.getControllerBinding(
                    ExternalControllerBinding.getKeyCodeForAxis(axes[i], Mathf.sign(values[i]))
                )
                if (controllerBinding != null) {
                    handleInputEvent(controllerBinding.binding, true, values[i])
                }
            } else {
                controllerBinding = controller.getControllerBinding(
                    ExternalControllerBinding.getKeyCodeForAxis(axes[i], 1.toByte())
                )
                if (controllerBinding != null) {
                    handleInputEvent(controllerBinding.binding, false, values[i])
                }
                controllerBinding = controller.getControllerBinding(
                    ExternalControllerBinding.getKeyCodeForAxis(axes[i], (-1).toByte())
                )
                if (controllerBinding != null) {
                    handleInputEvent(controllerBinding.binding, false, values[i])
                }
            }
        }
    }

    /**
     * Apply a binding to the virtual gamepad state and send to WinHandler.
     * Extracted from InputControlsView.handleInputEvent()
     */
    private fun handleInputEvent(binding: Binding, isActionDown: Boolean, offset: Float = 0f) {
        if (binding.isGamepad) {
            val winHandler = xServer?.winHandler
            val state = profile?.gamepadState

            if (state != null) {
                val buttonIdx = binding.ordinal - Binding.GAMEPAD_BUTTON_A.ordinal
                if (buttonIdx <= ExternalController.IDX_BUTTON_R2.toInt()) {
                    when (buttonIdx) {
                        ExternalController.IDX_BUTTON_L2.toInt() -> {
                            state.triggerL = if (isActionDown) 1.0f else 0f
                            state.setPressed(ExternalController.IDX_BUTTON_L2.toInt(), isActionDown)
                        }
                        ExternalController.IDX_BUTTON_R2.toInt() -> {
                            state.triggerR = if (isActionDown) 1.0f else 0f
                            state.setPressed(ExternalController.IDX_BUTTON_R2.toInt(), isActionDown)
                        }
                        else -> state.setPressed(buttonIdx, isActionDown)
                    }
                } else {
                    when (binding) {
                        Binding.GAMEPAD_LEFT_THUMB_UP, Binding.GAMEPAD_LEFT_THUMB_DOWN -> {
                            state.thumbLY = if (isActionDown) offset else 0f
                        }
                        Binding.GAMEPAD_LEFT_THUMB_LEFT, Binding.GAMEPAD_LEFT_THUMB_RIGHT -> {
                            state.thumbLX = if (isActionDown) offset else 0f
                        }
                        Binding.GAMEPAD_RIGHT_THUMB_UP, Binding.GAMEPAD_RIGHT_THUMB_DOWN -> {
                            state.thumbRY = if (isActionDown) offset else 0f
                        }
                        Binding.GAMEPAD_RIGHT_THUMB_LEFT, Binding.GAMEPAD_RIGHT_THUMB_RIGHT -> {
                            state.thumbRX = if (isActionDown) offset else 0f
                        }
                        Binding.GAMEPAD_DPAD_UP, Binding.GAMEPAD_DPAD_RIGHT,
                        Binding.GAMEPAD_DPAD_DOWN, Binding.GAMEPAD_DPAD_LEFT -> {
                            state.dpad[binding.ordinal - Binding.GAMEPAD_DPAD_UP.ordinal] = isActionDown
                        }
                        else -> {}
                    }
                }

                if (winHandler != null) {
                    val controller = winHandler.currentController
                    if (controller != null) {
                        controller.state.copy(state)
                    }
                    winHandler.sendGamepadState()
                    winHandler.sendVirtualGamepadState(state)
                }
            }
        } else {
            // Handle special bindings
            if (binding == Binding.OPEN_NAVIGATION_MENU) {
                if (isActionDown) {
                    Log.d(TAG, "Opening navigation menu from controller binding")
                    onOpenNavigationMenu?.invoke()
                }
            } else {
                // For keyboard/mouse bindings, inject into XServer
                val pointerButton = binding.pointerButton
                if (isActionDown) {
                    if (pointerButton != null) {
                        xServer?.injectPointerButtonPress(pointerButton)
                    } else {
                        xServer?.injectKeyPress(binding.keycode)
                    }
                } else {
                    if (pointerButton != null) {
                        xServer?.injectPointerButtonRelease(pointerButton)
                    } else {
                        xServer?.injectKeyRelease(binding.keycode)
                    }
                }
            }
        }
    }
}
