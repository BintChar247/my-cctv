package com.myhome.cctv;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * One camera tile / full-screen view.
 * 1) checks the camera answers on its RTSP port, 2) tries each possible stream address
 * (RTSP over TCP, which works far more reliably than Android's old built-in player),
 * 3) shows a plain-language reason if it fails.
 */
public class CamView extends FrameLayout {

    private final PlayerView pv;
    private final TextView status;
    private final TextView label;
    private ExoPlayer player;
    private List<String> urls;
    private String host;
    private int port;
    private boolean muted;
    private int attempt;
    private int session;   // ignore callbacks from older attempts
    private final StringBuilder log = new StringBuilder();

    public CamView(Context ctx) {
        super(ctx);
        setBackgroundColor(Color.BLACK);
        pv = new PlayerView(ctx);
        pv.setUseController(false);
        pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.setShutterBackgroundColor(Color.BLACK);
        addView(pv, new LayoutParams(-1, -1));

        status = new TextView(ctx);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setPadding(16, 16, 16, 16);
        addView(status, new LayoutParams(-1, -1));

        label = new TextView(ctx);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setBackgroundColor(0x88000000);
        label.setPadding(12, 4, 12, 4);
        addView(label, new LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    }

    public void setLabel(String s) { label.setText(s); }
    public String getLabel() { return label.getText().toString(); }
    public String getLog() { return log.toString(); }

    public void start(List<String> urls, String host, int port, boolean muted) {
        this.urls = urls; this.host = host; this.port = port; this.muted = muted;
        stop();
        final int my = ++session;
        attempt = 0;
        log.setLength(0);
        status.setVisibility(VISIBLE);
        if (host == null || host.isEmpty()) {
            fail("No camera address set.\nLong-press to edit.");
            return;
        }
        status.setText("Connecting to " + host + "…");
        new Thread(() -> {
            String err = null;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 3000);
            } catch (Exception e) {
                err = e.getClass().getSimpleName();
            }
            final String fErr = err;
            post(() -> {
                if (my != session) return;
                if (fErr != null) {
                    log.append("Port check ").append(host).append(":").append(port)
                       .append(" failed: ").append(fErr).append('\n');
                    fail("Can't reach " + host + ":" + port + "\n\n"
                            + "• Wrong IP? Use “Scan” to find cameras\n"
                            + "• Phone on the same Wi-Fi as the camera?\n"
                            + "• RTSP may be off on this camera");
                } else {
                    log.append("Port ").append(port).append(" open on ").append(host).append('\n');
                    tryNext(my);
                }
            });
        }).start();
    }

    private void tryNext(final int my) {
        if (attempt >= urls.size()) {
            fail("Camera found, but video won't play.\n\n"
                    + "• Check username / password\n"
                    + "• Long-press → Details for the error");
            return;
        }
        String url = urls.get(attempt++);
        releasePlayer();
        status.setText("Opening video… (" + attempt + "/" + urls.size() + ")");
        player = new ExoPlayer.Builder(getContext()).build();
        player.setVolume(muted ? 0f : 1f);
        pv.setPlayer(player);
        RtspMediaSource src = new RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(8000)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)));
        player.setMediaSource(src);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (my != session) return;
                if (state == Player.STATE_READY) status.setVisibility(GONE);
            }
            @Override public void onPlayerError(PlaybackException e) {
                if (my != session) return;
                log.append("Tried ").append(hide(url)).append("\n  → ")
                   .append(e.getErrorCodeName()).append(": ")
                   .append(e.getCause() != null ? e.getCause().getMessage() : e.getMessage())
                   .append('\n');
                tryNext(my);
            }
        });
        player.setPlayWhenReady(true);
        player.prepare();
    }

    private void fail(String msg) {
        releasePlayer();
        status.setVisibility(VISIBLE);
        status.setText(msg);
    }

    public void stop() {
        session++;
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            pv.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private static String hide(String url) {   // don't show passwords in the details box
        return url.replaceAll("password=[^&_]*", "password=***").replaceAll("//([^:/@]+):[^@]*@", "//$1:***@");
    }
}
