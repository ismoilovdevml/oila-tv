package uz.oila.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.Toast;

import java.util.TimeZone;

/** Big clock and date, plus the only two time options a family needs. */
public final class DateTimeActivity extends Activity {
    private ListRow auto, h24;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.CLOCK, 0xFFF4B068, 0xFFE57C33, "Sana va vaqt"),
                new LinearLayout.LayoutParams(-1, 72));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0xFFF4B068, 0xFFE57C33 });
        bg.setCornerRadius(22);
        card.setBackground(bg);
        card.addView(clock("HH:mm", 96, Typeface.BOLD));
        card.addView(clock("EEEE, d MMMM yyyy", 28, Typeface.NORMAL));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, 200);
        cl.topMargin = 22;
        cl.leftMargin = 160;
        cl.rightMargin = 160;
        root.addView(card, cl);

        LinearLayout col = ListRow.column(this, root);
        auto = ListRow.add(col, new ListRow(this, "Vaqtni internetdan olish", null).onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { flip(true); }
        }));
        h24 = ListRow.add(col, new ListRow(this, "24 soatlik format", null).onClick(new View.OnClickListener() {
            @Override public void onClick(View v) { flip(false); }
        }));
        TimeZone tz = TimeZone.getDefault();
        int off = tz.getRawOffset() / 60000;
        ListRow.add(col, new ListRow(this, "Vaqt mintaqasi", String.format("%s  GMT%+03d:%02d",
                tz.getID().replace("Asia/", ""), off / 60, Math.abs(off % 60))));

        root.addView(Ui.footer(this, "▲ ▼", "tanlash", "OK", "yoqish / o'chirish", "BACK", "orqaga"),
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
            Toast.makeText(this, "Bu sozlamani o'zgartirishga ruxsat yo'q", Toast.LENGTH_LONG).show();
        }
        refresh();
    }
}
