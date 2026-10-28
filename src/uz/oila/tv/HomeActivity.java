package uz.oila.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

public final class HomeActivity extends Activity {
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String TV_PLAYER = "com.ktc.tvplayer";
    private static final String TV_PLAYER_ROOT = "com.ktc.tvplayer.activity.RootActivity";
    private static final String FALLBACK_TUNER = "com.mediatek.tvinput/.tuner.TunerInputService/HW0";

    private Tile[] tiles;
    private Ui.GlyphView wifi;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        tiles[0].requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        tiles[0].requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        wifi.setAlpha(ni != null && ni.isConnected() ? 1f : 0.25f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back has nowhere to go.
    }

    private void open(int index) {
        PackageManager pm = getPackageManager();
        String what = tiles[index].title;
        switch (index) {
            case 0:
                Ui.start(this, what,
                        explicit(YOUTUBE, "com.google.android.apps.youtube.tv.activity.ShellActivity"),
                        launchIntent(pm, YOUTUBE));
                break;
            case 1:
                SourceActivity.watch(this, SourceActivity.TV, tunerInputId());
                break;
            case 2:
                startActivity(new Intent(this, SourceActivity.class));
                break;
            case 3:
                Ui.start(this, what, launchIntent(pm, "com.netflix.mediaclient"));
                break;
            case 4:
                startActivity(new Intent(this, WifiActivity.class));
                break;
            case 5:
                startActivity(new Intent(this, MediaActivity.class));
                break;
            case 6:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            default:
                startActivity(new Intent(this, AppsActivity.class));
        }
    }

    /** The first hardware tuner input (antenna/cable), so "Live TV" never lands on HDMI. */
    private String tunerInputId() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_TUNER) return info.getId();
            }
        }
        return FALLBACK_TUNER;
    }

    private static Intent explicit(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls));
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // ---- UI (built in code: no XML inflation, no image assets) ----

    private View buildUi() {
        LinearLayout root = Ui.page(this);

        LinearLayout header = Ui.header(this, Glyphs.HOME, 0xFFF9C25A, 0xFFEA8A3B, "Oila TV");
        wifi = new Ui.GlyphView(this, Glyphs.WIFI, Ui.TEXT_SOFT, 0);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(44, 44);
        wl.leftMargin = 20;
        header.addView(wifi, wl);
        root.addView(header, new LinearLayout.LayoutParams(-1, 72));

        tiles = new Tile[] {
                new Tile(this, "YouTube", "Video va mult", Glyphs.YOUTUBE, 0xFFEE6B5F, 0xFFCF3429),
                new Tile(this, "Jonli TV", "Efir kanallari", Glyphs.LIVE_TV, 0xFF66A0F2, 0xFF2F63DD),
                new Tile(this, "Qurilma", "HDMI / Pristavka", Glyphs.SOURCE, 0xFF968AF3, 0xFF5548DD),
                new Tile(this, "Netflix", "Kino va serial", Glyphs.NETFLIX, 0xFFC4393C, 0xFF8A1A1E),
                new Tile(this, "Wi-Fi", "Tarmoqqa ulanish", Glyphs.WIFI, 0xFF74D0BC, 0xFF41A08C),
                new Tile(this, "Galereya", "USB: video, rasm", Glyphs.GALLERY, 0xFFBD7CEA, 0xFF8538D2),
                new Tile(this, "Sozlamalar", "Til, vaqt, ekran", Glyphs.SETTINGS, 0xFF8F98AB, 0xFF596174),
                new Tile(this, "Ilovalar", "Barcha ilovalar", Glyphs.APPS, 0xFF5CB8E8, 0xFF2D7FC0) };
        for (int i = 0; i < tiles.length; i++) {
            final int index = i;
            tiles[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(index); }
            });
        }

        root.addView(Ui.tileRow(this, tiles[0], tiles[1], tiles[2], tiles[3]), Ui.weighted(22));
        root.addView(Ui.tileRow(this, tiles[4], tiles[5], tiles[6], tiles[7]), Ui.weighted(22));

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "◀ ▲ ▼ ▶", "yurish", "OK", "tanlash"), footLp);
        return root;
    }
}
