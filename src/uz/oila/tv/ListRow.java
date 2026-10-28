package uz.oila.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** A wide focusable row: label on the left, value (or an on/off pill) on the right. */
final class ListRow extends View {
    static final int GREEN = 0xFF6FE3A5;

    String title;
    String value;
    int valueColor = Ui.TEXT_DIM;
    /** null = plain value; TRUE/FALSE draws a switch pill instead of the value text. */
    Boolean toggle;
    boolean chevron;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    ListRow(Context c, String title, String value) {
        super(c);
        this.title = title;
        this.value = value;
        setFocusable(true);
        setFocusableInTouchMode(true);
        name.setColor(Ui.TEXT);
        name.setTextSize(28f);
        name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        side.setTextSize(24f);
        side.setTextAlign(Paint.Align.RIGHT);
        side.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    ListRow onClick(View.OnClickListener l) {
        setClickable(true);
        setOnClickListener(l);
        return this;
    }

    void set(String value, int color) {
        this.value = value;
        this.valueColor = color;
        invalidate();
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
        drawCard(c, w, h, isFocused(), bg, box);
        float right = w - 30f;

        if (toggle != null) {
            float pw = 74f, ph = 38f, top = (h - ph) / 2;
            box.set(right - pw, top, right, top + ph);
            bg.setColor(toggle ? GREEN : 0xFF475069);
            c.drawRoundRect(box, ph / 2, ph / 2, bg);
            bg.setColor(0xFFFFFFFF);
            c.drawCircle(toggle ? right - ph / 2 : right - pw + ph / 2, h / 2, ph / 2 - 5, bg);
            right -= pw + 20f;
            side.setColor(toggle ? GREEN : Ui.TEXT_DIM);
            if (value != null) c.drawText(value, right, h / 2 + 8f, side);
            right -= value == null ? 0 : side.measureText(value);
        } else {
            if (chevron) {
                side.setColor(Ui.TEXT_DIM);
                c.drawText("›", right, h / 2 + 10f, side);
                right -= 30f;
            }
            side.setColor(valueColor);
            String shown = value == null ? null : Tile.fit(value, side, w * 0.55f);
            if (shown != null) c.drawText(shown, right, h / 2 + 8f, side);
            right -= shown == null ? 0 : side.measureText(shown);
        }
        float max = right - 60f;
        c.drawText(Tile.fit(title, name, Math.max(120f, max)), 30f, h / 2 + 10f, name);
    }

    /**
     * Row background with the focus border built from two fills (yellow, then the card inset
     * on top). This TV's GPU drops the sides of a stroked round rect on a scaled view.
     */
    static void drawCard(Canvas c, float w, float h, boolean focused, Paint p, RectF box) {
        box.set(0, 0, w, h);
        if (focused) {
            p.setColor(Tile.FOCUS_COLOR);
            c.drawRoundRect(box, 16f, 16f, p);
            box.set(5f, 5f, w - 5f, h - 5f);
            p.setColor(0xFF2C3753);
            c.drawRoundRect(box, 12f, 12f, p);
        } else {
            p.setColor(0xFF1C2232);
            c.drawRoundRect(box, 16f, 16f, p);
        }
    }

    /** A centred, scrolling column of rows under a header — the layout every list screen shares. */
    static LinearLayout column(android.app.Activity a, LinearLayout root) {
        ScrollView scroll = new ScrollView(a);
        // A ScrollView clips its content only when it has some padding (FLAG_PADDING_NOT_NULL);
        // keep a sliver top/bottom so rows never slide over the footer.
        scroll.setPadding(0, 2, 0, 2);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        // Room for the focused row to grow; padding on the scroller itself would clip it.
        col.setPadding(16, 12, 16, 12);
        col.setClipToPadding(false);
        scroll.addView(col);
        LinearLayout.LayoutParams lp = Ui.weighted(22);
        lp.leftMargin = 150;
        lp.rightMargin = 150;
        root.addView(scroll, lp);
        return col;
    }

    static ListRow add(LinearLayout col, ListRow row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 76);
        lp.bottomMargin = 12;
        col.addView(row, lp);
        return row;
    }

    static void section(LinearLayout col, String text) {
        android.widget.TextView t = Ui.label(col.getContext(), text, 22, Ui.TEXT_DIM, Typeface.BOLD);
        t.setPadding(6, col.getChildCount() == 0 ? 0 : 14, 0, 12);
        col.addView(t);
    }
}
