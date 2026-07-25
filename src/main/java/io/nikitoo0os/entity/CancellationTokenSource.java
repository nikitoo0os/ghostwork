package io.nikitoo0os.entity;

import io.nikitoo0os.CancellationCause;
import io.nikitoo0os.CancellationRegistration;
import io.nikitoo0os.CancellationToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

final class CancellationTokenSource {
    private final Object monitor = new Object();
    private final Map<Long, Runnable> callbacks = new LinkedHashMap<>();
    private final AtomicLong callbackIds = new AtomicLong();
    private final Runnable observed;
    private final Runnable callbackExecuted;
    private final Consumer<Throwable> callbackFailed;
    private Request request;
    private boolean closed;

    CancellationTokenSource(
            Runnable observed,
            Runnable callbackExecuted,
            Consumer<Throwable> callbackFailed
    ) {
        this.observed = observed;
        this.callbackExecuted = callbackExecuted;
        this.callbackFailed = callbackFailed;
    }

    CancellationToken token() {
        return new Token();
    }

    boolean request(CancellationCause cause, Instant requestedAt) {
        java.util.List<Runnable> pending;
        synchronized (monitor) {
            if (request != null || closed) {
                return false;
            }
            request = new Request(cause, requestedAt);
            pending = new ArrayList<>(callbacks.values());
            callbacks.clear();
        }
        pending.forEach(this::invoke);
        return true;
    }

    void close() {
        synchronized (monitor) {
            closed = true;
            callbacks.clear();
        }
    }

    private void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable failure) {
            callbackFailed.accept(failure);
        } finally {
            callbackExecuted.run();
        }
    }

    private final class Token implements CancellationToken {
        @Override
        public boolean isCancellationRequested() {
            synchronized (monitor) {
                if (request == null) {
                    return false;
                }
            }
            observed.run();
            return true;
        }

        @Override
        public Optional<CancellationCause> cause() {
            synchronized (monitor) {
                return request == null
                        ? Optional.empty()
                        : Optional.of(request.cause());
            }
        }

        @Override
        public Optional<Instant> requestedAt() {
            synchronized (monitor) {
                return request == null
                        ? Optional.empty()
                        : Optional.of(request.requestedAt());
            }
        }

        @Override
        public CancellationRegistration onCancellation(Runnable callback) {
            java.util.Objects.requireNonNull(callback, "Callback must not be null");
            long id;
            boolean invokeImmediately;
            synchronized (monitor) {
                if (closed) {
                    return () -> {
                    };
                }
                invokeImmediately = request != null;
                if (invokeImmediately) {
                    id = -1;
                } else {
                    id = callbackIds.incrementAndGet();
                    callbacks.put(id, callback);
                }
            }
            if (invokeImmediately) {
                invoke(callback);
                return () -> {
                };
            }
            long callbackId = id;
            return () -> {
                synchronized (monitor) {
                    callbacks.remove(callbackId);
                }
            };
        }
    }

    private record Request(
            CancellationCause cause,
            Instant requestedAt
    ) {
    }
}
