package uz.oila.tv;

import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

/**
 * The TV's keyboard: instead of keys it shows a QR code, and the phone page types into the field.
 * OK switches to the stock on-screen keyboard for anyone who prefers the remote.
 */
public final class PhoneKeyboard extends InputMethodService implements PhoneLink.Target {
    private static final String REMOTE_KEYBOARD = "com.android.inputmethod.latin/.LatinIME";
    private final Handler main = new Handler(Looper.getMainLooper());
    private QrView qr;

    @Override
    public void onCreate() {
        super.onCreate();
        PhoneLink.start(this);
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // landscape would otherwise cover the whole screen with an extract view
    }

    @Override
    public View onCreateInputView() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(56, 18, 56, 18);
        panel.setBackgroundColor(0xF2171C2A);

        qr = new QrView(this);
        panel.addView(qr, new LinearLayout.LayoutParams(220, 220));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(36, 0, 0, 0);
        text.addView(Ui.label(this, "Telefoningizda yozing", 34, Ui.TEXT, Typeface.BOLD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = 14;
        text.addView(Ui.label(this, "Telefon kamerasini QR kodga qarating va ochilgan sahifada yozing.",
                24, Ui.TEXT_SOFT, Typeface.NORMAL), lp);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, 52);
        fl.topMargin = 20;
        text.addView(Ui.footer(this, "OK", "pult bilan yozish", "BACK", "yopish"), fl);
        panel.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        return panel;
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        PhoneLink.target = info.inputType == InputType.TYPE_NULL ? null : this;
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        if (PhoneLink.target == this) PhoneLink.target = null;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        qr.setText(PhoneLink.url(this)); // the address can change after a Wi-Fi reconnect
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isInputViewShown() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            switchInputMethod(REMOTE_KEYBOARD);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        if (PhoneLink.target == this) PhoneLink.target = null;
        super.onDestroy();
    }

    /** Replaces the whole field with {@code text}, so the phone page is always the source of truth. */
    @Override
    public void setText(final String text) {
        main.post(new Runnable() {
            @Override public void run() {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                ic.beginBatchEdit();
                ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                if (et != null && et.text != null) {
                    ic.setSelection(0, et.text.length());
                } else {
                    ic.deleteSurroundingText(10000, 10000);
                }
                ic.commitText(text, 1);
                ic.endBatchEdit();
            }
        });
    }

    @Override
    public void enter() {
        main.post(new Runnable() {
            @Override public void run() {
                InputConnection ic = getCurrentInputConnection();
                EditorInfo info = getCurrentInputEditorInfo();
                if (ic == null || info == null) return;
                int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
                if (action > EditorInfo.IME_ACTION_NONE) {
                    ic.performEditorAction(action);
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                }
            }
        });
    }
}
