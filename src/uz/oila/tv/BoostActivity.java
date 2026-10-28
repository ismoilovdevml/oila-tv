package uz.oila.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/** Speed-up tools for a 1 GB TV: free memory held by background apps, and test the internet. */
public final class BoostActivity extends Activity {
    /** Never touched: the TV's own picture/input services and this app. */
    private static final Set<String> KEEP = new HashSet<>(java.util.Arrays.asList(
            "uz.oila.tv", "com.ktc.tvplayer", "com.ktc.tv.api.impl", "com.mediatek.tvinput",
            "com.mediatek.tv.agent", "com.android.systemui", "com.android.inputmethod.latin",
            "com.mediatek.hotkey.dispatcher", "android"));
    // Cloudflare's speed endpoint serves exactly the requested number of bytes.
    private static final String SPEED_URL = "https://speed.cloudflare.com/__down?bytes=5000000";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ListRow memory, speed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.BOOST, 0xFF6FE3A5, 0xFF2FA86B, "Tezlashtirish"),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);
        ListRow.section(col, "Televizor sekinlashsa, shu tugmani bosing");
        memory = ListRow.add(col, new ListRow(this, "Xotirani bo'shatish", "").onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { freeMemory(); }
        }));
        ListRow.section(col, "YouTube qotib qolsa, internetni tekshiring");
        speed = ListRow.add(col, new ListRow(this, "Internet tezligini o'lchash", "").onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { testSpeed(); }
        }));
        root.addView(Ui.footer(this, "▲ ▼", "tanlash", "OK", "boshlash", "BACK", "orqaga"),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        memory.set(freeMb() + " MB bo'sh", Ui.TEXT_DIM);
        memory.requestFocus();
    }

    private long freeMb() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem / 1048576;
    }

    private void freeMemory() {
        final long before = freeMb();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
            if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) continue;
            for (String pkg : p.pkgList) if (!KEEP.contains(pkg)) am.killBackgroundProcesses(pkg);
        }
        memory.set("Tozalanmoqda…", Ui.TEXT_DIM);
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                long after = freeMb();
                memory.set("+" + Math.max(0, after - before) + " MB  →  " + after + " MB bo'sh", ListRow.GREEN);
            }
        }, 1500);
    }

    private void testSpeed() {
        speed.set("O'lchanmoqda…", Ui.TEXT_DIM);
        new Thread(new Runnable() {
            @Override public void run() {
                String result;
                int color = ListRow.GREEN;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(SPEED_URL).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(15000);
                    long t0 = System.nanoTime(), n = 0;
                    byte[] buf = new byte[32768];
                    try (InputStream in = c.getInputStream()) {
                        for (int r; (r = in.read(buf)) > 0; ) n += r;
                    }
                    double mbps = n * 8 / ((System.nanoTime() - t0) / 1e9) / 1e6;
                    result = String.format("%.1f Mbit/s  —  %s", mbps,
                            mbps >= 10 ? "YouTube HD uchun yetarli" : mbps >= 3 ? "Oddiy sifat uchun yetarli" : "Sekin");
                    if (mbps < 3) color = 0xFFFF8A80;
                } catch (Exception e) {
                    result = "Internet yo'q";
                    color = 0xFFFF8A80;
                }
                final String r = result;
                final int col = color;
                ui.post(new Runnable() {
                    @Override public void run() { speed.set(r, col); }
                });
            }
        }).start();
    }
}
