package uz.oila.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;

/** Full-screen photo viewer: ◀ ▶ flip through, BACK returns to the grid. */
public final class ViewerActivity extends Activity {
    private ArrayList<Uri> uris;
    private int index;
    private ImageView image;
    private TextView counter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        uris = getIntent().getParcelableArrayListExtra("uris");
        index = getIntent().getIntExtra("index", 0);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        counter = Ui.label(this, "", 22, 0xCCFFFFFF, android.graphics.Typeface.BOLD);
        counter.setPadding(18, 8, 18, 8);
        counter.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        cl.bottomMargin = 30;
        root.addView(counter, cl);
        setContentView(root);
        show();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT && index < uris.size() - 1) { index++; show(); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) { index--; show(); return true; }
        return super.onKeyDown(code, e);
    }

    private void show() {
        counter.setText("◀   " + (index + 1) + " / " + uris.size() + "   ▶");
        image.setImageBitmap(decode(uris.get(index), 1280, 720));
    }

    /** Decodes no larger than the screen, so big camera photos do not exhaust the 1 GB of RAM. */
    private Bitmap decode(Uri uri, int maxW, int maxH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxW || o.outHeight / (sample * 2) >= maxH) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception e) {
            return null;
        }
    }
}
