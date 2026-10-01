package com.swordfish.lemuroid.app.shared.input

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

class InputBindingUpdater(
    private val inputDeviceManager: InputDeviceManager,
    intent: Intent,
    /**
     * Scope owned by the application, not by the activity. Both binding activities call
     * `finish()` as soon as [handleKeyEvent] returns, so a lifecycle scope would cancel the
     * write and the binding would be silently lost.
     */
    private val scope: CoroutineScope,
) {
    val extras = parseExtras(intent)

    fun getTitle(context: Context): String {
        val keyName = InputKey(extras.retroKey).displayName()
        return context.getString(R.string.gamepad_binding_update_title, keyName)
    }

    fun getMessage(context: Context): String {
        return context.getString(R.string.gamepad_binding_update_description, extras.device.name)
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        Timber.d("Received input binding event: $event ${event.device}")
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> onKeyDown(event)
            KeyEvent.ACTION_UP -> onKeyUp(event)
            else -> false
        }
    }

    private fun onKeyDown(event: KeyEvent): Boolean {
        return isTargetedDevice(event.device)
    }

    private fun onKeyUp(event: KeyEvent): Boolean {
        if (!isTargetedDevice(event.device)) return false

        val device = requireNotNull(event.device)
        val retroKey = RetroKey(extras.retroKey)
        val inputKey = InputKey(event.keyCode)

        scope.safeLaunch {
            inputDeviceManager.updateBinding(device, retroKey, inputKey)
        }

        return true
    }

    private fun isTargetedDevice(device: InputDevice?): Boolean {
        return device != null && extras.device.name == device.name
    }

    private fun parseExtras(intent: Intent): IntentExtras {
        val device =
            intent.extras?.getParcelable<InputDevice>(REQUEST_DEVICE)
                ?: throw IllegalArgumentException("REQUEST_DEVICE has not been passed")

        val retroKey =
            intent.extras?.getInt(REQUEST_RETRO_KEY)
                ?: throw IllegalArgumentException("REQUEST_RETRO_KEY has not been passed")

        return IntentExtras(device, retroKey)
    }

    data class IntentExtras(val device: InputDevice, val retroKey: Int)

    companion object {
        const val REQUEST_DEVICE = "REQUEST_DEVICE"
        const val REQUEST_RETRO_KEY = "REQUEST_RETRO_KEY"
    }
}
