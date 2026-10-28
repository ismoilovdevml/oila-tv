package uz.oila.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Remote shortcuts that work on every screen: red opens YouTube, SOURCE opens our input picker. */
public final class KeyService extends AccessibilityService {
    private static final String YOUTUBE = "com.google.android.youtube.tv";

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        Intent target;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_PROG_RED:
                target = getPackageManager().getLeanbackLaunchIntentForPackage(YOUTUBE);
                if (target == null) target = getPackageManager().getLaunchIntentForPackage(YOUTUBE);
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
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
