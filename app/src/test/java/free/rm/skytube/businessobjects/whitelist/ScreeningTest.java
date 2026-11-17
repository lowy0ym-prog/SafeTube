package free.rm.skytube.businessobjects.whitelist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import free.rm.skytube.businessobjects.whitelist.Screening.Verdict;

class ScreeningTest {
    static final String FCC = "UC8butISFwT-Wl7EV0hUK0BQ";
    static final String MESSER = "UCkefXKtInZ9PLsoGRtml2FQ";

    static class Mem implements WhitelistStorage {
        final Map<String, Set<String>> sets = new HashMap<>();
        final Map<String, String> strings = new HashMap<>();
        public Set<String> getSet(String k) { return new HashSet<>(sets.getOrDefault(k, new HashSet<>())); }
        public void putSet(String k, Set<String> v) { sets.put(k, new HashSet<>(v)); }
        public String getString(String k) { return strings.get(k); }
        public void putString(String k, String v) { if (v == null) strings.remove(k); else strings.put(k, v); }
    }

    @Test
    void femaleWordsAndNamesAreBlocked() {
        assertEquals(Verdict.BLOCK, Screening.classify("Women in Tech panel", "", false));
        assertEquals(Verdict.BLOCK, Screening.classify("She teaches Python", "", false));
        assertEquals(Verdict.BLOCK, Screening.classify("Machine Learning", "Course created by Kylie Ying", false));
        assertEquals(Verdict.BLOCK, Screening.classify("Python", "Guest: Estefania Cassingena Navone", false));
    }

    @Test
    void plainTechTextIsAllowedForNormalChannels() {
        assertEquals(Verdict.ALLOW, Screening.classify("Network+ N10-009 - 1.2", "Latency is measured in ms", false));
        assertEquals(Verdict.ALLOW, Screening.classify("Python Tutorial 8: Functions", "In this video we learn functions", false));
    }

    @Test
    void defaultModeBlocksOnlyOnClearEvidence() {
        assertEquals(Verdict.ALLOW, Screening.classify("Interview with a developer", "A long chat about careers", false));
        assertEquals(Verdict.ALLOW, Screening.classify("Learn Java", "Help her friends: this course is free", false));
        assertEquals(Verdict.ALLOW, Screening.classify("Learn Java", "Created by Beau Carnes", false));
        assertEquals(Verdict.ALLOW, Screening.classify("Podcast #5", "Guest: Quincy Larson", false));
        assertEquals(Verdict.BLOCK, Screening.classify("Podcast #6", "Featuring Sarah Drasner", false));
    }

    @Test
    void namesReportedByUserAreBlocked() {
        for (String n : new String[]{"Ania Kubow", "Jessica Chan", "Emma Bostian", "Rachel Fernandez", "Andrea Griffiths"}) {
            assertEquals(Verdict.BLOCK, Screening.classify("Course", "Created by " + n, false), n);
            assertEquals(Verdict.BLOCK, Screening.classify("Podcast", "Guest: " + n, false), n);
        }
    }

    @Test
    void creditHandlesWithFemaleNamesAreBlocked() {
        assertEquals(Verdict.BLOCK, Screening.classify("How an Apple Engineer Pranked Lawyers", "Video created by @aniakubow", false));
        assertEquals(Verdict.ALLOW, Screening.classify("Course", "Course created by @beaucarnes", false));
        assertEquals(Verdict.ALLOW, Screening.classify("Course", "Thanks to @anthonycampbell2148 and @rajibdassharma497", false));
    }

    @Test
    void namedPeopleAreBlockedAnywhereInText() {
        assertEquals(Verdict.BLOCK, Screening.classify("Course", "Thanks to Ania Kubow for the slides", false));
        assertEquals(Verdict.BLOCK, Screening.classify("Course", "see youtube.com/@jessica_chan", false));
        assertEquals(Verdict.BLOCK, Screening.classify("x", "Emma Bostian explains", true));
        assertEquals(Verdict.ALLOW, Screening.classify("Course", "Created by Beau Carnes", false));
    }

    @Test
    void secondBatchOfBlockedChannelsAndPeople() {
        assertEquals(Verdict.BLOCK, Screening.classify("x", "Follow https://www.youtube.com/channel/UCiWmRIZ-sEbPaZZfelakuXQ", false));
        assertEquals(Verdict.BLOCK, Screening.classify("x", "More at youtube.com/channel/UCng0h8WiHLmT57JJ8At4LfQ", false));
        assertEquals(Verdict.BLOCK, Screening.classify("x", "Thanks Patt Vira!", false));
        assertEquals(Verdict.BLOCK, Screening.classify("x", "@codingwithestefania", false));
    }

    @Test
    void migrationTurnsOffOldStrictFlagsOnce() {
        Mem m = new Mem();
        m.putString("seeded_v1", "1");
        Set<String> old = new HashSet<>();
        old.add(FCC);
        m.putSet("strict_channels", old);
        m.putSet("screen_block", new HashSet<>(java.util.Arrays.asList("abcdefghijk")));
        Whitelist w = new Whitelist(m, () -> 0L);
        w.addChannel(FCC, "freeCodeCamp");
        assertTrue(w.isStrict(FCC));
        w.seedDefaultsIfFirstRun(new HashMap<>(), new HashSet<>());
        assertFalse(w.isStrict(FCC));
        assertEquals(0, w.blockedVerdictCount());
        w.setStrict(FCC, true); // admin opts back in
        w.seedDefaultsIfFirstRun(new HashMap<>(), new HashSet<>());
        assertTrue(w.isStrict(FCC));
    }

    @Test
    void strictNeedsCreditedMaleInstructor() {
        assertEquals(Verdict.ALLOW, Screening.classify("Learn Java", "This course was created by Beau Carnes. Enjoy.", true));
        assertEquals(Verdict.ALLOW, Screening.classify("Learn Git", "Course developed by Colt Steele and Quincy Larson", true));
        assertEquals(Verdict.UNKNOWN, Screening.classify("Learn Java", "No credit line here", true));
        assertEquals(Verdict.UNKNOWN, Screening.classify("Learn Java", "Created by Alex Smith", true));
        assertEquals(Verdict.BLOCK, Screening.classify("Learn Java", "Created by Tiffany Zhong", true));
    }

    @Test
    void strictBlocksGuestFormats() {
        assertEquals(Verdict.BLOCK, Screening.classify("Interview with a developer", "Created by Beau Carnes", true));
        assertEquals(Verdict.BLOCK, Screening.classify("freeCodeCamp Podcast #12", "Hosted by Quincy Larson", true));
    }

    @Test
    void strictChannelHidesUnknownAndShowsAllowed() {
        Whitelist w = new Whitelist(new Mem(), () -> 0L);
        w.addChannel(FCC, "freeCodeCamp");
        w.setStrict(FCC, true);
        final String vid = "abcdefghijk";
        final String[] seen = new String[1];
        w.setUnknownListener((c, v) -> seen[0] = v);
        assertFalse(w.isVideoAllowed(FCC, vid));
        assertEquals(vid, seen[0]);
        w.recordVerdict(vid, Verdict.ALLOW);
        assertTrue(w.isVideoAllowed(FCC, vid));
        w.recordVerdict(vid, Verdict.BLOCK);
        assertFalse(w.isVideoAllowed(FCC, vid));
    }

    @Test
    void nonStrictChannelShowsUnknownButHonoursBlockVerdict() {
        Whitelist w = new Whitelist(new Mem(), () -> 0L);
        w.addChannel(MESSER, "Professor Messer");
        assertTrue(w.isVideoAllowed(MESSER, "abcdefghijk"));
        w.recordVerdict("abcdefghijk", Verdict.BLOCK);
        assertFalse(w.isVideoAllowed(MESSER, "abcdefghijk"));
    }

    @Test
    void adminOverrideBeatsScreeningButNotManualBlock() {
        Whitelist w = new Whitelist(new Mem(), () -> 0L);
        w.addChannel(FCC, "freeCodeCamp");
        w.setStrict(FCC, true);
        w.recordVerdict("abcdefghijk", Verdict.BLOCK);
        w.addOverride("abcdefghijk");
        assertTrue(w.isVideoAllowed(FCC, "abcdefghijk"));
        w.blockVideo("abcdefghijk");
        assertFalse(w.isVideoAllowed(FCC, "abcdefghijk"));
    }

    @Test
    void strictChannelPlaylistsAreHidden() {
        Whitelist w = new Whitelist(new Mem(), () -> 0L);
        w.addChannel(FCC, "freeCodeCamp");
        assertTrue(w.isPlaylistAllowed(FCC, "PLabcdefghijklmnop"));
        w.setStrict(FCC, true);
        assertFalse(w.isPlaylistAllowed(FCC, "PLabcdefghijklmnop"));
    }

    @Test
    void defaultsAreSeededOnceAndRemovalSticks() {
        Mem m = new Mem();
        Map<String, String> d = new HashMap<>();
        d.put(FCC, "freeCodeCamp");
        d.put(MESSER, "Professor Messer");
        Set<String> strict = new HashSet<>();
        Whitelist w = new Whitelist(m, () -> 0L);
        w.seedDefaultsIfFirstRun(d, strict);
        assertTrue(w.isChannelApproved(FCC));
        assertFalse(w.isStrict(FCC));
        w.removeChannel(MESSER);
        Whitelist w2 = new Whitelist(m, () -> 0L);
        w2.seedDefaultsIfFirstRun(d, strict);
        assertFalse(w2.isChannelApproved(MESSER));
        assertTrue(w2.isChannelApproved(FCC));
    }

    @Test
    void verdictsPersist() {
        Mem m = new Mem();
        Whitelist w = new Whitelist(m, () -> 0L);
        w.recordVerdict("abcdefghijk", Verdict.ALLOW);
        w.recordVerdict("bcdefghijkl", Verdict.BLOCK);
        w.flushVerdicts();
        Whitelist w2 = new Whitelist(m, () -> 0L);
        assertEquals(Verdict.ALLOW, w2.getVerdict("abcdefghijk"));
        assertEquals(Verdict.BLOCK, w2.getVerdict("bcdefghijkl"));
    }
}
