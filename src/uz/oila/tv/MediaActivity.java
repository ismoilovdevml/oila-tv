package uz.oila.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;

/** Gallery hub: photos, videos and music from the TV and any plugged-in USB stick. */
public final class MediaActivity extends Activity {
    static final int PHOTOS = 0, VIDEOS = 1, MUSIC = 2;
    static final Uri[] STORES = { MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI };

    private Tile[] tiles;
    private ListRow storage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.GALLERY, 0xFFBD7CEA, 0xFF8538D2, "Galereya"),
                new LinearLayout.LayoutParams(-1, 72));

        tiles = new Tile[] {
                new Tile(this, "Rasmlar", "", Glyphs.GALLERY, 0xFFEE8A6B, 0xFFD5523A, 36f).centered(),
                new Tile(this, "Videolar", "", Glyphs.YOUTUBE, 0xFFF4B068, 0xFFE57C33, 36f).centered(),
                new Tile(this, "Musiqa", "", Glyphs.MUSIC, 0xFF5CB8E8, 0xFF2D7FC0, 36f).centered() };
        for (int i = 0; i < tiles.length; i++) {
            final int kind = i;
            tiles[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(MediaActivity.this, MediaListActivity.class).putExtra("kind", kind));
                }
            });
        }
        root.addView(new View(this), Ui.weighted(0));
        root.addView(Ui.tileRow(this, tiles), new LinearLayout.LayoutParams(-1, 280));

        storage = new ListRow(this, "", null);
        storage.setFocusable(false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 76);
        sl.topMargin = 26;
        root.addView(storage, sl);
        root.addView(new View(this), Ui.weighted(0));

        root.addView(Ui.footer(this, "◀ ▶", "tanlash", "OK", "ochish", "BACK", "orqaga"),
                new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
        tiles[0].requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (int i = 0; i < tiles.length; i++) {
            int n = count(this, i);
            tiles[i].subtitle = n < 0 ? "Ruxsat yo'q" : n == 0 ? "Hali yo'q" : n + " ta";
            tiles[i].invalidate();
        }
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        int usb = 0;
        StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);
        for (StorageVolume v : sm.getStorageVolumes()) {
            if (v.isRemovable() && Environment.MEDIA_MOUNTED.equals(v.getState())) usb++;
        }
        storage.title = usb > 0 ? "USB fleshka ulangan (" + usb + ")" : "USB fleshka ulanmagan";
        storage.set("Ichki xotira: " + fs.getAvailableBytes() / 1048576 / 1024.0f + " GB bo'sh",
                usb > 0 ? ListRow.GREEN : Ui.TEXT_DIM);
        storage.value = String.format("Ichki xotira: %.1f GB bo'sh", fs.getAvailableBytes() / 1073741824.0);
    }

    static int count(Context c, int kind) {
        try (Cursor cur = c.getContentResolver().query(STORES[kind], new String[] { "_id" }, null, null, null)) {
            return cur == null ? 0 : cur.getCount();
        } catch (SecurityException e) {
            return -1;
        }
    }
}
