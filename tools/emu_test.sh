#!/bin/bash
# Runs inside the CI emulator. Output goes to ./shots (committed to branch "shots" by the workflow).
mkdir -p shots
PKG=com.lowy.safetube.oss
APK=$(ls apk/*.apk | head -1)
adb install -r "$APK" > shots/install.txt 2>&1
adb logcat -c
shot() { adb exec-out screencap -p > "shots/$1.png"; adb shell uiautomator dump /sdcard/$1.xml >/dev/null 2>&1; adb pull /sdcard/$1.xml shots/$1.xml >/dev/null 2>&1; }
# tap(): tap the first node whose text/content-desc contains $2 in dump $1
tapnode() { python3 - "$1" "$2" <<'PY'
import re,sys,subprocess
x=open(sys.argv[1],encoding='utf-8').read(); key=sys.argv[2].lower()
for m in re.finditer(r'<node[^>]*>',x):
    n=m.group(0)
    t=(re.search(r' text="([^"]*)"',n) or [0,''])[1]+(re.search(r'content-desc="([^"]*)"',n) or [0,''])[1]
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if key in t.lower() and b:
        a,bb,c,d=map(int,b.groups()); print((a+c)//2,(bb+d)//2); sys.exit(0)
print("NONE")
PY
}
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > shots/launch.txt 2>&1
sleep 25; shot 01_home_25s
sleep 60; shot 02_home_85s
# open drawer
adb shell input tap 50 100; sleep 3; shot 03_drawer
# open freeCodeCamp channel from the drawer
read X Y <<< "$(tapnode shots/03_drawer.xml freecodecamp)"; echo "fcc tap: $X $Y" > shots/steps.txt
if [ "$X" != "NONE" ]; then
  adb shell input tap $X $Y; sleep 25; shot 04_channel
  for i in 1 2 3 4 5 6; do adb shell input swipe 540 1500 540 600 400; sleep 3; shot 05_scroll_$i; done
fi
# keyword check: titles of videos that must NOT appear
cat shots/0[45]_*.xml 2>/dev/null > shots/all_text.xml
for k in "Gemini 4 Argon" "Dijkstra" "Apple Engineer" "Shaders" "Hong Kong Hackathon" "Zapier" "Godot Game" "Fine-Tuning Large" "Transformer"; do
  echo "$k: $(grep -ci "$k" shots/all_text.xml)" >> shots/blocked_title_check.txt
done
rm -f shots/all_text.xml
# try to open the first video card
read X Y <<< "$(tapnode shots/04_channel.xml ' ')"
adb shell input keyevent KEYCODE_BACK 2>/dev/null
adb logcat -d -v time > shots/logcat_full.txt 2>&1
grep -E " E/| F/|AndroidRuntime|SafeTube|SkyTube|NewPipe|Whitelist|Screen" shots/logcat_full.txt | grep -v "mdi\|MDD" | tail -300 > shots/logcat_filtered.txt
rm shots/logcat_full.txt
ls shots
