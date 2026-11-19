/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.businessobjects.whitelist;

import android.content.Context;
import android.net.ConnectivityManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.newpipe.ChannelId;
import free.rm.skytube.businessobjects.YouTube.newpipe.NewPipeService;
import free.rm.skytube.businessobjects.YouTube.newpipe.VideoPagerWithChannel;

/**
 * Slow, gentle checker for videos of "deep" channels (freeCodeCamp). A video of such a channel
 * stays hidden until its description has been read and passed the rules.
 *
 * Two queues: videos the user is about to see (checked first, on any network) and the background
 * fill of older videos (only on an unmetered connection such as Wi-Fi, and switchable off in the
 * admin screen). One video at a time with a pause between requests. If YouTube pushes back
 * (bot check / HTTP 403 / 429) everything stops for 30 minutes.
 */
public final class ScreenWorker {
    private static final long PACE_MS = 8000;
    private static final long BACKOFF_MS = 30L * 60 * 1000;

    private static final Map<String, String> QUEUE = new LinkedHashMap<>();    // wanted now: videoId -> channelId
    private static final Map<String, String> BG_QUEUE = new LinkedHashMap<>(); // background fill
    private static final Map<String, Integer> FAILURES = new HashMap<>();
    private static final Random RND = new Random();
    private static final ThreadLocal<Boolean> BACKGROUND_CONTEXT = new ThreadLocal<>();
    private static Thread worker;
    private static volatile boolean scanning = false;
    private static volatile long pausedUntil = 0;
    /** Last state line, shown in the admin screen. */
    public static volatile String status = "";

    private ScreenWorker() { }

    /** Called when a hidden video is about to be shown (list/search/feed). */
    public static synchronized void enqueue(String channelId, String videoId) {
        if (videoId == null) return;
        if (Boolean.TRUE.equals(BACKGROUND_CONTEXT.get())) {
            enqueueBackground(channelId, videoId);
            return;
        }
        if (QUEUE.containsKey(videoId)) return;
        if (FAILURES.getOrDefault(videoId, 0) >= 3) return;
        BG_QUEUE.remove(videoId);
        QUEUE.put(videoId, channelId);
        ensureWorker();
    }

    public static synchronized void enqueueBackground(String channelId, String videoId) {
        if (videoId == null || QUEUE.containsKey(videoId) || BG_QUEUE.containsKey(videoId)) return;
        if (FAILURES.getOrDefault(videoId, 0) >= 3) return;
        BG_QUEUE.put(videoId, channelId);
        ensureWorker();
    }

    private static void ensureWorker() {
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(ScreenWorker::run, "safetube-screen");
            worker.setDaemon(true);
            worker.start();
        }
    }

    static boolean unmetered() {
        try {
            Context c = SkyTubeApp.getContext();
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected() && !cm.isActiveNetworkMetered();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static synchronized String[] next(boolean bgAllowed) {
        if (!QUEUE.isEmpty()) {
            String id = QUEUE.keySet().iterator().next();
            return new String[]{id, QUEUE.remove(id), "q"};
        }
        if (bgAllowed && !BG_QUEUE.isEmpty()) {
            String id = BG_QUEUE.keySet().iterator().next();
            return new String[]{id, BG_QUEUE.remove(id), "b"};
        }
        return null;
    }

    private static synchronized boolean bgPending() { return !BG_QUEUE.isEmpty(); }

    private static synchronized boolean shouldExit() {
        if (QUEUE.isEmpty() && BG_QUEUE.isEmpty()) {
            worker = null;
            return true;
        }
        return false;
    }

    private static void run() {
        Whitelist w = Whitelists.get();
        int idle = 0;
        while (w != null) {
            if (System.currentTimeMillis() < pausedUntil) {
                status = "Paused: YouTube asked to slow down. Resumes by itself.";
                sleep(30_000);
                continue;
            }
            boolean bgAllowed = w.isBackgroundCheckEnabled() && unmetered();
            String[] item = next(bgAllowed);
            if (item == null) {
                w.flushVerdicts();
                w.flushDeepSeen();
                if (bgPending()) {
                    status = w.isBackgroundCheckEnabled() ? "Waiting for Wi-Fi." : "Background check is switched off.";
                    sleep(30_000);
                    continue;
                }
                if (++idle > 20 && shouldExit()) return;
                sleep(1000);
                continue;
            }
            idle = 0;
            if (w.getVerdict(item[0]) != Screening.Verdict.UNKNOWN) continue;
            boolean ok = true;
            try {
                String[] d = NewPipeService.get().fetchForScreening(item[0]);
                String channel = d[0] != null ? d[0] : item[1];
                if (!w.isChannelApproved(channel)) {
                    w.recordVerdict(item[0], Screening.Verdict.BLOCK); // not from an approved channel
                } else {
                    w.recordVerdict(item[0], Screening.classify(d[1], d[2], w.isStrict(channel)));
                }
                status = "Checking videos…";
            } catch (Exception e) {
                ok = false;
                String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                if (m.contains("not a bot") || m.contains("confirm you") || m.contains("403")
                        || m.contains("429") || m.contains("too many") || m.contains("sign in")) {
                    pausedUntil = System.currentTimeMillis() + BACKOFF_MS;
                    synchronized (ScreenWorker.class) {
                        // put it back at the front of its queue, not counted as a failure
                        Map<String, String> q = "q".equals(item[2]) ? QUEUE : BG_QUEUE;
                        Map<String, String> copy = new LinkedHashMap<>(q);
                        q.clear();
                        q.put(item[0], item[1]);
                        q.putAll(copy);
                    }
                } else {
                    synchronized (ScreenWorker.class) {
                        FAILURES.merge(item[0], 1, Integer::sum);
                    }
                }
                Logger.e(ScreenWorker.class, "screening failed for " + item[0] + ": " + e.getMessage(), e);
            }
            if (ok) Logger.i("ScreenWorker", "SAFETUBE_CHECKED %s", item[0]);
            sleep(PACE_MS + RND.nextInt(2000)); // one video at a time, with a pause
        }
    }

    /**
     * Background fill for deep channels: walks the channel's video pages (cheap list requests, no
     * watch pages), remembers every video id, and queues the unchecked ones for the slow checker.
     * Once the walk has finished it is not repeated; later uploads arrive through the RSS check.
     */
    public static void startBackgroundFill() {
        final Whitelist w = Whitelists.get();
        if (w == null || scanning) return;
        scanning = true;
        Thread t = new Thread(() -> {
            BACKGROUND_CONTEXT.set(Boolean.TRUE);
            try {
                if (!w.isBackgroundCheckEnabled()) return;
                if (w.isDeepScanDone()) {
                    for (String v : w.deepUnchecked()) enqueueBackground(null, v);
                    return;
                }
                while (!unmetered()) {
                    if (!w.isBackgroundCheckEnabled()) return;
                    sleep(30_000);
                }
                boolean finished = true;
                for (String id : w.getApprovedChannels().keySet()) {
                    if (!w.isDeep(id)) continue;
                    try {
                        VideoPagerWithChannel pager = NewPipeService.get().getChannelPager(new ChannelId(id));
                        int pages = 0;
                        do {
                            // Each page goes through VideoPager.extract: it remembers the ids and queues unchecked ones.
                            pager.getNextPage();
                            sleep(3000);
                        } while (pager.isHasNextPage() && ++pages < 400 && w.isBackgroundCheckEnabled());
                        if (pager.isHasNextPage()) finished = false;
                    } catch (Exception e) {
                        finished = false;
                        Logger.e(ScreenWorker.class, "background fill failed for " + id + ": " + e.getMessage(), e);
                    }
                }
                w.flushDeepSeen();
                if (finished) w.setDeepScanDone(true);
            } finally {
                scanning = false;
            }
        }, "safetube-fill");
        t.setDaemon(true);
        t.start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
