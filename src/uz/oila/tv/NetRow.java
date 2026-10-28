package uz.oila.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** One Wi-Fi network in the list: signal bars, name, and a status on the right. */
final class NetRow extends View {
    final String ssid;
    final boolean secured;
    private final int level;
    private final String status;
    private final int statusColor;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    NetRow(Context c, String ssid, int level, boolean secured, String status, int statusColor) {
        super(c);
        this.ssid = ssid;
        this.level = level;
        this.secured = secured;
        this.status = status;
        this.statusColor = statusColor;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        name.setColor(Ui.TEXT);
        name.setTextSize(28f);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        side.setTextSize(21f);
        side.setTextAlign(Paint.Align.RIGHT);
        side.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect prev) {
        super.onFocusChanged(focused, direction, prev);
        float s = focused ? 1.02f : 1f;
        animate().scaleX(s).scaleY(s).setDuration(100).start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        ListRow.drawCard(c, w, h, isFocused(), bg, box);

        Glyphs.signal(c, level, 26f, h / 2 - 15f, 30f, Ui.TEXT);
        float right = w - 26f;
        if (secured) {
            Glyphs.draw(c, Glyphs.LOCK, right - 26f, h / 2 - 13f, 26f, Ui.TEXT_DIM,
                    isFocused() ? 0xFF2C3753 : 0xFF1C2232);
            right -= 40f;
        }
        side.setColor(statusColor);
        c.drawText(status, right, h / 2 + 8f, side);

        float nameMax = right - side.measureText(status) - 100f;
        c.drawText(Tile.fit(ssid, name, nameMax), 80f, h / 2 + 10f, name);

    }
}
