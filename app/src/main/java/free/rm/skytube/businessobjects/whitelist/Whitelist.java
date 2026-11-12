/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */
package free.rm.skytube.businessobjects.whitelist;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Strict, fail-closed whitelist policy.
 *
 * Everything is keyed by stable YouTube channel IDs (UC + 22 chars), never by display names.
 * Anything whose channel cannot be resolved to an approved channel ID is NOT visible.
 */
public class Whitelist {
    private static final String K_CHANNELS = "approved_channels";   // entries "UCid\tname"
    private static final String K_BLOCKED_VIDEOS = "blocked_videos";
    private static final String K_BLOCKED_PLAYLISTS = "blocked_playlists";
    private static final String K_PIN_SALT = "pin_salt";
    private static final String K_PIN_HASH = "pin_hash";
    private static final String K_FAILS = "pin_fails";
    private static final String K_LOCK_UNTIL = "pin_lock_until";

    private static final int PBKDF2_ITERATIONS = 100_000;
    public static final int MIN_PIN_LENGTH = 4;
    private static final Pattern CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_-]{22}");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PLAYLIST_ID = Pattern.compile("(?:list=)?((?:PL|UU|OL|FL|LL|RD)[A-Za-z0-9_-]{10,})");

    public interface Clock { long now(); }

    private final WhitelistStorage storage;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private Map<String, String> channels = new HashMap<>();
    private Set<String> blockedVideos = new HashSet<>();
    private Set<String> blockedPlaylists = new HashSet<>();

    public Whitelist(WhitelistStorage storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
        reload();
    }

    public synchronized void reload() {
        Map<String, String> c = new HashMap<>();
        for (String e : storage.getSet(K_CHANNELS)) {
            int t = e.indexOf('\t');
            String id = t < 0 ? e : e.substring(0, t);
            String name = t < 0 ? id : e.substring(t + 1);
            String norm = normalizeChannelId(id);
            if (norm != null) {
                c.put(norm, name);
            }
        }
        channels = c;
        blockedVideos = new HashSet<>(storage.getSet(K_BLOCKED_VIDEOS));
        blockedPlaylists = new HashSet<>(storage.getSet(K_BLOCKED_PLAYLISTS));
    }

    // ---------------------------------------------------------------- id helpers

    /** Extracts a canonical channel ID from an ID or channel URL. Returns null if none (=> fail closed). */
    public static String normalizeChannelId(String ref) {
        if (ref == null) return null;
        Matcher m = CHANNEL_ID.matcher(ref);
        return m.find() ? m.group() : null;
    }

    public static String normalizeVideoId(String ref) {
        if (ref == null) return null;
        String r = ref.trim();
        int v = r.indexOf("v=");
        if (v >= 0) r = r.substring(v + 2);
        int slash = r.lastIndexOf('/');
        if (slash >= 0) r = r.substring(slash + 1);
        int amp = r.indexOf('&');
        if (amp >= 0) r = r.substring(0, amp);
        int q = r.indexOf('?');
        if (q >= 0) r = r.substring(0, q);
        return VIDEO_ID.matcher(r).matches() ? r : null;
    }

    public static String normalizePlaylistId(String ref) {
        if (ref == null) return null;
        Matcher m = PLAYLIST_ID.matcher(ref);
        return m.find() ? m.group(1) : null;
    }

    // ---------------------------------------------------------------- visibility checks (fail closed)

    public synchronized boolean isChannelApproved(String channelRef) {
        String id = normalizeChannelId(channelRef);
        return id != null && channels.containsKey(id);
    }

    public synchronized boolean isVideoAllowed(String channelRef, String videoRef) {
        if (!isChannelApproved(channelRef)) return false;
        String vid = normalizeVideoId(videoRef);
        return vid != null && !blockedVideos.contains(vid);
    }

    public synchronized boolean isPlaylistAllowed(String ownerChannelRef, String playlistRef) {
        if (!isChannelApproved(ownerChannelRef)) return false;
        String pid = normalizePlaylistId(playlistRef);
        return pid != null && !blockedPlaylists.contains(pid);
    }

    // ---------------------------------------------------------------- management (callers must have verified the PIN)

    public synchronized Map<String, String> getApprovedChannels() {
        return Collections.unmodifiableMap(new HashMap<>(channels));
    }

    public synchronized Set<String> getBlockedVideos() {
        return Collections.unmodifiableSet(new HashSet<>(blockedVideos));
    }

    public synchronized Set<String> getBlockedPlaylists() {
        return Collections.unmodifiableSet(new HashSet<>(blockedPlaylists));
    }

    public synchronized boolean addChannel(String channelRef, String name) {
        String id = normalizeChannelId(channelRef);
        if (id == null) return false;
        channels.put(id, name == null || name.isEmpty() ? id : name);
        persistChannels();
        return true;
    }

    public synchronized boolean removeChannel(String channelRef) {
        String id = normalizeChannelId(channelRef);
        if (id == null || channels.remove(id) == null) return false;
        persistChannels();
        return true;
    }

    public synchronized boolean blockVideo(String videoRef) {
        String id = normalizeVideoId(videoRef);
        if (id == null) return false;
        blockedVideos.add(id);
        storage.putSet(K_BLOCKED_VIDEOS, blockedVideos);
        return true;
    }

    public synchronized boolean unblockVideo(String videoRef) {
        String id = normalizeVideoId(videoRef);
        if (id == null || !blockedVideos.remove(id)) return false;
        storage.putSet(K_BLOCKED_VIDEOS, blockedVideos);
        return true;
    }

    public synchronized boolean blockPlaylist(String playlistRef) {
        String id = normalizePlaylistId(playlistRef);
        if (id == null) return false;
        blockedPlaylists.add(id);
        storage.putSet(K_BLOCKED_PLAYLISTS, blockedPlaylists);
        return true;
    }

    public synchronized boolean unblockPlaylist(String playlistRef) {
        String id = normalizePlaylistId(playlistRef);
        if (id == null || !blockedPlaylists.remove(id)) return false;
        storage.putSet(K_BLOCKED_PLAYLISTS, blockedPlaylists);
        return true;
    }

    private void persistChannels() {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, String> e : channels.entrySet()) {
            out.add(e.getKey() + "\t" + e.getValue().replace('\t', ' '));
        }
        storage.putSet(K_CHANNELS, out);
    }

    // ---------------------------------------------------------------- PIN (no default, no master PIN)

    public synchronized boolean isPinSet() {
        return storage.getString(K_PIN_HASH) != null && storage.getString(K_PIN_SALT) != null;
    }

    /** Sets the first PIN. Refused if a PIN already exists (use {@link #changePin}). */
    public synchronized boolean setInitialPin(String pin) {
        if (isPinSet() || !isValidPin(pin)) return false;
        storePin(pin);
        return true;
    }

    public synchronized boolean changePin(String oldPin, String newPin) {
        if (!isValidPin(newPin) || verifyPin(oldPin) != PinResult.OK) return false;
        storePin(newPin);
        return true;
    }

    public enum PinResult { OK, WRONG, LOCKED, NOT_SET }

    /** Seconds until another PIN attempt is allowed (0 if not locked). */
    public synchronized long lockedForSeconds() {
        long until = parseLong(storage.getString(K_LOCK_UNTIL));
        long left = until - clock.now();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    public synchronized PinResult verifyPin(String pin) {
        if (!isPinSet()) return PinResult.NOT_SET;
        if (lockedForSeconds() > 0) return PinResult.LOCKED;
        byte[] expected = fromHex(storage.getString(K_PIN_HASH));
        byte[] actual = hash(pin == null ? "" : pin, fromHex(storage.getString(K_PIN_SALT)));
        if (MessageDigest.isEqual(expected, actual)) {
            storage.putString(K_FAILS, "0");
            return PinResult.OK;
        }
        long fails = parseLong(storage.getString(K_FAILS)) + 1;
        storage.putString(K_FAILS, String.valueOf(fails));
        if (fails >= 5) {
            long delaySec = Math.min(3600, 30L * (1L << Math.min(10, fails - 5)));
            storage.putString(K_LOCK_UNTIL, String.valueOf(clock.now() + delaySec * 1000));
        }
        return PinResult.WRONG;
    }

    private boolean isValidPin(String pin) {
        return pin != null && pin.length() >= MIN_PIN_LENGTH;
    }

    private void storePin(String pin) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        storage.putString(K_PIN_SALT, toHex(salt));
        storage.putString(K_PIN_HASH, toHex(hash(pin, salt)));
        storage.putString(K_FAILS, "0");
        storage.putString(K_LOCK_UNTIL, "0");
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }

    private static long parseLong(String s) {
        try { return s == null ? 0 : Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] fromHex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
