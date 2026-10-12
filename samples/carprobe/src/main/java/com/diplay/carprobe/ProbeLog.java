package com.diplay.carprobe;

import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Screen + logcat sink so a probe run can be read either on the head unit or over adb. */
final class ProbeLog {
    private static final String TAG = "CarProbe";
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static TextView view;
    private static final StringBuilder history = new StringBuilder();

    private ProbeLog() {
    }

    static void attach(TextView textView) {
        view = textView;
        textView.setText(history.toString());
    }

    static void i(String message) {
        android.util.Log.i(TAG, message);
        append(message);
    }

    static void e(String message, Throwable error) {
        android.util.Log.e(TAG, message, error);
        append("!! " + message + " -> " + describe(error));
    }

    static String describe(Throwable error) {
        if (error == null) {
            return "no error";
        }
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }

    private static void append(String line) {
        String stamped = TIME.format(new Date()) + "  " + line + "\n";
        history.append(stamped);
        TextView target = view;
        if (target != null) {
            target.post(() -> {
                target.append(stamped);
            });
        }
    }
}
