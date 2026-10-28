package uz.oila.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.View;
import android.widget.LinearLayout;

/** What this TV is, how full it is, and where it sits on the network — read-only. */
public final class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.INFO, 0xFFBD7CEA, 0xFF8538D2, "Televizor haqida"),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);

        ListRow.section(col, "Qurilma");
        ListRow first = ListRow.add(col, new ListRow(this, "Model", prop("ktc.board.model", Build.MODEL)));
        ListRow.add(col, new ListRow(this, "Android", Build.VERSION.RELEASE));
        ListRow.add(col, new ListRow(this, "Proshivka", prop("ro.product.version", Build.DISPLAY)));
        ListRow.add(col, new ListRow(this, "Yangilangan sana",
                new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(Build.TIME))));

        ListRow.section(col, "Xotira");
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        ListRow.add(col, new ListRow(this, "Operativ xotira",
                gb(mi.availMem) + " bo'sh / " + gb(mi.totalMem)));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long free = fs.getAvailableBytes(), total = fs.getTotalBytes();
        ListRow disk = ListRow.add(col, new ListRow(this, "Ichki xotira", gb(free) + " bo'sh / " + gb(total)));
        if (free < total / 10) disk.valueColor = 0xFFFF8A80;

        ListRow.section(col, "Tarmoq");
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ip = wm == null ? 0 : wm.getConnectionInfo().getIpAddress();
        ListRow.add(col, new ListRow(this, "IP manzil", ip == 0 ? "ulanmagan"
                : (ip & 255) + "." + (ip >> 8 & 255) + "." + (ip >> 16 & 255) + "." + (ip >> 24 & 255)));

        ListRow.section(col, "Qo'shimcha");
        ListRow dev = ListRow.add(col, new ListRow(this, "Dasturchi vositalari", "Developer tools"));
        dev.chevron = true;
        dev.onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(AboutActivity.this, DevActivity.class)); }
        });
        ListRow all = ListRow.add(col, new ListRow(this, "Zavod sozlamalari menyusi", "Kengaytirilgan"));
        all.chevron = true;
        all.onClick(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.start(AboutActivity.this, "Sozlamalar", new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.ktc.setting", "com.ktc.setting.view.main.MainActivity")));
            }
        });

        root.addView(Ui.footer(this, "▲ ▼", "ko'rish", "BACK", "orqaga"),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        first.requestFocus();
    }

    /** Vendor build properties (model, version) are only reachable through the hidden reader. */
    private static String prop(String key, String fallback) {
        try {
            String v = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
            return v == null || v.isEmpty() ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String gb(long bytes) {
        double g = bytes / 1073741824.0;
        return g >= 1 ? String.format("%.1f GB", g) : String.format("%d MB", bytes / 1048576);
    }
}
