import json, os, subprocess, sys, urllib.request, io
from PIL import Image, ImageDraw, ImageFont

CHANNELS = json.loads(os.environ["CHANNELS"])  # {"name": [channel_id, count]}
OUT = "out"
os.makedirs(OUT, exist_ok=True)
font = ImageFont.load_default(size=15)
W, H, LABEL = 320, 180, 22
COLS, ROWS = 4, 6
for name, (cid, count) in CHANNELS.items():
    r = subprocess.run(["yt-dlp", "--flat-playlist", "--playlist-end", str(count), "-J",
                        f"https://www.youtube.com/channel/{cid}/videos"], capture_output=True, text=True)
    try:
        items = json.loads(r.stdout)["entries"]
    except Exception:
        print(name, "LIST FAILED", r.stderr[-400:])
        continue
    vids = [(e["id"], e.get("title", "")) for e in items if e and e.get("id")]
    print(name, len(vids), "videos")
    per = COLS * ROWS
    index = []
    for s in range(0, len(vids), per):
        sheet = Image.new("RGB", (COLS * W, ROWS * (H + LABEL)), "white")
        d = ImageDraw.Draw(sheet)
        for k, (vid, title) in enumerate(vids[s:s + per]):
            n = s + k + 1
            x, y = (k % COLS) * W, (k // COLS) * (H + LABEL)
            try:
                data = urllib.request.urlopen(f"https://i.ytimg.com/vi/{vid}/mqdefault.jpg", timeout=20).read()
                im = Image.open(io.BytesIO(data)).convert("RGB").resize((W, H))
                sheet.paste(im, (x, y))
            except Exception as e:
                d.rectangle((x, y, x + W, y + H), fill="gray")
            d.text((x + 2, y + H + 2), f"#{n} {vid}", fill="black", font=font)
            index.append({"n": n, "id": vid, "title": title})
        sheet.save(f"{OUT}/{name}_{s // per + 1:02d}.jpg", quality=72)
    json.dump(index, open(f"{OUT}/{name}_index.json", "w"), indent=0)
