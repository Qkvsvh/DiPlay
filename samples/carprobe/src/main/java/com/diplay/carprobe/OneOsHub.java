package com.diplay.carprobe;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/**
 * The Geely OneOS API hub ({@code com.geely.service.oneosapi}, a system app) hands out every
 * car service by number. The stock map talks to it through {@code com.geely.lib.oneosapi};
 * here we only need {@code IServiceManager.getService(type)} plus two of the services:
 * the navigation server (HUD/widget Surfaces) and the setting manager (HUD state and adjust).
 */
final class OneOsHub {

    static final String PACKAGE = "com.geely.service.oneosapi";
    static final String CLASS = "com.geely.service.oneosapi.OneOSApiService";

    static final String DESCRIPTOR = "com.geely.lib.oneosapi.IServiceManager";
    static final int GET_SERVICE = 2;
    static final int GET_PERMISSION_SERVICE = 3;
    static final int REGISTER_CALLBACK = 4;
    static final int UNREGISTER_CALLBACK = 5;

    static final int TYPE_NAVI = 1;
    static final int TYPE_SETTING_MANAGER = 41;

    static final String MYCAR_DESCRIPTOR = "com.geely.lib.oneosapi.setting.IMyCarService";
    static final int MYCAR_REGISTER_MULTI_WINDOW_DISPLAY_LISTENER = 10;
    static final int MYCAR_REQUEST_SUPPORT_EXPAND = 12;
    static final int MYCAR_ABANDON_EXPAND = 14;
    static final int MYCAR_IS_HUD_ACTIVE = 42;
    static final int MYCAR_IS_HUD_BRIGHTNESS_POSITION_ADJUST_ON = 43;
    static final int MYCAR_SET_HUD_BRIGHTNESS_ADJUST = 44;
    static final int MYCAR_SET_HUD_POSITION_ADJUST = 45;
    static final int MYCAR_IS_HUD_ANGEL_ADJUST_ON = 46;
    static final int MYCAR_SET_HUD_ANGEL_ADJUST = 47;

    static final String NAVI_DESCRIPTOR = "com.geely.lib.oneosapi.navi.INaviServer";
    static final int NAVI_ADD_SURFACE = 46;
    static final int NAVI_ADD_SURFACE_WITH_SOURCE_TYPE = 68;
    static final int NAVI_REMOVED_SURFACE_WITH_SOURCE_TYPE = 70;

    /** {@code com.geely.lib.oneosapi.navi.model.base.SourceType}: 0 = widget, 1 = HUD. */
    static final int SOURCE_WIDGET = 0;
    static final int SOURCE_HUD = 1;

    interface Listener {
        void onHubReady(boolean connected);
    }

    private IBinder hub;
    private final Context context;
    private Listener listener;
    private boolean bound;

    OneOsHub(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean isConnected() {
        return hub != null;
    }

    /** The hub binder itself, so its interface descriptor can be logged. */
    IBinder raw() {
        return hub;
    }

    void bind(Listener callback) {
        listener = callback;
        if (hub != null) {
            callback.onHubReady(true);
            return;
        }
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(PACKAGE, CLASS));
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            ProbeLog.i("bindService(" + PACKAGE + "/" + CLASS + ") -> " + bound);
            if (!bound) {
                callback.onHubReady(false);
            }
        } catch (Throwable error) {
            ProbeLog.e("bindService(" + PACKAGE + ")", error);
            callback.onHubReady(false);
        }
    }

    void unbind() {
        if (!bound) {
            return;
        }
        try {
            context.unbindService(connection);
        } catch (Throwable error) {
            ProbeLog.e("unbindService(" + PACKAGE + ")", error);
        }
        bound = false;
        hub = null;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            hub = service;
            ProbeLog.i("OneOS hub connected: " + name.flattenToShortString());
            if (listener != null) {
                listener.onHubReady(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            hub = null;
            ProbeLog.i("OneOS hub disconnected: " + name.flattenToShortString());
            if (listener != null) {
                listener.onHubReady(false);
            }
        }
    };

    /** {@code IServiceManager.getService(type)} through the hub, or a direct binder as fallback. */
    IBinder service(int type) {
        if (hub == null) {
            ProbeLog.i("OneOS hub is not connected yet");
            return null;
        }
        Ipc.Result result = Ipc.call(hub, DESCRIPTOR, GET_SERVICE, data -> data.writeInt(type), Ipc.BINDER);
        ProbeLog.i("IServiceManager.getService(" + type + ") -> " + result.text());
        return result.ok() && result.value instanceof IBinder ? (IBinder) result.value : null;
    }

    /** {@code IServiceManager.getPermissionService(type, pkg)}: the permission-checked variant. */
    IBinder permissionService(int type, String pkg) {
        if (hub == null) {
            return null;
        }
        Ipc.Result result = Ipc.call(hub, DESCRIPTOR, GET_PERMISSION_SERVICE,
                data -> {
                    data.writeInt(type);
                    data.writeString(pkg == null ? "" : pkg);
                }, Ipc.BINDER);
        ProbeLog.i("IServiceManager.getPermissionService(" + type + ", " + pkg + ") -> " + result.text());
        return result.ok() && result.value instanceof IBinder ? (IBinder) result.value : null;
    }

    void registerCallback(IBinder callback) {
        if (hub == null || callback == null) {
            return;
        }
        Ipc.probe("IServiceManager.registerCallback(cb)", hub, DESCRIPTOR, REGISTER_CALLBACK,
                data -> data.writeStrongBinder(callback), Ipc.VOID);
    }

    void unregisterCallback(IBinder callback) {
        if (hub == null || callback == null) {
            return;
        }
        Ipc.probe("IServiceManager.unregisterCallback(cb)", hub, DESCRIPTOR, UNREGISTER_CALLBACK,
                data -> data.writeStrongBinder(callback), Ipc.VOID);
    }

    // --- setting manager ---------------------------------------------------

    static void readHudState(IBinder myCar) {
        if (myCar == null) {
            return;
        }
        Ipc.probe("IMyCarService.isHudActive()", myCar, MYCAR_DESCRIPTOR, MYCAR_IS_HUD_ACTIVE, null, Ipc.BOOL);
        Ipc.probe("IMyCarService.isHudBrightnessAndPositionAdjustOn()", myCar, MYCAR_DESCRIPTOR,
                MYCAR_IS_HUD_BRIGHTNESS_POSITION_ADJUST_ON, null, Ipc.BOOL);
        Ipc.probe("IMyCarService.isHudAngelAdjustOn()", myCar, MYCAR_DESCRIPTOR,
                MYCAR_IS_HUD_ANGEL_ADJUST_ON, null, Ipc.BOOL);
    }

    /** The adjust calls behave like a key press: keyStatus (0/1 = up/down) and keyCode. */
    static void adjustHud(IBinder myCar, int which, int keyStatus, int keyCode) {
        if (myCar == null) {
            return;
        }
        int code;
        switch (which) {
            case 0:
                code = MYCAR_SET_HUD_BRIGHTNESS_ADJUST;
                break;
            case 1:
                code = MYCAR_SET_HUD_POSITION_ADJUST;
                break;
            default:
                code = MYCAR_SET_HUD_ANGEL_ADJUST;
                break;
        }
        Ipc.probe("IMyCarService hud adjust#" + code + "(keyStatus=" + keyStatus + ", keyCode=" + keyCode + ")",
                myCar, MYCAR_DESCRIPTOR, code,
                data -> {
                    data.writeInt(keyStatus);
                    data.writeInt(keyCode);
                }, Ipc.VOID);
    }

    static void requestSupportExpand(IBinder myCar, String packageName, int showMode, IBinder clusterListener) {
        if (myCar == null) {
            return;
        }
        Ipc.probe("IMyCarService.requestSupportExpand(" + packageName + ", " + showMode + ")", myCar,
                MYCAR_DESCRIPTOR, MYCAR_REQUEST_SUPPORT_EXPAND, data -> {
                    data.writeString(packageName);
                    data.writeInt(showMode);
                    data.writeStrongBinder(clusterListener);
                }, Ipc.BOOL);
    }

    static void abandonExpand(IBinder myCar, String packageName) {
        if (myCar == null) {
            return;
        }
        Ipc.probe("IMyCarService.abandonExpand(" + packageName + ")", myCar, MYCAR_DESCRIPTOR,
                MYCAR_ABANDON_EXPAND, data -> data.writeString(packageName), Ipc.BOOL);
    }

    // --- navi server -------------------------------------------------------

    static void addSurfaceWithSourceType(IBinder navi, android.view.Surface surface, int sourceType, int type, int height, int width) {
        if (navi == null) {
            return;
        }
        Ipc.probe("INaviServer.addSurfaceWithSourceType(sourceType=" + sourceType + ", type=" + type
                        + ", " + width + "x" + height + ")", navi, NAVI_DESCRIPTOR, NAVI_ADD_SURFACE_WITH_SOURCE_TYPE,
                data -> {
                    Ipc.writeSurface(data, surface);
                    data.writeInt(sourceType);
                    data.writeInt(type);
                    data.writeInt(height);
                    data.writeInt(width);
                }, Ipc.BOOL);
    }

    static void removedSurfaceWithSourceType(IBinder navi, android.view.Surface surface, int sourceType, int type) {
        if (navi == null) {
            return;
        }
        Ipc.probe("INaviServer.removedSurfaceWithSourceType(sourceType=" + sourceType + ", type=" + type + ")",
                navi, NAVI_DESCRIPTOR, NAVI_REMOVED_SURFACE_WITH_SOURCE_TYPE, data -> {
                    Ipc.writeSurface(data, surface);
                    data.writeInt(sourceType);
                    data.writeInt(type);
                }, Ipc.BOOL);
    }

    /** {@code IServiceManagerCallback}: tells us when a service type is added or removed. */
    static final class ServiceManagerCallback extends Binder {
        static final String CB_DESCRIPTOR = "com.geely.lib.oneosapi.IServiceManagerCallback";

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 1598968902) {
                try {
                    reply.writeString(CB_DESCRIPTOR);
                } catch (Throwable ignored) {
                    // nothing to do
                }
                return true;
            }
            try {
                data.enforceInterface(CB_DESCRIPTOR);
                if (code == 1) {
                    // registerCallback is oneway: no reply is written.
                    ProbeLog.i("IServiceManagerCallback.onServiceChanged(type=" + data.readInt() + ")");
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            } catch (Throwable error) {
                ProbeLog.e("IServiceManagerCallback failed", error);
            }
            return true;
        }
    }
}
