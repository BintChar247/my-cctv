package com.myhome.cctv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Finds cameras on the home Wi-Fi by checking which devices answer on camera ports. */
public class Scanner {

    public static class Found {
        public String ip;
        public boolean rtsp;     // port 554
        public boolean xmeye;    // port 34567 = iCSee / XMeye
        public boolean onvif;    // port 8899 or 80 ONVIF-ish
        public String describe() {
            StringBuilder s = new StringBuilder(ip).append("  —  ");
            if (xmeye) s.append("iCSee camera");
            else if (rtsp) s.append("camera (RTSP)");
            else s.append("possible camera");
            if (!rtsp) s.append(", RTSP off");
            return s.toString();
        }
    }

    /** Returns e.g. "192.168.1." for the phone's current Wi-Fi, or null. */
    public static String subnet(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(n);
            for (LinkAddress la : lp.getLinkAddresses()) {
                if (la.getAddress() instanceof Inet4Address) {
                    String ip = la.getAddress().getHostAddress();
                    return ip.substring(0, ip.lastIndexOf('.') + 1);
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    public static String myIp(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (LinkAddress la : cm.getLinkProperties(cm.getActiveNetwork()).getLinkAddresses())
                if (la.getAddress() instanceof Inet4Address) return la.getAddress().getHostAddress();
        } catch (Exception ignored) { }
        return "?";
    }

    /** Blocking: run off the main thread. Takes ~10 seconds. */
    public static List<Found> scan(String subnet) {
        final List<Found> out = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(64);
        for (int i = 1; i < 255; i++) {
            final String ip = subnet + i;
            pool.execute(() -> {
                boolean xm = open(ip, 34567), r = open(ip, 554), ov = open(ip, 8899);
                if (xm || r || ov) {
                    Found f = new Found();
                    f.ip = ip; f.rtsp = r; f.xmeye = xm; f.onvif = ov;
                    out.add(f);
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(40, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        List<Found> sorted = new ArrayList<>(out);
        Collections.sort(sorted, (a, b) -> Integer.compare(last(a.ip), last(b.ip)));
        return sorted;
    }

    private static int last(String ip) { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); }

    private static boolean open(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 600);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
