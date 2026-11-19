/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.businessobjects.whitelist;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import free.rm.skytube.businessobjects.Logger;

/**
 * Reads the public RSS feed of an approved channel (newest 15 videos, with full descriptions) and
 * records a BLOCK verdict for every video whose title/description names a blocked person or
 * credits a woman. One small request per channel, cached, and it never touches the watch endpoint
 * (which is what makes YouTube show "confirm you're not a bot").
 */
public final class FeedScreen {
    private static final long TTL_MS = 20L * 60 * 1000;
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();
    private static final Pattern ENTRY = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("<yt:videoId>([^<]+)</yt:videoId>");
    private static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>");
    private static final Pattern DESC = Pattern.compile("<media:description>([^<]*)</media:description>");

    /** Result of the last run, for the emulator test log. */
    public static volatile String lastSummary = "";

    private FeedScreen() { }

    /** Blocks up to ~6 s. Safe to call from a background thread only. */
    public static void ensure(String channelRef) {
        Whitelist w = Whitelists.get();
        if (w == null || channelRef == null || !w.isChannelApproved(channelRef) || !w.isDeep(channelRef)) return;
        String id = channelRef.replaceAll(".*/channel/", "");
        if (!id.startsWith("UC")) return;
        long now = System.currentTimeMillis();
        Long last = LAST.get(id);
        if (last != null && now - last < TTL_MS) return;
        LAST.put(id, now);
        try {
            String xml = fetch("https://www.youtube.com/feeds/videos.xml?channel_id=" + id);
            int entries = 0, blocked = 0;
            Matcher m = ENTRY.matcher(xml);
            while (m.find()) {
                String e = m.group(1);
                Matcher mi = ID.matcher(e);
                if (!mi.find()) continue;
                String vid = mi.group(1);
                Matcher mt = TITLE.matcher(e);
                Matcher md = DESC.matcher(e);
                String title = mt.find() ? unescape(mt.group(1)) : "";
                String desc = md.find() ? unescape(md.group(1)) : "";
                entries++;
                w.noteDeepSeen(vid);
                Screening.Verdict v = Screening.classify(title, desc, w.isStrict(id));
                w.recordVerdict(vid, v); // BLOCK, or ALLOW: these 15 are fully checked
                if (v == Screening.Verdict.BLOCK) blocked++;
            }
            w.flushVerdicts();
            w.flushDeepSeen();
            lastSummary = id + " entries=" + entries + " blocked=" + blocked;
            Logger.i("FeedScreen", "SAFETUBE_FEED %s", lastSummary);
        } catch (Exception ex) {
            LAST.remove(id); // retry next time
            Logger.i("FeedScreen", "SAFETUBE_FEED failed %s: %s", id, ex);
        }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(5000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SafeTube");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&");
    }

    public static List<String> channelsSeen() { return new ArrayList<>(LAST.keySet()); }
}
