# Oila TV

A tiny, fast, ad-free home screen and settings for low-end Android smart TVs, with big tiles
and big text for grandparents and kids. The interface is in Uzbek.

![Home screen](docs/home.png)

- **~50 KB APK.** It uses no libraries and no image assets: every icon is drawn in code
  ([`Glyphs.java`](src/uz/oila/tv/Glyphs.java)).
- **~9 MB RAM** at runtime, with a cold start of about 0.5 s on a 1 GB TV. The stock launcher
  it replaces used 37 MB.
- **Remote-first.** The D-pad moves a yellow focus ring, rows never dead-end, and every screen
  shows a key-hint footer.
- **No ads, no tracking.** The app makes no network calls of its own, except the speed test
  you start yourself.

## What it replaces

| Oila TV screen | Class | Replaces |
|---|---|---|
| Home (8 tiles) | `HomeActivity` | Vendor launcher with ad rows |
| Live TV / "no channels" | `SourceActivity.watch`, `NoChannelsActivity` | Bare black "scan channels" page |
| Inputs: Antenna, AV, HDMI 1/2 | `SourceActivity` | Vendor source menu |
| Apps | `AppsActivity` | "My apps" row |
| Gallery: photos, videos, music | `MediaActivity`, `MediaListActivity`, `ViewerActivity`, `PlayerActivity` | Vendor file manager and media player |
| Settings hub | `SettingsActivity` | Vendor settings |
| Wi-Fi | `WifiActivity` | Vendor network page |
| Language | `LanguageActivity` | Vendor language page |
| Date & time | `DateTimeActivity` | Vendor time page |
| About + Developer tools (English) + build.prop | `AboutActivity`, `DevActivity`, `PropsActivity` | Vendor "about" and developer panel |
| Speed-up (free memory, internet speed test) | `BoostActivity` | Vendor cleaner app |
| Remote shortcuts (red = YouTube, SOURCE = inputs) | `KeyService` | — |

## Tested on

**Premier TV6681**: MediaTek MT5621 board, Android 9 userdebug, 1280×720 at 160 dpi, 1 GB RAM,
MT7603 2.4 GHz-only Wi-Fi, no Bluetooth.

The tuner, input and media integrations target the KTC-platform apps on this family of TVs
(`com.ktc.tvplayer`, `com.ktc.setting`). Where a standard Android intent exists, the app falls
back to it on other TVs.

## Repository layout

```
AndroidManifest.xml          app manifest (minSdk/targetSdk 28)
build.sh                     build the APK: aapt2 → javac → d8 → zipalign → apksigner (no Gradle)
src/uz/oila/tv/              all app code, one class per screen
  Ui.java                    page, header, footer, tile rows, safe activity start
  Tile.java, ListRow.java    focusable card / wide row (focus ring, toggle pill)
  Glyphs.java                hand-drawn icons (56 px design box)
res/xml/key_service.xml      accessibility config for KeyService (remote shortcuts)
tv-tune.sh                   runtime setup over adb: home, permissions, trims (apply|rollback)
device-install.sh            system-level setup over root adb (apply|rollback)
device/
  oila-remote-fix.rc         init rule: keep the remote working after a power cut
  boot/bootlogo.jpg          boot-loader logo   (1366×768 baseline JPEG)
  boot/bootvideo.mp4         boot video         (H.264 640×368 24 fps, 10 s)
  make_boot_media.py         regenerate both with your own text
overlays/                    resource overlays, built by overlays/build.sh
  android-power/             hold power = full power-off (framework config)
  ktc-setting-en/            English strings for the vendor developer panel
tools/fapi/                  app_process helper that calls the TV's own boot-logo API
docs/                        screenshots
```

## Build

You need a JDK and the Android SDK (build-tools 35 and platform android-35). Gradle is not used.

```sh
./build.sh                 # -> build/oila-tv.apk, signed with ~/.android/debug.keystore
overlays/build.sh          # -> overlays/*/build/*.apk
tools/fapi/build.sh        # -> tools/fapi/build/fapi.jar
```

`android.jar` has no `LambdaMetafactory`, so the code uses anonymous classes and no lambdas.
`build.sh` wipes `build/` on every run, so do not keep anything there.

## Install

Installation has three layers. Each one is independent and can be reversed.

**1. The app**

```sh
adb connect <tv-ip>:5555
adb install -r build/oila-tv.apk
```

**2. Runtime setup**

```sh
./tv-tune.sh <tv-ip>:5555 apply      # rollback restores everything
```

`apply` does the following:

- sets Oila TV as the home app;
- disables the stock launcher, ad/telemetry services, the OTA client, the cleaner, the vendor
  media player, Bluetooth helpers and the DLNA renderer;
- sets all animation scales to 0;
- grants the development-level permissions the screens need (`CHANGE_CONFIGURATION`,
  `WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`) plus location access, without which Android 9
  returns no Wi-Fi scan results;
- enables the remote-shortcut service.

**3. System level** (needs root adb; tested only on the TV6681)

```sh
./device-install.sh <tv-ip>:5555 apply    # rollback puts the factory files back
```

`apply` writes the following on the TV:

- the init rule (`device/oila-remote-fix.rc`);
- the two overlays;
- the boot video;
- the boot-loader logo, set through the platform API;
- `MUSIC_ON=0` in `boot.ini`, which removes the boot jingle.

Every replaced file is first copied to `<file>.bak.oila`. Afterwards, **unplug the TV for about
10 s** and plug it back in. The remote's power button only puts the TV to sleep, so it does not
reload any of this. Finally, enable the English panel overlay once:

```sh
adb shell cmd overlay enable uz.oila.tv.overlay.ktcsetting
```

### Custom boot text

```sh
pip install pillow                       # ffmpeg must be installed too
python3 device/make_boot_media.py --logo "Android TV" --video "Shaftoli qoqi"
./device-install.sh <tv-ip>:5555 apply
```

## Adding a screen

1. Create `XyzActivity extends Activity`.
2. Build the page with `Ui.page()`, `Ui.header(...)`, then either:
   - `Ui.tileRow(...)` with `Tile`s for a grid of choices, or
   - `ListRow.column(...)` with `ListRow.add(...)` for a list.
3. Finish the page with `Ui.footer(...)`, which shows the key hints.
4. Register the activity in `AndroidManifest.xml` with `excludeFromRecents` and `landscape`, like
   the other screens.
5. Start other apps with `Ui.start(activity, label, candidates...)`. It tries each intent in turn
   and shows a toast instead of crashing.

Things to know about this hardware:

- **Draw focus borders with two fills, not a stroke.** This GPU drops the sides of a stroked
  round rect on a scaled view. See `ListRow.drawCard`.
- **Give list containers padding and `setClipToPadding(false)`, and give the `ScrollView` a
  non-zero padding.** A scroller with zero padding does not clip, so rows slide over the footer.

## Firmware notes (TV6681)

These are facts measured on the device that shaped the design:

- **Remote dead after a power cut.** The window manager drops every key until
  `persist.sys.bootanim_exit=1`. Only the first-run wizard ever sets it, and SurfaceFlinger
  clears it on every cold boot. `device/oila-remote-fix.rc` sets it once `sys.boot_completed=1`.
- **The power button means sleep (STR), not off.** Holding the button does nothing by default;
  the `android-power` overlay makes a hold a full power-off. A full off is also the only way the
  boot logo and video play again.
- **Wi-Fi drops after every wake from sleep.** Client mode fails on resume, and Android toggles
  Wi-Fi to recover, which takes 5–30 s. A full power-off avoids it.
- **Switching HDMI or AV while the player is open** works through the player's source-change
  broadcast. A channel URI is ignored while the player is already open. The `TV` action needs
  the extra `tunerMode` (`dvbt` for an antenna).
- **The boot logo is cached** by the boot loader. Replacing the JPEG alone does nothing until the
  platform's logo call clears its cache flag; `tools/fapi` makes that call.
- **Overlays** installed under `/data` are refused unless platform-signed, so they live in
  `/vendor/overlay`.

## Troubleshooting

| Symptom | Check |
|---|---|
| Remote does nothing after power-on | `adb shell getprop persist.sys.bootanim_exit` should be `1`; is `oila-remote-fix.rc` in `/vendor/etc/init`? |
| Wi-Fi list is empty | Location permission and providers: run `tv-tune.sh apply` again |
| Language or time switch shows "ruxsat yo'q" | The development permissions were not granted: run `tv-tune.sh apply` |
| Red button or SOURCE does nothing | `settings get secure enabled_accessibility_services` should list `uz.oila.tv/uz.oila.tv.KeyService`. `adb shell input keyevent` bypasses accessibility, so test with the real remote |
| Boot logo did not change | `Fapi get db_table` should print `0` before the cold boot |

## License

MIT
