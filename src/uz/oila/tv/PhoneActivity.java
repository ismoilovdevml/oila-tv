package uz.oila.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;

/** Big QR code for the phone page, so a family member can save it once and type from the phone. */
public final class PhoneActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        PhoneLink.start(this);
        String url = PhoneLink.url(this);

        LinearLayout root = Ui.page(this);
        root.addView(Ui.header(this, Glyphs.PHONE, 0xFFF27B9B, 0xFFD23A68, "Telefondan yozish"),
                new LinearLayout.LayoutParams(-1, 72));

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        QrView qr = new QrView(this);
        qr.setText(url);
        body.addView(qr, new LinearLayout.LayoutParams(420, 420));

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(48, 0, 0, 0);
        if (url == null) {
            steps.addView(Ui.label(this, "Televizor tarmoqqa ulanmagan", 34, Ui.TEXT, Typeface.BOLD));
            steps.addView(step("Avval Sozlamalar → Wi-Fi orqali ulaning."));
        } else {
            steps.addView(Ui.label(this, "Telefon bilan yozing va qidiring", 34, Ui.TEXT, Typeface.BOLD));
            steps.addView(step("1. Telefon va televizor bitta Wi-Fi'da bo'lsin."));
            steps.addView(step("2. Telefon kamerasini QR kodga qarating."));
            steps.addView(step("3. Ochilgan sahifada yozing: YouTube'da izlash yoki TV'dagi maydonga."));
            steps.addView(step("4. Sahifani telefonda saqlab qo'ying — keyingi safar QR kerak emas."));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = 24;
            steps.addView(Ui.label(this, url, 22, Ui.TEXT_DIM, Typeface.NORMAL), lp);
        }
        body.addView(steps, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(body, Ui.weighted(22));

        root.addView(Ui.footer(this, "BACK", "orqaga"), new LinearLayout.LayoutParams(-1, 52));
        setContentView(root);
    }

    private LinearLayout step(String s) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, 18, 0, 0);
        row.addView(Ui.label(this, s, 26, Ui.TEXT_SOFT, Typeface.NORMAL));
        return row;
    }
}
