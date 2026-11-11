package free.rm.skytube.businessobjects.whitelist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhitelistTest {
    static class MemStorage implements WhitelistStorage {
        final Map<String, Set<String>> sets = new HashMap<>();
        final Map<String, String> strings = new HashMap<>();
        public Set<String> getSet(String k) { return new HashSet<>(sets.getOrDefault(k, new HashSet<>())); }
        public void putSet(String k, Set<String> v) { sets.put(k, new HashSet<>(v)); }
        public String getString(String k) { return strings.get(k); }
        public void putString(String k, String v) { if (v == null) strings.remove(k); else strings.put(k, v); }
    }

    static final String CH_A = "UCaaaaaaaaaaaaaaaaaaaaaa";
    static final String CH_B = "UCbbbbbbbbbbbbbbbbbbbbbb";
    static final String VID = "dQw4w9WgXcQ";
    static final String PL = "PLabcdefghijklmnop";

    MemStorage storage;
    long now;
    Whitelist w;

    @BeforeEach
    void setUp() {
        storage = new MemStorage();
        now = 1_000_000L;
        w = new Whitelist(storage, () -> now);
    }

    @Test
    void emptyWhitelistDeniesEverything() {
        assertFalse(w.isChannelApproved(CH_A));
        assertFalse(w.isVideoAllowed(CH_A, VID));
        assertFalse(w.isPlaylistAllowed(CH_A, PL));
    }

    @Test
    void unresolvableChannelFailsClosed() {
        w.addChannel(CH_A, "A");
        assertFalse(w.isChannelApproved(null));
        assertFalse(w.isChannelApproved(""));
        assertFalse(w.isChannelApproved("https://www.youtube.com/@somehandle"));
        assertFalse(w.isVideoAllowed(null, VID));
        assertFalse(w.isVideoAllowed(CH_A, null));
    }

    @Test
    void approvedChannelMatchesByIdOrUrlNotName() {
        assertTrue(w.addChannel("https://www.youtube.com/channel/" + CH_A, "Old Name"));
        assertTrue(w.isChannelApproved(CH_A));
        assertTrue(w.isChannelApproved("https://www.youtube.com/channel/" + CH_A));
        assertFalse(w.isChannelApproved(CH_B));
        // a different channel using the same display name is not approved
        assertFalse(w.isVideoAllowed(CH_B, VID));
    }

    @Test
    void blockedVideoAndPlaylistAreDeniedAndRestorable() {
        w.addChannel(CH_A, "A");
        assertTrue(w.isVideoAllowed(CH_A, VID));
        assertTrue(w.isPlaylistAllowed(CH_A, PL));
        w.blockVideo(VID);
        w.blockPlaylist(PL);
        assertFalse(w.isVideoAllowed(CH_A, VID));
        assertFalse(w.isPlaylistAllowed(CH_A, "https://www.youtube.com/playlist?list=" + PL));
        w.unblockVideo(VID);
        w.unblockPlaylist(PL);
        assertTrue(w.isVideoAllowed(CH_A, VID));
        assertTrue(w.isPlaylistAllowed(CH_A, PL));
    }

    @Test
    void removingChannelHidesItsContent() {
        w.addChannel(CH_A, "A");
        assertTrue(w.removeChannel(CH_A));
        assertFalse(w.isVideoAllowed(CH_A, VID));
    }

    @Test
    void dataPersistsAcrossInstances() {
        w.addChannel(CH_A, "A");
        w.blockVideo(VID);
        w.blockPlaylist(PL);
        w.setInitialPin("4821");
        Whitelist w2 = new Whitelist(storage, () -> now);
        assertTrue(w2.isChannelApproved(CH_A));
        assertFalse(w2.isVideoAllowed(CH_A, VID));
        assertFalse(w2.isPlaylistAllowed(CH_A, PL));
        assertEquals(Whitelist.PinResult.OK, w2.verifyPin("4821"));
    }

    @Test
    void noDefaultPin() {
        assertFalse(w.isPinSet());
        assertEquals(Whitelist.PinResult.NOT_SET, w.verifyPin("0000"));
        assertEquals(Whitelist.PinResult.NOT_SET, w.verifyPin("1234"));
        assertEquals(Whitelist.PinResult.NOT_SET, w.verifyPin(""));
    }

    @Test
    void pinIsHashedNotStoredInPlainText() {
        assertTrue(w.setInitialPin("987654"));
        for (String v : storage.strings.values()) {
            assertFalse(v.contains("987654"));
        }
        assertNotNull(storage.strings.get("pin_salt"));
    }

    @Test
    void shortPinRejectedAndInitialPinCannotBeOverwritten() {
        assertFalse(w.setInitialPin("12"));
        assertTrue(w.setInitialPin("4821"));
        assertFalse(w.setInitialPin("9999"));
        assertEquals(Whitelist.PinResult.OK, w.verifyPin("4821"));
        assertEquals(Whitelist.PinResult.WRONG, w.verifyPin("9999"));
    }

    @Test
    void changePinRequiresOldPin() {
        w.setInitialPin("4821");
        assertFalse(w.changePin("0000", "5555"));
        assertTrue(w.changePin("4821", "5555"));
        assertEquals(Whitelist.PinResult.OK, w.verifyPin("5555"));
        assertEquals(Whitelist.PinResult.WRONG, w.verifyPin("4821"));
    }

    @Test
    void lockoutAfterRepeatedWrongPins() {
        w.setInitialPin("4821");
        for (int i = 0; i < 5; i++) {
            assertEquals(Whitelist.PinResult.WRONG, w.verifyPin("0000"));
        }
        assertEquals(Whitelist.PinResult.LOCKED, w.verifyPin("4821"));
        assertTrue(w.lockedForSeconds() > 0);
        now += 31_000;
        assertEquals(Whitelist.PinResult.OK, w.verifyPin("4821"));
    }
}
