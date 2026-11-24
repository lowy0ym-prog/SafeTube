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
                    // Covers checked by eye on contact sheets (a woman is clearly shown): newest 240 freeCodeCamp videos.
                    seedScanResults(context, w);
                    w.seedBlockedVideosOnce("covers1", new java.util.HashSet<>(java.util.Arrays.asList(
                            "JGly6PSK5iw",
                            "FVN0t1vb7k8",
                            "jIo2ccqPnLQ",
                            "yVga-_gMfIM",
                            "PVAstrvWPkQ",
                            "YdhXnB5E-4s",
                            "n1VUnHD62r0",
                            "rQxPGqPq8wk",
                            "j6hnjNhx_MM",
                            "MnG0ugK2JAI",
                            "GmtOxMl39Tc",
                            "-leIp449qXA",
                            "pxMUG3gcoik",
                            "tchN4xa4pfg",
                            "BMt-qvrEcFY",
                            "voPcxCFMbbM",
                            "GpxFF82I8Nk",
                            "rgt5GPFgryI",
                            "B0TJC4lmzEM",
                            "MiPkcTaRbfQ",
                            "Zv2a6ixXEQc",
                            "H-oCV5brtU4",
                            "9tANiA0LKn4",
                            "N0K0m7dZBJI",
                            "DrAZf4ZHqaM",
                            "NCvKYoTeJR0")));
                    try {
                        java.io.File f = new java.io.File(context.getFilesDir(), "extra_blocked_people.txt");
                        if (f.isFile()) {
                            Screening.setExtraPeople(java.nio.file.Files.readAllLines(f.toPath()));
                        }
                    } catch (Exception ignored) {
                        // test hook only
                    }
                    // Only freeCodeCamp gets the deep check (hidden until its description has been read).
                    w.setDeepChannels(new java.util.HashSet<>(java.util.Arrays.asList("UC8butISFwT-Wl7EV0hUK0BQ")));
                    w.setUnknownListener((channelId, videoId) -> ScreenWorker.enqueue(channelId, videoId));
                    instance = w;
                    // Newest 15 videos: read descriptions now (one small request), the slow background fill starts from MainActivity.
                    Thread t = new Thread(() -> {
                        FeedScreen.ensure("UC8butISFwT-Wl7EV0hUK0BQ");
                    }, "safetube-start");
                    t.setDaemon(true);
                    t.start();
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
            }
            // Note: no background requests for non-strict channels. Extra watch-page requests make
            // YouTube temporarily block this phone's IP ("Sign in to confirm you're not a bot").
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

    /** Remembers videos of deep channels so the admin screen can show progress. */
    public static void noteDeepSeen(String channelRef, String videoRef) {
        Whitelist w = instance;
        if (w != null && w.isDeep(channelRef)) w.noteDeepSeen(videoRef);
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

    /** Verdicts for every freeCodeCamp video, decided before the app was built (assets/fcc_verdicts.txt: "A id" / "B id"). */
    private static void seedScanResults(Context context, Whitelist w) {
        java.util.Set<String> allow = new java.util.HashSet<>();
        java.util.Set<String> block = new java.util.HashSet<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(context.getAssets().open("fcc_verdicts.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.length() < 13) continue;
                String id = line.substring(2).trim();
                if (line.charAt(0) == 'A') allow.add(id); else if (line.charAt(0) == 'B') block.add(id);
            }
        } catch (Exception e) {
            return; // no asset: nothing to seed
        }
        w.seedVerdictsOnce("fccscan3", allow, block);
    }
}
