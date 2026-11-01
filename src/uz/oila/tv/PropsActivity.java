package uz.oila.tv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.FileReader;

/** Read-only list of the build properties from /system/build.prop and /vendor/build.prop. */
public final class PropsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.SLIDERS, 0xFF8F98AB, 0xFF596174, getString(R.string.props_title)),
                new LinearLayout.LayoutParams(-1, 72));
        LinearLayout col = ListRow.column(this, root);
        ListRow first = null;
        for (String file : new String[] { "/system/build.prop", "/vendor/build.prop" }) {
            ListRow.section(col, file);
            try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                for (String l; (l = r.readLine()) != null; ) {
                    int eq = l.indexOf('=');
                    if (l.startsWith("#") || eq <= 0) continue;
                    ListRow row = ListRow.add(col, new ListRow(this, l.substring(0, eq), l.substring(eq + 1)));
                    row.setClickable(true);
                    if (first == null) first = row;
                }
            } catch (Exception e) {
                ListRow.add(col, new ListRow(this, getString(R.string.props_not_readable),
                        e.getClass().getSimpleName()));
            }
        }
        root.addView(Ui.footer(this, "▲ ▼", getString(R.string.common_scroll), "BACK",
                getString(R.string.common_back)), new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        if (first != null) first.requestFocus();
    }
}
