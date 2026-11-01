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
        root.addView(Ui.header(this, Glyphs.BOOST, 0xFF6FE3A5, 0xFF2FA86B, getString(R.string.boost_title)),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);
        ListRow.section(col, getString(R.string.boost_memory_hint));
        memory = ListRow.add(col, new ListRow(this, getString(R.string.boost_free_memory), "").onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { freeMemory(); }
        }));
        ListRow.section(col, getString(R.string.boost_internet_hint));
        speed = ListRow.add(col, new ListRow(this, getString(R.string.boost_speed_test), "").onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { testSpeed(); }
        }));
        root.addView(Ui.footer(this, "▲ ▼", getString(R.string.common_select), "OK",
                getString(R.string.common_start), "BACK", getString(R.string.common_back)),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        memory.set(getString(R.string.boost_mb_free, freeMb()), Ui.TEXT_DIM);
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
        memory.set(getString(R.string.boost_cleaning), Ui.TEXT_DIM);
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                long after = freeMb();
                memory.set(getString(R.string.boost_freed, Math.max(0, after - before), after), ListRow.GREEN);
            }
        }, 1500);
    }

    private void testSpeed() {
        speed.set(getString(R.string.boost_measuring), Ui.TEXT_DIM);
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
                    result = getString(R.string.boost_speed_result, mbps, getString(mbps >= 10 ? R.string.boost_speed_hd
                            : mbps >= 3 ? R.string.boost_speed_normal : R.string.boost_speed_slow));
                    if (mbps < 3) color = 0xFFFF8A80;
                } catch (Exception e) {
                    result = getString(R.string.boost_no_internet);
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
