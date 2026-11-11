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
                    instance = new Whitelist(new PrefsWhitelistStorage(context), System::currentTimeMillis);
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

    public static boolean playlistAllowed(String ownerChannelRef, String playlistRef) {
        Whitelist w = instance;
        return w != null && w.isPlaylistAllowed(ownerChannelRef, playlistRef);
    }
}
