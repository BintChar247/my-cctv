package com.myhome.cctv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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

import java.util.ArrayList;
import java.util.List;

/** Home screen: all cameras in one grid. Tap = full screen, long-press = edit/delete. */
public class MainActivity extends Activity {

    private static final int BG = Color.parseColor("#101418");
    private static final int ACCENT = Color.parseColor("#2BB3A3");

    private List<Store.Cam> cams = new ArrayList<>();
    private final List<CamView> players = new ArrayList<>();
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
        Button scan = smallButton("Scan");
        scan.setOnClickListener(v -> scanDialog());
        bar.addView(scan);
        Button add = smallButton("+ Add");
        add.setOnClickListener(v -> editDialog(-1, null));
        bar.addView(add);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        FrameLayout holder = new FrameLayout(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(6), 0, dp(6), dp(12));
        holder.addView(grid);
        empty = new TextView(this);
        empty.setText("No cameras yet.\nTap “Scan” to find cameras on your Wi-Fi,\nor “+ Add” to enter one by hand.");
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
        final Store.Cam c = cams.get(idx);
        FrameLayout f = new FrameLayout(this);
        final CamView cv = new CamView(this);
        cv.setLabel(c.name.isEmpty() ? "Camera " + (idx + 1) : c.name);
        cv.setTag(c);
        f.addView(cv, new FrameLayout.LayoutParams(-1, -1));
        players.add(cv);

        View touch = new View(this);   // tap layer above the video
        touch.setOnClickListener(v -> {
            Intent it = new Intent(this, PlayerActivity.class);
            it.putExtra("idx", idx);
            startActivity(it);
        });
        touch.setOnLongClickListener(v -> { tileMenu(idx, cv); return true; });
        f.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    private void startAll() {
        for (CamView cv : players) {
            Store.Cam c = (Store.Cam) cv.getTag();
            cv.start(c.candidates(false), c.host(), c.rtspPort(), true);
        }
    }

    private void stopAll() {
        for (CamView cv : players) cv.stop();
    }

    private void scanDialog() {
        final String net = Scanner.subnet(this);
        if (net == null) {
            new AlertDialog.Builder(this).setMessage("Connect to your home Wi-Fi first.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        final AlertDialog wait = new AlertDialog.Builder(this)
                .setTitle("Scanning " + net + "x …")
                .setMessage("Looking for cameras on your Wi-Fi.\nThis takes about 15 seconds.")
                .setCancelable(false).show();
        new Thread(() -> {
            final List<Scanner.Found> found = Scanner.scan(net);
            runOnUiThread(() -> {
                wait.dismiss();
                if (found.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("No cameras found")
                            .setMessage("Your phone is " + Scanner.myIp(this) + ".\n\n"
                                    + "No device on this Wi-Fi answered on camera ports. "
                                    + "The cameras may be on a different Wi-Fi/router, "
                                    + "or the router may block devices from seeing each other "
                                    + "(“AP isolation” / guest network).")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                String[] items = new String[found.size()];
                for (int i = 0; i < items.length; i++) items[i] = found.get(i).describe();
                new AlertDialog.Builder(this)
                        .setTitle("Found " + found.size() + " — tap to add")
                        .setItems(items, (d, w) -> editDialog(-1, found.get(w).ip))
                        .setPositiveButton("Close", null).show();
            });
        }).start();
    }

    private void tileMenu(final int idx, final CamView cv) {
        new AlertDialog.Builder(this)
                .setTitle(cv.getLabel())
                .setItems(new String[]{"Edit", "Move up", "Delete", "Details (why it fails)"}, (d, w) -> {
                    if (w == 3) {
                        String log = cv.getLog();
                        new AlertDialog.Builder(this).setTitle("Connection details")
                                .setMessage("Phone IP: " + Scanner.myIp(this) + "\n\n"
                                        + (log.isEmpty() ? "Still connecting…" : log))
                                .setPositiveButton("OK", null).show();
                    } else if (w == 0) editDialog(idx, null);
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
    private void editDialog(final int idx, String prefillIp) {
        final Store.Cam c = idx >= 0 ? cams.get(idx) : new Store.Cam();
        if (prefillIp != null) c.ip = prefillIp;
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
