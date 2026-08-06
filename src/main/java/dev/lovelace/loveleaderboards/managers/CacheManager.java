package dev.lovelace.loveleaderboards.managers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class CacheManager<K, V> {
    private final int maxCacheSize;
    private final long ttlMs;

    private final Map<K, CachedValue<V>> cache;

    public CacheManager(int maxCacheSize, long ttlMs) {
        this.maxCacheSize = maxCacheSize;
        this.ttlMs = ttlMs;

        this.cache = Collections.synchronizedMap(new LinkedHashMap<K, CachedValue<V>>(
            maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CachedValue<V>> eldest) {
                return size() > maxCacheSize;
            }
        });
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public Optional<V> get(K key) {
        CachedValue<V> cached = cache.get(key);
        if (cached == null) return Optional.empty();
        
        if (System.currentTimeMillis() - cached.timestamp > ttlMs) {
            cache.remove(key);
            return Optional.empty();
        }
        
        return Optional.of(cached.value);
    }

    public void put(K key, V value) {
        cache.put(key, new CachedValue<>(value));
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private record CachedValue<V>(V value, long timestamp) {
        private CachedValue(V value) {
            this(value, System.currentTimeMillis());
        }
    }
}
