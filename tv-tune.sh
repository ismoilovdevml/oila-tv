#!/usr/bin/env bash
# Make Oila TV the home screen and trim background load on the Premier TV6681 (1 GB RAM).
# Usage: tv-tune.sh <ip:port> apply|rollback
# Every change is reversible; "rollback" restores the stock launcher and settings.
set -euo pipefail
S=${1:?ip:port}; MODE=${2:?apply|rollback}
adb -s "$S" root >/dev/null; sleep 3; adb connect "$S" >/dev/null
A="adb -s $S shell"

HOME_NEW=uz.oila.tv/.HomeActivity
HOME_STOCK=com.ktc.mslauncher/.MainActivity
# Background apps with no user-facing value here:
#   zeasn asdprovider/general = ad + telemetry delivery for the stock launcher
#   ktc.serialport            = factory auto-test service
#   ktc.mslauncher            = stock launcher (replaced)
#   systemupdate.service      = OTA client (no updates published for this model)
#   ktc.assistant             = cleaner app (replaced by the Boost screen)
#   ktc.media                 = media player + scanner (replaced by the Gallery screen)
#   airoha / mediatek.autopair = Bluetooth updater / remote pairing (this TV has no Bluetooth)
#   ecloud.emedia             = DLNA renderer
TRIM="com.zeasn.deviceportal.asdprovider.ktc com.zeasn.services.general com.ktc.serialport com.ktc.mslauncher \
android.systemupdate.service com.ktc.assistant com.ktc.media com.airoha.android.bluetoothlegatt \
com.mediatek.autopair com.ecloud.emedia"
ANIM="window_animation_scale transition_animation_scale animator_duration_scale"

case $MODE in
  apply)
    $A cmd package set-home-activity $HOME_NEW
    for p in $TRIM; do $A pm disable-user --user 0 $p; done
    for k in $ANIM; do $A settings put global $k 0; done
    # Android 9 only returns Wi-Fi scan results to apps holding location access.
    $A pm grant uz.oila.tv android.permission.ACCESS_FINE_LOCATION
    $A settings put secure location_providers_allowed +network
    $A settings put secure location_providers_allowed +gps
    # Development-level permissions the Language / Date & time / Gallery screens use.
    $A pm grant uz.oila.tv android.permission.CHANGE_CONFIGURATION
    $A pm grant uz.oila.tv android.permission.WRITE_SECURE_SETTINGS
    $A pm grant uz.oila.tv android.permission.READ_EXTERNAL_STORAGE
    $A appops set uz.oila.tv WRITE_SETTINGS allow
    # Global remote shortcuts (red = YouTube) via the app's key-filtering accessibility service.
    $A settings put secure enabled_accessibility_services uz.oila.tv/uz.oila.tv.KeyService
    $A settings put secure accessibility_enabled 1
    # This firmware drops every remote key until persist.sys.bootanim_exit=1, which only
    # its first-run wizard sets; SurfaceFlinger clears it on each cold boot.
    $A setprop persist.sys.bootanim_exit 1
    ;;
  rollback)
    for p in $TRIM; do $A pm enable $p; done
    $A cmd package set-home-activity $HOME_STOCK
    for k in $ANIM; do $A settings put global $k 1.0; done
    $A settings delete secure enabled_accessibility_services
    $A settings put secure accessibility_enabled 0
    ;;
  *) echo "mode must be apply|rollback" >&2; exit 2 ;;
esac
$A cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
$A pm list packages -d
