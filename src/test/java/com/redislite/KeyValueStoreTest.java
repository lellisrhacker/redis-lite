package com.redislite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

public class KeyValueStoreTest {

    private AtomicLong now;
    private KeyValueStore store;

    @Before
    public void setUp() {
        now = new AtomicLong(1_000_000L);
        store = new KeyValueStore(now::get);
    }

    @Test
    public void setAndGetReturnsValue() {
        store.set("name", "alice");
        assertEquals("alice", store.get("name"));
    }

    @Test
    public void getMissingKeyReturnsNull() {
        assertNull(store.get("missing"));
    }

    @Test
    public void setOverwritesExistingValue() {
        store.set("k", "v1");
        store.set("k", "v2");
        assertEquals("v2", store.get("k"));
    }

    @Test
    public void deleteRemovesKey() {
        store.set("k", "v");
        assertTrue(store.delete("k"));
        assertNull(store.get("k"));
    }

    @Test
    public void deleteMissingKeyReturnsFalse() {
        assertFalse(store.delete("missing"));
    }

    @Test
    public void keyIsAvailableBeforeTtlAndGoneAfter() {
        store.set("session", "abc", 5);

        now.addAndGet(4_999);
        assertEquals("abc", store.get("session"));

        now.addAndGet(1);
        assertNull(store.get("session"));
    }

    @Test
    public void overwritingWithoutTtlClearsOldExpiry() {
        store.set("k", "v1", 5);
        store.set("k", "v2");

        now.addAndGet(10_000);
        assertEquals("v2", store.get("k"));
    }

    @Test
    public void ttlFollowsRedisConvention() {
        assertEquals(-2, store.ttl("missing"));

        store.set("forever", "x");
        assertEquals(-1, store.ttl("forever"));

        store.set("temp", "x", 10);
        assertEquals(10, store.ttl("temp"));

        now.addAndGet(3_500);
        assertEquals(7, store.ttl("temp")); // 6.5s left, rounded up

        now.addAndGet(10_000);
        assertEquals(-2, store.ttl("temp"));
    }

    @Test
    public void deleteOfExpiredKeyReturnsFalse() {
        store.set("k", "v", 1);
        now.addAndGet(2_000);
        assertFalse(store.delete("k"));
    }

    @Test
    public void removeExpiredCleansOnlyExpiredKeys() {
        store.set("short", "a", 1);
        store.set("long", "b", 100);
        store.set("forever", "c");

        now.addAndGet(2_000);

        assertEquals(1, store.removeExpired());
        assertEquals(2, store.size());
        assertEquals("b", store.get("long"));
        assertEquals("c", store.get("forever"));
    }

    @Test
    public void concurrentWritesFromManyThreadsAreAllStored() throws Exception {
        int threads = 16;
        int keysPerThread = 500;
        KeyValueStore realStore = new KeyValueStore();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < keysPerThread; i++) {
                    realStore.set("t" + threadId + "-k" + i, "v" + i);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(threads * keysPerThread, realStore.size());
        assertEquals("v42", realStore.get("t3-k42"));
    }
}
