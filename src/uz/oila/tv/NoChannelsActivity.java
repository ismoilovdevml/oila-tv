package uz.oila.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

/** Friendly stand-in for the tuner's bare "scan channels" screen when no channel is stored. */
public final class NoChannelsActivity extends Activity {
    static final String READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA";

    /**
     * Number of stored tuner channels, or -1 when it cannot be known. Without READ_EPG_DATA the
     * provider only returns this app's own channels, so a zero would be meaningless.
     */
    static int tunerChannelCount(Context c) {
        if (c.checkSelfPermission(READ_EPG) != PackageManager.PERMISSION_GRANTED) return -1;
        Set<String> tuners = new HashSet<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return -1;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.getType() == TvInputInfo.TYPE_TUNER) tuners.add(info.getId());
        }
        String[] cols = { TvContract.Channels.COLUMN_INPUT_ID };
        int n = 0;
        try (Cursor cur = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, cols, null, null, null)) {
            if (cur == null) return -1;
            while (cur.moveToNext()) if (tuners.contains(cur.getString(0))) n++;
        } catch (SecurityException e) {
            return -1;
        }
        return n;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.LIVE_TV, 0xFF66A0F2, 0xFF2F63DD, "Jonli TV"),
                new LinearLayout.LayoutParams(-1, 72));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(40, 34, 40, 30);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C2232);
        bg.setCornerRadius(22);
        card.setBackground(bg);

        card.addView(new Ui.GlyphView(this, Glyphs.NO_SIGNAL, 0xFF9AB8F2, 0xFF1C2232),
                new LinearLayout.LayoutParams(84, 84));
        TextView title = Ui.label(this, "Kanallar topilmadi", 38, Ui.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = 18;
        card.addView(title, tl);
        TextView body = Ui.label(this,
                "1.  Antenna kabelini televizor orqasidagi ANT uyasiga ulang\n"
                        + "2.  «Kanallarni qidirish»ni bosing, so'ng pultda MENU → CH → Avtomatik sozlash",
                23, Ui.TEXT_SOFT, Typeface.NORMAL);
        body.setLineSpacing(12, 1);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = 16;
        card.addView(body, bl);

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = 22;
        root.addView(card, cl);

        Tile scan = new Tile(this, "Kanallarni qidirish", "Antenna ulangandan keyin", Glyphs.LIVE_TV,
                0xFF66A0F2, 0xFF2F63DD, 28f).centered();
        Tile source = new Tile(this, "Qurilma", "HDMI / Pristavka", Glyphs.SOURCE,
                0xFF968AF3, 0xFF5548DD, 28f).centered();
        Tile youtube = new Tile(this, "YouTube", "Video va mult", Glyphs.YOUTUBE,
                0xFFEE6B5F, 0xFFCF3429, 28f).centered();
        scan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(NoChannelsActivity.this, "Pultda MENU → CH → Avtomatik sozlash",
                        Toast.LENGTH_LONG).show();
                // Antenna mode first, so the player's own scan searches the air, not cable.
                sendBroadcast(new Intent(SourceActivity.TV).putExtra("tunerMode", "dvbt").setComponent(
                        new ComponentName(SourceActivity.TV_PLAYER,
                                "com.ktc.tvplayer.activity.receiver.SourceReceiver")));
                Ui.start(NoChannelsActivity.this, "Jonli TV", new Intent(Intent.ACTION_MAIN)
                        .setComponent(new ComponentName(SourceActivity.TV_PLAYER, SourceActivity.TV_PLAYER_ROOT)));
            }
        });
        source.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(NoChannelsActivity.this, SourceActivity.class));
            }
        });
        youtube.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.start(NoChannelsActivity.this, "YouTube", getPackageManager()
                        .getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv"));
            }
        });
        LinearLayout.LayoutParams rl = Ui.weighted(22);
        root.addView(Ui.tileRow(this, scan, source, youtube), rl);

        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, 52);
        footLp.topMargin = 14;
        root.addView(Ui.footer(this, "◀ ▶", "tanlash", "OK", "ochish", "BACK", "orqaga"), footLp);
        setContentView(root);
        scan.requestFocus();
    }
}
