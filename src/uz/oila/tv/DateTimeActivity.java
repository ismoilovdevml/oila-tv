package uz.oila.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.Toast;

import java.util.Locale;
import java.util.TimeZone;

/** Big clock and date, plus the only two time options a family needs. */
public final class DateTimeActivity extends Activity {
    private ListRow auto, h24;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.CLOCK, 0xFFF4B068, 0xFFE57C33, getString(R.string.settings_datetime)),
                new LinearLayout.LayoutParams(-1, 72));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0xFFF4B068, 0xFFE57C33 });
        bg.setCornerRadius(22);
        card.setBackground(bg);
        card.addView(clock("HH:mm", 96, Typeface.BOLD));
        // Weekday, day, month and year in the order and words of the current language.
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        card.addView(clock(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), 28, Typeface.NORMAL));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, 200);
        cl.topMargin = 22;
        cl.leftMargin = 160;
        cl.rightMargin = 160;
        root.addView(card, cl);

        LinearLayout col = ListRow.column(this, root);
        auto = ListRow.add(col, new ListRow(this, getString(R.string.datetime_auto), null).onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { flip(true); }
        }));
        h24 = ListRow.add(col, new ListRow(this, getString(R.string.datetime_24h), null).onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { flip(false); }
        }));
        TimeZone tz = TimeZone.getDefault();
        int off = tz.getRawOffset() / 60000;
        // Asia/Samarkand and Asia/Tashkent are the same clock; families know it as Uzbekistan time.
        String zone = tz.getID().matches("Asia/(Samarkand|Tashkent)")
                ? getString(R.string.datetime_uzbekistan) : tz.getID().replace("Asia/", "");
        ListRow.add(col, new ListRow(this, getString(R.string.datetime_zone),
                String.format("%s  GMT%+03d:%02d", zone, off / 60, Math.abs(off % 60))));

        root.addView(Ui.footer(this, "▲ ▼", getString(R.string.common_select),
                "OK", getString(R.string.common_toggle), "BACK", getString(R.string.common_back)),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        refresh();
        auto.requestFocus();
    }

    private TextClock clock(String fmt, int px, int style) {
        TextClock t = new TextClock(this);
        t.setFormat24Hour(fmt);
        t.setFormat12Hour(fmt);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private void refresh() {
        auto.toggle = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
        h24.toggle = !"12".equals(Settings.System.getString(getContentResolver(), Settings.System.TIME_12_24));
        auto.invalidate();
        h24.invalidate();
    }

    /** Needs WRITE_SECURE_SETTINGS / WRITE_SETTINGS, granted once over adb (tv-tune.sh). */
    private void flip(boolean autoTime) {
        try {
            if (autoTime) {
                Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME, auto.toggle ? 0 : 1);
            } else {
                Settings.System.putString(getContentResolver(), Settings.System.TIME_12_24, h24.toggle ? "12" : "24");
            }
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.datetime_no_permission, Toast.LENGTH_LONG).show();
        }
        refresh();
    }
}
