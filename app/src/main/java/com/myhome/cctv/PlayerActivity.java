package com.myhome.cctv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.List;

/** One camera, full screen, high quality, with sound. Tap = retry, long-press = details. */
public class PlayerActivity extends Activity {

    private CamView cv;
    private Store.Cam cam;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        List<Store.Cam> cams = Store.load(this);
        int idx = getIntent().getIntExtra("idx", 0);
        if (idx < 0 || idx >= cams.size()) { finish(); return; }
        cam = cams.get(idx);

        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        cv = new CamView(this);
        cv.setLabel(cam.name.isEmpty() ? "Camera " + (idx + 1) : cam.name);
        f.addView(cv, new FrameLayout.LayoutParams(-1, -1));

        View touch = new View(this);
        touch.setOnClickListener(v -> play());
        touch.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Connection details")
                    .setMessage(cv.getLog().isEmpty() ? "Still connecting…" : cv.getLog())
                    .setPositiveButton("OK", null).show();
            return true;
        });
        f.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        setContentView(f);
    }

    private void play() {
        cv.start(cam.candidates(true), cam.host(), cam.rtspPort(), false);
    }

    @Override protected void onResume() { super.onResume(); if (cv != null) play(); }
    @Override protected void onPause() { super.onPause(); if (cv != null) cv.stop(); }
}
