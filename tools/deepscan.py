#!/usr/bin/env python3
"""Collects title + full description of every video of one channel, slowly, and resumes where it stopped.
Lists ids with yt-dlp (list pages only), reads descriptions from a public Invidious API (one video at a time,
a pause between requests). Output: data/fcc_meta.jsonl (one JSON object per line), data/progress.txt."""
import html, re, json, os, random, subprocess, sys, time, urllib.request, urllib.error

CH = "UC8butISFwT-Wl7EV0hUK0BQ"
BASE = os.environ.get("INV_BASE", "https://yt.chocolatemoo53.com")
OUT = "data/fcc_meta2.jsonl"
IDS = "data/fcc_ids.txt"
LOG = "data/progress.txt"
MAX_SECONDS = int(os.environ.get("MAX_SECONDS", str(5 * 3600 + 15 * 60)))
t0 = time.time()

def log(m):
    line = time.strftime("%H:%M:%S ") + m
    print(line, flush=True)
    with open(LOG, "a") as f:
        f.write(line + "\n")

def commit(msg):
    subprocess.run(["git", "add", "data"], check=False)
    subprocess.run(["git", "-c", "user.name=scan", "-c", "user.email=scan@example.invalid", "commit", "-qm", msg + " [skip ci]"], check=False)
    subprocess.run(["git", "pull", "-q", "--rebase", "origin", "deepscan"], check=False)
    subprocess.run(["git", "push", "-q", "origin", "HEAD:deepscan"], check=False)

def to_text(h):
    h = re.sub(r"(?i)<br\s*/?>", "\n", h or "")
    h = re.sub(r"<[^>]+>", "", h)
    return html.unescape(h).strip()

def get(url, timeout=40):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; safetube-scan)"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read().decode("utf-8", "replace")

def list_ids():
    if os.path.exists(IDS) and len(open(IDS).read().split()) > 100:
        return open(IDS).read().split()
    ids = []
    for tab in ("videos",):
        try:
            p = subprocess.run(["yt-dlp", "--flat-playlist", "--print", "id", "--sleep-requests", "1",
                                f"https://www.youtube.com/channel/{CH}/{tab}"], capture_output=True, text=True, timeout=1500)
            got = [x.strip() for x in p.stdout.split() if len(x.strip()) == 11]
            log(f"yt-dlp {tab}: {len(got)} ids; rc={p.returncode} err={p.stderr[-300:]!r}")
            ids += got
        except Exception as e:
            log(f"yt-dlp failed: {e}")
    if len(ids) < 50:  # fallback: Invidious playlist of the channel's uploads
        pl = "UU" + CH[2:]
        for page in range(1, 40):
            try:
                st, body = get(f"{BASE}/api/v1/playlists/{pl}?page={page}")
                vids = json.loads(body).get("videos", [])
                log(f"invidious playlist page {page}: {len(vids)}")
                if not vids: break
                ids += [v["videoId"] for v in vids]
            except Exception as e:
                log(f"invidious playlist page {page} failed: {e}")
                break
            time.sleep(3)
    seen = set(); out = []
    for i in ids:
        if i not in seen: seen.add(i); out.append(i)
    open(IDS, "w").write("\n".join(out) + "\n")
    commit(f"ids: {len(out)}")
    return out

def main():
    os.makedirs("data", exist_ok=True)
    ids = list_ids()
    log(f"total ids: {len(ids)}")
    done = set()
    if os.path.exists(OUT):
        for l in open(OUT):
            try: done.add(json.loads(l)["id"])
            except Exception: pass
    todo = [i for i in ids if i not in done]
    log(f"already done: {len(done)}; to do: {len(todo)}")
    fails = 0; n = 0
    for vid in todo:
        if time.time() - t0 > MAX_SECONDS:
            log("time budget reached, stopping (rerun to continue)"); break
        rec = None
        try:
            st, body = get(f"{BASE}/api/v1/videos/{vid}?fields=videoId,title,description,descriptionHtml,authorId,error")
            d = json.loads(body)
            if d.get("error"):
                rec = {"id": vid, "error": str(d["error"])[:200]}
            else:
                rec = {"id": vid, "title": d.get("title", ""), "description": to_text(d.get("description") or d.get("descriptionHtml", "")), "authorId": d.get("authorId", "")}
            fails = 0
        except urllib.error.HTTPError as e:
            fails += 1
            log(f"{vid}: HTTP {e.code}")
            if e.code in (403, 429, 500, 502, 503):
                time.sleep(min(600, 60 * fails))
            elif e.code == 404:
                rec = {"id": vid, "error": "404"}
        except Exception as e:
            fails += 1
            log(f"{vid}: {type(e).__name__} {e}")
            time.sleep(min(600, 30 * fails))
        if rec:
            with open(OUT, "a") as f: f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            n += 1
            if n % 25 == 0:
                empty = sum(1 for l in open(OUT) if '"description": ""' in l)
                log(f"progress: {len(done) + n}/{len(ids)} (empty descriptions so far: {empty})")
            if n % 25 == 0: commit(f"progress {len(done) + n}")
        if fails >= 12:
            log("too many consecutive failures, stopping"); break
        time.sleep(2.0 + random.random() * 1.5)
    log(f"finished loop: {len(done) + n}/{len(ids)}")
    commit(f"final {len(done) + n}")

main()
# rerun to fill the few videos that failed
