package io.nikitoo0os.entity;

import io.nikitoo0os.CancellationCause;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CancellationTokenSourceTest {

    @Test
    void tokenShouldStartNonCancelledAndKeepFirstCause() {
        CancellationTokenSource source = source();
        var token = source.token();

        assertFalse(token.isCancellationRequested());
        assertTrue(source.request(
                CancellationCause.USER_REQUEST,
                Instant.parse("2026-07-25T00:00:00Z")
        ));
        assertFalse(source.request(
                CancellationCause.OPERATION_FAILED,
                Instant.parse("2026-07-25T00:00:01Z")
        ));

        assertTrue(token.isCancellationRequested());
        assertEquals(CancellationCause.USER_REQUEST, token.cause().orElseThrow());
    }

    @Test
    void callbacksShouldRunOnceAndRemovedCallbackShouldNotRun() {
        AtomicInteger calls = new AtomicInteger();
        CancellationTokenSource source = source();
        var removed = source.token().onCancellation(calls::incrementAndGet);
        source.token().onCancellation(calls::incrementAndGet);
        removed.close();

        source.request(CancellationCause.USER_REQUEST, Instant.now());
        source.request(CancellationCause.USER_REQUEST, Instant.now());

        assertEquals(1, calls.get());
    }

    @Test
    void lateCallbackShouldRunImmediately() {
        CancellationTokenSource source = source();
        source.request(CancellationCause.USER_REQUEST, Instant.now());
        AtomicInteger calls = new AtomicInteger();

        source.token().onCancellation(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    void failingCallbackShouldNotPreventFollowingCallbacks() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CancellationTokenSource source = new CancellationTokenSource(
                () -> {
                },
                () -> {
                },
                failure::set
        );
        source.token().onCancellation(() -> {
            throw new IllegalStateException("callback");
        });
        source.token().onCancellation(calls::incrementAndGet);

        source.request(CancellationCause.USER_REQUEST, Instant.now());

        assertEquals(1, calls.get());
        assertEquals("callback", failure.get().getMessage());
    }

    @Test
    void throwIfCancellationRequestedShouldUseStandardException() {
        CancellationTokenSource source = source();
        source.request(CancellationCause.USER_REQUEST, Instant.now());

        assertThrows(
                java.util.concurrent.CancellationException.class,
                source.token()::throwIfCancellationRequested
        );
    }

    private static CancellationTokenSource source() {
        return new CancellationTokenSource(
                () -> {
                },
                () -> {
                },
                ignored -> {
                }
        );
    }
}
