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
TRIM="com.zeasn.deviceportal.asdprovider.ktc com.zeasn.services.general com.ktc.serialport com.ktc.mslauncher"
ANIM="window_animation_scale transition_animation_scale animator_duration_scale"

case $MODE in
  apply)
    $A cmd package set-home-activity $HOME_NEW
    for p in $TRIM; do $A pm disable-user --user 0 $p; done
    for k in $ANIM; do $A settings put global $k 0; done
    # Android 9 only returns Wi-Fi scan results to apps holding location access.
    $A pm grant uz.oila.tv android.permission.ACCESS_FINE_LOCATION
    # This firmware drops every remote key until persist.sys.bootanim_exit=1, which only
    # its first-run wizard sets; SurfaceFlinger clears it on each cold boot.
    $A setprop persist.sys.bootanim_exit 1
    ;;
  rollback)
    for p in $TRIM; do $A pm enable $p; done
    $A cmd package set-home-activity $HOME_STOCK
    for k in $ANIM; do $A settings put global $k 1.0; done
    ;;
  *) echo "mode must be apply|rollback" >&2; exit 2 ;;
esac
$A cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
$A pm list packages -d
