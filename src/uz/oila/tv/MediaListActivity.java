package uz.oila.tv;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Grid of one media kind, newest first; thumbnails load off the UI thread. */
public final class MediaListActivity extends Activity {
    private static final int COLUMNS = 4;
    private static final String[] TITLES = { "Rasmlar", "Videolar", "Musiqa" };
    private static final int[][] COLORS = { { 0xFFEE8A6B, 0xFFD5523A }, { 0xFFF4B068, 0xFFE57C33 },
            { 0xFF5CB8E8, 0xFF2D7FC0 } };

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int kind;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        kind = getIntent().getIntExtra("kind", MediaActivity.PHOTOS);
        int glyph = kind == MediaActivity.PHOTOS ? Glyphs.GALLERY : kind == MediaActivity.VIDEOS ? Glyphs.YOUTUBE : Glyphs.MUSIC;
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, glyph, COLORS[kind][0], COLORS[kind][1], TITLES[kind]),
                new LinearLayout.LayoutParams(-1, 72));

        final ArrayList<Uri> uris = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Uri store = MediaActivity.STORES[kind];
        try (Cursor c = getContentResolver().query(store, new String[] { "_id", MediaStore.MediaColumns.DISPLAY_NAME },
                null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext()) {
                uris.add(ContentUris.withAppendedId(store, c.getLong(0)));
                names.add(c.getString(1));
            }
        } catch (SecurityException ignored) {
            // shown as an empty list below
        }

        if (uris.isEmpty()) {
            TextView t = Ui.label(this, "Bu yerda hali hech narsa yo'q.\nUSB fleshkani televizorga ulang.",
                    28, Ui.TEXT_DIM, android.graphics.Typeface.BOLD);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(12, 1);
            root.addView(t, Ui.weighted(0));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setPadding(10, 10, 10, 10);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setClipChildren(false);
            scroll.addView(grid);
            Thumb first = null;
            for (int i = 0; i < uris.size(); i += COLUMNS) {
                List<Tile> row = new ArrayList<>();
                for (int j = i; j < Math.min(i + COLUMNS, uris.size()); j++) {
                    Thumb t = new Thumb(j, names.get(j));
                    if (first == null) first = t;
                    final int index = j;
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { open(uris, index); }
                    });
                    if (kind != MediaActivity.MUSIC) t.load(uris.get(j));
                    row.add(t);
                }
                while (row.size() < COLUMNS) {
                    Tile pad = new Tile(this, "", "", Glyphs.NONE, 0, 0);
                    pad.setFocusable(false);
                    pad.setVisibility(View.INVISIBLE);
                    row.add(pad);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 180);
                lp.bottomMargin = 22;
                grid.addView(Ui.tileRow(this, row.toArray(new Tile[0])), lp);
            }
            root.addView(scroll, Ui.weighted(18));
            first.requestFocus();
        }
        root.addView(Ui.footer(this, "◀ ▲ ▼ ▶", "yurish", "OK", "ochish", "BACK", "orqaga"),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        super.onDestroy();
    }

    private void open(ArrayList<Uri> uris, int index) {
        Class<?> target = kind == MediaActivity.PHOTOS ? ViewerActivity.class : PlayerActivity.class;
        startActivity(new Intent(this, target).putParcelableArrayListExtra("uris", uris)
                .putExtra("index", index).putExtra("audio", kind == MediaActivity.MUSIC));
    }

    /** A tile that shows a picture thumbnail once it has loaded, with the file name underneath. */
    private final class Thumb extends Tile {
        private Bitmap bmp;
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        Thumb(int i, String name) {
            super(MediaListActivity.this, name, "", kind == MediaActivity.MUSIC ? Glyphs.MUSIC : Glyphs.NONE,
                    COLORS[kind][0], COLORS[kind][1], 20f);
        }

        void load(final Uri uri) {
            loader.execute(new Runnable() {
                @Override public void run() {
                    final Bitmap b = thumbnail(uri);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            bmp = b;
                            invalidate();
                        }
                    });
                }
            });
        }

        @Override
        void drawContent(Canvas c, float w, float h) {
            if (bmp != null) {
                // centre-crop into the tile, leaving a strip for the name
                float tw = w, th = h - 40, scale = Math.max(tw / bmp.getWidth(), th / bmp.getHeight());
                float sw = tw / scale, sh = th / scale;
                src.set((int) ((bmp.getWidth() - sw) / 2), (int) ((bmp.getHeight() - sh) / 2),
                        (int) ((bmp.getWidth() + sw) / 2), (int) ((bmp.getHeight() + sh) / 2));
                dst.set(0, 0, tw, th);
                c.save();
                c.clipRect(dst);
                c.drawBitmap(bmp, src, dst, null);
                c.restore();
            }
            c.drawText(fit(title, titlePaint, w - 28), 14f, h - 14f, titlePaint);
        }
    }

    private Bitmap thumbnail(Uri uri) {
        long id = ContentUris.parseId(uri);
        try {
            return kind == MediaActivity.PHOTOS
                    ? MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id,
                            MediaStore.Images.Thumbnails.MINI_KIND, null)
                    : MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
