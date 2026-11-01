package uz.oila.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Big volume card used instead of the system's thin bar. This firmware consumes the volume keys
 * before any app sees them, so the card follows the system's volume-changed broadcasts instead;
 * the stock bar is switched off by a SystemUI overlay (overlays/systemui-volume).
 */
final class VolumePanel {
    private static final int STREAM = AudioManager.STREAM_MUSIC;
    // Hidden AudioManager broadcasts; dynamic receivers may listen to them.
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";
    private static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final int HIDE_MS = 2500;
    private static final int W = 900, H = 96;

    private final Context context;
    private final WindowManager wm;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Card card;
    private boolean attached;

    private final Runnable hide = new Runnable() {
        @Override public void run() {
            if (attached) wm.removeViewImmediate(card);
            attached = false;
        }
    };

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (i.getIntExtra(EXTRA_STREAM, -1) != STREAM) return;
            show(audio.getStreamVolume(STREAM), audio.getStreamMaxVolume(STREAM), audio.isStreamMute(STREAM));
        }
    };

    VolumePanel(AccessibilityService service) {
        context = service;
        wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        audio = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
        card = new Card(service);
        IntentFilter f = new IntentFilter(VOLUME_CHANGED);
        f.addAction(MUTE_CHANGED);
        service.registerReceiver(changes, f);
    }

    void release() {
        context.unregisterReceiver(changes);
        main.removeCallbacks(hide);
        hide.run();
    }

    private void show(int level, int max, boolean muted) {
        card.set(level, max, muted);
        if (!attached) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(W, H,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = 64;
            wm.addView(card, lp);
            attached = true;
        }
        main.removeCallbacks(hide);
        main.postDelayed(hide, HIDE_MS);
    }

    /** Speaker glyph, a thick level bar and the number (or "sound off" when muted). */
    private static final class Card extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final String mutedLabel;
        private int level, max = 1;
        private boolean muted;

        Card(Context c) {
            super(c);
            text.setColor(Ui.TEXT);
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mutedLabel = c.getString(R.string.volume_muted);
        }

        void set(int level, int max, boolean muted) {
            this.level = level;
            this.max = Math.max(1, max);
            this.muted = muted;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            fill.setColor(0xF0171C2A);
            r.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(r, 24, 24, fill);

            int accent = muted ? 0xFFFF8A80 : 0xFF6FE3A5;
            Glyphs.draw(c, muted ? Glyphs.VOLUME_OFF : Glyphs.VOLUME, 30, (getHeight() - 48) / 2f, 48, muted ? accent : Ui.TEXT, 0);

            float left = 104, right = getWidth() - 112, cy = getHeight() / 2f;
            if (muted) {
                text.setTextSize(30);
                text.setTextAlign(Paint.Align.LEFT);
                c.drawText(mutedLabel, left, cy + 11, text);
                return;
            }
            fill.setColor(0xFF2A3142);
            r.set(left, cy - 8, right, cy + 8);
            c.drawRoundRect(r, 8, 8, fill);
            fill.setColor(accent);
            r.set(left, cy - 8, left + (right - left) * level / max, cy + 8);
            c.drawRoundRect(r, 8, 8, fill);

            text.setTextSize(44);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.valueOf(level), getWidth() - 30, cy + 16, text);
        }
    }
}
