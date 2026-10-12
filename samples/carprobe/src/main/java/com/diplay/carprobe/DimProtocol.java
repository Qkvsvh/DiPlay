package com.diplay.carprobe;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;

/**
 * The car's DIM ("driver information module") protocol: what the stock map uses to put
 * turn-by-turn guidance on the instrument cluster and the HUD.
 *
 * <p>Two binders are registered on the head unit and are reachable through ServiceManager:
 * {@code dimprotocol} ({@code IDIMProtocolService}) and {@code DimNaviService}
 * ({@code IDimNaviService}). Codes are the {@code TRANSACTION_*} constants of the vendor Stubs.
 */
final class DimProtocol {

    static final String PROTOCOL_SERVICE = "dimprotocol";
    static final String NAVI_SERVICE = "DimNaviService";

    static final String PROTOCOL_DESCRIPTOR = "ecarx.dimprotocol.IDIMProtocolService";
    static final String NAVI_DESCRIPTOR = "ecarx.dimprotocol.custom.service.IDimNaviService";

    // IDIMProtocolService
    static final int P_SEND_MESSAGE_TO_DIM = 5;
    static final int P_GET_HUD_MODE = 6;
    static final int P_ON_CHANGE_NAVI_MODE = 7;
    static final int P_SET_NAVI_MODE = 12;
    static final int P_GET_NAVI_MODE = 13;

    // IDimNaviService
    static final int N_NOTIFY_STATUS = 1;
    static final int N_UPDATE_ETA = 2;
    static final int N_UPDATE_DISTANCE_TO_DESTINATION = 3;
    static final int N_UPDATE_DISTANCE_TO_NEXT_GUIDANCE = 4;
    static final int N_UPDATE_NEXT_GUIDANCE_NAME = 5;
    static final int N_UPDATE_TURN_ID = 6;
    static final int N_UPDATE_LANE_INFO = 7;
    static final int N_UPDATE_EXTENSION_INFO = 8;
    static final int N_NOTIFY_PROJECT_SCREEN_SWITCH = 9;
    static final int N_GET_PROJECT_SCREEN_STATUS = 10;
    static final int N_UPDATE_NAVI_LIGHT = 11;
    static final int N_UPDATE_CRUISE_LIGHT = 12;
    static final int N_REGISTER_INTERACTION_CALLBACK = 13;
    static final int N_UNREGISTER_INTERACTION_CALLBACK = 14;

    static final int STATUS_NAVI_SUCCESS = 1;
    static final int STATUS_NAVI_START = 2;
    static final int STATUS_NAVI_END = 3;
    static final int STATUS_CRUISE_START = 4;
    static final int STATUS_CRUISE_END = 5;

    static final String KEY_ETA = "KEY_NAVI_INFO_ESTIMATED_ARRIVAL_TIME";
    static final String KEY_ROAD_TYPE = "KEY_NAVI_INFO_ROAD_TYPE";
    static final String KEY_FRONT_DESK_STATUS = "KEY_NAVI_INFO_NAVI_FRONT_DESK_STATUS";
    static final String KEY_SCREEN_READY_STATUS = "KEY_NAVI_INFO_NAVI_SCREEN_READY_STATUS";

    private DimProtocol() {
    }

    static IBinder protocol() {
        return Ipc.service(PROTOCOL_SERVICE);
    }

    static IBinder navi() {
        return Ipc.service(NAVI_SERVICE);
    }

    // --- dimprotocol -------------------------------------------------------

    static void readHudState() {
        IBinder binder = protocol();
        if (binder == null) {
            ProbeLog.i("dimprotocol: service not registered");
            return;
        }
        Ipc.probe("dimprotocol.getHUDMode()", binder, PROTOCOL_DESCRIPTOR, P_GET_HUD_MODE, null, Ipc.INT);
        Ipc.probe("dimprotocol.getNaviMode()", binder, PROTOCOL_DESCRIPTOR, P_GET_NAVI_MODE, null, Ipc.INT);
    }

    static void setNaviMode(int mode) {
        IBinder binder = protocol();
        if (binder == null) {
            ProbeLog.i("dimprotocol: service not registered");
            return;
        }
        Ipc.probe("dimprotocol.setNaviMode(" + mode + ")", binder, PROTOCOL_DESCRIPTOR, P_SET_NAVI_MODE,
                data -> data.writeInt(mode), Ipc.VOID);
        Ipc.probe("dimprotocol.getNaviMode()", binder, PROTOCOL_DESCRIPTOR, P_GET_NAVI_MODE, null, Ipc.INT);
    }

    static void readProjectScreenStatus() {
        IBinder binder = navi();
        if (binder == null) {
            ProbeLog.i("DimNaviService: service not registered");
            return;
        }
        Ipc.probe("DimNaviService.getDimProjectScreenStatus()", binder, NAVI_DESCRIPTOR,
                N_GET_PROJECT_SCREEN_STATUS, null, Ipc.BOOL);
    }

    static void setProjectScreen(boolean on) {
        IBinder binder = navi();
        if (binder == null) {
            ProbeLog.i("DimNaviService: service not registered");
            return;
        }
        Ipc.probe("DimNaviService.notifyDimProjectScreenSwitch(" + on + ")", binder, NAVI_DESCRIPTOR,
                N_NOTIFY_PROJECT_SCREEN_SWITCH, data -> data.writeInt(on ? 1 : 0), Ipc.VOID);
    }

    // --- IDimNaviService guidance -----------------------------------------

    static void notifyStatus(int status) {
        IBinder binder = navi();
        if (binder == null) {
            ProbeLog.i("DimNaviService: service not registered");
            return;
        }
        Ipc.probe("DimNaviService.notifyNavigationStatus(" + status + ")", binder, NAVI_DESCRIPTOR,
                N_NOTIFY_STATUS, data -> data.writeInt(status), Ipc.VOID);
    }

    static void updateTurnId(int turnId) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateNavigationTurnId(" + turnId + ")", binder, NAVI_DESCRIPTOR,
                N_UPDATE_TURN_ID, data -> data.writeInt(turnId), Ipc.VOID);
    }

    static void updateNextGuidancePointName(String name) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateNextGuidancePointName(" + name + ")", binder, NAVI_DESCRIPTOR,
                N_UPDATE_NEXT_GUIDANCE_NAME, data -> data.writeString(name), Ipc.VOID);
    }

    static void updateDistanceToNextGuidancePoint(int meters) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateDistanceToNextGuidancePoint(" + meters + ")", binder, NAVI_DESCRIPTOR,
                N_UPDATE_DISTANCE_TO_NEXT_GUIDANCE, data -> data.writeInt(meters), Ipc.VOID);
    }

    static void updateDistanceToDestination(int meters) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateDistanceToDestination(" + meters + ")", binder, NAVI_DESCRIPTOR,
                N_UPDATE_DISTANCE_TO_DESTINATION, data -> data.writeInt(meters), Ipc.VOID);
    }

    static void updateEta(int seconds) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateETA(" + seconds + ")", binder, NAVI_DESCRIPTOR,
                N_UPDATE_ETA, data -> data.writeInt(seconds), Ipc.VOID);
    }

    static void updateLaneInfo(List<Lane> lanes) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateLaneInfo(" + lanes.size() + " lanes)", binder, NAVI_DESCRIPTOR,
                N_UPDATE_LANE_INFO, data -> data.writeTypedList(lanes), Ipc.VOID);
    }

    static void updateExtensionInfo(Bundle extras) {
        IBinder binder = navi();
        if (binder == null) {
            return;
        }
        Ipc.probe("DimNaviService.updateExtensionInfo(" + extras.keySet() + ")", binder, NAVI_DESCRIPTOR,
                N_UPDATE_EXTENSION_INFO, data -> Ipc.writeBundle(data, extras), Ipc.VOID);
    }

    /** Quiet stop: what the probe calls when it gives the cluster back. */
    static void stopNavigation() {
        notifyStatus(STATUS_NAVI_END);
        notifyStatus(STATUS_CRUISE_END);
    }

    static List<Lane> demoLanes(int highlighted) {
        return Collections.singletonList(new Lane(highlighted, 0, null));
    }

    /**
     * Mirrors {@code ecarx.dimprotocol.custom.bean.AdaptLaneInfo} on the wire: three fields,
     * written in that order. The class name never travels, so our own Parcelable is accepted.
     */
    static final class Lane implements Parcelable {
        final int laneNumber;
        final int laneBackIconId;
        final Parcelable laneBackIcon;

        Lane(int laneNumber, int laneBackIconId, Parcelable laneBackIcon) {
            this.laneNumber = laneNumber;
            this.laneBackIconId = laneBackIconId;
            this.laneBackIcon = laneBackIcon;
        }

        Lane(Parcel in) {
            laneNumber = in.readInt();
            laneBackIconId = in.readInt();
            laneBackIcon = in.readParcelable(Lane.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(laneNumber);
            dest.writeInt(laneBackIconId);
            dest.writeParcelable(laneBackIcon, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Lane> CREATOR = new Creator<Lane>() {
            @Override
            public Lane createFromParcel(Parcel in) {
                return new Lane(in);
            }

            @Override
            public Lane[] newArray(int size) {
                return new Lane[size];
            }
        };
    }

    /** Minimal {@code IDimNavigationInteractionCallback}: the cluster can ask us for a search. */
    static final class InteractionCallback extends Binder {
        static final String DESCRIPTOR = "ecarx.dimprotocol.custom.listener.IDimNavigationInteractionCallback";

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
                switch (code) {
                    case 1: {
                        int action = data.readInt();
                        Parcelable address = data.readParcelable(InteractionCallback.class.getClassLoader());
                        ProbeLog.i("cluster callback onDoInteractionAction(action=" + action + ", address=" + address + ")");
                        break;
                    }
                    case 2: {
                        String keyword = data.readString();
                        ProbeLog.i("cluster callback onSearchAddress(" + keyword + ")");
                        break;
                    }
                    default:
                        return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException();
            } catch (Throwable error) {
                ProbeLog.e("cluster callback failed", error);
            }
            return true;
        }
    }
}
