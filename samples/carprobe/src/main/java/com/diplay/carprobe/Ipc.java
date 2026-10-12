package com.diplay.carprobe;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.reflect.Method;

/**
 * Raw binder plumbing.
 *
 * <p>No vendor AIDL is copied into this tree: every head-unit interface is reached by its
 * descriptor plus the transaction codes read out of the vendor APKs' generated Stubs, and the
 * argument layout mirrors the matching {@code onTransact} branch.
 */
final class Ipc {

    interface Args {
        void write(Parcel data) throws RemoteException;
    }

    interface Reader {
        Object read(Parcel reply) throws RemoteException;
    }

    static final Reader VOID = reply -> null;
    static final Reader BOOL = reply -> reply.readInt() != 0;
    static final Reader INT = Parcel::readInt;
    static final Reader STRING = Parcel::readString;
    static final Reader BINDER = Parcel::readStrongBinder;

    /** Result of one transact: a value, or the exception the service (or binder) raised. */
    static final class Result {
        final Object value;
        final Throwable error;

        Result(Object value, Throwable error) {
            this.value = value;
            this.error = error;
        }

        boolean ok() {
            return error == null;
        }

        String text() {
            return ok() ? String.valueOf(value) : ProbeLog.describe(error);
        }

        long asLong(long fallback) {
            return ok() && value instanceof Number ? ((Number) value).longValue() : fallback;
        }

        boolean asBool() {
            return ok() && Boolean.TRUE.equals(value);
        }
    }

    private Ipc() {
    }

    /** {@code android.os.ServiceManager.getService(String)} is a hidden API. */
    static IBinder service(String name) {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getMethod("getService", String.class);
            return (IBinder) getService.invoke(null, name);
        } catch (Throwable error) {
            ProbeLog.e("ServiceManager.getService(" + name + ")", error);
            return null;
        }
    }

    static Result call(IBinder binder, String descriptor, int code, Args args, Reader reader) {
        if (binder == null) {
            return new Result(null, new IllegalStateException("binder " + descriptor + " is null"));
        }
        Parcel data = null;
        Parcel reply = Parcel.obtain();
        try {
            data = Parcel.obtain();
            data.writeInterfaceToken(descriptor);
            if (args != null) {
                args.write(data);
            }
            binder.transact(code, data, reply, 0);
            reply.readException();
            return new Result(reader == null ? null : reader.read(reply), null);
        } catch (Throwable error) {
            return new Result(null, error);
        } finally {
            reply.recycle();
            if (data != null) {
                data.recycle();
            }
        }
    }

    /** Logs "what: result" for one probe step. */
    static Result probe(String what, IBinder binder, String descriptor, int code, Args args, Reader reader) {
        Result result = call(binder, descriptor, code, args, reader);
        ProbeLog.i(what + " -> " + result.text());
        return result;
    }

    /** Writes a {@code Surface} argument the way the generated proxies do. */
    static void writeSurface(Parcel data, android.view.Surface surface) {
        if (surface == null) {
            data.writeInt(0);
            return;
        }
        data.writeInt(1);
        surface.writeToParcel(data, 0);
    }

    /** Writes a {@code Bundle} argument the way the generated proxies do. */
    static void writeBundle(Parcel data, android.os.Bundle bundle) {
        if (bundle == null) {
            data.writeInt(0);
            return;
        }
        data.writeInt(1);
        bundle.writeToParcel(data, 0);
    }

    /** The interface a binder actually implements ({@code IBinder.INTERFACE_TRANSACTION}). */
    static String interfaceName(IBinder binder) {
        if (binder == null) {
            return "null";
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            binder.transact(1598968902, data, reply, 0);
            return reply.readString();
        } catch (Throwable error) {
            return "err:" + ProbeLog.describe(error);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
