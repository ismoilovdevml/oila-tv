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
                new Tile(this, "Til", "Interfeys tili", Glyphs.LANGUAGE, 0xFF66A0F2, 0xFF2F63DD),
                new Tile(this, "Sana va vaqt", "Soat, vaqt mintaqasi", Glyphs.CLOCK, 0xFFF4B068, 0xFFE57C33),
                new Tile(this, "Umumiy", "Asosiy parametrlar", Glyphs.SLIDERS, 0xFF968AF3, 0xFF5548DD),
                new Tile(this, "Bluetooth", "Pult va qurilmalar", Glyphs.BLUETOOTH, 0xFF5CB8E8, 0xFF2D7FC0),
                new Tile(this, "Televizor haqida", "Model, versiya", Glyphs.INFO, 0xFFBD7CEA, 0xFF8538D2),
                new Tile(this, "Barcha sozlamalar", "Kengaytirilgan", Glyphs.SETTINGS, 0xFF8F98AB, 0xFF596174) };
        for (int i = 0; i < tiles.length; i++) {
            final int index = i;
            tiles[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { open(index); }
            });
        }
        root.addView(Ui.tileRow(this, tiles[0], tiles[1], tiles[2], tiles[3]), Ui.weighted(22));
        root.addView(Ui.tileRow(this, tiles[4], tiles[5], tiles[6]), Ui.weighted(22));

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "◀ ▲ ▼ ▶", "yurish", "OK", "tanlash", "BACK", "orqaga"), footLp);
        setContentView(root);
        tiles[0].requestFocus();
    }

    private void open(int index) {
        String what = tiles[index].title;
        switch (index) {
            case 0:
                startActivity(new Intent(this, WifiActivity.class));
                break;
            case 1:
                Ui.start(this, what, new Intent(Settings.ACTION_LOCALE_SETTINGS));
                break;
            case 2:
                Ui.start(this, what, new Intent(Settings.ACTION_DATE_SETTINGS));
                break;
            case 3:
                Ui.start(this, what, vendor(".view.universal.UniversalActivity"),
                        new Intent(Settings.ACTION_SETTINGS));
                break;
            case 4:
                Ui.start(this, what, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                break;
            case 5:
                Ui.start(this, what, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
                break;
            default:
                Ui.start(this, what, vendor(".view.main.MainActivity"),
                        new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private static Intent vendor(String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(VENDOR, VENDOR + cls));
    }
}
