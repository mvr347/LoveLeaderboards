package dev.lovelace.loveleaderboards.managers;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InFlightGuardTest {
    @Test
    void concurrentRequestsShareOneLoad() {
        InFlightGuard<String, Integer> guard = new InFlightGuard<>();
        Queue<Runnable> deferred = new ArrayDeque<>();
        AtomicInteger loads = new AtomicInteger();

        CompletableFuture<Integer> a = guard.load("k", loads::incrementAndGet, deferred::add);
        CompletableFuture<Integer> b = guard.load("k", loads::incrementAndGet, deferred::add);
        assertSame(a, b);
        assertEquals(1, guard.size());

        deferred.poll().run();
        assertEquals(1, loads.get());
        assertEquals(1, a.join());
        assertEquals(0, guard.size());

        guard.load("k", loads::incrementAndGet, Runnable::run);
        assertEquals(2, loads.get());
    }

    @Test
    void failureReleasesKey() {
        InFlightGuard<String, Integer> guard = new InFlightGuard<>();
        CompletableFuture<Integer> f = guard.load("k", () -> { throw new IllegalStateException("x"); }, Runnable::run);
        assertTrue(f.isCompletedExceptionally());
        assertEquals(0, guard.size());
    }
}
