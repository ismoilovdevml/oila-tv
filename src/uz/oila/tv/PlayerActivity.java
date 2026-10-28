package uz.oila.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;

/** Video and music player: OK pauses, ◀ ▶ jump 10 s, ▲ ▼ previous/next file. */
public final class PlayerActivity extends Activity {
    private ArrayList<Uri> uris;
    private int index;
    private VideoView video;
    private TextView time, state;
    private ProgressBar bar;
    private LinearLayout panel;
    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            int pos = video.getCurrentPosition(), dur = Math.max(1, video.getDuration());
            bar.setProgress((int) (1000L * pos / dur));
            time.setText(clock(pos) + " / " + clock(dur));
            h.postDelayed(this, 500);
        }
    };
    private final Runnable hide = new Runnable() {
        @Override public void run() { if (video.isPlaying()) panel.setVisibility(View.GONE); }
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        uris = getIntent().getParcelableArrayListExtra("uris");
        index = getIntent().getIntExtra("index", 0);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        if (getIntent().getBooleanExtra("audio", false)) {
            Ui.GlyphView note = new Ui.GlyphView(this, Glyphs.MUSIC, 0xFF5CB8E8, 0xFF000000);
            root.addView(note, new FrameLayout.LayoutParams(220, 220, Gravity.CENTER));
        }

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(40, 20, 40, 26);
        panel.setBackgroundColor(0xB3000000);
        state = Ui.label(this, "", 26, Ui.TEXT, Typeface.BOLD);
        panel.addView(state);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 16);
        bl.topMargin = 12;
        panel.addView(bar, bl);
        time = Ui.label(this, "", 22, Ui.TEXT_SOFT, Typeface.NORMAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = 10;
        panel.addView(time, tl);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if (index < uris.size() - 1) { index++; play(); } else finish();
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                state.setText("Bu faylni ochib bo'lmadi");
                panel.setVisibility(View.VISIBLE);
                return true;
            }
        });
        play();
    }

    private void play() {
        video.setVideoURI(uris.get(index));
        video.start();
        state.setText("▶  " + (index + 1) + " / " + uris.size());
        flash();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    private void flash() {
        panel.setVisibility(View.VISIBLE);
        h.removeCallbacks(hide);
        h.postDelayed(hide, 3000);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (video.isPlaying()) { video.pause(); state.setText("❚❚  Pauza"); }
                else { video.start(); state.setText("▶  " + (index + 1) + " / " + uris.size()); }
                flash();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                video.seekTo(video.getCurrentPosition() + 10000); flash(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                video.seekTo(Math.max(0, video.getCurrentPosition() - 10000)); flash(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index < uris.size() - 1) { index++; play(); } return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (index > 0) { index--; play(); } return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        video.pause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        video.stopPlayback();
        super.onDestroy();
    }

    private static String clock(int ms) {
        int s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
