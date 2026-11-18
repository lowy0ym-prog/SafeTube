curl -s -A "Mozilla/5.0" "https://www.youtube.com/feeds/videos.xml?channel_id=UC8butISFwT-Wl7EV0hUK0BQ" -o rss.xml
echo "RSS bytes: $(wc -c < rss.xml) entries: $(grep -o '<entry>' rss.xml | wc -l) desc: $(grep -o '<media:description' rss.xml | wc -l)"
python3 - <<'PY'
import re
x=open('rss.xml',encoding='utf-8').read()
for e in re.findall(r'<entry>.*?</entry>',x,flags=re.S):
    vid=re.search(r'<yt:videoId>(.*?)</yt:videoId>',e).group(1)
    t=re.search(r'<title>(.*?)</title>',e).group(1)[:55]
    d=(re.search(r'<media:description>(.*?)</media:description>',e,flags=re.S) or [0,''])[1]
    credit=re.search(r'(?:created|made|taught|hosted)\s+by[^\n]{0,60}',d,flags=re.I)
    print(vid,'|',t,'|',credit.group(0) if credit else '-')
PY
