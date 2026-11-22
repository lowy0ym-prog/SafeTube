import os, json, sys, urllib.request, concurrent.futures as cf
import cv2, numpy as np

ids = open("data/fcc_ids.txt").read().split()
os.makedirs("thumbs", exist_ok=True); os.makedirs("out", exist_ok=True)

def dl(i):
    p = f"thumbs/{i}.jpg"
    if os.path.exists(p): return i, True
    for q in ("hqdefault",):
        try:
            req = urllib.request.Request(f"https://i.ytimg.com/vi/{i}/{q}.jpg", headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=30) as r: open(p, "wb").write(r.read())
            return i, True
        except Exception as e:
            err = str(e)
    return i, False

with cf.ThreadPoolExecutor(6) as ex:
    res = list(ex.map(dl, ids))
ok = [i for i, g in res if g]
print("downloaded", len(ok), "of", len(ids), flush=True)

det = [cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml"),
       cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_profileface.xml")]
face, noface = [], []
tiles = {}
for i in ok:
    im = cv2.imread(f"thumbs/{i}.jpg")
    if im is None: noface.append(i); continue
    h, w = im.shape[:2]
    im = im[int(h * 0.125):int(h * 0.875), :]  # crop letterbox bars
    tiles[i] = im
    g = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY)
    has = False
    for d in det:
        f = d.detectMultiScale(g, 1.1, 3, minSize=(18, 18))
        if len(f): has = True; break
    (face if has else noface).append(i)
print("with face-ish:", len(face), "without:", len(noface), flush=True)

def sheet(lst, cols, rows, tw, th, prefix):
    per = cols * rows; keys = {}
    for s in range(0, len(lst), per):
        chunk = lst[s:s + per]
        canvas = np.zeros((rows * th, cols * tw, 3), np.uint8)
        for k, i in enumerate(chunk):
            t = cv2.resize(tiles[i], (tw, th))
            cv2.rectangle(t, (0, 0), (34, 20), (0, 0, 0), -1)
            cv2.putText(t, str(k + 1), (3, 15), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 255, 255), 1, cv2.LINE_AA)
            r, c = divmod(k, cols)
            canvas[r * th:(r + 1) * th, c * tw:(c + 1) * tw] = t
        name = f"{prefix}_{s // per + 1:03d}"
        cv2.imwrite(f"out/{name}.jpg", canvas, [cv2.IMWRITE_JPEG_QUALITY, 80])
        keys[name] = chunk
    return keys

keys = {}
keys.update(sheet(face, 5, 4, 320, 180, "face"))
keys.update(sheet(noface, 8, 6, 200, 112, "plain"))
json.dump(keys, open("out/keys.json", "w"))
print("sheets", len(keys))
