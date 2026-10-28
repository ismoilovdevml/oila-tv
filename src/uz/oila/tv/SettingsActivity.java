package uz.oila.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;

/** Settings hub: the handful of options a family actually needs, as big tiles. */
public final class SettingsActivity extends Activity {
    private static final String VENDOR = "com.ktc.setting";

    private Tile[] tiles;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.SETTINGS, 0xFF8F98AB, 0xFF596174, "Sozlamalar"),
                new LinearLayout.LayoutParams(-1, 72));

        tiles = new Tile[] {
                new Tile(this, "Wi-Fi", "Tarmoqqa ulanish", Glyphs.WIFI, 0xFF74D0BC, 0xFF41A08C),
                new Tile(this, "Til", "O'zbekcha, Русский, English", Glyphs.LANGUAGE, 0xFF66A0F2, 0xFF2F63DD),
                new Tile(this, "Sana va vaqt", "Soat, 24 soat", Glyphs.CLOCK, 0xFFF4B068, 0xFFE57C33),
                new Tile(this, "Televizor haqida", "Model, xotira, IP", Glyphs.INFO, 0xFFBD7CEA, 0xFF8538D2),
                new Tile(this, "Barcha sozlamalar", "Kengaytirilgan", Glyphs.SETTINGS, 0xFF8F98AB, 0xFF596174) };
        final Class<?>[] screens = { WifiActivity.class, LanguageActivity.class, DateTimeActivity.class,
                AboutActivity.class, null };
        for (int i = 0; i < tiles.length; i++) {
            final int index = i;
            tiles[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (screens[index] != null) {
                        startActivity(new Intent(SettingsActivity.this, screens[index]));
                    } else {
                        Ui.start(SettingsActivity.this, tiles[index].title, vendor(".view.main.MainActivity"),
                                new Intent(Settings.ACTION_SETTINGS));
                    }
                }
            });
        }
        root.addView(Ui.tileRow(this, tiles[0], tiles[1], tiles[2]), Ui.weighted(22));
        root.addView(Ui.tileRow(this, tiles[3], tiles[4]), Ui.weighted(22));

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "◀ ▲ ▼ ▶", "yurish", "OK", "tanlash", "BACK", "orqaga"), footLp);
        setContentView(root);
        tiles[0].requestFocus();
    }

    private static Intent vendor(String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(VENDOR, VENDOR + cls));
    }
}
