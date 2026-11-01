package uz.oila.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** Developer tools: what the vendor debug panel shows, minus the risky tools. */
public final class DevActivity extends Activity {
    private ListRow adb;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.SLIDERS, 0xFF8F98AB, 0xFF596174, getString(R.string.dev_title)),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);

        ListRow.section(col, getString(R.string.dev_software));
        ListRow first = ListRow.add(col, new ListRow(this, "Android", Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")"));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_firmware), prop("ro.product.version", "?") + "  ·  " + Build.TYPE));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_build_date), new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(Build.TIME))));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_security_patch), Build.VERSION.SECURITY_PATCH));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_kernel), System.getProperty("os.version", "?")));

        ListRow.section(col, getString(R.string.dev_hardware));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_board), prop("ktc.board.model", Build.BOARD)));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_chip),
                prop("ro.board.platform", Build.HARDWARE).toUpperCase(Locale.ROOT)));
        ListRow.add(col, new ListRow(this, "CPU", getString(R.string.dev_cpu_value,
                Runtime.getRuntime().availableProcessors(), Build.SUPPORTED_ABIS[0])));
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        ListRow.add(col, new ListRow(this, getString(R.string.dev_ram),
                getString(R.string.dev_free_of, mb(mi.availMem), mb(mi.totalMem))));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        ListRow.add(col, new ListRow(this, getString(R.string.dev_storage),
                getString(R.string.dev_free_of, mb(fs.getAvailableBytes()), mb(fs.getTotalBytes()))));

        ListRow.section(col, getString(R.string.dev_screen));
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        ListRow.add(col, new ListRow(this, getString(R.string.dev_resolution), dm.widthPixels + " × " + dm.heightPixels));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_density), dm.densityDpi + " dpi"));

        ListRow.section(col, getString(R.string.dev_network));
        network(col);

        ListRow.section(col, getString(R.string.dev_debugging));
        adb = ListRow.add(col, new ListRow(this, getString(R.string.dev_adb), null).onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleAdb(); }
        }));
        refreshAdb();

        ListRow.section(col, getString(R.string.dev_tools));
        ListRow props = ListRow.add(col, new ListRow(this, getString(R.string.dev_build_props), "build.prop"));
        props.chevron = true;
        props.onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(DevActivity.this, PropsActivity.class)); }
        });

        root.addView(Ui.footer(this, "▲ ▼", getString(R.string.common_scroll), "OK",
                getString(R.string.common_select), "BACK", getString(R.string.common_back)),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        first.requestFocus();
    }

    private void network(LinearLayout col) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network n = cm == null ? null : cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) {
            ListRow.add(col, new ListRow(this, getString(R.string.dev_status), getString(R.string.dev_not_connected)));
            return;
        }
        ListRow.add(col, new ListRow(this, getString(R.string.dev_interface), lp.getInterfaceName()));
        for (LinkAddress a : lp.getLinkAddresses()) {
            if (a.getAddress().getAddress().length == 4) ListRow.add(col, new ListRow(this, getString(R.string.dev_ip), a.toString()));
        }
        for (RouteInfo r : lp.getRoutes()) {
            if (r.isDefaultRoute() && r.getGateway() != null) {
                ListRow.add(col, new ListRow(this, getString(R.string.dev_gateway), r.getGateway().getHostAddress()));
                break;
            }
        }
        StringBuilder dns = new StringBuilder();
        for (InetAddress d : lp.getDnsServers()) dns.append(dns.length() == 0 ? "" : ", ").append(d.getHostAddress());
        ListRow.add(col, new ListRow(this, "DNS", dns.toString()));
        ListRow.add(col, new ListRow(this, getString(R.string.dev_mac), mac(lp.getInterfaceName())));
    }

    private void refreshAdb() {
        adb.toggle = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        adb.invalidate();
    }

    /** Turning debugging off also cuts the network adb this TV is maintained over, so ask first. */
    private void toggleAdb() {
        if (!adb.toggle) {
            setAdb(1);
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.dev_adb_off_title)
                .setMessage(R.string.dev_adb_off_message)
                .setPositiveButton(R.string.dev_turn_off, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { setAdb(0); }
                })
                .setNegativeButton(R.string.dev_cancel, null)
                .show();
    }

    private void setAdb(int on) {
        try {
            Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, on);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.dev_no_permission, Toast.LENGTH_LONG).show();
        }
        refreshAdb();
    }

    static String prop(String key, String fallback) {
        try {
            String v = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
            return v == null || v.isEmpty() ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String mac(String iface) {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.getName().equals(iface) || ni.getHardwareAddress() == null) continue;
                StringBuilder sb = new StringBuilder();
                for (byte b : ni.getHardwareAddress()) sb.append(sb.length() == 0 ? "" : ":").append(String.format("%02X", b));
                return sb.toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "?";
    }

    private static String mb(long bytes) {
        return bytes >= 1073741824L ? String.format("%.1f GB", bytes / 1073741824.0) : (bytes / 1048576) + " MB";
    }
}
