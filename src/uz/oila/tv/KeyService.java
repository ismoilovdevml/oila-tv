package uz.oila.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote shortcuts that work on every screen: red opens YouTube, green opens our YouTube search,
 * SOURCE opens our input picker, MENU acts as HOME. While connected it also shows our volume card.
 */
public final class KeyService extends AccessibilityService {
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    // The live-TV player owns MENU (picture/sound, channel scan), so we let MENU through there.
    private static final String TV_PLAYER = "com.ktc.tvplayer";
    private VolumePanel volume;
    private volatile CharSequence foreground = "";

    @Override
    protected void onServiceConnected() {
        volume = new VolumePanel(this);
    }

    @Override
    public void onDestroy() {
        if (volume != null) volume.release();
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        Intent target;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_MENU:
                if (TextUtils.equals(foreground, TV_PLAYER)) return false; // its own menu
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                }
                return true;
            case KeyEvent.KEYCODE_PROG_RED:
                target = getPackageManager().getLeanbackLaunchIntentForPackage(YOUTUBE);
                if (target == null) target = getPackageManager().getLaunchIntentForPackage(YOUTUBE);
                break;
            case KeyEvent.KEYCODE_PROG_GREEN:
                target = new Intent(this, SearchActivity.class);
                break;
            case KeyEvent.KEYCODE_TV_INPUT:
                target = new Intent(this, SourceActivity.class);
                break;
            default:
                return false;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && target != null) {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true; // swallow both down and up so the screen underneath never sees it
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Remember which app is in front so MENU can defer to the live-TV player.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            foreground = event.getPackageName();
        }
    }

    @Override
    public void onInterrupt() {}
}
