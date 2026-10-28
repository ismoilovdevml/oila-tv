package uz.oila.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Hand-drawn white glyphs, so the APK ships no image assets. Shapes are tuned for a 56px box. */
final class Glyphs {
    static final int NONE = -1, YOUTUBE = 0, LIVE_TV = 1, SOURCE = 2, NETFLIX = 3, WIFI = 4,
            GALLERY = 5, SETTINGS = 6, HOME = 7, LANGUAGE = 8, CLOCK = 9, BLUETOOTH = 10,
            STORAGE = 11, INFO = 12, SLIDERS = 13, LOCK = 14, APPS = 15, NO_SIGNAL = 16;

    private static final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF r = new RectF();
    private static final Path p = new Path();

    static {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        letter.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    }

    /**
     * Draws glyph {@code g} in an s×s box at (x, y) using {@code ink}; {@code accent} punches
     * holes in solid shapes.
     */
    static void draw(Canvas c, int g, float x, float y, float s, int ink, int accent) {
        c.save();
        c.translate(x, y);
        c.scale(s / 56f, s / 56f);
        solid.setColor(ink);
        line.setColor(ink);
        line.setStrokeWidth(5f);
        letter.setColor(ink);
        cut.setColor(accent);
        cut.setStyle(Paint.Style.FILL);
        shape(c, g);
        c.restore();
    }

    /** Four rising bars, {@code level} of them lit (0..4). */
    static void signal(Canvas c, int level, float x, float y, float s, int ink) {
        float bw = s / 6f;
        for (int i = 0; i < 4; i++) {
            float h = s * (0.3f + 0.233f * i);
            solid.setColor(i < level ? ink : (ink & 0x00FFFFFF) | 0x40000000);
            r.set(x + i * bw * 1.5f, y + s - h, x + i * bw * 1.5f + bw, y + s);
            c.drawRoundRect(r, 2, 2, solid);
        }
    }

    private static void shape(Canvas c, int g) {
        final float s = 56f, cx = 28f, cy = 28f;
        switch (g) {
            case YOUTUBE:
                r.set(0, 9, s, s - 9);
                c.drawRoundRect(r, 11, 11, solid);
                p.reset();
                p.moveTo(cx - 7, cy - 10);
                p.lineTo(cx - 7, cy + 10);
                p.lineTo(cx + 11, cy);
                p.close();
                c.drawPath(p, cut);
                break;
            case LIVE_TV:
                r.set(4, 18, s - 4, s - 4);
                c.drawRoundRect(r, 9, 9, line);
                c.drawLine(cx, 17, cx - 11, 4, line);
                c.drawLine(cx, 17, cx + 11, 4, line);
                c.drawLine(14, 30, s - 14, 30, line);
                break;
            case SOURCE:
                r.set(2, 15, s - 2, s - 15);
                c.drawRoundRect(r, 8, 8, solid);
                p.reset();
                p.moveTo(12, 23);
                p.lineTo(s - 12, 23);
                p.lineTo(s - 17, s - 22);
                p.lineTo(17, s - 22);
                p.close();
                c.drawPath(p, cut);
                break;
            case NETFLIX:
                letter.setTextSize(68f);
                c.drawText("N", 2, s + 2, letter);
                break;
            case WIFI:
                for (int i = 1; i <= 3; i++) {
                    float rad = 13 * i;
                    r.set(cx - rad, 48 - rad, cx + rad, 48 + rad);
                    c.drawArc(r, 225, 90, false, line);
                }
                c.drawCircle(cx, 48, 5, solid);
                break;
            case GALLERY:
                r.set(3, 6, s - 3, s - 6);
                c.drawRoundRect(r, 8, 8, line);
                c.drawCircle(17, 19, 5, solid);
                p.reset();
                p.moveTo(6, s - 12);
                p.lineTo(21, 30);
                p.lineTo(31, 40);
                p.lineTo(39, 30);
                p.lineTo(s - 6, s - 12);
                c.drawPath(p, line);
                break;
            case SETTINGS:
                c.save();
                for (int i = 0; i < 8; i++) {
                    r.set(cx - 5.5f, cy - 25, cx + 5.5f, cy - 12);
                    c.drawRoundRect(r, 2, 2, solid);
                    c.rotate(45, cx, cy);
                }
                c.restore();
                c.drawCircle(cx, cy, 17, solid);
                c.drawCircle(cx, cy, 8, cut);
                break;
            case HOME:
                p.reset();
                p.moveTo(cx, 6);
                p.lineTo(s - 6, cy - 2);
                p.lineTo(s - 10, cy - 2);
                p.lineTo(s - 10, s - 8);
                p.lineTo(10, s - 8);
                p.lineTo(10, cy - 2);
                p.lineTo(6, cy - 2);
                p.close();
                c.drawPath(p, solid);
                r.set(cx - 5, cy + 4, cx + 5, s - 8);
                c.drawRect(r, cut);
                break;
            case LANGUAGE:
                letter.setTextSize(40f);
                c.drawText("Aa", 4, 42, letter);
                c.drawLine(4, 50, s - 4, 50, line);
                break;
            case CLOCK:
                c.drawCircle(cx, cy, 23, line);
                c.drawLine(cx, cy, cx, cy - 13, line);
                c.drawLine(cx, cy, cx + 10, cy + 6, line);
                break;
            case BLUETOOTH:
                p.reset();
                p.moveTo(16, 18);
                p.lineTo(38, 38);
                p.lineTo(28, 48);
                p.lineTo(28, 8);
                p.lineTo(38, 18);
                p.lineTo(16, 38);
                c.drawPath(p, line);
                break;
            case STORAGE:
                for (int i = 0; i < 3; i++) {
                    r.set(5, 6 + i * 16, s - 5, 18 + i * 16);
                    c.drawRoundRect(r, 4, 4, solid);
                    c.drawCircle(s - 13, 12 + i * 16, 2.5f, cut);
                }
                break;
            case INFO:
                c.drawCircle(cx, cy, 24, solid);
                c.drawCircle(cx, 16, 3.5f, cut);
                r.set(cx - 3.5f, 23, cx + 3.5f, 42);
                c.drawRoundRect(r, 2, 2, cut);
                break;
            case SLIDERS:
                for (int i = 0; i < 3; i++) {
                    float yy = 12 + i * 16, kx = i == 1 ? 36 : (i == 0 ? 18 : 26);
                    c.drawLine(6, yy, s - 6, yy, line);
                    c.drawCircle(kx, yy, 6.5f, solid);
                }
                break;
            case NO_SIGNAL:
                r.set(4, 18, s - 4, s - 4);
                c.drawRoundRect(r, 9, 9, line);
                c.drawLine(cx, 17, cx - 11, 4, line);
                c.drawLine(cx, 17, cx + 11, 4, line);
                c.drawLine(cx - 8, 26, cx + 8, 42, line);
                c.drawLine(cx + 8, 26, cx - 8, 42, line);
                break;
            case APPS:
                for (int i = 0; i < 4; i++) {
                    float gx = 5 + (i % 2) * 26, gy = 5 + (i / 2) * 26;
                    r.set(gx, gy, gx + 20, gy + 20);
                    c.drawRoundRect(r, 6, 6, solid);
                }
                break;
            case LOCK:
                r.set(10, 26, s - 10, s - 6);
                c.drawRoundRect(r, 5, 5, solid);
                r.set(17, 10, s - 17, 38);
                c.drawArc(r, 180, 180, false, line);
                c.drawLine(17, 24, 17, 28, line);
                c.drawLine(s - 17, 24, s - 17, 28, line);
                break;
        }
    }

    private Glyphs() {}
}
