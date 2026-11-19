package com.myhome.cctv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saves the camera list on the phone (nothing goes to any cloud). */
public class Store {

    public static class Cam {
        public String name = "";
        public String type = "icsee";   // icsee | custom
        public String ip = "";
        public int port = 554;
        public String user = "admin";
        public String pass = "";
        public int channel = 1;
        public String mainUrl = "";     // used when type == custom
        public String subUrl = "";      // optional low-res stream for the grid

        /** High-quality stream (full screen). */
        public String main() {
            if ("icsee".equals(type)) return icsee(0);
            return mainUrl;
        }

        /** Low-res stream for the grid; falls back to main. */
        public String sub() {
            if ("icsee".equals(type)) return icsee(1);
            return (subUrl == null || subUrl.trim().isEmpty()) ? mainUrl : subUrl;
        }

        // iCSee / XMeye (Xiongmai) style RTSP address
        private String icsee(int stream) {
            return "rtsp://" + ip + ":" + port + "/user=" + user + "&password=" + pass
                    + "&channel=" + channel + "&stream=" + stream + ".sdp?real_stream";
        }

        /** All addresses worth trying, best first (firmwares differ). */
        public java.util.List<String> candidates(boolean hq) {
            java.util.List<String> l = new java.util.ArrayList<>();
            if ("icsee".equals(type)) {
                int s = hq ? 0 : 1;
                String base = "rtsp://" + ip + ":" + port;
                String auth = "rtsp://" + user + ":" + pass + "@" + ip + ":" + port;
                l.add(icsee(s));
                l.add(base + "/user=" + user + "&password=" + pass + "&channel=" + channel + "&stream=" + s + ".sdp");
                l.add(base + "/user=" + user + "_password=" + pass + "_channel=" + channel + "_stream=" + s + ".sdp");
                l.add(auth + "/stream" + (s + 1));
                l.add(auth + "/live/ch0" + s + "_0");
                if (!hq) l.add(icsee(0));   // some cameras have no sub-stream
            } else {
                String m = mainUrl == null ? "" : mainUrl.trim();
                String sb = subUrl == null ? "" : subUrl.trim();
                if (!hq && !sb.isEmpty()) l.add(sb);
                if (!m.isEmpty()) l.add(m);
            }
            return l;
        }

        public String host() {
            if ("icsee".equals(type)) return ip;
            try { return android.net.Uri.parse(mainUrl.trim()).getHost(); } catch (Exception e) { return ""; }
        }

        public int rtspPort() {
            if ("icsee".equals(type)) return port;
            try {
                int p = android.net.Uri.parse(mainUrl.trim()).getPort();
                return p > 0 ? p : 554;
            } catch (Exception e) { return 554; }
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("type", type); o.put("ip", ip); o.put("port", port);
            o.put("user", user); o.put("pass", pass); o.put("channel", channel);
            o.put("mainUrl", mainUrl); o.put("subUrl", subUrl);
            return o;
        }

        static Cam fromJson(JSONObject o) {
            Cam c = new Cam();
            c.name = o.optString("name"); c.type = o.optString("type", "icsee");
            c.ip = o.optString("ip"); c.port = o.optInt("port", 554);
            c.user = o.optString("user", "admin"); c.pass = o.optString("pass");
            c.channel = o.optInt("channel", 1);
            c.mainUrl = o.optString("mainUrl"); c.subUrl = o.optString("subUrl");
            return c;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("cams", Context.MODE_PRIVATE);
    }

    public static List<Cam> load(Context ctx) {
        List<Cam> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) list.add(Cam.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return list;
    }

    public static void save(Context ctx, List<Cam> list) {
        try {
            JSONArray a = new JSONArray();
            for (Cam c : list) a.put(c.toJson());
            prefs(ctx).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) { }
    }
}
