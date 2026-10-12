package com.diplay.carprobe;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A deliberately loud Presentation, so "did it appear on the instrument?" needs no guessing.
 * It ticks a counter and drifts a square, which also shows whether the display keeps refreshing.
 */
final class ProbePresentation extends Presentation {

    private final int displayId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView counter;
    private TextView mover;
    private int ticks;
    private int direction = 1;

    ProbePresentation(Context outerContext, Display display, int displayId) {
        super(outerContext, display, android.R.style.Theme_DeviceDefault_NoActionBar);
        this.displayId = displayId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(176, 0, 32));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(getContext());
        title.setText("CARPROBE " + displayId);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        counter = new TextView(getContext());
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        counter.setGravity(Gravity.CENTER);
        root.addView(counter);

        mover = new TextView(getContext());
        mover.setText(">>>>>>");
        mover.setTextColor(Color.YELLOW);
        mover.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        root.addView(mover);

        setContentView(root);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

        handler.post(ticker);
        ProbeLog.i("presentation shown on display " + displayId);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            ticks++;
            counter.setText("tick " + ticks);
            mover.setPadding(ticks % 200 * direction, 0, 0, 0);
            if (ticks % 200 == 0) {
                direction = -direction;
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    public void onDisplayRemoved() {
        ProbeLog.i("presentation on display " + displayId + " removed by the system");
        super.onDisplayRemoved();
    }

    @Override
    protected void onStop() {
        ProbeLog.i("presentation on display " + displayId + " stopped");
        handler.removeCallbacks(ticker);
        super.onStop();
    }

    void release() {
        handler.removeCallbacks(ticker);
        try {
            dismiss();
        } catch (Throwable error) {
            ProbeLog.e("presentation dismiss", error);
        }
    }
}
