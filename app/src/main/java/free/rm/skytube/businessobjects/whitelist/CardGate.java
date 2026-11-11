/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.businessobjects.whitelist;

import java.util.ArrayList;
import java.util.List;

import free.rm.skytube.businessobjects.YouTube.POJOs.CardData;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubePlaylist;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;

/**
 * Last line of defence: every card that is about to be put into a list adapter must pass here.
 * Unknown card types and anything unverifiable are rejected (fail closed).
 */
public final class CardGate {
    private CardGate() { }

    public static boolean isVisible(CardData card) {
        try {
            if (card instanceof YouTubeVideo) {
                YouTubeVideo v = (YouTubeVideo) card;
                return v.getChannel() != null && v.getChannel().getChannelId() != null
                        && Whitelists.videoAllowed(v.getChannel().getChannelId().getRawId(), v.getId());
            }
            if (card instanceof YouTubePlaylist) {
                YouTubePlaylist p = (YouTubePlaylist) card;
                return Whitelists.playlistAllowed(p.getOwnerChannelRef(), p.getId());
            }
            if (card instanceof YouTubeChannel) {
                YouTubeChannel c = (YouTubeChannel) card;
                return c.getChannelId() != null && Whitelists.channelApproved(c.getChannelId().getRawId());
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }

    public static List<CardData> filter(List<? extends CardData> in) {
        List<CardData> out = new ArrayList<>();
        if (in != null) {
            for (CardData c : in) {
                if (c != null && isVisible(c)) out.add(c);
            }
        }
        return out;
    }
}
