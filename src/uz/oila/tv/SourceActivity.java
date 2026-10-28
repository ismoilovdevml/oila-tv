package uz.oila.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Input picker: antenna TV, AV and the HDMI ports as big tiles. */
public final class SourceActivity extends Activity {
    static final String TV_PLAYER = "com.ktc.tvplayer";
    static final String TV_PLAYER_ROOT = "com.ktc.tvplayer.activity.RootActivity";
    private static final int HDMI_PORTS_SHOWN = 2;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.SOURCE, 0xFF968AF3, 0xFF5548DD, "Qurilma"),
                new LinearLayout.LayoutParams(-1, 72));

        List<Tile> tiles = new ArrayList<>();
        TvInputInfo tuner = first(TvInputInfo.TYPE_TUNER);
        TvInputInfo av = first(TvInputInfo.TYPE_COMPOSITE);
        if (tuner != null) tiles.add(tile("Antenna TV", "Efir kanallari", Glyphs.LIVE_TV,
                0xFF66A0F2, 0xFF2F63DD, tuner.getId()));
        if (av != null) tiles.add(tile("AV", "Sariq-oq-qizil kabel", Glyphs.SOURCE,
                0xFFF4B068, 0xFFE57C33, av.getId()));
        // The input list comes back in no useful order; the trailing hardware number follows the
        // physical port order (HW6 = HDMI 1), so sort on it.
        List<TvInputInfo> ports = inputs();
        Collections.sort(ports, new Comparator<TvInputInfo>() {
            @Override public int compare(TvInputInfo a, TvInputInfo b) {
                return hardwareNumber(a) - hardwareNumber(b);
            }
        });
        int n = 0;
        for (TvInputInfo info : ports) {
            if (info.getType() != TvInputInfo.TYPE_HDMI || n >= HDMI_PORTS_SHOWN) continue;
            n++;
            tiles.add(tile("HDMI " + n, n == 1 ? "Pristavka, konsol" : "Noutbuk, pleyer", Glyphs.SOURCE,
                    n == 1 ? 0xFF968AF3 : 0xFFBD7CEA, n == 1 ? 0xFF5548DD : 0xFF8538D2, info.getId()));
        }
        if (tiles.isEmpty()) {
            // Not a TV-input device: fall back to whatever source menu the firmware offers.
            Ui.start(this, "Qurilma", new Intent("android.intent.action.INPUT_SOURCE_ACTIVITY"));
            finish();
            return;
        }

        // A compact row centred on screen reads better than four tall pillars.
        root.addView(new View(this), Ui.weighted(0));
        root.addView(Ui.tileRow(this, tiles.toArray(new Tile[0])), new LinearLayout.LayoutParams(-1, 300));
        root.addView(new View(this), Ui.weighted(0));
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "◀ ▶", "tanlash", "OK", "ochish", "BACK", "orqaga"), footLp);
        setContentView(root);
        tiles.get(0).requestFocus();
    }

    private Tile tile(String title, String sub, int glyph, int c0, int c1, final String inputId) {
        Tile t = new Tile(this, title, sub, glyph, c0, c1, 38f).centered();
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(inputId); }
        });
        return t;
    }

    private void open(String inputId) {
        Ui.start(this, "Qurilma",
                new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
                        .setComponent(new ComponentName(TV_PLAYER, TV_PLAYER_ROOT)),
                new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)));
    }

    private List<TvInputInfo> inputs() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        return tim == null ? new ArrayList<TvInputInfo>() : new ArrayList<>(tim.getTvInputList());
    }

    private static int hardwareNumber(TvInputInfo info) {
        String id = info.getId();
        int i = id.length();
        while (i > 0 && Character.isDigit(id.charAt(i - 1))) i--;
        return i < id.length() ? Integer.parseInt(id.substring(i)) : Integer.MAX_VALUE;
    }

    private TvInputInfo first(int type) {
        for (TvInputInfo info : inputs()) if (info.getType() == type) return info;
        return null;
    }
}
