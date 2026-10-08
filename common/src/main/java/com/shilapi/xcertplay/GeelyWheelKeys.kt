package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.geely.lib.oneosapi.input.IInputListener
import com.geely.lib.oneosapi.input.IInputManager
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.network.LynkHeadUnit

/**
 * Geely/Lynk steering-wheel media keys.
 *
 * The head unit maps the wheel's keys to vendor key codes and routes them only through its own input
 * stack - no standard Android KeyEvent is produced - so a media session or accessibility service
 * never sees them. Its input service (`com.geely.inputservice.InputService`) is exported and its
 * `registerListener` has no permission check, so we subscribe to the raw codes there (exactly how
 * the car's own `KeyInputController` does) and forward them to the active CarPlay session.
 *
 * Silently does nothing off this head unit ([LynkHeadUnit.detected]).
 */
internal object GeelyWheelKeys {
    private const val TAG = "lynkPlay-GeelyWheel"
    private const val INPUT_PACKAGE = "com.geely.inputservice"
    private const val INPUT_CLASS = "com.geely.inputservice.InputService"

    // KeyCode.KEYCODE_R_MEDIA_* in the head unit's com.geely.lib.oneosapi.input.
    private const val KEY_PLAY_PAUSE = 200085
    private const val KEY_NEXT = 200087
    private const val KEY_PREVIOUS = 200088
    // KEYCODE_R_VOICE_ASSIST: the steering-wheel microphone key (long press used to open HiCar).
    private const val KEY_VOICE_ASSIST = 200231
    private val KEY_CODES = intArrayOf(KEY_PLAY_PAUSE, KEY_NEXT, KEY_PREVIOUS, KEY_VOICE_ASSIST)

    private const val RETRY_MILLIS = 3_000L

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var bound = false
    private var manager: IInputManager? = null
    private var listener: IInputListener? = null

    /** Binds while a CarPlay session is active; safe to call repeatedly. */
    @Synchronized
    fun start(context: Context) {
        if (!LynkHeadUnit.detected) return
        appContext = context.applicationContext
        if (bound) return
        bindLocked()
    }

    /** Unbinds when the CarPlay session ends. */
    @Synchronized
    fun stop() {
        listener?.let { registered ->
            val packageName = appContext?.packageName ?: return@let
            runCatching { manager?.unregisterListener(registered, packageName) }
        }
        listener = null
        manager = null
        handler.removeCallbacksAndMessages(null)
        val context = appContext
        if (bound && context != null) runCatching { context.unbindService(connection) }
        bound = false
    }

    private fun bindLocked() {
        val context = appContext ?: return
        val intent = Intent().setComponent(ComponentName(INPUT_PACKAGE, INPUT_CLASS))
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)
        Log.i(TAG, "bind geely input service requested=$bound")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val context = appContext ?: return
            val input = IInputManager.Stub.asInterface(service)
            val registered = object : IInputListener.Stub() {
                override fun onKeyCodeEvent(keyCode: Int, event: Int, softKeyFunction: Int) {}
                override fun onShortClick(keyCode: Int, softKeyFunction: Int) {
                    forward(keyCode)
                }
                override fun onHoldingPressStarted(keyCode: Int, softKeyFunction: Int) {}
                override fun onHoldingPressStopped(keyCode: Int, softKeyFunction: Int) {}
                override fun onLongPressTriggered(keyCode: Int, softKeyFunction: Int) {
                    Log.i(TAG, "long press key=$keyCode func=$softKeyFunction")
                    // The microphone key's long press: Siri instead of the (disabled) HiCar.
                    if (keyCode == KEY_VOICE_ASSIST) {
                        Log.i(TAG, "Siri requested=${CarPlayMediaKeys.onExternalSiriKey()}")
                    }
                }
                override fun onDoubleClick(keyCode: Int, softKeyFunction: Int) {}
            }
            synchronized(this@GeelyWheelKeys) {
                manager = input
                listener = registered
            }
            runCatching { input.registerListener(registered, context.packageName, KEY_CODES) }
                .onSuccess { Log.i(TAG, "registered for wheel media keys") }
                .onFailure { Log.w(TAG, "registerListener failed", it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            val retry: Boolean
            synchronized(this@GeelyWheelKeys) {
                manager = null
                listener = null
                retry = appContext != null
            }
            if (retry) {
                // The car service is persistent; rebind shortly after an unexpected drop.
                handler.postDelayed({
                    synchronized(this@GeelyWheelKeys) {
                        if (appContext != null && manager == null) {
                            bound = false
                            bindLocked()
                        }
                    }
                }, RETRY_MILLIS)
            }
        }
    }

    private fun forward(keyCode: Int) {
        val index = when (keyCode) {
            KEY_PLAY_PAUSE -> CarPlayMediaButton.PLAY_PAUSE
            KEY_NEXT -> CarPlayMediaButton.NEXT
            KEY_PREVIOUS -> CarPlayMediaButton.PREVIOUS
            else -> return
        }
        CarPlayMediaKeys.onExternalMediaKey(index)
    }
}
