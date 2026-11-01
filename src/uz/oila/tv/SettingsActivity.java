package uz.oila.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/** Settings hub: the handful of options a family actually needs, as big tiles. */
public final class SettingsActivity extends Activity {
    private Tile[] tiles;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.SETTINGS, 0xFF8F98AB, 0xFF596174, getString(R.string.home_settings)),
                new LinearLayout.LayoutParams(-1, 72));

        tiles = new Tile[] {
                new Tile(this, "Wi-Fi", getString(R.string.home_wifi_sub), Glyphs.WIFI, 0xFF74D0BC, 0xFF41A08C),
                // Language names stay in their own script whatever the current language is.
                new Tile(this, getString(R.string.settings_language), "O'zbekcha, Русский, English",
                        Glyphs.LANGUAGE, 0xFF66A0F2, 0xFF2F63DD),
                new Tile(this, getString(R.string.settings_datetime), getString(R.string.settings_datetime_sub),
                        Glyphs.CLOCK, 0xFFF4B068, 0xFFE57C33),
                new Tile(this, getString(R.string.settings_boost), getString(R.string.settings_boost_sub),
                        Glyphs.BOOST, 0xFF6FE3A5, 0xFF2FA86B),
                new Tile(this, getString(R.string.settings_about), getString(R.string.settings_about_sub),
                        Glyphs.INFO, 0xFFBD7CEA, 0xFF8538D2),
                new Tile(this, getString(R.string.settings_phone), getString(R.string.settings_phone_sub),
                        Glyphs.PHONE, 0xFFF27B9B, 0xFFD23A68) };
        final Class<?>[] screens = { WifiActivity.class, LanguageActivity.class, DateTimeActivity.class,
                BoostActivity.class, AboutActivity.class, PhoneActivity.class };
        for (int i = 0; i < tiles.length; i++) {
            final int index = i;
            tiles[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, screens[index]));
                }
            });
        }
        // The vendor menu (factory reset etc.) stays reachable only from About, away from daily use.
        root.addView(Ui.tileRow(this, tiles[0], tiles[1], tiles[2]), Ui.weighted(22));
        root.addView(Ui.tileRow(this, tiles[3], tiles[4], tiles[5]), Ui.weighted(22));

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "◀ ▲ ▼ ▶", getString(R.string.common_move),
                "OK", getString(R.string.common_select), "BACK", getString(R.string.common_back)), footLp);
        setContentView(root);
        tiles[0].requestFocus();
    }
}
