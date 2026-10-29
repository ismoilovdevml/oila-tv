#!/usr/bin/env bash
# Install (or remove) the system-level pieces on the Premier TV6681 over root adb:
#   /vendor/etc/init/oila-remote-fix.rc        remote works after a cold boot
#   /vendor/overlay/KtcSettingEn/*.apk          English text for the vendor developer panel
#   /vendor/overlay/OilaPower/*.apk             hold the power button = full power-off
#   /vendor/stream/video.mp4                    boot video  (device/boot/bootvideo.mp4)
#   boot-loader logo                            boot logo   (device/boot/bootlogo.jpg)
#   /vendor/tvconfig/boot.ini MUSIC_ON=0        no boot jingle
# Overlays outside the vendor/system partitions are refused unless platform-signed, hence /vendor.
# Every replaced file is kept as <file>.bak.oila on the TV; rollback puts the originals back.
# Usage: device-install.sh <ip:port> apply|rollback
#        then unplug the TV for ~10 s and plug it back in (remote power is sleep, not a reboot).
set -euo pipefail
S=${1:?ip:port}; MODE=${2:?apply|rollback}
cd "$(dirname "$0")"
adb -s "$S" root >/dev/null; sleep 3; adb connect "$S" >/dev/null
A="adb -s $S shell"
RC=/vendor/etc/init/oila-remote-fix.rc
OV=/vendor/overlay/KtcSettingEn
PW=/vendor/overlay/OilaPower
VIDEO=/vendor/stream/video.mp4
TVCFG=/vendor/tvconfig            # its own read-write partition
LOGO_SRC=$TVCFG/boot0.jpg         # must sit where the platform service may read it
STOCK_LOGO=$TVCFG/config/customer/bootlogo.jpg
FAPI="CLASSPATH=/data/local/tmp/fapi.jar:/system/product/app/KtcTvplayer/KtcTvplayer.apk app_process /system/bin Fapi"

# Keep the factory original as <file>.bak.oila. An older backup (<file>.bak.*) wins over the live
# file, so re-running apply on an already modified TV never saves a modified file as "original".
backup() {
  $A "[ -e $1.bak.oila ] && exit 0; old=\$(ls $1.bak.* 2>/dev/null | head -1);
      if [ -n \"\$old\" ]; then cp -p \"\$old\" $1.bak.oila; else cp -p $1 $1.bak.oila; fi"
}

$A mount -o rw,remount /vendor
case $MODE in
  apply)
    overlays/build.sh >/dev/null
    tools/fapi/build.sh >/dev/null
    adb -s "$S" push device/oila-remote-fix.rc "$RC" >/dev/null
    $A mkdir -p "$OV" "$PW"
    adb -s "$S" push overlays/ktc-setting-en/build/ktc-setting-en.apk "$OV/KtcSettingEn.apk" >/dev/null
    adb -s "$S" push overlays/android-power/build/android-power.apk "$PW/OilaPower.apk" >/dev/null
    backup "$VIDEO"
    adb -s "$S" push device/boot/bootvideo.mp4 "$VIDEO" >/dev/null
    $A "chmod 644 $RC $OV/KtcSettingEn.apk $PW/OilaPower.apk $VIDEO; chmod 755 $OV $PW"
    $A "restorecon $RC $VIDEO; restorecon -R $OV $PW"
    # Logo: the boot loader shows a cached copy until its db_table flag is cleared, which the
    # platform's own logo call does (and copies the file into place).
    backup "$LOGO_SRC"; backup "$TVCFG/boot.ini"
    adb -s "$S" push device/boot/bootlogo.jpg "$LOGO_SRC" >/dev/null
    adb -s "$S" push tools/fapi/build/fapi.jar /data/local/tmp/fapi.jar >/dev/null
    $A "$FAPI logo $LOGO_SRC"
    $A "sed -i 's/^MUSIC_ON = 1;/MUSIC_ON = 0;/' $TVCFG/boot.ini"
    ;;
  rollback)
    $A "rm -f $RC; rm -rf $OV $PW"
    $A "[ -e $VIDEO.bak.oila ] && cp -p $VIDEO.bak.oila $VIDEO || true"
    $A "[ -e $TVCFG/boot.ini.bak.oila ] && cp -p $TVCFG/boot.ini.bak.oila $TVCFG/boot.ini || true"
    $A "[ -e $LOGO_SRC.bak.oila ] && cp -p $LOGO_SRC.bak.oila $LOGO_SRC || true"
    adb -s "$S" push tools/fapi/build/fapi.jar /data/local/tmp/fapi.jar >/dev/null 2>&1 || true
    $A "$FAPI logo $STOCK_LOGO"
    ;;
  *) echo "mode must be apply|rollback" >&2; exit 2 ;;
esac
$A sync
$A mount -o ro,remount /vendor 2>/dev/null || true
$A "$FAPI get db_table _BootlogoFile; grep MUSIC_ON $TVCFG/boot.ini; ls -lZ $RC $VIDEO 2>&1"
echo "Now unplug the TV for ~10 s and plug it back in. The English panel overlay then needs once:"
echo "  adb -s $S shell cmd overlay enable uz.oila.tv.overlay.ktcsetting"
