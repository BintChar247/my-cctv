package com.myhome.cctv;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

/** One camera, full screen, high quality, with sound. Tap to retry if it drops. */
public class PlayerActivity extends Activity {

    private VideoView vv;
    private TextView status;
    private String url;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        url = getIntent().getStringExtra("url");

        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        vv = new VideoView(this);
        f.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);
        f.addView(status, new FrameLayout.LayoutParams(-1, -1));

        TextView name = new TextView(this);
        name.setText(getIntent().getStringExtra("name"));
        name.setTextColor(Color.WHITE);
        name.setBackgroundColor(0x88000000);
        name.setPadding(24, 8, 24, 8);
        f.addView(name, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        View touch = new View(this);
        touch.setOnClickListener(v -> play());
        f.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        setContentView(f);

        vv.setOnPreparedListener(mp -> { status.setText(""); vv.start(); });
        vv.setOnErrorListener((mp, w, e) -> {
            status.setText("Can't connect to camera\n\nCheck you're on the same Wi-Fi\nand the IP / password are right.\n\nTap to retry");
            return true;
        });
    }

    private void play() {
        status.setText("Connecting…");
        vv.stopPlayback();
        vv.setVideoURI(Uri.parse(url));
    }

    @Override protected void onResume() { super.onResume(); play(); }
    @Override protected void onPause() { super.onPause(); vv.stopPlayback(); }
}
