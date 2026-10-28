package uz.oila.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Focusable card: gradient fill, optional glyph top-left, title + subtitle bottom-left. */
class Tile extends View {
    static final int FOCUS_COLOR = 0xFFFFD54A;

    String title;
    String subtitle;
    private final int glyph;
    private final int colorStart;
    private final int colorEnd;
    private final float titleSize;
    private boolean centered;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    Tile(Context ctx, String title, String subtitle, int glyph, int colorStart, int colorEnd) {
        this(ctx, title, subtitle, glyph, colorStart, colorEnd, 34f);
    }

    Tile(Context ctx, String title, String subtitle, int glyph, int colorStart, int colorEnd,
            float titleSize) {
        super(ctx);
        this.title = title;
        this.subtitle = subtitle;
        this.glyph = glyph;
        this.colorStart = colorStart;
        this.colorEnd = colorEnd;
        this.titleSize = titleSize;

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(6f);
        ring.setColor(FOCUS_COLOR);

        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextSize(titleSize);
        titlePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titlePaint.setShadowLayer(4f, 0f, 2f, 0x40000000);

        subPaint.setColor(0xE6FFFFFF);
        subPaint.setTextSize(Math.max(17f, titleSize * 0.56f));
        subPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /** Big glyph in the middle with the text centred under it (for short rows of choices). */
    Tile centered() {
        centered = true;
        titlePaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextAlign(Paint.Align.CENTER);
        return this;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        fill.setShader(new LinearGradient(w, 0, 0, h, colorStart, colorEnd, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect prev) {
        super.onFocusChanged(focused, direction, prev);
        float s = focused ? 1.05f : 1f;
        animate().scaleX(s).scaleY(s).setDuration(110).start();
        setElevation(focused ? 12f : 0f);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        box.set(0, 0, w, h);
        c.drawRoundRect(box, 22f, 22f, fill);

        if (centered) {
            float g = Math.min(96f, h * 0.36f);
            Glyphs.draw(c, glyph, (w - g) / 2, h * 0.42f - g * 0.75f, g, 0xFFFFFFFF, colorEnd);
            c.drawText(fit(title, titlePaint, w - 32), w / 2, h * 0.72f, titlePaint);
            c.drawText(fit(subtitle, subPaint, w - 32), w / 2, h * 0.72f + subPaint.getTextSize() * 1.5f, subPaint);
        } else {
            if (glyph != Glyphs.NONE) Glyphs.draw(c, glyph, 24f, 22f, 52f, 0xFFFFFFFF, colorEnd);
            drawContent(c, w, h);
        }

        if (isFocused()) {
            box.inset(3f, 3f);
            c.drawRoundRect(box, 20f, 20f, ring);
        }
    }

    /** Text block; subclasses may add more. */
    void drawContent(Canvas c, float w, float h) {
        float subY = h - subPaint.getTextSize() * 1.1f;
        c.drawText(fit(title, titlePaint, w - 44), 22f, subY - subPaint.getTextSize() * 1.35f, titlePaint);
        c.drawText(fit(subtitle, subPaint, w - 44), 22f, subY + 4, subPaint);
    }

    static String fit(String s, Paint p, float max) {
        if (s == null) return "";
        if (p.measureText(s) <= max) return s;
        int n = p.breakText(s, true, max - p.measureText("…"), null);
        return s.substring(0, Math.max(0, n)) + "…";
    }
}
