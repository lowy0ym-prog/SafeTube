/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.businessobjects.whitelist;

import android.content.Context;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.newpipe.ChannelId;
import free.rm.skytube.businessobjects.db.DatabaseTasks;
import free.rm.skytube.businessobjects.db.SubscriptionsDb;
import free.rm.skytube.businessobjects.YouTube.POJOs.PersistentChannel;

/**
 * Keeps the app's internal subscription list (which drives the combined feed) identical to the
 * administrator whitelist. Users cannot subscribe/unsubscribe themselves.
 */
public final class WhitelistSync {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private WhitelistSync() { }

    public static void reconcileAsync(Context context) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                reconcile(app);
            } catch (RuntimeException e) {
                Logger.e(WhitelistSync.class, "Whitelist reconcile failed: " + e.getMessage(), e);
            }
        });
    }

    private static void reconcile(Context app) {
        Whitelist wl = Whitelists.get();
        if (wl == null) return;
        SubscriptionsDb db = SubscriptionsDb.getSubscriptionsDb();
        List<ChannelId> subscribed = db.getSubscribedChannelIdsAsync().blockingGet();
        // remove everything that is not approved
        for (ChannelId id : subscribed) {
            if (!wl.isChannelApproved(id.getRawId())) {
                PersistentChannel pc = db.getCachedChannel(id);
                if (pc != null) {
                    db.unsubscribe(pc);
                }
            }
        }
        // subscribe approved channels that are missing
        Map<String, String> approved = wl.getApprovedChannels();
        for (String raw : approved.keySet()) {
            ChannelId id = new ChannelId(raw);
            if (!db.isUserSubscribedToChannel(id)) {
                try {
                    DatabaseTasks.subscribeToChannel(true, null, app, id, false)
                            .subscribe(r -> { }, e -> Logger.e(WhitelistSync.class, e, "subscribe %s failed", raw));
                } catch (RuntimeException e) {
                    Logger.e(WhitelistSync.class, "subscribe failed for " + raw, e);
                }
            }
        }
    }
}
