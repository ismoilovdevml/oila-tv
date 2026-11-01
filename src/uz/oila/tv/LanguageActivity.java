package uz.oila.tv;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Locale;

/** Interface language: three big choices, applied the moment OK is pressed. */
public final class LanguageActivity extends Activity {
    // Each language is listed under its own name, never translated.
    private static final String[][] LANGS = {
            { "uz-UZ", "O'zbekcha" }, { "ru-RU", "Русский" }, { "en-US", "English" } };

    private final ListRow[] rows = new ListRow[LANGS.length];

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.LANGUAGE, 0xFF66A0F2, 0xFF2F63DD, getString(R.string.settings_language)),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);
        ListRow.section(col, getString(R.string.language_choose));
        for (int i = 0; i < LANGS.length; i++) {
            final Locale l = Locale.forLanguageTag(LANGS[i][0]);
            rows[i] = ListRow.add(col, new ListRow(this, LANGS[i][1], null).onClick(new View.OnClickListener() {
                @Override public void onClick(View v) { apply(l); }
            }));
        }
        root.addView(Ui.footer(this, "▲ ▼", getString(R.string.common_select),
                "OK", getString(R.string.common_apply), "BACK", getString(R.string.common_back)),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        String now = getResources().getConfiguration().getLocales().get(0).getLanguage();
        for (int i = 0; i < LANGS.length; i++) {
            boolean on = LANGS[i][0].startsWith(now);
            rows[i].set(on ? getString(R.string.language_selected) : null, ListRow.GREEN);
            if (on) rows[i].requestFocus();
        }
    }

    /**
     * Sets the system locale the way the platform's own language picker does. Needs
     * CHANGE_CONFIGURATION, granted once over adb (tv-tune.sh); without it we say so.
     */
    private void apply(Locale l) {
        try {
            Class<?> picker = Class.forName("com.android.internal.app.LocalePicker");
            picker.getMethod("updateLocales", LocaleList.class).invoke(null, new LocaleList(l));
        } catch (Throwable first) {
            try {
                Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
                Configuration cfg = (Configuration) am.getClass().getMethod("getConfiguration").invoke(am);
                cfg.setLocales(new LocaleList(l));
                Configuration.class.getField("userSetLocale").setBoolean(cfg, true);
                am.getClass().getMethod("updatePersistentConfiguration", Configuration.class).invoke(am, cfg);
            } catch (Throwable second) {
                Toast.makeText(this, R.string.language_no_permission, Toast.LENGTH_LONG).show();
                return;
            }
        }
        refresh();
    }
}
