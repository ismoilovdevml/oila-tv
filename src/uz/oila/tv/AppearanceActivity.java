package uz.oila.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/** Picks the home-screen wallpaper (ten palettes) for the WebView home; applied on the next resume. */
public final class AppearanceActivity extends Activity {
    // id (asset ?wp=) and display name
    private static final String[][] WALLPAPERS = {
            { "kol", "Ko'l sohili" }, { "tong", "Tong" }, { "tun", "Tun" }, { "ormon", "O'rmon" },
            { "issiq", "Issiq" }, { "shafaq", "Shafaq" }, { "dengiz", "Dengiz" }, { "qumtepa", "Qumtepa" },
            { "lavanda", "Lavanda" }, { "tungikol", "Tungi ko'l" } };

    private final ListRow[] rows = new ListRow[WALLPAPERS.length];

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.SLIDERS, 0xFF74D0BC, 0xFF41A08C, "Ko'rinish"),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);
        ListRow.section(col, "Fon rasmi");

        final SharedPreferences p = getSharedPreferences("home", MODE_PRIVATE);
        final String current = p.getString("wp", "kol");
        for (int i = 0; i < WALLPAPERS.length; i++) {
            final String id = WALLPAPERS[i][0];
            rows[i] = ListRow.add(col, new ListRow(this, WALLPAPERS[i][1], null).onClick(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    p.edit().putString("wp", id).apply();
                    mark(id);
                }
            }));
        }
        root.addView(Ui.footer(this, "▲ ▼", "tanlash", "OK", "qo'llash", "BACK", "orqaga"),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        mark(current);
    }

    private void mark(String id) {
        for (int i = 0; i < WALLPAPERS.length; i++) {
            boolean on = WALLPAPERS[i][0].equals(id);
            rows[i].set(on ? "Tanlangan" : null, ListRow.GREEN);
            if (on) rows[i].requestFocus();
        }
    }
}
