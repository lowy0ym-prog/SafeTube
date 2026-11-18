set -x
pip install -q yt-dlp
mkdir -p out
curl -s "https://www.youtube.com/feeds/videos.xml?channel_id=UC8butISFwT-Wl7EV0hUK0BQ" -o out/rss.xml; wc -c out/rss.xml; grep -c "<entry>" out/rss.xml; grep -c "media:description" out/rss.xml
yt-dlp --flat-playlist --playlist-end 400 --print id "https://www.youtube.com/channel/UC8butISFwT-Wl7EV0hUK0BQ/videos" > out/ids.txt; wc -l out/ids.txt
head -12 out/ids.txt | while read v; do yt-dlp --skip-download --no-warnings --print "%(id)s|%(title).50s|%(description).90s" "https://www.youtube.com/watch?v=$v" 2>&1 | head -2; sleep 1; done > out/desc_test.txt; cat out/desc_test.txt
