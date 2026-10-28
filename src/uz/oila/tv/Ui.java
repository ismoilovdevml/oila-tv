package uz.oila.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

/** Shared look for every screen: dark page, header with badge + clock, key-hint footer. */
final class Ui {
    static final int TEXT = 0xFFFFFFFF, TEXT_DIM = 0xFF9AA3B2, TEXT_SOFT = 0xFFC9D1DC;

    static LinearLayout page(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 32, 56, 18);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0xFF171C2A, 0xFF0D1018 }));
        return root;
    }

    /** Header row: coloured badge with glyph, title, then the clock at the right edge. */
    static LinearLayout header(Context c, int glyph, int badgeStart, int badgeEnd, String title) {
        LinearLayout h = new LinearLayout(c);
        h.setGravity(Gravity.CENTER_VERTICAL);

        GlyphView badge = new GlyphView(c, glyph, 0xFFFFFFFF, badgeEnd);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { badgeStart, badgeEnd });
        bg.setCornerRadius(14);
        badge.setBackground(bg);
        badge.setPadding(10, 10, 10, 10);
        h.addView(badge, new LinearLayout.LayoutParams(58, 58));

        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = 18;
        h.addView(label(c, title, 40, TEXT, Typeface.BOLD), tl);

        TextClock clock = new TextClock(c);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34);
        clock.setTextColor(TEXT_SOFT);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        h.addView(clock);
        return h;
    }

    /** Footer of key hints: alternating key caps and words, e.g. ("OK", "tanlash", "◀", "orqaga"). */
    static LinearLayout footer(Context c, String... keysAndWords) {
        LinearLayout f = new LinearLayout(c);
        f.setGravity(Gravity.CENTER);
        for (int i = 0; i < keysAndWords.length; i++) {
            if (i % 2 == 0) {
                for (String k : keysAndWords[i].split(" ")) f.addView(key(c, k));
            } else {
                TextView t = label(c, keysAndWords[i], 21, TEXT_DIM, Typeface.BOLD);
                t.setPadding(8, 0, 22, 0);
                f.addView(t);
            }
        }
        return f;
    }

    private static TextView key(Context c, String s) {
        TextView t = label(c, s, 20, 0xFFE6EAF0, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(40);
        t.setPadding(10, 4, 10, 4);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A3142);
        bg.setCornerRadius(8);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, 38);
        lp.leftMargin = 4;
        lp.rightMargin = 4;
        t.setLayoutParams(lp);
        return t;
    }

    static TextView label(Context c, String s, int px, int color, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A row of equal-width tiles with the standard gap. */
    static LinearLayout tileRow(Context c, Tile... tiles) {
        LinearLayout r = new LinearLayout(c);
        r.setClipChildren(false);
        for (int i = 0; i < tiles.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.leftMargin = i == 0 ? 0 : 11;
            lp.rightMargin = i == tiles.length - 1 ? 0 : 11;
            r.addView(tiles[i], lp);
        }
        return r;
    }

    static LinearLayout.LayoutParams weighted(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = topMargin;
        return lp;
    }

    /** Starts the first candidate that resolves; toasts {@code what} if none does. */
    static void start(Activity a, String what, Intent... candidates) {
        for (Intent i : candidates) {
            if (i == null) continue;
            try {
                a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // try the next candidate
            }
        }
        Toast.makeText(a, what + ": ochib bo'lmadi", Toast.LENGTH_LONG).show();
    }

    /** Fixed-size view that draws one glyph inside its padding. */
    static final class GlyphView extends View {
        private final int glyph, ink, accent;

        GlyphView(Context c, int glyph, int ink, int accent) {
            super(c);
            this.glyph = glyph;
            this.ink = ink;
            this.accent = accent;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() - getPaddingLeft() - getPaddingRight();
            float h = getHeight() - getPaddingTop() - getPaddingBottom();
            float s = Math.min(w, h);
            Glyphs.draw(c, glyph, getPaddingLeft() + (w - s) / 2, getPaddingTop() + (h - s) / 2,
                    s, ink, accent);
        }
    }

    private Ui() {}
}
