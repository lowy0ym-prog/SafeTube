#!/bin/bash
# Runs inside the CI emulator. Output goes to ./shots (committed to branch "shots" by the workflow).
mkdir -p shots
PKG=com.lowy.safetube.oss
APK=$(ls apk/*.apk | head -1)
adb install -r "$APK" > shots/install.txt 2>&1
adb logcat -c
shot() { adb exec-out screencap -p > "shots/$1.png"; adb shell uiautomator dump /sdcard/$1.xml >/dev/null 2>&1; adb pull /sdcard/$1.xml shots/$1.xml >/dev/null 2>&1; }
tapnode() { python3 - "$1" "$2" "${3:-9999}" "${4:-0}" <<'PY'
import re,sys
x=open(sys.argv[1],encoding='utf-8').read(); key=sys.argv[2].lower()
for m in re.finditer(r'<node[^>]*>',x):
    n=m.group(0)
    t=(re.search(r' text="([^"]*)"',n) or [0,''])[1]+(re.search(r'content-desc="([^"]*)"',n) or [0,''])[1]
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if key in t.lower() and b and int(b.group(1))<int(sys.argv[3]) and int(b.group(1))>=int(sys.argv[4]):
        a,bb,c,d=map(int,b.groups()); print((a+c)//2,(bb+d)//2); sys.exit(0)
print("NONE")
PY
}
# clickable rows of a dump: "x y label"
rows() { python3 - "$1" <<'PY'
import re,sys
x=open(sys.argv[1],encoding='utf-8').read()
seen=set()
for m in re.finditer(r'<node[^>]*>',x):
    n=m.group(0)
    if 'clickable="true"' not in n: continue
    b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
    if not b: continue
    a,bb,c,d=map(int,b.groups())
    if c-a<5 or d-bb<5: continue
    # label: own text or desc, else text of children inside bounds
    t=(re.search(r' text="([^"]*)"',n) or [0,''])[1] or (re.search(r'content-desc="([^"]*)"',n) or [0,''])[1]
    if not t:
        for m2 in re.finditer(r'<node[^>]*text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
            tt,a2,b2,c2,d2=m2.group(1),*map(int,m2.groups()[1:])
            if a2>=a and b2>=bb and c2<=c and d2<=d: t=tt; break
    key=(a,bb)
    if key in seen: continue
    seen.add(key); print((a+c)//2,(bb+d)//2,t.replace(' ','_') or 'unlabeled')
PY
}
titles() { cat shots/$1_*.xml 2>/dev/null | grep -o ' text="[^"]\{12,\}"' | sort -u; }

visit_channel() { # $1 = tag
  adb logcat -c
  adb shell am force-stop $PKG; sleep 2
  adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 20
  adb shell input tap 28 52; sleep 3; shot $1_drawer
  read X Y <<< "$(tapnode shots/$1_drawer.xml freecodecamp 400 50)"; echo "$1 fcc tap: $X $Y" >> shots/steps.txt
  [ "$X" = "NONE" ] && return
  adb shell input tap $X $Y; sleep 25; shot $1_channel_0
  for i in 1 2 3 4 5 6 7 8; do adb shell input swipe 160 560 160 160 400; sleep 3; shot $1_channel_$i; done
  titles $1_channel > shots/$1_titles.txt
  adb logcat -d -v brief 2>/dev/null | grep SAFETUBE_FEED > shots/$1_feed.txt
}

# ---- Phase 1: baseline with default rules, first launch (also first-run seeding)
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > shots/launch.txt 2>&1
sleep 25; shot 01_home_25s
visit_channel base
shot 09_base_last

# ---- Phase 2: verification of the "named person" rule on the newest videos (RSS descriptions).
# Test name "beau" appears in credits ("created by @beau") of several recent freeCodeCamp videos.
adb shell "run-as $PKG sh -c 'echo beau > files/extra_blocked_people.txt'" > shots/hook.txt 2>&1
visit_channel beau
diff <(sort shots/base_titles.txt) <(sort shots/beau_titles.txt) > shots/titles_diff.txt
echo "titles base: $(wc -l < shots/base_titles.txt)  with-test-name: $(wc -l < shots/beau_titles.txt)" > shots/verify_summary.txt
echo "removed by test name (lines starting <):" >> shots/verify_summary.txt
grep '^<' shots/titles_diff.txt >> shots/verify_summary.txt
adb shell "run-as $PKG rm -f files/extra_blocked_people.txt"

# keyword check on baseline: titles of videos that must NOT appear
for k in "MCP Security" "Hybrid Search" "Gemini 4 Argon" "Dijkstra" "Apple Engineer" "Shaders" "Hong Kong Hackathon" "Zapier" "Godot Game" "Fine-Tuning Large" "Transformer"; do
  echo "$k: $(grep -ci "$k" shots/base_titles.txt)" >> shots/blocked_title_check.txt
done

# ---- Phase 3: player (playback itself is expected to fail: YouTube 403s the CI IP)
adb shell am force-stop $PKG; sleep 2
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1; sleep 15
adb shell input tap 28 52; sleep 3
read X Y <<< "$(tapnode shots/base_drawer.xml "professor messer" 400 50)"
adb shell input tap $X $Y; sleep 20; shot 20_messer
adb shell input tap 160 400; sleep 10; shot 21_player_10s
adb shell input tap 160 100; sleep 2; shot 22_player_controls
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2

# ---- Phase 4: walk every settings / menu screen
adb shell am force-stop $PKG; sleep 2
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1; sleep 15
shot 30_main
adb shell input tap 300 52; sleep 2; shot 31_overflow
rows shots/31_overflow.xml > shots/31_rows.txt
# Administrator screen and Watched Video Cleaner (only look, no changes)
read AX AY <<< "$(tapnode shots/31_overflow.xml administrator)"; echo "admin tap: $AX $AY" > shots/walk_admin.txt
adb shell input tap $AX $AY; sleep 3; shot 35_admin
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap 300 52; sleep 2
read WX WY <<< "$(tapnode shots/31_overflow.xml cleaner)"
adb shell input tap $WX $WY; sleep 3; shot 36_cleaner
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input tap 28 52; sleep 2; shot 32_drawer; rows shots/32_drawer.xml > shots/32_rows.txt
adb shell input swipe 100 500 100 100 300; sleep 1; shot 33_drawer_scrolled
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input tap 300 52; sleep 2
read X Y <<< "$(tapnode shots/31_overflow.xml preferences)"
echo "settings tap: $X $Y" > shots/walk.txt
if [ "$X" != "NONE" ]; then
  adb shell input tap $X $Y; sleep 3; shot 40_settings
  rows shots/40_settings.xml > shots/40_rows.txt
  n=0
  while read RX RY RL; do
    case "$RL" in Navigate_up) continue;; esac
    n=$((n+1)); [ $n -gt 14 ] && break
    echo "tap $n: $RX $RY $RL" >> shots/walk.txt
    adb shell input tap $RX $RY; sleep 2; shot 41_settings_item_$n
    adb shell input keyevent KEYCODE_BACK; sleep 1
    shot 41_after_back_$n
    # if a dialog / sub screen is still open, back once more to return to the list
    if ! grep -qi "$(head -1 shots/40_settings.xml | grep -o 'text="[^"]*"' | head -1 | cut -d'"' -f2 | head -c 5)" shots/41_after_back_$n.xml 2>/dev/null; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi
  done < shots/40_rows.txt
fi

adb logcat -d -v time > shots/logcat_full.txt 2>&1
grep -E " E/| F/|AndroidRuntime|FATAL|SAFETUBE|Whitelist" shots/logcat_full.txt | grep -v "mdi\|MDD" | tail -300 > shots/logcat_filtered.txt
rm shots/logcat_full.txt
ls shots
