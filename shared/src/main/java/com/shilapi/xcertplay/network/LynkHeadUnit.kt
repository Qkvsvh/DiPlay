package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb

/**
 * Hard-coded behaviour for the Lynk & Co / Geely `geely_fx11` head unit (`CS11_A4`, Android 11)
 * that this branch targets. Two traits were confirmed on the car itself:
 *
 * 1. The Wi-Fi client switch must be on before a Wi-Fi Direct group can be created, but the car
 *    starts with it off. A normal app cannot turn it on through `WifiManager` on Android 10+, so
 *    when the car's own adbd is already approved we ask it to (`cmd wifi set-wifi-enabled enabled`).
 * 2. Media sent over the iPhone's realtime AAC stream (`type=102`) stops after about a second while
 *    video keeps running. The iPhone's buffered-audio stream (`type=103`, TCP) keeps playing, and a
 *    larger music buffer avoids needless underruns.
 *
 * Everything below is gated by [detected] so the same build keeps stock behaviour on other head
 * units (and in unit tests, where `Build` reports Robolectric).
 */
object LynkHeadUnit {
    private const val TAG = "lynkPlay-Lynk"

    /** Wi-Fi Direct channel 149 (5 GHz): the car's framework rejects the automatically tried 36. */
    const val PREFERRED_WIFI_P2P_CHANNEL = 149

    /** Music jitter buffer used by default on this car. */
    const val MEDIA_BUFFER_MILLIS = 2000

    /** CarPlay shows this name under the car button instead of the stock "BYD". */
    const val OEM_LABEL = "LYNK & CO"

    private val identifiers: List<String>
        get() = runCatching {
            listOf(
                Build.PRODUCT,      // geely_fx11
                Build.DEVICE,       // msmnile_gvmq
                Build.MODEL,        // CS11_A4
                Build.MANUFACTURER,
                Build.BRAND,
            ).map { it.lowercase() }
        }.getOrDefault(emptyList())

    val detected: Boolean = identifiers.any { id ->
        id.contains("geely") || id.contains("lynk") || id == "cs11_a4" || id == "msmnile_gvmq"
    }

    fun isWifiEnabled(context: Context): Boolean =
        runCatching { context.getSystemService(WifiManager::class.java)?.isWifiEnabled }.getOrNull() == true

    /**
     * Asks the head unit's own adbd to switch Wi-Fi on. This never opens the car's approval dialog;
     * it is a no-op when adbd is off, listening only on USB, or the key was not approved.
     */
    fun enableWifiThroughAdb(context: Context): Boolean {
        if (!detected) return false
        val adb = runCatching { LocalAdb(AdbKeys.load(context)) }.getOrNull() ?: return false
        return try {
            if (adb.connect(mayAsk = false) != LocalAdb.Access.READY) {
                false
            } else {
                adb.shell(ENABLE_WIFI_COMMAND) != null
            }
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { adb.close() }
        }
    }

    /** Polls the framework until it reports Wi-Fi on, or the timeout expires. */
    fun awaitWifiEnabled(context: Context, timeoutMillis: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (true) {
            if (isWifiEnabled(context)) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            try {
                Thread.sleep(WIFI_POLL_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
    }

    /** Turns Wi-Fi on if it is off, then waits for the framework to report it. */
    fun ensureWifiEnabled(context: Context, timeoutMillis: Long): Boolean {
        if (isWifiEnabled(context)) return true
        Log.i(TAG, "Wi-Fi is off; asking the head unit to switch it on")
        enableWifiThroughAdb(context)
        return awaitWifiEnabled(context, timeoutMillis)
    }

    private const val ENABLE_WIFI_COMMAND = "cmd wifi set-wifi-enabled enabled"
    private const val WIFI_POLL_MILLIS = 250L
}
