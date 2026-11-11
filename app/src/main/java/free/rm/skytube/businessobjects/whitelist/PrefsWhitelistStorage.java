/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */
package free.rm.skytube.businessobjects.whitelist;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** SharedPreferences-backed storage. Lives in app data, so it survives restarts and app updates. */
public class PrefsWhitelistStorage implements WhitelistStorage {
    private final SharedPreferences prefs;

    public PrefsWhitelistStorage(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences("safetube_whitelist", Context.MODE_PRIVATE);
    }

    @Override
    public Set<String> getSet(String key) {
        Set<String> s = prefs.getStringSet(key, null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    @Override
    public void putSet(String key, Set<String> value) {
        prefs.edit().putStringSet(key, new HashSet<>(value)).commit();
    }

    @Override
    public String getString(String key) {
        return prefs.getString(key, null);
    }

    @Override
    public void putString(String key, String value) {
        if (value == null) {
            prefs.edit().remove(key).commit();
        } else {
            prefs.edit().putString(key, value).commit();
        }
    }
}
