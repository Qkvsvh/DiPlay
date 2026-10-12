package com.shilapi.xcertplay

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Turns the instrument panel's projection into DiPlay's display.
 *
 * <p>On `geely_fx11` the cluster keeps its own layer on top of Android's display 2 until the
 * head unit is told to project to it. The stock map does this through the DIM service
 * (`com.autolink.diminteraction`); a normal app can call the same binder directly: the
 * `DimNaviService` binder, `notifyDimProjectScreenSwitch` (transaction 9, an int). Without it a
 * `Presentation` on the cluster display stays invisible behind the factory dials.
 *
 * <p>Only the Geely device takes this route; everywhere else the cluster projection is driven by
 * the car's own cluster support.
 */
internal object GeelyClusterProjection {

    private const val TAG = "lynkPlay-Cluster"
    private const val SERVICE = "DimNaviService"
    private const val DESCRIPTOR = "ecarx.dimprotocol.custom.service.IDimNaviService"
    private const val NOTIFY_PROJECT_SCREEN_SWITCH = 9

    /**
     * Asks the head unit to show (or stop showing) the projected cluster. Returns whether the
     * call reached the service; a missing binder is not an error worth failing over.
     */
    fun set(on: Boolean): Boolean {
        val binder = service() ?: run {
            Log.i(TAG, "dim projection: DimNaviService is not registered")
            return false
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(if (on) 1 else 0)
            binder.transact(NOTIFY_PROJECT_SCREEN_SWITCH, data, reply, 0)
            reply.readException()
            Log.i(TAG, "dim projection: notifyDimProjectScreenSwitch($on) ok")
            true
        } catch (error: Throwable) {
            Log.w(TAG, "dim projection: notifyDimProjectScreenSwitch($on) failed", error)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** {@code android.os.ServiceManager.getService(String)} is a hidden API. */
    private fun service(): IBinder? = try {
        val serviceManager = Class.forName("android.os.ServiceManager")
        serviceManager.getMethod("getService", String::class.java).invoke(null, SERVICE) as? IBinder
    } catch (error: Throwable) {
        Log.w(TAG, "dim projection: ServiceManager.getService($SERVICE) failed", error)
        null
    }
}
