package uz.oila.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * YouTube search with our keyboard: YouTube draws its own on-screen keys, so the green remote
 * button opens this field instead. The phone (QR) or the remote keyboard types; Enter searches.
 */
public final class SearchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.YOUTUBE, 0xFFEE6B5F, 0xFFCF3429, getString(R.string.search_title)),
                new LinearLayout.LayoutParams(-1, 72));

        final EditText query = new EditText(this);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setSingleLine(true);
        query.setTextSize(TypedValue.COMPLEX_UNIT_PX, 40);
        query.setTextColor(Ui.TEXT);
        query.setHintTextColor(Ui.TEXT_DIM);
        query.setHint(getString(R.string.search_field_hint));
        query.setPadding(28, 18, 28, 18);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF171C2A);
        bg.setStroke(3, 0xFFFFD54F);
        bg.setCornerRadius(16);
        query.setBackground(bg);
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                String q = query.getText().toString().trim();
                if (q.isEmpty()) return true;
                if (PhoneLink.youtube(SearchActivity.this, q)) finish();
                return true;
            }
        });
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, -2);
        ql.topMargin = 36;
        root.addView(query, ql);

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = 18;
        root.addView(Ui.label(this, getString(R.string.search_help),
                24, Ui.TEXT_SOFT, Typeface.NORMAL), hl);

        setContentView(root);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        query.requestFocus();
    }
}
