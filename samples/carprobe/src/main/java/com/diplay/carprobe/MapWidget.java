package com.diplay.carprobe;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.view.MotionEvent;
import android.view.Surface;

/**
 * The stock map's cross-app Surface service.
 *
 * <p>{@code com.geely.map} exports {@code AutoSimilarWidgetService} (action
 * {@code com.similar.widget.view.service}) with no permission check. A host hands it a Surface
 * and the map renders into it: {@code sourceType = 1} is the HUD path (engine 3, 200x185 in the
 * shipped config), {@code sourceType = 0} is the widget path (engine 4, 1920x720).
 */
final class MapWidget {

    static final String PACKAGE = "com.geely.map";
    static final String CLASS = "com.autonavi.amapauto.adapter.internal.widget.AutoSimilarWidgetService";

    static final String DESCRIPTOR = "com.autosimilarwidget.view.IAutoSimilarWidgetViewService";
    static final int ADD_SURFACE = 1;
    static final int REMOVED_SURFACE = 2;
    static final int IS_MAP_RUNNING = 3;
    static final int SET_WIDGET_STATE_CONTROL = 4;
    static final int DISPATCH_TOUCH_EVENT = 5;

    static final String PROVIDER_DESCRIPTOR = "com.autosimilarwidget.view.IAutoWidgetStateProvider";

    interface Listener {
        void onMapService(boolean connected);
    }

    private IBinder service;
    private final Context context;
    private Listener listener;
    private boolean bound;
    private WidgetStateProvider provider;

    MapWidget(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean isConnected() {
        return service != null;
    }

    void bind(Listener callback) {
        listener = callback;
        if (service != null) {
            callback.onMapService(true);
            return;
        }
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(PACKAGE, CLASS));
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            ProbeLog.i("bindService(" + PACKAGE + "/" + CLASS + ") -> " + bound);
            if (!bound) {
                callback.onMapService(false);
            }
        } catch (Throwable error) {
            ProbeLog.e("bindService(" + PACKAGE + ")", error);
            callback.onMapService(false);
        }
    }

    void unbind() {
        if (!bound) {
            return;
        }
        try {
            context.unbindService(connection);
        } catch (Throwable error) {
            ProbeLog.e("map unbindService", error);
        }
        bound = false;
        service = null;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = binder;
            ProbeLog.i("map widget service connected: " + name.flattenToShortString());
            provider = new WidgetStateProvider();
            for (int sourceType = 0; sourceType <= 1; sourceType++) {
                final int source = sourceType;
                Ipc.probe("setWidgetStateControl(provider, sourceType=" + sourceType + ")", service,
                        DESCRIPTOR, SET_WIDGET_STATE_CONTROL, data -> {
                            data.writeStrongBinder(provider);
                            data.writeInt(source);
                        }, Ipc.VOID);
            }
            if (listener != null) {
                listener.onMapService(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            ProbeLog.i("map widget service disconnected: " + name.flattenToShortString());
            if (listener != null) {
                listener.onMapService(false);
            }
        }
    };

    void addSurface(Surface surface, int sourceType, int mode) {
        Ipc.probe("map addSurface(sourceType=" + sourceType + ", mode=" + mode + ", surface=" + surface + ")",
                service, DESCRIPTOR, ADD_SURFACE, data -> {
                    Ipc.writeSurface(data, surface);
                    data.writeInt(sourceType);
                    data.writeInt(mode);
                }, Ipc.VOID);
    }

    void removedSurface(Surface surface, int sourceType, int mode) {
        Ipc.probe("map removedSurface(sourceType=" + sourceType + ", mode=" + mode + ")",
                service, DESCRIPTOR, REMOVED_SURFACE, data -> {
                    Ipc.writeSurface(data, surface);
                    data.writeInt(sourceType);
                    data.writeInt(mode);
                }, Ipc.VOID);
    }

    void isMapRunning(int sourceType) {
        Ipc.probe("map isMapRunning(sourceType=" + sourceType + ")", service, DESCRIPTOR, IS_MAP_RUNNING,
                data -> data.writeInt(sourceType), Ipc.BOOL);
    }

    void dispatchTouch(MotionEvent event, int sourceType) {
        Ipc.probe("map dispatchTouchEvent(" + event.getAction() + ", sourceType=" + sourceType + ")",
                service, DESCRIPTOR, DISPATCH_TOUCH_EVENT, data -> {
                    if (event == null) {
                        data.writeInt(0);
                    } else {
                        data.writeInt(1);
                        event.writeToParcel(data, 0);
                    }
                    data.writeInt(sourceType);
                }, Ipc.VOID);
    }

    /** The map's first-frame / pause / operate-result callback. */
    static final class WidgetStateProvider extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 1598968902) {
                try {
                    reply.writeString(PROVIDER_DESCRIPTOR);
                } catch (Throwable ignored) {
                    // nothing to do
                }
                return true;
            }
            try {
                data.enforceInterface(PROVIDER_DESCRIPTOR);
                switch (code) {
                    case 1:
                        ProbeLog.i("map callback onWidgetFirstFrameDrawn()");
                        break;
                    case 2:
                        ProbeLog.i("map callback onWidgetRenderPause()");
                        break;
                    case 3:
                        ProbeLog.i("map callback onReceiveWidgetOperateResult(" + data.readInt() + ")");
                        break;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException();
            } catch (Throwable error) {
                ProbeLog.e("map callback failed", error);
            }
            return true;
        }
    }

    /** {@code IMultiWindowClusterListener} for the OneOS expand requests. */
    static final class ClusterExpandListener extends Binder {
        static final String DESCRIPTOR = "com.geely.lib.oneosapi.setting.IMultiWindowClusterListener";

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 1598968902) {
                try {
                    reply.writeString(DESCRIPTOR);
                } catch (Throwable ignored) {
                    // nothing to do
                }
                return true;
            }
            try {
                data.enforceInterface(DESCRIPTOR);
                String pkg = data.readString();
                switch (code) {
                    case 1:
                        ProbeLog.i("cluster onAbandonExpand(" + pkg + ")");
                        break;
                    case 2:
                        ProbeLog.i("cluster onCancelExpand(" + pkg + ")");
                        break;
                    case 3:
                        ProbeLog.i("cluster onExpand(" + pkg + ")");
                        break;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException();
            } catch (Throwable error) {
                ProbeLog.e("cluster expand callback failed", error);
            }
            return true;
        }
    }
}
