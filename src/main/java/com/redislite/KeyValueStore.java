package com.redislite;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe in-memory key-value store with optional per-key TTL.
 *
 * Expiration works in two ways:
 *  1. Lazy   - an expired key is removed the moment someone reads it.
 *  2. Active - {@link #removeExpired()} is called periodically by the server
 *              so keys nobody reads do not stay in memory forever.
 */
public class KeyValueStore {

    static final long NO_EXPIRY = -1L;

    private final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public KeyValueStore() {
        this(System::currentTimeMillis);
    }

    /** Clock is injectable so tests can control time without Thread.sleep. */
    public KeyValueStore(LongSupplier clock) {
        this.clock = clock;
    }

    public void set(String key, String value) {
        set(key, value, 0);
    }

    /** @param ttlSeconds time to live in seconds; 0 or less means the key never expires */
    public void set(String key, String value, long ttlSeconds) {
        long expiresAt = ttlSeconds > 0 ? clock.getAsLong() + ttlSeconds * 1000 : NO_EXPIRY;
        data.put(key, new Entry(value, expiresAt));
    }

    /** @return the value, or null if the key does not exist or has expired */
    public String get(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(clock.getAsLong())) {
            data.remove(key, entry); // only removes if nobody overwrote it in the meantime
            return null;
        }
        return entry.value();
    }

    /** @return true if a live key was deleted */
    public boolean delete(String key) {
        Entry removed = data.remove(key);
        return removed != null && !removed.isExpired(clock.getAsLong());
    }

    /**
     * @return remaining seconds to live, -1 if the key has no expiry,
     *         -2 if the key does not exist (same convention as Redis)
     */
    public long ttl(String key) {
        Entry entry = data.get(key);
        if (entry == null) {
            return -2;
        }
        long now = clock.getAsLong();
        if (entry.isExpired(now)) {
            data.remove(key, entry);
            return -2;
        }
        if (entry.expiresAt() == NO_EXPIRY) {
            return -1;
        }
        return (entry.expiresAt() - now + 999) / 1000; // round up
    }

    /** Removes every expired key. Returns how many were removed. */
    public int removeExpired() {
        long now = clock.getAsLong();
        int before = data.size();
        data.entrySet().removeIf(e -> e.getValue().isExpired(now));
        return Math.max(0, before - data.size());
    }

    public int size() {
        return data.size();
    }

    private record Entry(String value, long expiresAt) {
        boolean isExpired(long now) {
            return expiresAt != NO_EXPIRY && now >= expiresAt;
        }
    }
}
