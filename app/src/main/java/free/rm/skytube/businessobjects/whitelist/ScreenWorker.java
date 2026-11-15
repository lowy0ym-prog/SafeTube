/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.businessobjects.whitelist;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.newpipe.ChannelId;
import free.rm.skytube.businessobjects.YouTube.newpipe.NewPipeService;
import free.rm.skytube.businessobjects.YouTube.newpipe.VideoPagerWithChannel;

/**
 * Background screening. Videos with no verdict are queued (by {@link Whitelist} when a strict
 * channel's video is hidden, or by {@link Whitelists#noteListing}). One slow worker reads each
 * video's description and stores the verdict. New uploads are picked up the same way: the first
 * time they appear in a feed or channel list they are hidden, queued, screened and, if clean,
 * shown on the next refresh.
 */
public final class ScreenWorker {
    private static final Map<String, String> QUEUE = new LinkedHashMap<>(); // videoId -> channelId
    private static final Map<String, Integer> FAILURES = new HashMap<>();
    private static Thread worker;
    private static volatile boolean scanning = false;

    private ScreenWorker() { }

    public static synchronized void enqueue(String channelId, String videoId) {
        if (videoId == null || QUEUE.containsKey(videoId)) return;
        if (FAILURES.getOrDefault(videoId, 0) >= 3) return;
        QUEUE.put(videoId, channelId);
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(ScreenWorker::run, "safetube-screen");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static synchronized String[] next() {
        if (QUEUE.isEmpty()) return null;
        String id = QUEUE.keySet().iterator().next();
        String ch = QUEUE.remove(id);
        return new String[]{id, ch};
    }

    private static void run() {
        Whitelist w = Whitelists.get();
        int idle = 0;
        while (w != null && idle < 20) {
            String[] item = next();
            if (item == null) {
                w.flushVerdicts();
                idle++;
                sleep(1000);
                continue;
            }
            idle = 0;
            if (w.getVerdict(item[0]) != Screening.Verdict.UNKNOWN) continue;
            try {
                String[] d = NewPipeService.get().fetchForScreening(item[0]);
                String channel = d[0] != null ? d[0] : item[1];
                if (!w.isChannelApproved(channel)) {
                    w.recordVerdict(item[0], Screening.Verdict.BLOCK); // not from an approved channel
                } else {
                    w.recordVerdict(item[0], Screening.classify(d[1], d[2], w.isStrict(channel)));
                }
            } catch (Exception e) {
                synchronized (ScreenWorker.class) {
                    FAILURES.merge(item[0], 1, Integer::sum);
                }
                Logger.e(ScreenWorker.class, "screening failed for " + item[0] + ": " + e.getMessage(), e);
            }
            sleep(1500); // be gentle with the server
        }
        w.flushVerdicts();
    }

    /** Walks every page of every strict channel so all existing videos get queued and screened. */
    public static void startFullScan() {
        if (scanning) return;
        scanning = true;
        Thread t = new Thread(() -> {
            try {
                Whitelist w = Whitelists.get();
                if (w == null) return;
                for (String id : w.getApprovedChannels().keySet()) {
                    if (!w.isStrict(id)) continue;
                    try {
                        VideoPagerWithChannel pager = NewPipeService.get().getChannelPager(new ChannelId(id));
                        int pages = 0;
                        // Each page runs through the filter, which hides + queues every unknown video.
                        do {
                            pager.getNextPage();
                            sleep(1000);
                        } while (pager.isHasNextPage() && ++pages < 200);
                    } catch (Exception e) {
                        Logger.e(ScreenWorker.class, "full scan failed for " + id + ": " + e.getMessage(), e);
                    }
                }
            } finally {
                scanning = false;
            }
        }, "safetube-scan");
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
