#!/bin/bash
# Runs inside the CI emulator. Output goes to ./shots (committed to branch "shots" by the workflow).
mkdir -p shots
PKG=com.lowy.safetube.oss
APK=$(ls apk/*.apk | head -1)
adb install -r "$APK" > shots/install.txt 2>&1
adb logcat -c
shot() { adb exec-out screencap -p > "shots/$1.png"; adb shell uiautomator dump /sdcard/$1.xml >/dev/null 2>&1; adb pull /sdcard/$1.xml shots/$1.xml >/dev/null 2>&1; }
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > shots/launch.txt 2>&1
sleep 30; shot 01_home_30s
sleep 60; shot 02_home_90s
python3 - <<'PY' > shots/tap.txt
import re
x=open('shots/02_home_90s.xml',encoding='utf-8').read()
m=re.search(r'resource-id="com.lowy.safetube.oss:id/thumbnail_image_view"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x)
if m:
    a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2)
else:
    print("NOCARD")
PY
cat shots/tap.txt
read X Y < shots/tap.txt
if [ "$X" != "NOCARD" ]; then
  adb shell input tap $X $Y
  sleep 5;  shot 03_player_5s
  sleep 25; shot 04_player_30s
  adb shell input tap 540 400; sleep 2; shot 05_player_controls
fi
adb logcat -d -v time > shots/logcat_full.txt 2>&1
grep -E " E/| F/|AndroidRuntime|SafeTube|SkyTube|NewPipe|Whitelist|Screen" shots/logcat_full.txt | tail -400 > shots/logcat_filtered.txt
rm shots/logcat_full.txt
ls -la shots
