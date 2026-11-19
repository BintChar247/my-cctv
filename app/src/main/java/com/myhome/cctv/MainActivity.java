package com.myhome.cctv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

/** Home screen: all cameras in one grid. Tap = full screen, long-press = edit/delete. */
public class MainActivity extends Activity {

    private static final int BG = Color.parseColor("#101418");
    private static final int ACCENT = Color.parseColor("#2BB3A3");

    private List<Store.Cam> cams = new ArrayList<>();
    private final List<VideoView> players = new ArrayList<>();
    private LinearLayout grid;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(14), dp(12), dp(10));
        TextView title = new TextView(this);
        title.setText("My CCTV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = smallButton("⟳");
        refresh.setOnClickListener(v -> startAll());
        bar.addView(refresh);
        Button add = smallButton("+ Add");
        add.setOnClickListener(v -> editDialog(-1));
        bar.addView(add);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        FrameLayout holder = new FrameLayout(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(6), 0, dp(6), dp(12));
        holder.addView(grid);
        empty = new TextView(this);
        empty.setText("No cameras yet.\nTap “+ Add” to add your iCSee, EYESEC or other camera.");
        empty.setTextColor(Color.LTGRAY);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(80), dp(24), 0);
        holder.addView(empty);
        scroll.addView(holder);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); rebuild(); }
    @Override protected void onPause() { super.onPause(); stopAll(); }

    private void rebuild() {
        stopAll();
        grid.removeAllViews();
        players.clear();
        cams = Store.load(this);
        empty.setVisibility(cams.isEmpty() ? View.VISIBLE : View.GONE);
        int cols = getResources().getConfiguration().screenWidthDp > 600 ? 3 : 2;
        int tileH = (getResources().getDisplayMetrics().widthPixels / cols) * 9 / 16;
        LinearLayout row = null;
        for (int i = 0; i < cams.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                grid.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tileH, 1);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(tile(i), lp);
        }
        // pad last row so tiles keep the same size
        if (row != null) for (int k = cams.size() % cols; k != 0 && k < cols; k++) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, tileH, 1));
        }
        startAll();
    }

    private View tile(final int idx) {
        Store.Cam c = cams.get(idx);
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);

        final VideoView vv = new VideoView(this);
        vv.setTag(c.sub());
        f.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        players.add(vv);

        final TextView status = new TextView(this);
        status.setText("Connecting…");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        f.addView(status, new FrameLayout.LayoutParams(-1, -1));

        TextView label = new TextView(this);
        label.setText(c.name.isEmpty() ? "Camera " + (idx + 1) : c.name);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setBackgroundColor(0x88000000);
        label.setPadding(dp(6), dp(2), dp(6), dp(2));
        f.addView(label, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        vv.setOnPreparedListener(mp -> {
            mp.setVolume(0f, 0f);       // grid is muted
            status.setVisibility(View.GONE);
            vv.start();
        });
        vv.setOnErrorListener((mp, what, extra) -> {
            status.setVisibility(View.VISIBLE);
            status.setText("Offline\ntap ⟳ to retry");
            return true;
        });

        View touch = new View(this);   // VideoView eats taps, so put a layer on top
        touch.setOnClickListener(v -> {
            Intent it = new Intent(this, PlayerActivity.class);
            it.putExtra("url", c.main());
            it.putExtra("name", label.getText().toString());
            startActivity(it);
        });
        touch.setOnLongClickListener(v -> { tileMenu(idx); return true; });
        f.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    private void startAll() {
        for (VideoView vv : players) {
            vv.stopPlayback();
            vv.setVideoURI(Uri.parse((String) vv.getTag()));
        }
    }

    private void stopAll() {
        for (VideoView vv : players) vv.stopPlayback();
    }

    private void tileMenu(final int idx) {
        new AlertDialog.Builder(this)
                .setTitle(cams.get(idx).name)
                .setItems(new String[]{"Edit", "Move up", "Delete"}, (d, w) -> {
                    if (w == 0) editDialog(idx);
                    else if (w == 1 && idx > 0) {
                        cams.add(idx - 1, cams.remove(idx));
                        Store.save(this, cams); rebuild();
                    } else if (w == 2) {
                        new AlertDialog.Builder(this).setMessage("Delete this camera?")
                                .setPositiveButton("Delete", (x, y) -> {
                                    cams.remove(idx); Store.save(this, cams); rebuild();
                                })
                                .setNegativeButton("Cancel", null).show();
                    }
                }).show();
    }

    /** Add (idx = -1) or edit a camera. */
    private void editDialog(final int idx) {
        final Store.Cam c = idx >= 0 ? cams.get(idx) : new Store.Cam();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText name = field(form, "Name (e.g. Front gate)", c.name, InputType.TYPE_CLASS_TEXT);

        final RadioGroup type = new RadioGroup(this);
        final RadioButton rIcsee = new RadioButton(this); rIcsee.setText("iCSee / XMeye camera");
        final RadioButton rCustom = new RadioButton(this); rCustom.setText("Other (EYESEC, Tuya, any RTSP link)");
        rIcsee.setId(View.generateViewId()); rCustom.setId(View.generateViewId());
        type.addView(rIcsee); type.addView(rCustom);
        form.addView(type);

        final LinearLayout icseeBox = new LinearLayout(this);
        icseeBox.setOrientation(LinearLayout.VERTICAL);
        final EditText ip = field(icseeBox, "Camera IP (e.g. 192.168.1.20)", c.ip, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText user = field(icseeBox, "Username", c.user, InputType.TYPE_CLASS_TEXT);
        final EditText pass = field(icseeBox, "Password (blank if none)", c.pass, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        final EditText port = field(icseeBox, "RTSP port", String.valueOf(c.port), InputType.TYPE_CLASS_NUMBER);
        final EditText ch = field(icseeBox, "Channel", String.valueOf(c.channel), InputType.TYPE_CLASS_NUMBER);
        form.addView(icseeBox);

        final LinearLayout customBox = new LinearLayout(this);
        customBox.setOrientation(LinearLayout.VERTICAL);
        final EditText main = field(customBox, "rtsp://user:pass@IP:554/…", c.mainUrl, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText sub = field(customBox, "Low-res link for grid (optional)", c.subUrl, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(customBox);

        type.setOnCheckedChangeListener((g, id) -> {
            boolean ic = id == rIcsee.getId();
            icseeBox.setVisibility(ic ? View.VISIBLE : View.GONE);
            customBox.setVisibility(ic ? View.GONE : View.VISIBLE);
        });
        type.check("custom".equals(c.type) ? rCustom.getId() : rIcsee.getId());

        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        new AlertDialog.Builder(this)
                .setTitle(idx >= 0 ? "Edit camera" : "Add camera")
                .setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    c.name = name.getText().toString().trim();
                    c.type = type.getCheckedRadioButtonId() == rIcsee.getId() ? "icsee" : "custom";
                    c.ip = ip.getText().toString().trim();
                    c.user = user.getText().toString().trim();
                    c.pass = pass.getText().toString();
                    c.port = parse(port.getText().toString(), 554);
                    c.channel = parse(ch.getText().toString(), 1);
                    c.mainUrl = main.getText().toString().trim();
                    c.subUrl = sub.getText().toString().trim();
                    if (idx < 0) cams.add(c);
                    Store.save(this, cams);
                    rebuild();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- small helpers ----
    private EditText field(ViewGroup parent, String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(inputType);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(ACCENT);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
