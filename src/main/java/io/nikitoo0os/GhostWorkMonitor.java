package io.nikitoo0os;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class GhostWorkMonitor {

    private final GhostWork ghostWork;
    private final ScheduledExecutorService scheduler;

    public GhostWorkMonitor(
            GhostWork ghostWork,
            ScheduledExecutorService scheduler
    ) {
        this.ghostWork = Objects.requireNonNull(
                ghostWork,
                "GhostWork must not be null"
        );
        this.scheduler = Objects.requireNonNull(
                scheduler,
                "Scheduler must not be null"
        );
    }

    public ScheduledFuture<?> start(
            Duration interval,
            Duration stuckThreshold,
            Consumer<GhostWorkReport> reportConsumer
    ) {
        Objects.requireNonNull(interval, "Interval must not be null");
        Objects.requireNonNull(stuckThreshold, "Stuck threshold must not be null");
        Objects.requireNonNull(reportConsumer, "Report consumer must not be null");

        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Interval must be positive");
        }

        if (stuckThreshold.isZero() || stuckThreshold.isNegative()) {
            throw new IllegalArgumentException(
                    "Stuck threshold must be positive"
            );
        }

        return scheduler.scheduleAtFixedRate(
                () -> reportConsumer.accept(
                        ghostWork.report(stuckThreshold)
                ),
                0,
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }
}