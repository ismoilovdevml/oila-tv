package uz.oila.tv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
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
            reply(out, "403 Forbidden", "text/html", PAGE_DENIED);
            return;
        }
        if (request[0].equals("GET") && path.equals("/")) {
            reply(out, "200 OK", "text/html", PAGE);
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
            + "#s{margin-top:14px;min-height:1.4em;color:#9aa3b2}</style>";

    private static final String PAGE = "<!doctype html><html lang=uz><head>" + STYLE + "</head><body>"
            + "<h1>Oila TV &mdash; telefondan yozish</h1>"
            + "<textarea id=t placeholder='Shu yerga yozing...' autofocus></textarea>"
            + "<button class=yt id=yt>YouTube'da izlash</button>"
            + "<button class=tv id=en>TV'ga yuborish (Enter)</button>"
            + "<button class=gh id=cl>Tozalash</button><div id=s></div><script>"
            + "var k=new URLSearchParams(location.search).get('k'),t=document.getElementById('t'),"
            + "s=document.getElementById('s'),w;"
            + "function post(p,b){return fetch(p+'?k='+encodeURIComponent(k),{method:'POST',body:b||''})"
            + ".then(function(r){return r.text()}).catch(function(){return 'off'})}"
            + "function say(r,ok){s.textContent=r=='ok'?ok:r=='nofield'?"
            + "\"TV'da yozish maydoni yo'q — YouTube'da izlash tugmasini bosing\":r=='off'?"
            + "\"Televizor bilan aloqa yo'q. Telefon va TV bitta Wi-Fi'dami?\":r=='noapp'?"
            + "\"YouTube ochilmadi\":r}"
            + "t.oninput=function(){clearTimeout(w);w=setTimeout(function(){post('/type',t.value)"
            + ".then(function(r){say(r,\"TV'da yozilmoqda\")})},250)};"
            + "document.getElementById('yt').onclick=function(){if(t.value.trim())post('/yt',t.value)"
            + ".then(function(r){say(r,\"YouTube'da qidirilmoqda\")})};"
            + "document.getElementById('en').onclick=function(){post('/type',t.value).then(function(){"
            + "return post('/enter')}).then(function(r){say(r,'Yuborildi')})};"
            + "document.getElementById('cl').onclick=function(){t.value='';post('/type','');t.focus()};"
            + "</script></body></html>";

    private static final String PAGE_DENIED = "<!doctype html><html lang=uz><head>" + STYLE + "</head><body>"
            + "<h1>Kirish kodi noto'g'ri</h1><p>Televizordagi QR kodni qaytadan skanerlang:"
            + " Sozlamalar &rarr; Telefon.</p></body></html>";
}
