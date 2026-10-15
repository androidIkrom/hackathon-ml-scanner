#!/usr/bin/env bash
# Measures the camera screens on the phone: runs PerfTour (Live, Reader, Find, Walk, Live again) while the phone
# samples the app's memory, CPU and the battery's heat every 3 s, then prints one table per screen.
#
#   tools/perf/tour.sh <adb serial> [seconds per screen] [out dir] [CPU|GPU]
#
# The app and its test APK must be installed. The app's voice (music stream) is muted for the run and set back after.
set -euo pipefail
serial="$1"
secs="${2:-25}"
out="${3:-build/perf}"
compute="${4:-}"
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$out"
adb="adb -s $serial"

volume=$($adb shell cmd media_session volume --stream 3 --get 2>/dev/null | sed -n 's/.*volume is \([0-9]*\).*/\1/p' || true)
$adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1 || true
restore() {
    if [ -n "$volume" ]; then $adb shell cmd media_session volume --stream 3 --set "$volume" >/dev/null 2>&1 || true; fi
}
trap restore EXIT

MSYS_NO_PATHCONV=1 $adb push "$(cygpath -m "$here/sample.sh" 2>/dev/null || echo "$here/sample.sh")" /data/local/tmp/nungil-sample.sh >/dev/null
$adb logcat -c
$adb logcat -v time > "$out/logcat.txt" &
logger=$!
# The phone's loop ends on its own a little after the tour: killing adb here would not stop it there.
MSYS_NO_PATHCONV=1 $adb shell sh /data/local/tmp/nungil-sample.sh $(( (6 + 5 * secs + 5 * 4 + 30) / 3 )) > "$out/samples.txt" &
sampler=$!

MSYS_NO_PATHCONV=1 $adb shell am instrument -w -e class com.nungil.bench.PerfTour -e secs "$secs" ${compute:+-e compute "$compute"} \
    com.nungil.test/androidx.test.runner.AndroidJUnitRunner | tail -3
sleep 2
kill $sampler $logger 2>/dev/null || true

python "$here/report.py" "$out/logcat.txt" "$out/samples.txt"
