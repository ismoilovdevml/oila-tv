package uz.oila.tv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Wi-Fi screen: status card + toggle on the left, nearby networks on the right. */
public final class WifiActivity extends Activity {
    private static final int GREEN = 0xFF6FE3A5;

    private WifiManager wm;
    private TextView state, name, detail;
    private Tile toggle;
    private LinearLayout list;
    private String listSignature = "";
    private boolean listFocused;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        setContentView(buildUi());
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Android 9 hides scan results from apps without location access.
            requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(receiver, f);
        if (wm.isWifiEnabled()) wm.startScan();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (wm.isWifiEnabled()) wm.startScan();
        refresh();
    }

    // ---- state ----

    private void refresh() {
        boolean on = wm.isWifiEnabled();
        WifiInfo info = on ? wm.getConnectionInfo() : null;
        String current = info != null && info.getNetworkId() != -1 ? unquote(info.getSSID()) : null;

        toggle.title = on ? "Wi-Fi yoniq" : "Wi-Fi o'chiq";
        toggle.subtitle = on ? "O'chirish uchun OK" : "Yoqish uchun OK";
        toggle.invalidate();

        if (!on) {
            state.setText("O'chiq");
            name.setText("Wi-Fi o'chirilgan");
            detail.setText("Internet uchun Wi-Fi'ni yoqing");
        } else if (current != null) {
            int ip = info.getIpAddress();
            state.setText("Ulangan");
            name.setText(current);
            detail.setText("Signal: " + signalWord(WifiManager.calculateSignalLevel(info.getRssi(), 5))
                    + (ip == 0 ? "" : "\nIP: " + (ip & 255) + "." + (ip >> 8 & 255) + "."
                            + (ip >> 16 & 255) + "." + (ip >> 24 & 255)));
        } else {
            state.setText("Ulanmagan");
            name.setText("Tarmoqni tanlang");
            detail.setText("O'ngdagi ro'yxatdan tarmoqni tanlab, OK bosing");
        }
        state.setTextColor(on && current != null ? GREEN : Ui.TEXT_DIM);
        rebuildList(on, current);
    }

    private void rebuildList(boolean on, String current) {
        List<ScanResult> nets = on ? strongestPerSsid(current) : new ArrayList<ScanResult>();
        StringBuilder sig = new StringBuilder(String.valueOf(current));
        for (ScanResult r : nets) sig.append('|').append(r.SSID).append(WifiManager.calculateSignalLevel(r.level, 5));
        if (sig.toString().equals(listSignature)) return;
        listSignature = sig.toString();

        View focused = getCurrentFocus();
        String focusedSsid = focused instanceof NetRow ? ((NetRow) focused).ssid : null;

        list.removeAllViews();
        if (!on) {
            list.addView(hintRow("Wi-Fi o'chiq — chapdagi tugma bilan yoqing"));
            return;
        }
        if (nets.isEmpty()) {
            list.addView(hintRow("Tarmoqlar qidirilmoqda…"));
            return;
        }
        for (final ScanResult r : nets) {
            boolean connected = r.SSID.equals(current);
            boolean secured = isSecured(r);
            NetRow row = new NetRow(this, r.SSID, WifiManager.calculateSignalLevel(r.level, 5), secured,
                    connected ? "Ulangan" : secured ? "Parol bilan" : "Ochiq",
                    connected ? GREEN : Ui.TEXT_DIM);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { choose(r); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 74);
            lp.bottomMargin = 12;
            list.addView(row, lp);
            if (r.SSID.equals(focusedSsid)) row.requestFocus();
        }
        // Start on the list, not the toggle: one stray OK must not switch Wi-Fi off.
        if (!listFocused) {
            listFocused = true;
            list.getChildAt(0).requestFocus();
        }
    }

    /** Scan results deduplicated by SSID, connected first, then strongest first. */
    private List<ScanResult> strongestPerSsid(final String current) {
        Map<String, ScanResult> best = new HashMap<>();
        List<ScanResult> results = wm.getScanResults();
        if (results != null) {
            for (ScanResult r : results) {
                if (r.SSID == null || r.SSID.isEmpty()) continue;
                ScanResult prev = best.get(r.SSID);
                if (prev == null || r.level > prev.level) best.put(r.SSID, r);
            }
        }
        List<ScanResult> out = new ArrayList<>(best.values());
        Collections.sort(out, new Comparator<ScanResult>() {
            @Override public int compare(ScanResult a, ScanResult b) {
                if (a.SSID.equals(current)) return -1;
                if (b.SSID.equals(current)) return 1;
                return b.level - a.level;
            }
        });
        return out;
    }

    // ---- actions ----

    private void choose(ScanResult r) {
        WifiInfo info = wm.getConnectionInfo();
        if (info != null && r.SSID.equals(unquote(info.getSSID())) && info.getNetworkId() != -1) {
            Toast.makeText(this, r.SSID + " — allaqachon ulangan", Toast.LENGTH_SHORT).show();
            return;
        }
        WifiConfiguration saved = savedConfig(r.SSID);
        if (!isSecured(r)) {
            connect(saved != null ? saved.networkId : wm.addNetwork(config(r, null)), r.SSID);
        } else if (isEnterprise(r)) {
            Toast.makeText(this, "Bu tarmoq turi qo'llab-quvvatlanmaydi", Toast.LENGTH_LONG).show();
        } else {
            askPassword(r, saved);
        }
    }

    private void askPassword(final ScanResult r, final WifiConfiguration saved) {
        final EditText pw = new EditText(this);
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pw.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32);
        pw.setSingleLine(true);
        pw.setHint(saved != null ? "Saqlangan parol uchun bo'sh qoldiring" : "Parol");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(40, 16, 40, 0);
        box.addView(pw, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(r.SSID)
                .setView(box)
                .setPositiveButton("Ulanish", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) {
                        String p = pw.getText().toString();
                        if (p.isEmpty() && saved != null) {
                            connect(saved.networkId, r.SSID);
                        } else if (p.length() < 8 && !r.capabilities.contains("WEP")) {
                            Toast.makeText(WifiActivity.this, "Parol kamida 8 belgi bo'lishi kerak",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            connect(wm.addNetwork(config(r, p)), r.SSID);
                        }
                    }
                })
                .setNegativeButton("Bekor qilish", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        d.show();
        pw.requestFocus();
    }

    private void connect(int netId, String ssid) {
        if (netId == -1) {
            Toast.makeText(this, ssid + ": saqlab bo'lmadi. \"Barcha sozlamalar\" orqali urinib ko'ring",
                    Toast.LENGTH_LONG).show();
            return;
        }
        wm.disconnect();
        wm.enableNetwork(netId, true);
        wm.reconnect();
        Toast.makeText(this, ssid + " — ulanmoqda…", Toast.LENGTH_SHORT).show();
    }

    private WifiConfiguration savedConfig(String ssid) {
        List<WifiConfiguration> all = wm.getConfiguredNetworks();
        if (all == null) return null;
        for (WifiConfiguration c : all) {
            if (ssid.equals(unquote(c.SSID))) return c;
        }
        return null;
    }

    private static WifiConfiguration config(ScanResult r, String password) {
        WifiConfiguration c = new WifiConfiguration();
        c.SSID = "\"" + r.SSID + "\"";
        if (password == null) {
            c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else if (r.capabilities.contains("WEP")) {
            c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            c.wepKeys[0] = "\"" + password + "\"";
            c.wepTxKeyIndex = 0;
        } else {
            c.preSharedKey = "\"" + password + "\"";
        }
        return c;
    }

    private static boolean isSecured(ScanResult r) {
        String c = r.capabilities;
        return c.contains("WEP") || c.contains("PSK") || c.contains("SAE") || c.contains("EAP");
    }

    private static boolean isEnterprise(ScanResult r) {
        return r.capabilities.contains("EAP");
    }

    private static String unquote(String s) {
        if (s == null) return "";
        return s.length() > 1 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    private static String signalWord(int level) {
        return level >= 4 ? "a'lo" : level == 3 ? "yaxshi" : level == 2 ? "o'rtacha" : "zaif";
    }

    // ---- UI ----

    private View buildUi() {
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.WIFI, 0xFF74D0BC, 0xFF41A08C, "Wi-Fi"),
                new LinearLayout.LayoutParams(-1, 72));

        LinearLayout body = new LinearLayout(this);
        body.setClipChildren(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 30, 30, 30);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xFF1C2232);
        cbg.setCornerRadius(22);
        card.setBackground(cbg);

        Ui.GlyphView big = new Ui.GlyphView(this, Glyphs.WIFI, 0xFF74D0BC, 0);
        card.addView(big, new LinearLayout.LayoutParams(76, 76));
        state = Ui.label(this, "", 24, Ui.TEXT_DIM, Typeface.BOLD);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = 22;
        card.addView(state, sl);
        name = Ui.label(this, "", 36, Ui.TEXT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = 10;
        card.addView(name, nl);
        detail = Ui.label(this, "", 21, Ui.TEXT_SOFT, Typeface.NORMAL);
        detail.setLineSpacing(8, 1);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, 0, 1f);
        dl.topMargin = 12;
        card.addView(detail, dl);

        toggle = new Tile(this, "Wi-Fi", "", Glyphs.NONE, 0xFF74D0BC, 0xFF41A08C, 28f);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                wm.setWifiEnabled(!wm.isWifiEnabled());
                listSignature = "";
            }
        });
        card.addView(toggle, new LinearLayout.LayoutParams(-1, 92));

        body.addView(card, new LinearLayout.LayoutParams(400, -1));

        // The scroller must clip, or rows slide over the footer; padding leaves room for the focus ring.
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(10, 10, 10, 10);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        scroll.addView(list, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scl = new LinearLayout.LayoutParams(0, -1, 1f);
        scl.leftMargin = 20;
        body.addView(scroll, scl);

        root.addView(body, Ui.weighted(22));

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "▲ ▼", "tarmoq", "OK", "ulanish", "BACK", "orqaga"), footLp);
        return root;
    }

    private TextView hintRow(String s) {
        TextView t = Ui.label(this, s, 24, Ui.TEXT_DIM, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 60, 0, 0);
        return t;
    }
}
