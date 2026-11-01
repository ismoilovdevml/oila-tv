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
        root.addView(Ui.header(this, Glyphs.INFO, 0xFFBD7CEA, 0xFF8538D2, getString(R.string.about_title)),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);

        ListRow.section(col, getString(R.string.about_device));
        ListRow first = ListRow.add(col, new ListRow(this, getString(R.string.about_model), prop("ktc.board.model", Build.MODEL)));
        ListRow.add(col, new ListRow(this, "Android", Build.VERSION.RELEASE));
        ListRow.add(col, new ListRow(this, getString(R.string.about_firmware), prop("ro.product.version", Build.DISPLAY)));
        ListRow.add(col, new ListRow(this, getString(R.string.about_updated),
                new java.text.SimpleDateFormat("dd.MM.yyyy").format(new java.util.Date(Build.TIME))));

        ListRow.section(col, getString(R.string.about_memory));
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        ListRow.add(col, new ListRow(this, getString(R.string.about_ram),
                getString(R.string.about_free_of, gb(mi.availMem), gb(mi.totalMem))));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long free = fs.getAvailableBytes(), total = fs.getTotalBytes();
        ListRow disk = ListRow.add(col, new ListRow(this, getString(R.string.about_storage),
                getString(R.string.about_free_of, gb(free), gb(total))));
        if (free < total / 10) disk.valueColor = 0xFFFF8A80;

        ListRow.section(col, getString(R.string.about_network));
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ip = wm == null ? 0 : wm.getConnectionInfo().getIpAddress();
        ListRow.add(col, new ListRow(this, getString(R.string.about_ip),
                ip == 0 ? getString(R.string.about_not_connected)
                : (ip & 255) + "." + (ip >> 8 & 255) + "." + (ip >> 16 & 255) + "." + (ip >> 24 & 255)));

        ListRow.section(col, getString(R.string.about_more));
        ListRow dev = ListRow.add(col, new ListRow(this, getString(R.string.about_dev_tools),
                getString(R.string.about_dev_tools_hint)));
        dev.chevron = true;
        dev.onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(AboutActivity.this, DevActivity.class)); }
        });
        ListRow all = ListRow.add(col, new ListRow(this, getString(R.string.about_factory_menu),
                getString(R.string.about_advanced)));
        all.chevron = true;
        all.onClick(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.start(AboutActivity.this, getString(R.string.about_settings), new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName("com.ktc.setting", "com.ktc.setting.view.main.MainActivity")));
            }
        });

        root.addView(Ui.footer(this, "▲ ▼", getString(R.string.common_scroll), "BACK",
                getString(R.string.common_back)),
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
