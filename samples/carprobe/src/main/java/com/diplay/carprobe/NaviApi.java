package com.diplay.carprobe;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * The OneOS navigation server ({@code com.geely.lib.oneosapi.navi.INaviServer}): the stock map's
 * route to the instrument cluster and HUD.
 *
 * <p>Two shapes reach the service:
 * <ul>
 *   <li>plain AIDL methods ({@code switchARNavigation}, {@code getGuideInfo},
 *       {@code addNaviEventNotify}) reached by descriptor + transaction code; and</li>
 *   <li>{@code invokeNaviAPI(TransferModel)} (transaction 64), where the client hand-builds a
 *       {@code TransferModel} that wraps a {@code NaviBaseModel} subclass. The stock client
 *       ({@code NaviAPI.switchDimNaviMode}, {@code switchMapARGuide}, {@code getGuideInfo},
 *       {@code switchMapPresentation}) uses this for the DIM/HUD display mode.</li>
 * </ul>
 *
 * <p>No vendor classes are copied here: the argument layout mirrors the generated
 * {@code onTransact} branches read out of {@code com.geely.map}'s bundled OneOS library.
 */
final class NaviApi {

    static final String DESCRIPTOR = "com.geely.lib.oneosapi.navi.INaviServer";

    static final int TRANSACTION_REGISTER_NAVI_OBSERVER = 1;
    static final int TRANSACTION_UNREGISTER_NAVI_OBSERVER = 2;
    static final int TRANSACTION_GET_GUIDE_INFO = 9;
    static final int TRANSACTION_MAP_IS_LAUNCHED = 18;
    static final int TRANSACTION_SWITCH_AR_NAVIGATION = 27;
    static final int TRANSACTION_GET_PACKAGE_NAME = 45;
    static final int TRANSACTION_INVOKE_NAVI_API = 64;
    static final int TRANSACTION_INVOKE_NAVI_API_ASYNC = 65;
    static final int TRANSACTION_ADD_NAVI_EVENT_NOTIFY = 66;
    static final int TRANSACTION_REMOVE_NAVI_EVENT_NOTIFY = 67;

    /** {@code NaviProtocolID} / per-model protocol ids. */
    static final int PROTO_MAP_OPERA_SWITCH_AR = 3007;
    static final int PROTO_GET_GUIDE_INFO = 3407;
    static final int PROTO_SWITCH_MAP_PRESENTATION = 3425;
    static final int PROTO_NAVI_EVENT_CONFIG = 4000;
    static final int PROTO_DIM_DISPLAY_MODE = 4100;
    static final int PROTO_START_NAVI = 3426;
    static final int PROTO_ROUTE_PLAN = 3401;

    static final String CLASS_NAVI_BASE_MODEL = "com.geely.lib.oneosapi.navi.model.base.NaviBaseModel";
    static final String CLASS_DIM_DISPLAY_MODE = "com.geely.lib.oneosapi.navi.model.client.DimDisplayMode";
    static final String CLASS_MAP_OPERA_SWITCH_AR = "com.geely.lib.oneosapi.navi.model.client.MapOperaSwitchAR";
    static final String CLASS_SWITCH_MAP_PRESENTATION = "com.geely.lib.oneosapi.navi.model.client.SwitchMapPresentation";
    static final String CLASS_NAVI_EVENT_CONFIG = "com.geely.lib.oneosapi.navi.model.client.NaviEventConfig";
    static final String CLASS_START_NAVI = "com.geely.lib.oneosapi.navi.model.client.StartNaviModel";
    static final String CLASS_NAVI_ROUTE_PLAN = "com.geely.lib.oneosapi.navi.model.client.NaviRoutePlan";
    static final String CLASS_POI_INFO = "com.geely.lib.oneosapi.navi.model.base.PoiInfo";
    static final String CLASS_LATLNG = "com.geely.lib.oneosapi.navi.model.base.LatLng";

    /** {@code NaviRoutePlan.action}: 0 = plan only, 1 = plan and navigate, 2 = info only. */
    static final int ROUTE_ACTION_PLAN = 0;
    static final int ROUTE_ACTION_NAVI = 1;

    /** {@code DimDisplayMode}: 0 = off, 1 = simplify, 2 = full, 3 = AR. */
    static final int DIM_OFF = 0;
    static final int DIM_SIMPLIFY = 1;
    static final int DIM_FULL = 2;
    static final int DIM_AR = 3;

    /** {@code MapOperaSwitchAR}: the constants are inverted (0 = on, 1 = off). */
    static final int AR_ON = 0;
    static final int AR_OFF = 1;

    /** {@code NaviEventConfig.highFrequencyEventConfig} bits. */
    static final long EVENT_GUIDE = 1;
    static final long EVENT_LANES = 2;
    static final long EVENT_SPEED_LIMIT = 4;
    static final long EVENT_ROAD_CONDITION = 8;

    private NaviApi() {
    }

    interface Extras {
        void write(Parcel data);
    }

    static String dimName(int mode) {
        switch (mode) {
            case DIM_OFF:
                return "OFF";
            case DIM_SIMPLIFY:
                return "SIMPLIFY";
            case DIM_FULL:
                return "FULL";
            case DIM_AR:
                return "AR";
            default:
                return "mode=" + mode;
        }
    }

    private static String simple(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static int nextCallbackId() {
        return ((int) (System.currentTimeMillis() & 0x3fffffff)) | 1;
    }

    /** The map vendor the navi service routes to (see {@code MapVendor}); 6 = {@code com.geely.map}. */
    static volatile int currentMapVendor = 6;

    // --- invokeNaviAPI(TransferModel) --------------------------------------

    /**
     * One {@code invokeNaviAPIAsync} with a hand-built {@code TransferModel}. The async variant is
     * used because the sync one serialises a reply model that can be null (a service-side NPE).
     * The reply model, if any, arrives at {@code INaviObserver.onNaviAPICallback}.
     */
    static void invokeNaviApi(IBinder navi, IBinder observer, String label, int protocolId,
                              String modelClass, String packageName, Extras extras) {
        if (navi == null) {
            ProbeLog.i(label + ": navi server binder is null");
            return;
        }
        int callbackId = nextCallbackId();
        String pkg = packageName == null ? "" : packageName;
        Ipc.probe(label + " invokeNaviAPIAsync(proto=" + protocolId + ", model=" + simple(modelClass)
                        + ", callbackId=" + callbackId + ")",
                navi, DESCRIPTOR, TRANSACTION_INVOKE_NAVI_API_ASYNC, data -> {
                    // Proxy: TransferModel non-null.
                    data.writeInt(1);
                    // TransferModel.writeToParcel: the base model's class name...
                    data.writeString(modelClass);
                    // ...then writeParcelable(), which writes the class name again, then the model.
                    data.writeString(modelClass);
                    // NaviBaseModel.writeToParcel: protocolID, callbackId, protocolVersion,
                    // packageName, extraData(Bundle), mapVendor.
                    data.writeInt(protocolId);
                    data.writeInt(callbackId);
                    data.writeInt(1);
                    data.writeString(pkg);
                    data.writeBundle(new Bundle());
                    data.writeInt(currentMapVendor);
                    if (extras != null) {
                        extras.write(data);
                    }
                    data.writeStrongBinder(observer);
                }, Ipc.INT);
    }

    /** {@code NaviAPI.switchDimNaviMode}: switch the instrument/HUD navigation display mode. */
    static void switchDimNaviMode(IBinder navi, IBinder observer, int mode, String packageName) {
        invokeNaviApi(navi, observer, "switchDimNaviMode(" + dimName(mode) + ")",
                PROTO_DIM_DISPLAY_MODE, CLASS_DIM_DISPLAY_MODE, packageName,
                data -> data.writeInt(mode));
    }

    /** {@code NaviAPI.routePlanOrNavi}: ask the engine to plan/start a route (route B trigger). */
    static void routePlan(IBinder navi, IBinder observer, int action, double lat, double lng,
                          String name, String packageName) {
        invokeNaviApi(navi, observer, "routePlan(action=" + action + ", dest=" + lat + "," + lng + ")",
                PROTO_ROUTE_PLAN, CLASS_NAVI_ROUTE_PLAN, packageName, data -> {
                    // NaviRoutePlan: action, strategy, destPoiInfo, startPoiInfo, viaRoad, viaPoiInfos.
                    data.writeInt(action);
                    data.writeInt(-1);
                    // destPoiInfo (PoiInfo): writeParcelable writes the class name first.
                    data.writeString(CLASS_POI_INFO);
                    data.writeString(name == null ? "PROBE" : name); // name
                    data.writeString("");                            // uid
                    data.writeString(null);                          // type (null PoiType)
                    data.writeString("");                            // address
                    data.writeInt(0);                                // distance
                    data.writeString(CLASS_LATLNG);                  // latLng
                    data.writeDouble(lat);
                    data.writeDouble(lng);
                    data.writeString("gcj02");                       // coordType
                    data.writeString(null);                          // startPoiInfo = null
                    data.writeString(null);                          // naviViaRoadBean = null
                    data.writeInt(0);                                // viaPoiInfos: empty typed list
                });
    }

    /** {@code NaviAPI.startNavi}: ask the navi service to start navigation (route B). */
    static void startNavi(IBinder navi, IBinder observer, int naviType, String packageName) {
        invokeNaviApi(navi, observer, "startNavi(naviType=" + naviType + ")",
                PROTO_START_NAVI, CLASS_START_NAVI, packageName, data -> data.writeInt(naviType));
    }

    /** {@code NaviAPI.routePlanOrNavi} is not implemented here; startNavi is the trigger we test. */

    /** {@code NaviAPI.switchMapARGuide}. */
    static void switchMapArGuide(IBinder navi, IBinder observer, int action, String packageName) {
        invokeNaviApi(navi, observer, "switchMapARGuide(" + (action == AR_ON ? "ON" : "OFF") + ")",
                PROTO_MAP_OPERA_SWITCH_AR, CLASS_MAP_OPERA_SWITCH_AR, packageName,
                data -> data.writeInt(action));
    }

    /** {@code NaviAPI.switchMapPresentation}: move the map to another screen/window. */
    static void switchMapPresentation(IBinder navi, IBinder observer, int operateType, int screenMode,
                                      int screenWindow, String packageName) {
        invokeNaviApi(navi, observer, "switchMapPresentation(operateType=" + operateType + ", screenMode="
                        + screenMode + ", screenWindow=" + screenWindow + ")",
                PROTO_SWITCH_MAP_PRESENTATION, CLASS_SWITCH_MAP_PRESENTATION, packageName, data -> {
                    data.writeInt(operateType);
                    data.writeInt(screenMode);
                    data.writeInt(screenWindow);
                });
    }

    /** {@code NaviAPI.getGuideInfo}: the service answers on the observer. */
    static void getGuideInfo(IBinder navi, IBinder observer, String packageName) {
        invokeNaviApi(navi, observer, "getGuideInfo(proto=" + PROTO_GET_GUIDE_INFO + ")",
                PROTO_GET_GUIDE_INFO, CLASS_NAVI_BASE_MODEL, packageName, null);
    }

    /** {@code NaviAPI.setNaviEventConfig}: ask the service to push high-frequency events. */
    static void setNaviEventConfig(IBinder navi, IBinder observer, long flags, String packageName) {
        invokeNaviApi(navi, observer, "setNaviEventConfig(flags=0x" + Long.toHexString(flags) + ")",
                PROTO_NAVI_EVENT_CONFIG, CLASS_NAVI_EVENT_CONFIG, packageName,
                data -> data.writeLong(flags));
    }

    // --- plain AIDL methods ------------------------------------------------

    /** {@code INaviServer.switchARNavigation(boolean)}. */
    static void switchARNavigation(IBinder navi, boolean on) {
        Ipc.probe("INaviServer.switchARNavigation(" + on + ")", navi, DESCRIPTOR,
                TRANSACTION_SWITCH_AR_NAVIGATION, data -> data.writeInt(on ? 1 : 0), Ipc.VOID);
    }

    /** {@code INaviServer.getGuideInfo()}: no reply; the result arrives on a registered callback. */
    static void getGuideInfoDirect(IBinder navi) {
        Ipc.probe("INaviServer.getGuideInfo()", navi, DESCRIPTOR,
                TRANSACTION_GET_GUIDE_INFO, null, Ipc.VOID);
    }

    static void addNaviEventNotify(IBinder navi, IBinder callback) {
        if (callback == null) {
            ProbeLog.i("addNaviEventNotify: no callback yet");
            return;
        }
        Ipc.probe("INaviServer.addNaviEventNotify(cb)", navi, DESCRIPTOR,
                TRANSACTION_ADD_NAVI_EVENT_NOTIFY, data -> data.writeStrongBinder(callback), Ipc.BOOL);
    }

    static void removeNaviEventNotify(IBinder navi, IBinder callback) {
        Ipc.probe("INaviServer.removeNaviEventNotify(cb)", navi, DESCRIPTOR,
                TRANSACTION_REMOVE_NAVI_EVENT_NOTIFY, data -> data.writeStrongBinder(callback), Ipc.BOOL);
    }

    static void registerNaviObserver(IBinder navi, IBinder observer) {
        Ipc.probe("INaviServer.registerNaviObserver(cb)", navi, DESCRIPTOR,
                TRANSACTION_REGISTER_NAVI_OBSERVER, data -> data.writeStrongBinder(observer), Ipc.BOOL);
    }

    /** Read-only checks that prove the resolved binder really is the navi server. */
    static void inspect(IBinder navi) {
        if (navi == null) {
            ProbeLog.i("inspect: INaviServer is null");
            return;
        }
        Ipc.probe("INaviServer.getPackageName()", navi, DESCRIPTOR,
                TRANSACTION_GET_PACKAGE_NAME, null, Parcel::readString);
        Ipc.probe("INaviServer.mapIsLaunched()", navi, DESCRIPTOR,
                TRANSACTION_MAP_IS_LAUNCHED, null, Ipc.BOOL);
    }

    // --- the IServiceBinder the hub actually stores under type 1 -----------

    /**
     * {@code IServiceManager.getService(1)} returns an {@code IServiceBinder}, not the
     * {@code INaviServer}. The real server arrives through {@code getNaviServer(...)} on an
     * {@code IServerCallback}.
     */
    static final String SERVICE_BINDER_DESCRIPTOR = "com.geely.lib.oneosapi.navi.IServiceBinder";
    static final int SB_GET_NAVI_SERVER = 1;
    static final int SB_GET_DEFAULT_MAP = 3;

    static int getDefaultMap(IBinder serviceBinder) {
        Ipc.Result result = Ipc.call(serviceBinder, SERVICE_BINDER_DESCRIPTOR, SB_GET_DEFAULT_MAP, null, Ipc.INT);
        ProbeLog.i("IServiceBinder.getDefaultMap() -> " + result.text());
        return (int) result.asLong(0);
    }

    static void getNaviServer(IBinder serviceBinder, int mapVendor, boolean launchMap, IBinder callback) {
        if (serviceBinder == null) {
            ProbeLog.i("getNaviServer: IServiceBinder is null");
            return;
        }
        currentMapVendor = mapVendor;
        Ipc.probe("IServiceBinder.getNaviServer(mapVendor=" + mapVendor + ", launchMap=" + launchMap + ")",
                serviceBinder, SERVICE_BINDER_DESCRIPTOR, SB_GET_NAVI_SERVER, data -> {
                    data.writeInt(mapVendor);
                    data.writeInt(launchMap ? 1 : 0);
                    data.writeStrongBinder(callback);
                }, Ipc.VOID);
    }

    /** {@code IServerCallback}: hands back the real {@code INaviServer}. */
    static final class ServerCallback extends Binder {
        static final String CALLBACK_DESCRIPTOR = "com.geely.lib.oneosapi.navi.IServerCallback";

        volatile IBinder naviServer;

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 1598968902) {
                try {
                    reply.writeString(CALLBACK_DESCRIPTOR);
                } catch (Throwable ignored) {
                    // nothing to do
                }
                return true;
            }
            try {
                data.enforceInterface(CALLBACK_DESCRIPTOR);
                switch (code) {
                    case 1:
                        reply.writeNoException();
                        reply.writeString("carprobe");
                        return true;
                    case 2: {
                        IBinder server = data.readStrongBinder();
                        naviServer = server;
                        ProbeLog.i("IServerCallback.initSuccess(naviServer=" + server
                                + " interface=" + Ipc.interfaceName(server) + ")");
                        break;
                    }
                    case 3:
                        ProbeLog.i("IServerCallback.initFailed(code=" + data.readInt()
                                + ", message=" + data.readString() + ")");
                        break;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException();
            } catch (Throwable error) {
                ProbeLog.e("IServerCallback failed", error);
            }
            return true;
        }
    }

    // --- callbacks the service calls back into ----------------------------

    /** The model name carried by a {@code TransferModel}, without the vendor class on our path. */
    private static String readTransferModel(Parcel data) {
        if (data.readInt() == 0) {
            return "null";
        }
        String cls = data.readString();
        String name = cls == null ? "null" : simple(cls);
        try {
            data.readParcelable(NaviApi.class.getClassLoader());
        } catch (Throwable ignored) {
            // The vendor model class is not on our classpath; the class name is the useful part.
        }
        return name;
    }

    /** {@code INaviEventNotify}: high-frequency navigation events pushed by the service. */
    static final class EventNotify extends Binder {
        static final String NOTIFY_DESCRIPTOR = "com.geely.lib.oneosapi.navi.INaviEventNotify";

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 1598968902) {
                try {
                    reply.writeString(NOTIFY_DESCRIPTOR);
                } catch (Throwable ignored) {
                    // nothing to do
                }
                return true;
            }
            try {
                data.enforceInterface(NOTIFY_DESCRIPTOR);
                switch (code) {
                    case 1:
                        reply.writeNoException();
                        reply.writeString("carprobe");
                        return true;
                    case 2:
                        ProbeLog.i("INaviEventNotify.onNaviNotify(model=" + readTransferModel(data) + ")");
                        break;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException();
            } catch (Throwable error) {
                ProbeLog.e("INaviEventNotify callback failed", error);
            }
            return true;
        }
    }

    /** {@code INaviObserver}: the async reply channel of {@code invokeNaviAPIAsync}. */
    static final class Observer extends Binder {
        static final String OBSERVER_DESCRIPTOR = "com.geely.lib.oneosapi.navi.INaviObserver";

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 1598968902) {
                try {
                    reply.writeString(OBSERVER_DESCRIPTOR);
                } catch (Throwable ignored) {
                    // nothing to do
                }
                return true;
            }
            try {
                data.enforceInterface(OBSERVER_DESCRIPTOR);
                switch (code) {
                    case 1:
                        reply.writeNoException();
                        reply.writeString("carprobe");
                        return true;
                    case 2:
                        ProbeLog.i("INaviObserver.onNaviAPICallback(model=" + readTransferModel(data) + ")");
                        break;
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException();
            } catch (Throwable error) {
                ProbeLog.e("INaviObserver callback failed", error);
            }
            return true;
        }
    }
}
