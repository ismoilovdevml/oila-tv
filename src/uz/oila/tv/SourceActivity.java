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
    private static final String SOURCE_RECEIVER = "com.ktc.tvplayer.activity.receiver.SourceReceiver";
    static final String TV = "android.intent.action.CHANGE_TVINPUT_TYPE_TV";
    private static final String AV = "android.intent.action.CHANGE_TVINPUT_TYPE_AV";
    private static final String[] HDMI = { "android.intent.action.CHANGE_TVINPUT_TYPE_HDMI",
            "android.intent.action.CHANGE_TVINPUT_TYPE_HDMI2" };
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
                0xFF66A0F2, 0xFF2F63DD, tuner.getId(), TV));
        if (av != null) tiles.add(tile("AV", "Sariq-oq-qizil kabel", Glyphs.SOURCE,
                0xFFF4B068, 0xFFE57C33, av.getId(), AV));
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
                    n == 1 ? 0xFF968AF3 : 0xFFBD7CEA, n == 1 ? 0xFF5548DD : 0xFF8538D2, info.getId(), HDMI[n - 1]));
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

    private Tile tile(String title, String sub, int glyph, int c0, int c1, final String inputId,
            final String action) {
        Tile t = new Tile(this, title, sub, glyph, c0, c1, 38f).centered();
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { watch(SourceActivity.this, action, inputId); }
        });
        return t;
    }

    /**
     * Switches the TV to an input and shows it. On this firmware the player's own source receiver
     * does the switch reliably (a channel URI is ignored while the player is already open); other
     * TVs fall back to the standard passthrough URI. The antenna input goes through a channel check
     * first, so an empty channel list gets an explained screen instead of a bare black one.
     */
    static void watch(Activity a, String action, String inputId) {
        if (TV.equals(action) && NoChannelsActivity.tunerChannelCount(a) == 0) {
            a.startActivity(new Intent(a, NoChannelsActivity.class));
            return;
        }
        Intent switcher = new Intent(action).setComponent(new ComponentName(TV_PLAYER, SOURCE_RECEIVER));
        if (TV.equals(action)) switcher.putExtra("tunerMode", "dvbt"); // antenna, not cable
        if (!a.getPackageManager().queryBroadcastReceivers(switcher, 0).isEmpty()) {
            a.sendBroadcast(switcher);
            Ui.start(a, "Qurilma", new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(TV_PLAYER, TV_PLAYER_ROOT)));
            return;
        }
        Ui.start(a, "Qurilma",
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
