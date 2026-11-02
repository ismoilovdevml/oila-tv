package uz.oila.tv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny web server on the home network: a phone opens its page (from the QR code) and types into
 * the focused TV text field or sends a YouTube search. Every request must carry the TV's token.
 */
final class PhoneLink implements Runnable {
    static final int PORT = 8765;
    static final String KEYBOARD = "uz.oila.tv/.PhoneKeyboard";
    private static final String TAG = "OilaPhone";
    private static final int MAX_BODY = 4096, MAX_OPEN = 16, IDLE_MS = 60000;
    // The root input daemon (tools/remote) listens here and injects keys system-wide.
    private static final int REMOTE_PORT = 8766;
    // Only these keycodes may be injected, so the page can never send arbitrary events.
    private static final java.util.Set<Integer> ALLOWED_KEYS = new java.util.HashSet<Integer>(java.util.Arrays.asList(
            3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 26, 66, 67, 82, 85, 89, 90, 164, 166, 167));
    private Socket remote; // cached connection to the daemon
    private static PhoneLink running;
    private final AtomicInteger open = new AtomicInteger();

    /** Where phone text goes; set by PhoneKeyboard while a TV text field has input focus. */
    interface Target {
        void setText(String text);
        void enter();
    }

    static volatile Target target;

    private final Context app;
    private final byte[] token;

    private PhoneLink(Context app) {
        this.app = app;
        this.token = token(app).getBytes(StandardCharsets.UTF_8);
    }

    static synchronized void start(Context c) {
        if (running != null) return;
        running = new PhoneLink(c.getApplicationContext());
        Thread t = new Thread(running, "phone-link");
        t.setDaemon(true);
        t.start();
    }

    /** Page address for the QR code, or null while the TV has no network address. */
    static String url(Context c) {
        String ip = ip(c);
        return ip == null ? null : "http://" + ip + ":" + PORT + "/?k=" + token(c);
    }

    /** Six random digits, kept across restarts so a phone bookmark keeps working. */
    static synchronized String token(Context c) {
        SharedPreferences p = c.getSharedPreferences("phone", Context.MODE_PRIVATE);
        String t = p.getString("token", null);
        if (t == null) {
            t = String.format("%06d", new SecureRandom().nextInt(1000000));
            p.edit().putString("token", t).apply();
        }
        return t;
    }

    /** Switching to the remote keyboard makes it the default; make ours the default again. */
    static void restoreKeyboard(Context c) {
        try {
            String enabled = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_INPUT_METHODS);
            if (enabled != null && enabled.contains(KEYBOARD) && !KEYBOARD.equals(Settings.Secure.getString(
                    c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD))) {
                Settings.Secure.putString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, KEYBOARD);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "no WRITE_SECURE_SETTINGS, keyboard left as is");
        }
    }

    private static String ip(Context c) {
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wm == null ? 0 : wm.getConnectionInfo().getIpAddress();
        if (ip != 0) return (ip & 255) + "." + (ip >> 8 & 255) + "." + (ip >> 16 & 255) + "." + (ip >> 24 & 255);
        try { // wired Ethernet
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (IOException ignored) {
            // no interfaces
        }
        return null;
    }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                final Socket s = server.accept();
                // One thread per connection: browsers open spare connections ahead of time and
                // send nothing on them, which must not hold up the real request.
                if (open.incrementAndGet() > MAX_OPEN) {
                    open.decrementAndGet();
                    s.close();
                    continue;
                }
                Thread t = new Thread(new Runnable() {
                    @Override public void run() { serve(s); }
                }, "phone-link-conn");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            Log.e(TAG, "cannot listen on " + PORT + ": " + e);
            synchronized (PhoneLink.class) { running = null; }
        }
    }

    private void serve(Socket s) {
        try {
            // A spare connection may wait while the user confirms the link on the phone.
            s.setSoTimeout(IDLE_MS);
            handle(s.getInputStream(), s.getOutputStream(), s.getInetAddress().getHostAddress());
            s.shutdownOutput();
        } catch (IOException e) {
            Log.w(TAG, s.getInetAddress().getHostAddress() + " request failed: " + e);
        } finally {
            try { s.close(); } catch (IOException ignored) { /* already closed */ }
            open.decrementAndGet();
        }
    }

    private void handle(InputStream in, OutputStream out, String from) throws IOException {
        String first = line(in);
        String[] request = first.split(" ");
        if (request.length < 2) {
            Log.w(TAG, from + " not HTTP: " + first.substring(0, Math.min(first.length(), 24)));
            return;
        }
        int length = 0;
        for (String h = line(in); !h.isEmpty(); h = line(in)) {
            if (h.regionMatches(true, 0, "content-length:", 0, 15)) length = Integer.parseInt(h.substring(15).trim());
        }
        if (length < 0 || length > MAX_BODY) {
            reply(out, "413 Too Large", "text/plain", "too long");
            return;
        }
        byte[] body = new byte[length];
        for (int n = 0; n < length; ) {
            int r = in.read(body, n, length - n);
            if (r < 0) throw new IOException("short body");
            n += r;
        }
        String text = new String(body, StandardCharsets.UTF_8);

        String target = request[1];
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        String key = q < 0 ? "" : param(target.substring(q + 1), "k");
        Log.i(TAG, from + " " + request[0] + " " + path);
        if (!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), token)) {
            reply(out, "403 Forbidden", "text/html", denied());
            return;
        }
        if (request[0].equals("GET") && path.equals("/")) {
            reply(out, "200 OK", "text/html", page());
        } else if (request[0].equals("GET") && path.equals("/remote")) {
            reply(out, "200 OK", "text/html", remotePage());
        } else if (request[0].equals("POST") && path.equals("/key")) {
            reply(out, "200 OK", "text/plain", sendKey(text.trim()) ? "ok" : "noremote");
        } else if (request[0].equals("POST") && path.equals("/app")) {
            reply(out, "200 OK", "text/plain", launchApp(text.trim()) ? "ok" : "noapp");
        } else if (request[0].equals("GET") && path.equals("/state")) {
            reply(out, "200 OK", "application/json", stateJson());
        } else if (request[0].equals("POST") && path.equals("/yt")) {
            reply(out, "200 OK", "text/plain", youtube(app, text.trim()) ? "ok" : "noapp");
        } else if (request[0].equals("POST") && (path.equals("/type") || path.equals("/enter"))) {
            Target t = PhoneLink.target;
            if (t == null) {
                reply(out, "200 OK", "text/plain", "nofield");
            } else {
                if (path.equals("/type")) t.setText(text);
                else t.enter();
                reply(out, "200 OK", "text/plain", "ok");
            }
        } else {
            reply(out, "404 Not Found", "text/plain", "not found");
        }
    }

    /** Opens YouTube's results page for {@code query}. */
    static boolean youtube(Context c, String query) {
        if (query.isEmpty()) return true;
        try {
            Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"));
            Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(new Intent(i).setComponent(new ComponentName("com.google.android.youtube.tv",
                        "com.google.android.apps.youtube.tv.activity.ShellActivity")));
            } catch (ActivityNotFoundException e) {
                c.startActivity(i);
            }
            return true;
        } catch (IOException | ActivityNotFoundException e) {
            Log.w(TAG, "youtube search failed: " + e);
            return false;
        }
    }

    /** Forwards one whitelisted keycode to the root daemon, which injects it system-wide. */
    private synchronized boolean sendKey(String body) {
        int code;
        try {
            code = Integer.parseInt(body);
        } catch (NumberFormatException e) {
            return false;
        }
        if (!ALLOWED_KEYS.contains(code)) return false;
        byte[] line = ("K " + code + "\n").getBytes(StandardCharsets.US_ASCII);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (remote == null || remote.isClosed() || !remote.isConnected()) {
                    remote = new Socket();
                    remote.connect(new InetSocketAddress("127.0.0.1", REMOTE_PORT), 500);
                }
                remote.getOutputStream().write(line);
                remote.getOutputStream().flush();
                return true;
            } catch (IOException e) {
                try { if (remote != null) remote.close(); } catch (IOException ignored) { }
                remote = null; // reconnect once; the daemon may have restarted
            }
        }
        Log.w(TAG, "remote daemon unreachable on " + REMOTE_PORT);
        return false;
    }

    /** Current music volume and mute state, so the remote can show a real level. */
    private String stateJson() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        int max = am == null ? 1 : am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int vol = am == null ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = am != null && am.isStreamMute(AudioManager.STREAM_MUSIC);
        return "{\"vol\":" + vol + ",\"max\":" + max + ",\"muted\":" + muted + "}";
    }

    /** Launches a shortcut app or one of our own screens on the TV from the phone remote. */
    private boolean launchApp(String which) {
        if (which.equals("set") || which.equals("src")) {
            try {
                app.startActivity(new Intent(app, which.equals("set") ? SettingsActivity.class : SourceActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        String pkg = which.equals("yt") ? "com.google.android.youtube.tv"
                : which.equals("nf") ? "com.netflix.mediaclient"
                : which.equals("itv") ? "uz.i_tv.player" : null;
        if (pkg == null) return false;
        Intent i = app.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = app.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        try {
            app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "launch " + pkg + " failed: " + e);
            return false;
        }
    }

    private static String param(String query, String name) {
        for (String pair : query.split("&")) {
            if (pair.startsWith(name + "=")) return pair.substring(name.length() + 1);
        }
        return "";
    }

    /** One CRLF-terminated header line as ISO-8859-1; the body is read separately as UTF-8. */
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int c = in.read(); c != '\n'; c = in.read()) {
            if (c < 0) throw new IOException("connection closed");
            if (c != '\r' && b.size() < MAX_BODY) b.write(c);
        }
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static void reply(OutputStream out, String status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: "
                + b.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private static final String STYLE = "<meta charset=utf-8><meta name=viewport content='width=device-width,"
            + "initial-scale=1'><title>Oila TV</title><style>body{margin:0;padding:20px;background:#0d1018;"
            + "color:#fff;font:18px system-ui,sans-serif}h1{font-size:22px;margin:0 0 14px}textarea{width:100%;font:inherit;"
            + "box-sizing:border-box;height:100px;padding:12px;font-size:22px;color:#fff;background:#171c2a;"
            + "border:2px solid #2a3142;border-radius:12px}button{display:block;width:100%;margin-top:12px;"
            + "padding:16px;font-size:20px;font-weight:700;color:#fff;border:0;border-radius:12px}"
            + ".yt{background:#e53935}.tv{background:#2f63dd}.gh{background:#2a3142}"
            + "#s{margin-top:14px;min-height:1.4em;color:#9aa3b2}a.link{display:block;text-align:center;"
            + "margin-top:18px;color:#5cb8e8;font-size:18px}</style>";

    // "Pro pult" design (design/Pro pult-html): a framed panel with a circular d-pad, segmented
    // D-pad / Sensor / Numbers views, a volume cluster with a real level bar, playback and shortcuts.
    private static final String REMOTE_HEAD = "<meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Oila TV</title>"
            + "<link rel=stylesheet href='https://fonts.googleapis.com/css2?family=Manrope:wght@600;700;800&display=swap'>"
            + "<style>*{box-sizing:border-box}body{margin:0;background:#07090D;color:#E8EAF0;"
            + "font-family:Manrope,system-ui,-apple-system,sans-serif}"
            + "button{font-family:inherit;border:0;cursor:pointer;color:inherit;-webkit-tap-highlight-color:transparent;"
            + "transition:transform .08s,background-color .15s}button:active{transform:scale(.95)}"
            + ".frame{max-width:460px;margin:0 auto;padding:14px}"
            + ".panel{padding:18px;border-radius:32px;background:#10131A;border:1px solid #1F2430;display:flex;flex-direction:column;gap:12px}"
            + ".top{display:flex;align-items:center;justify-content:space-between;gap:12px}"
            + ".top h1{margin:0;font-size:22px;font-weight:800;letter-spacing:-.02em}"
            + ".stat{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:600;color:#A3ABBD;margin-top:4px}"
            + ".dot{width:8px;height:8px;border-radius:50%;background:#34D399}"
            + ".ic{width:44px;height:44px;border-radius:14px;background:#1A1F29;color:#C9CFDB;display:flex;align-items:center;justify-content:center}"
            + ".pwr{width:48px;height:48px;border-radius:50%;background:#2A1519;border:1px solid #4A2128;color:#FF7A7A;display:flex;align-items:center;justify-content:center}"
            + ".srow{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 14px;border-radius:14px;background:#161A22;border:1px solid #1F2430;font-size:13px}"
            + ".srow .l{color:#A3ABBD;font-weight:600}.srow b{font-weight:800}"
            + ".meta{display:flex;align-items:center;gap:10px;font-weight:700;color:#C9CFDB;font-variant-numeric:tabular-nums}"
            + ".vsep{width:1px;height:14px;background:#2E3546}"
            + ".apps{display:grid;grid-template-columns:repeat(3,minmax(0,1fr)) 52px;gap:8px}"
            + ".app{height:52px;border-radius:16px;color:#fff;font-size:15px;font-weight:800;display:flex;align-items:center;justify-content:center;text-align:center;line-height:1.05}"
            + ".yt{background:#CC1F1A}.nf{background:#B20710}.itv{background:#146EB4;font-size:14px}"
            + ".tabs{display:grid;grid-template-columns:repeat(3,1fr);gap:4px;padding:4px;border-radius:14px;background:#161A22;border:1px solid #1F2430}"
            + ".tab{height:36px;border-radius:10px;font-size:13px;font-weight:700;background:transparent;color:#A3ABBD}"
            + ".tab.on{background:#3D6BFF;color:#fff}"
            + ".stage{height:280px;display:flex;align-items:center;justify-content:center}"
            + ".dpad{position:relative;width:276px;height:276px;border-radius:50%;background:#161A22;border:1px solid #232938}"
            + ".dpad button{position:absolute;background:transparent;color:#E8EAF0;display:flex;align-items:center;justify-content:center}"
            + ".du{top:8px;left:98px;width:80px;height:72px;border-radius:22px}.dd{bottom:8px;left:98px;width:80px;height:72px;border-radius:22px}"
            + ".dl{left:8px;top:98px;width:72px;height:80px;border-radius:22px}.dr{right:8px;top:98px;width:72px;height:80px;border-radius:22px}"
            + ".ok{left:82px;top:82px;width:112px;height:112px;border-radius:50%;background:#3D6BFF;color:#fff;font-size:26px;font-weight:800;border:6px solid #10131A}"
            + ".touch{width:100%;height:276px;border-radius:28px;background:#161A22;border:1.5px dashed #2E3546;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#C9CFDB}"
            + ".touch b{font-size:16px;font-weight:800;color:#E8EAF0}.touch span{font-size:13px;font-weight:600;color:#A3ABBD}"
            + ".nums{width:100%;display:grid;grid-template-columns:repeat(3,1fr);gap:10px}"
            + ".num{height:61px;border-radius:16px;background:#1A1F29;border:1px solid #232938;font-weight:800;font-variant-numeric:tabular-nums;font-size:22px;color:#E8EAF0}.num.sm{font-size:13px}"
            + ".nav{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}"
            + ".navb{height:52px;border-radius:16px;background:#1A1F29;color:#E8EAF0;display:flex;align-items:center;justify-content:center;gap:8px;font-size:13px;font-weight:700}"
            + ".vrow{display:flex;align-items:stretch;justify-content:space-between;gap:12px}"
            + ".vcol{width:76px;height:150px;border-radius:38px;background:#1A1F29;border:1px solid #232938;display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:4px}"
            + ".vcol button{width:66px;height:54px;border-radius:30px;background:transparent;color:#E8EAF0;display:flex;align-items:center;justify-content:center}"
            + ".vcol span{font-size:11px;font-weight:800;letter-spacing:.12em;color:#A3ABBD}"
            + ".vmid{flex-grow:1;display:flex;flex-direction:column;justify-content:space-between;gap:10px}"
            + ".vcard{padding:12px 14px;border-radius:18px;background:#161A22;border:1px solid #1F2430;display:flex;flex-direction:column;gap:10px}"
            + ".vtop{display:flex;align-items:baseline;justify-content:space-between;font-size:12px;font-weight:700;color:#A3ABBD}"
            + ".vtop b{font-size:18px;font-weight:800;color:#E8EAF0;font-variant-numeric:tabular-nums}"
            + ".vbar{height:6px;border-radius:3px;background:#262C3A;overflow:hidden}.vfill{height:6px;border-radius:3px;background:#3D6BFF;width:0}"
            + ".mbtn{height:56px;border-radius:18px;background:#1A1F29;color:#E8EAF0;display:flex;align-items:center;justify-content:center;width:100%}"
            + ".mbtn.on{background:#3A1E24;color:#FF8A8A}"
            + ".play{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding:6px;border-radius:20px;background:#161A22;border:1px solid #1F2430}"
            + ".pbtn{height:48px;border-radius:14px;background:transparent;color:#C9CFDB;display:flex;align-items:center;justify-content:center}.pbtn.mid{background:#232938;color:#fff}"
            + ".kbd{height:52px;border-radius:16px;background:#161A22;border:1px solid #232938;color:#7EA0FF;display:flex;align-items:center;justify-content:center;gap:10px;font-size:14px;font-weight:700;text-decoration:none}"
            + "#s{min-height:1em;color:#A3ABBD;text-align:center;font-size:13px}[hidden]{display:none}</style>";

    /** The phone page in the TV's current language; JS strings are passed through {@link #js}. */
    private String page() {
        return "<!doctype html><html lang=" + lang() + "><head>" + STYLE + "</head><body>"
                + "<h1>" + html(app.getString(R.string.page_title)) + "</h1>"
                + "<textarea id=t placeholder='" + html(app.getString(R.string.page_placeholder)) + "' autofocus></textarea>"
                + "<button class=yt id=yt>" + html(app.getString(R.string.page_youtube)) + "</button>"
                + "<button class=tv id=en>" + html(app.getString(R.string.page_send)) + "</button>"
                + "<button class=gh id=cl>" + html(app.getString(R.string.page_clear)) + "</button>"
                + "<a class=link href='/remote?k=" + token(app) + "'>" + html(app.getString(R.string.remote_open))
                + "</a><div id=s></div><script>"
                + "var k=new URLSearchParams(location.search).get('k'),t=document.getElementById('t'),"
                + "s=document.getElementById('s'),w;"
                + "function post(p,b){return fetch(p+'?k='+encodeURIComponent(k),{method:'POST',body:b||''})"
                + ".then(function(r){return r.text()}).catch(function(){return 'off'})}"
                + "function say(r,ok){s.textContent=r=='ok'?ok:r=='nofield'?" + js(R.string.page_nofield)
                + ":r=='off'?" + js(R.string.page_offline) + ":r=='noapp'?" + js(R.string.page_noapp) + ":r}"
                + "t.oninput=function(){clearTimeout(w);w=setTimeout(function(){post('/type',t.value)"
                + ".then(function(r){say(r," + js(R.string.page_typing) + ")})},250)};"
                + "document.getElementById('yt').onclick=function(){if(t.value.trim())post('/yt',t.value)"
                + ".then(function(r){say(r," + js(R.string.page_searching) + ")})};"
                + "document.getElementById('en').onclick=function(){post('/type',t.value).then(function(){"
                + "return post('/enter')}).then(function(r){say(r," + js(R.string.page_sent) + ")})};"
                + "document.getElementById('cl').onclick=function(){t.value='';post('/type','');t.focus()};"
                + "</script></body></html>";
    }

    // Line-drawn icons from the Pro pult design (stroke = currentColor); single quotes avoid escaping.
    private static String svg(String inner, String extra) {
        return "<svg width=22 height=22 viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' "
                + "stroke-linecap='round' stroke-linejoin='round' " + extra + ">" + inner + "</svg>";
    }
    private static final String IC_INPUT = svg("<rect x=3 y=4 width=18 height=13 rx=2/><path d='M8 21h8M12 17v4'/>", ""),
            IC_POWER = svg("<path d='M12 3v9'/><path d='M6.3 6.3a8 8 0 1 0 11.4 0'/>", ""),
            IC_UP = svg("<path d='m6 15 6-6 6 6'/>", ""), IC_DOWN = svg("<path d='m6 9 6 6 6-6'/>", ""),
            IC_LEFT = svg("<path d='m15 6-6 6 6 6'/>", ""), IC_RIGHT = svg("<path d='m9 6 6 6-6 6'/>", ""),
            IC_BACK = svg("<path d='M9 14 4 9l5-5'/><path d='M4 9h11a5 5 0 0 1 0 10h-3'/>", ""),
            IC_HOME = svg("<path d='M3 10.5 12 3l9 7.5'/><path d='M5 9.5V21h14V9.5'/>", ""),
            IC_MENU = svg("<path d='M4 7h16M4 12h16M4 17h16'/>", ""),
            IC_PLUS = svg("<path d='M12 5v14M5 12h14'/>", ""), IC_MINUS = svg("<path d='M5 12h14'/>", ""),
            IC_MUTE = svg("<path d='M11 5 6 9H3v6h3l5 4z'/><path d='m22 9-6 6M16 9l6 6'/>", ""),
            IC_REW = svg("<path d='M11 19 2 12l9-7z'/><path d='M22 19l-9-7 9-7z'/>", "fill='currentColor'"),
            IC_FF = svg("<path d='m13 19 9-7-9-7z'/><path d='m2 19 9-7-9-7z'/>", "fill='currentColor'"),
            IC_PLAY = "<span id=pico>" + svg("<path d='M7 4v16l13-8z'/>", "fill='currentColor'") + "</span>",
            IC_KBD = svg("<rect x=2 y=6 width=20 height=12 rx=2/><path d='M7 14h10M6 10h.01M10 10h.01M14 10h.01M18 10h.01'/>", "");

    /**
     * The Pro pult remote: a framed panel with status, app shortcuts, a segmented d-pad / touch /
     * number view, a volume cluster with a real level bar, playback and a keyboard link.
     */
    private String remotePage() {
        String t = token(app);
        return "<!doctype html><html lang=" + lang() + "><head>" + REMOTE_HEAD + "</head><body><div class=frame>"
                + "<div class=panel>"
                + "<div class=top><div><h1>Oila TV</h1><div class=stat><span class=dot></span><span>"
                + html(app.getString(R.string.remote_status_on)) + " · TV</span></div></div>"
                + "<div style='display:flex;gap:10px'>"
                + "<button class=ic data-app=src data-l='" + a(R.string.home_source) + "'>" + IC_INPUT + "</button>"
                + "<button class=pwr data-k=26 data-l='" + a(R.string.remote_status_on) + "'>" + IC_POWER + "</button></div></div>"
                + "<div class=srow><div style='display:flex;gap:8px;min-width:0'><span class=l>"
                + html(app.getString(R.string.remote_last)) + "</span><b id=last>" + html(app.getString(R.string.remote_ready))
                + "</b></div><div class=meta><span>" + html(app.getString(R.string.remote_sec_volume))
                + " <b id=vol>–</b></span></div></div>"
                + "<div class=apps>"
                + "<button class='app yt' data-app=yt data-l=YouTube>YouTube</button>"
                + "<button class='app nf' data-app=nf data-l=Netflix>Netflix</button>"
                + "<button class='app itv' data-app=itv data-l='" + a(R.string.home_live_tv) + "'>"
                + html(app.getString(R.string.home_live_tv)) + "</button>"
                + "<button class=ic data-app=set data-l='" + a(R.string.home_settings) + "'>" + IC_MENU + "</button></div>"
                + "<div class=tabs><button class='tab on' data-tab=d>D-pad</button>"
                + "<button class=tab data-tab=t>" + html(app.getString(R.string.remote_sensor)) + "</button>"
                + "<button class=tab data-tab=n>" + html(app.getString(R.string.remote_sec_numbers)) + "</button></div>"
                + "<div class=stage>"
                + "<div id=vd class=dpad>"
                + "<button class=du data-k=19 data-l='▲'>" + IC_UP + "</button>"
                + "<button class=dd data-k=20 data-l='▼'>" + IC_DOWN + "</button>"
                + "<button class=dl data-k=21 data-l='◀'>" + IC_LEFT + "</button>"
                + "<button class=dr data-k=22 data-l='▶'>" + IC_RIGHT + "</button>"
                + "<button class=ok data-k=23 data-l=OK>OK</button></div>"
                + "<button id=vt hidden class=touch data-k=23 data-l=OK><b>"
                + html(app.getString(R.string.remote_touch1)) + "</b><span>"
                + html(app.getString(R.string.remote_touch2)) + "</span></button>"
                + "<div id=vn hidden class=nums>"
                + num(8, "1") + num(9, "2") + num(10, "3") + num(11, "4") + num(12, "5") + num(13, "6")
                + num(14, "7") + num(15, "8") + num(16, "9")
                + "<button class='num sm' data-k=82 data-l='" + a(R.string.remote_list) + "'>"
                + html(app.getString(R.string.remote_list)) + "</button>"
                + num(7, "0")
                + "<button class='num sm' data-k=4 data-l='" + a(R.string.remote_prev) + "'>"
                + html(app.getString(R.string.remote_prev)) + "</button></div></div>"
                + "<div class=nav>"
                + "<button class=navb data-k=4 data-l='" + a(R.string.remote_back) + "'>" + IC_BACK + "<span>"
                + html(app.getString(R.string.remote_back)) + "</span></button>"
                + "<button class=navb data-k=3 data-l='" + a(R.string.remote_home) + "'>" + IC_HOME + "<span>"
                + html(app.getString(R.string.remote_home)) + "</span></button>"
                + "<button class=navb data-k=82 data-l='" + a(R.string.remote_menu) + "'>" + IC_MENU + "<span>"
                + html(app.getString(R.string.remote_menu)) + "</span></button></div>"
                + "<div class=vrow>"
                + "<div class=vcol><button data-k=24 data-vol data-l='" + a(R.string.remote_sec_volume) + " +'>" + IC_PLUS + "</button>"
                + "<span>" + html(app.getString(R.string.remote_vol_cap)) + "</span>"
                + "<button data-k=25 data-vol data-l='" + a(R.string.remote_sec_volume) + " −'>" + IC_MINUS + "</button></div>"
                + "<div class=vmid><div class=vcard><div class=vtop><span>"
                + html(app.getString(R.string.remote_vol_level)) + "</span><b id=vol2>–</b></div>"
                + "<div class=vbar><div id=vfill class=vfill></div></div></div>"
                + "<button id=mute class=mbtn data-k=164 data-vol data-l='" + a(R.string.volume_muted) + "'>" + IC_MUTE + "</button></div>"
                + "<div class=vcol><button data-k=166 data-l='" + a(R.string.remote_ch_cap) + " +'>" + IC_UP + "</button>"
                + "<span>" + html(app.getString(R.string.remote_ch_cap)) + "</span>"
                + "<button data-k=167 data-l='" + a(R.string.remote_ch_cap) + " −'>" + IC_DOWN + "</button></div></div>"
                + "<div class=play><button class=pbtn data-k=89 data-l='⏪'>" + IC_REW + "</button>"
                + "<button id=play class='pbtn mid' data-k=85 data-l='⏯'>" + IC_PLAY + "</button>"
                + "<button class=pbtn data-k=90 data-l='⏩'>" + IC_FF + "</button></div>"
                + "<a class=kbd href='/?k=" + t + "'>" + IC_KBD + "<span>"
                + html(app.getString(R.string.remote_type)) + "</span></a>"
                + "<div id=s></div></div></div>" + remoteScript() + "</body></html>";
    }

    private static String num(int code, String label) {
        return "<button class=num data-k=" + code + " data-l='" + label + "'>" + label + "</button>";
    }

    /** Client logic: tab switching, posting keys/apps, the live volume bar and the swipe touchpad. */
    private String remoteScript() {
        return "<script>var k=new URLSearchParams(location.search).get('k'),s=document.getElementById('s');"
                + "function post(p,b){return fetch(p+'?k='+encodeURIComponent(k),{method:'POST',body:b})"
                + ".then(function(r){return r.text()}).then(function(r){if(r=='noremote')s.textContent="
                + js(R.string.page_offline) + ";else if(r=='noapp')s.textContent=" + js(R.string.remote_noapp)
                + ";else s.textContent='';return r}).catch(function(){})}"
                + "function st(){fetch('/state?k='+encodeURIComponent(k)).then(function(r){return r.json()})"
                + ".then(function(j){var p=j.muted?0:Math.round(j.vol*100/j.max);"
                + "document.getElementById('vol').textContent=j.muted?'0':j.vol;"
                + "document.getElementById('vol2').textContent=j.muted?'0':j.vol;"
                + "document.getElementById('vfill').style.width=p+'%';"
                + "document.getElementById('mute').classList.toggle('on',j.muted)}).catch(function(){})}"
                + "document.querySelectorAll('[data-k]').forEach(function(b){b.addEventListener('click',function(){"
                + "var l=b.getAttribute('data-l');if(l)document.getElementById('last').textContent=l;"
                + "post('/key',b.getAttribute('data-k')).then(function(){if(b.hasAttribute('data-vol'))setTimeout(st,120)});"
                + "if(b.id=='play'){document.getElementById('pico').innerHTML=document.getElementById('pico').dataset.p=="
                + "'1'?" + jsLit("<svg width=22 height=22 viewBox='0 0 24 24' fill='currentColor'><path d='M7 4v16l13-8z'/></svg>")
                + ":" + jsLit("<svg width=22 height=22 viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='3' stroke-linecap='round'><path d='M8 5v14M16 5v14'/></svg>")
                + ";document.getElementById('pico').dataset.p=document.getElementById('pico').dataset.p=='1'?'0':'1'}});});"
                + "document.querySelectorAll('[data-app]').forEach(function(b){b.addEventListener('click',function(){"
                + "var l=b.getAttribute('data-l');if(l)document.getElementById('last').textContent=l;"
                + "post('/app',b.getAttribute('data-app'))});});"
                + "var views={d:'vd',t:'vt',n:'vn'};document.querySelectorAll('[data-tab]').forEach(function(b){"
                + "b.addEventListener('click',function(){var m=b.getAttribute('data-tab');"
                + "document.querySelectorAll('.tab').forEach(function(x){x.classList.toggle('on',x==b)});"
                + "for(var key in views){document.getElementById(views[key]).hidden=key!=m}})});"
                + "var tp=document.getElementById('vt'),sx=0,sy=0,mv=0;"
                + "tp.addEventListener('touchstart',function(e){var t=e.touches[0];sx=t.clientX;sy=t.clientY;mv=0},{passive:true});"
                + "tp.addEventListener('touchend',function(e){var t=e.changedTouches[0],dx=t.clientX-sx,dy=t.clientY-sy;"
                + "if(Math.abs(dx)<24&&Math.abs(dy)<24){post('/key','23');return}"
                + "var c=Math.abs(dx)>Math.abs(dy)?(dx>0?'22':'21'):(dy>0?'20':'19');post('/key',c)});"
                + "st();setInterval(st,5000);</script>";
    }

    /** A resource string as an attribute value: HTML-escaped and never breaking the single quotes. */
    private String a(int id) {
        return html(app.getString(id));
    }

    private String denied() {
        return "<!doctype html><html lang=" + lang() + "><head>" + STYLE + "</head><body>"
                + "<h1>" + html(app.getString(R.string.page_denied_title)) + "</h1><p>"
                + html(app.getString(R.string.page_denied_body)) + "</p></body></html>";
    }

    private String lang() {
        return app.getResources().getConfiguration().getLocales().get(0).getLanguage();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** A plain string as a double-quoted JS literal, safe inside a script element. */
    private static String jsLit(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"";
    }

    /** A resource string as a double-quoted JS literal, safe inside a script element. */
    private String js(int id) {
        String s = app.getString(id).replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c");
        return "\"" + s + "\"";
    }
}
