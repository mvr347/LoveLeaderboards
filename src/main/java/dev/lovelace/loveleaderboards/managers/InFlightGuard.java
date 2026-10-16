package dev.lovelace.loveleaderboards.managers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Deduplicates concurrent loads of the same key: while a load for a key is running, further
 * requests share its future instead of starting another DB query.
 */
public final class InFlightGuard<K, V> {
    private final ConcurrentMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    public CompletableFuture<V> load(K key, Supplier<V> loader, Executor executor) {
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> existing = inFlight.computeIfAbsent(key, k -> mine);
        if (existing != mine) return existing;
        try {
            executor.execute(() -> {
                try {
                    mine.complete(loader.get());
                } catch (Throwable t) {
                    mine.completeExceptionally(t);
                } finally {
                    inFlight.remove(key, mine);
                }
            });
        } catch (RuntimeException e) {
            inFlight.remove(key, mine);
            mine.completeExceptionally(e);
        }
        return mine;
    }

    public int size() {
        return inFlight.size();
    }
}
