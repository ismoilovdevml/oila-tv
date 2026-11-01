package uz.oila.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Draws a QR code, black on white with the standard 4-module quiet zone, as large as fits. */
final class QrView extends View {
    private final Paint ink = new Paint(), paper = new Paint();
    private boolean[][] modules;

    QrView(Context c) {
        super(c);
        ink.setColor(0xFF000000);
        paper.setColor(0xFFFFFFFF);
    }

    void setText(String text) {
        modules = text == null ? null : Qr.encode(text);
        setVisibility(modules == null ? INVISIBLE : VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (modules == null) return;
        int n = modules.length + 8;
        int cell = Math.min(getWidth(), getHeight()) / n;
        if (cell < 1) return;
        float x0 = (getWidth() - cell * n) / 2f, y0 = (getHeight() - cell * n) / 2f;
        c.drawRect(x0, y0, x0 + cell * n, y0 + cell * n, paper);
        for (int y = 0; y < modules.length; y++) {
            for (int x = 0; x < modules.length; x++) {
                if (!modules[y][x]) continue;
                float l = x0 + (x + 4) * cell, t = y0 + (y + 4) * cell;
                c.drawRect(l, t, l + cell, t + cell, ink);
            }
        }
    }
}
