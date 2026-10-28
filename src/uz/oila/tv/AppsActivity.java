package uz.oila.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every launchable app, four per row, with its own icon. Factory/debug tools are left out. */
public final class AppsActivity extends Activity {
    private static final int COLUMNS = 4;
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "uz.oila.tv", "com.ktc.assistant", "com.ktc.serialport", "com.ktc.mslauncher",
            "mediatek.factorymenu.ui", "ktc.factorymenu.ui", "com.ktc.oobe",
            "com.airoha.android.bluetoothlegatt", "android.systemupdate.service"));
    private static final int[][] PALETTE = {
            { 0xFF3A4A6B, 0xFF252F45 }, { 0xFF3B5E5A, 0xFF243B39 },
            { 0xFF4E3F6B, 0xFF2F2645 }, { 0xFF6B4A3A, 0xFF452F25 } };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.APPS, 0xFF5CB8E8, 0xFF2D7FC0, "Ilovalar"),
                new LinearLayout.LayoutParams(-1, 72));

        ScrollView scroll = new ScrollView(this);
        // A ScrollView clips its content only when it has some padding (FLAG_PADDING_NOT_NULL);
        // keep a sliver top/bottom so rows never slide over the footer.
        scroll.setPadding(0, 2, 0, 2);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(18, 14, 18, 14);
        grid.setClipToPadding(false);
        scroll.addView(grid);

        List<ResolveInfo> apps = launchables();
        AppTile firstTile = null;
        for (int i = 0; i < apps.size(); i += COLUMNS) {
            List<Tile> row = new ArrayList<>();
            for (int j = i; j < Math.min(i + COLUMNS, apps.size()); j++) {
                AppTile t = new AppTile(this, apps.get(j), PALETTE[j % PALETTE.length]);
                if (firstTile == null) firstTile = t;
                row.add(t);
            }
            while (row.size() < COLUMNS) row.add(spacer());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 150);
            lp.bottomMargin = 22;
            grid.addView(Ui.tileRow(this, row.toArray(new Tile[0])), lp);
        }
        root.addView(scroll, Ui.weighted(18));

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 10;
        root.addView(Ui.footer(this, "◀ ▲ ▼ ▶", "yurish", "OK", "ochish", "BACK", "orqaga"), footLp);
        setContentView(root);
        if (firstTile != null) firstTile.requestFocus();
    }

    private Tile spacer() {
        Tile t = new Tile(this, "", "", Glyphs.NONE, 0, 0);
        t.setFocusable(false);
        t.setVisibility(View.INVISIBLE);
        return t;
    }

    /** TV (leanback) entries first, then phone-style launchers, one per package, by name. */
    private List<ResolveInfo> launchables() {
        final PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (String category : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            for (ResolveInfo ri : pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!HIDDEN.contains(pkg) && !byPkg.containsKey(pkg)) byPkg.put(pkg, ri);
            }
        }
        List<ResolveInfo> out = new ArrayList<>(byPkg.values());
        Collections.sort(out, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        return out;
    }

    /** Tile showing an app's icon and name; OK launches it. */
    private static final class AppTile extends Tile {
        private final Drawable icon;

        AppTile(final Activity a, final ResolveInfo ri, int[] colors) {
            super(a, ri.loadLabel(a.getPackageManager()).toString(), "", Glyphs.NONE,
                    colors[0], colors[1], 26f);
            icon = ri.loadIcon(a.getPackageManager());
            setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    PackageManager pm = a.getPackageManager();
                    String pkg = ri.activityInfo.packageName;
                    Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                    Ui.start(a, title, i != null ? i : pm.getLaunchIntentForPackage(pkg));
                }
            });
        }

        @Override
        void drawContent(Canvas c, float w, float h) {
            int s = 64;
            icon.setBounds(22, 20, 22 + s, 20 + s);
            icon.draw(c);
            c.drawText(fit(title, titlePaint, w - 44), 22f, h - 24f, titlePaint);
        }
    }
}
