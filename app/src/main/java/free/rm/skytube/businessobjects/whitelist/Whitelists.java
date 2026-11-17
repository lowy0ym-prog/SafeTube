/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */
package free.rm.skytube.businessobjects.whitelist;

import android.content.Context;

/** Process-wide access to the single {@link Whitelist}. */
public final class Whitelists {
    private static volatile Whitelist instance;

    private Whitelists() { }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (Whitelists.class) {
                if (instance == null) {
                    Whitelist w = new Whitelist(new PrefsWhitelistStorage(context), System::currentTimeMillis);
                    java.util.Map<String, String> defaults = new java.util.LinkedHashMap<>();
                    defaults.put("UCkefXKtInZ9PLsoGRtml2FQ", "Professor Messer");
                    defaults.put("UC8butISFwT-Wl7EV0hUK0BQ", "freeCodeCamp.org");
                    defaults.put("UC4SVo0Ue36XCfOyb5Lh1viQ", "Bro Code");
                    defaults.put("UCCezIgC97PvUuR4_gbFUs5g", "Corey Schafer");
                    java.util.Set<String> strict = new java.util.HashSet<>();
                    // No channel is strict by default; the admin screen can turn strict on per channel.
                    w.seedDefaultsIfFirstRun(defaults, strict);
                    // Videos checked by eye (thumbnail clearly shows a woman).
                    w.seedBlockedVideosOnce("thumbs1", new java.util.HashSet<>(java.util.Arrays.asList("JGly6PSK5iw")));
                    // Seen by eye: a woman presenting (user screenshot), credited "@aniakubow".
                    w.seedBlockedVideosOnce("seen2", new java.util.HashSet<>(java.util.Arrays.asList("PVAstrvWPkQ")));
                    w.setUnknownListener((channelId, videoId) -> ScreenWorker.enqueue(channelId, videoId));
                    instance = w;
                }
            }
        }
    }

    /** Never returns null once the Application has started; before that everything is denied by callers. */
    public static Whitelist get() {
        return instance;
    }

    public static boolean channelApproved(String ref) {
        Whitelist w = instance;
        return w != null && w.isChannelApproved(ref);
    }

    public static boolean videoAllowed(String channelRef, String videoRef) {
        Whitelist w = instance;
        return w != null && w.isVideoAllowed(channelRef, videoRef);
    }

    /** First time a video is seen in a list: its title alone can already rule it out. */
    public static void noteListing(String channelRef, String videoRef, String title) {
        Whitelist w = instance;
        if (w == null || !w.isChannelApproved(channelRef)) return;
        if (w.getVerdict(videoRef) == Screening.Verdict.UNKNOWN) {
            if (Screening.classify(title, null, false) == Screening.Verdict.BLOCK) {
                w.recordVerdict(videoRef, Screening.Verdict.BLOCK);
            } else if (!w.isStrict(channelRef)) {
                ScreenWorker.enqueue(Whitelist.normalizeChannelId(channelRef), videoRef);
            }
        }
    }

    /**
     * Screens unknown videos of approved channels BEFORE a list is shown, a few at a time, so a
     * blocked video never appears at all. Each item is {channelRef, videoId}. Failures and timeouts
     * leave the video unknown (the background worker retries); non-strict channels then show it.
     */
    public static void prescreen(java.util.List<String[]> items) {
        final Whitelist w = instance;
        if (w == null || items == null || items.isEmpty()) return;
        final java.util.List<String[]> todo = new java.util.ArrayList<>();
        for (String[] it : items) {
            if (w.isChannelApproved(it[0]) && w.getVerdict(it[1]) == Screening.Verdict.UNKNOWN) todo.add(it);
        }
        if (todo.isEmpty()) return;
        java.util.concurrent.ExecutorService ex =
                java.util.concurrent.Executors.newFixedThreadPool(Math.min(6, todo.size()));
        for (final String[] it : todo) {
            ex.submit(() -> {
                try {
                    String[] d = free.rm.skytube.businessobjects.YouTube.newpipe.NewPipeService.get().fetchForScreening(it[1]);
                    String channel = d[0] != null ? d[0] : it[0];
                    if (w.isChannelApproved(channel)) noteDetails(channel, it[1], d[1], d[2]);
                } catch (Exception ignored) {
                    // stays unknown
                }
            });
        }
        ex.shutdown();
        try {
            if (!ex.awaitTermination(12, java.util.concurrent.TimeUnit.SECONDS)) ex.shutdownNow();
        } catch (InterruptedException e) {
            ex.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Full metadata (title + description) is available: classify and remember the verdict. */
    public static void noteDetails(String channelRef, String videoRef, String title, String description) {
        Whitelist w = instance;
        if (w == null || !w.isChannelApproved(channelRef)) return;
        Screening.Verdict v = Screening.classify(title, description, w.isStrict(channelRef));
        w.recordVerdict(videoRef, v);
    }

    public static boolean playlistAllowed(String ownerChannelRef, String playlistRef) {
        Whitelist w = instance;
        return w != null && w.isPlaylistAllowed(ownerChannelRef, playlistRef);
    }
}
