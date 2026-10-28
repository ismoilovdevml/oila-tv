package uz.oila.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Remote shortcuts that work on every screen: the red button opens YouTube. */
public final class KeyService extends AccessibilityService {
    private static final String YOUTUBE = "com.google.android.youtube.tv";

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_PROG_RED) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(YOUTUBE);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(YOUTUBE);
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true; // swallow both down and up so the screen underneath never sees it
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
