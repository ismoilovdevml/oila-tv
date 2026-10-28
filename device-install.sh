#!/usr/bin/env bash
# Install (or remove) the read-only-partition pieces on the Premier TV6681 over root adb:
#   /vendor/etc/init/oila-remote-fix.rc        remote works after a cold boot
#   /vendor/overlay/KtcSettingEn/*.apk          English text for the vendor developer panel
#   /vendor/overlay/OilaPower/*.apk             hold the power button = full power-off
# Overlays outside the vendor/system partitions are refused unless platform-signed, hence /vendor.
# Usage: device-install.sh <ip:port> apply|rollback      (reboot afterwards to take effect)
set -euo pipefail
S=${1:?ip:port}; MODE=${2:?apply|rollback}
cd "$(dirname "$0")"
adb -s "$S" root >/dev/null; sleep 3; adb connect "$S" >/dev/null
A="adb -s $S shell"
RC=/vendor/etc/init/oila-remote-fix.rc
OV=/vendor/overlay/KtcSettingEn
PW=/vendor/overlay/OilaPower
$A mount -o rw,remount /vendor
case $MODE in
  apply)
    overlays/build.sh >/dev/null
    adb -s "$S" push device/oila-remote-fix.rc "$RC" >/dev/null
    $A mkdir -p "$OV"
    adb -s "$S" push overlays/ktc-setting-en/build/ktc-setting-en.apk "$OV/KtcSettingEn.apk" >/dev/null
    $A mkdir -p "$PW"
    adb -s "$S" push overlays/android-power/build/android-power.apk "$PW/OilaPower.apk" >/dev/null
    $A "chmod 644 $RC $OV/KtcSettingEn.apk $PW/OilaPower.apk; chmod 755 $OV $PW; restorecon $RC; restorecon -R $OV $PW"
    ;;
  rollback)
    $A "rm -f $RC; rm -rf $OV $PW"
    ;;
  *) echo "mode must be apply|rollback" >&2; exit 2 ;;
esac
$A sync
$A mount -o ro,remount /vendor || true
$A "ls -lZ $RC $OV 2>&1"
echo "unplug/replug the TV once; the power overlay is static (always on); the KtcSettingEn one needs: cmd overlay enable uz.oila.tv.overlay.ktcsetting"
