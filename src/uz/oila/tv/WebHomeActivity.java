package uz.oila.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Home screen rendered from an HTML design in assets/home/&lt;style&gt;/index.html, shown in a
 * WebView so the glassmorphism designs render exactly as drawn. The remote drives it: the d-pad
 * moves focus through the page (nav/ok in JS) and OK launches the focused item through {@link Bridge}.
 * If the WebView cannot start, it falls back to the native {@link HomeActivity} so the TV always
 * has a working home.
 */
public final class WebHomeActivity extends Activity {
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String FALLBACK_TUNER = "com.mediatek.tvinput/.tuner.TunerInputService/HW0";
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) setTurnScreenOn(true);
        try {
            web = new WebView(this);
            web.getSettings().setJavaScriptEnabled(true);
            web.setBackgroundColor(0xFF9FC4C3);
            web.setFocusable(false); // keys go to the activity, which drives focus in JS
            web.setFocusableInTouchMode(false);
            web.addJavascriptInterface(new Bridge(), "Android");
            web.loadUrl("file:///android_asset/home/" + style() + "/index.html");
            setContentView(web);
        } catch (Throwable t) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }
        PhoneLink.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PhoneLink.restoreKeyboard(this);
    }

    private String style() {
        SharedPreferences p = getSharedPreferences("home", MODE_PRIVATE);
        return p.getString("style", "kol");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        String js = null;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: js = "nav('left')"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: js = "nav('right')"; break;
            case KeyEvent.KEYCODE_DPAD_UP: js = "nav('up')"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: js = "nav('down')"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: js = "ok()"; break;
            default: return super.onKeyDown(keyCode, e);
        }
        if (web != null) web.evaluateJavascript(js, null);
        return true;
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back stays on the home screen.
    }

    /** Called from the page (OK or a tap) with the focused item's id; launches it on the UI thread. */
    private final class Bridge {
        @JavascriptInterface
        public void launch(final String id) {
            runOnUiThread(new Runnable() {
                @Override public void run() { open(id); }
            });
        }
    }

    private void open(String id) {
        PackageManager pm = getPackageManager();
        if ("youtube".equals(id)) {
            Ui.start(this, "YouTube", explicit(YOUTUBE, "com.google.android.apps.youtube.tv.activity.ShellActivity"),
                    launchIntent(pm, YOUTUBE));
        } else if ("jonli".equals(id)) {
            SourceActivity.watch(this, SourceActivity.TV, tunerInputId());
        } else if ("qurilma".equals(id)) {
            startActivity(new Intent(this, SourceActivity.class));
        } else if ("netflix".equals(id)) {
            Ui.start(this, "Netflix", launchIntent(pm, "com.netflix.mediaclient"));
        } else if ("wifi".equals(id)) {
            startActivity(new Intent(this, WifiActivity.class));
        } else if ("galereya".equals(id)) {
            startActivity(new Intent(this, MediaActivity.class));
        } else if ("sozlamalar".equals(id)) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if ("ilovalar".equals(id)) {
            startActivity(new Intent(this, AppsActivity.class));
        }
    }

    private String tunerInputId() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_TUNER) return info.getId();
            }
        }
        return FALLBACK_TUNER;
    }

    private static Intent explicit(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls));
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
