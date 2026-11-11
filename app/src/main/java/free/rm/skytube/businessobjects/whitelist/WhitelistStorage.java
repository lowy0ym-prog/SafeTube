/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 */
package free.rm.skytube.businessobjects.whitelist;

import java.util.Set;

/** Minimal persistent key/value storage used by {@link Whitelist}. Writes must be durable when the call returns. */
public interface WhitelistStorage {
    Set<String> getSet(String key);
    void putSet(String key, Set<String> value);
    String getString(String key);
    void putString(String key, String value);
}
