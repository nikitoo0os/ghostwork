import io.nikitoo0os.GhostWork;
import io.nikitoo0os.GhostWorkMonitor;
import io.nikitoo0os.GhostWorkReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GhostWorkMonitorTest {

    private final java.util.concurrent.ExecutorService workerExecutor =
            Executors.newFixedThreadPool(2);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final GhostWork ghostWork =
            GhostWork.create(workerExecutor);

    private final GhostWorkMonitor monitor =
            new GhostWorkMonitor(ghostWork, scheduler);

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        ghostWork.executor().shutdownNow();
    }

    @Test
    void startShouldPeriodicallyPublishReports()
            throws Exception {
        ghostWork.run(
                "MonitoredOperation",
                () -> {
                }
        );

        CountDownLatch reportReceived = new CountDownLatch(1);
        AtomicReference<GhostWorkReport> receivedReport =
                new AtomicReference<>();

        monitor.start(
                Duration.ofMillis(10),
                Duration.ofSeconds(30),
                report -> {
                    receivedReport.set(report);
                    reportReceived.countDown();
                }
        );

        assertTrue(reportReceived.await(1, TimeUnit.SECONDS));

        GhostWorkReport report = receivedReport.get();

        assertNotNull(report);
        assertEquals(1, report.operations().size());
        assertEquals(
                "MonitoredOperation",
                report.operations().getFirst().name()
        );
    }

    @Test
    void startShouldRejectNullInterval() {
        assertThrows(
                NullPointerException.class,
                () -> monitor.start(
                        null,
                        Duration.ofSeconds(30),
                        report -> {
                        }
                )
        );
    }

    @Test
    void startShouldRejectZeroInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> monitor.start(
                        Duration.ZERO,
                        Duration.ofSeconds(30),
                        report -> {
                        }
                )
        );
    }

    @Test
    void startShouldRejectNegativeInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> monitor.start(
                        Duration.ofMillis(-1),
                        Duration.ofSeconds(30),
                        report -> {
                        }
                )
        );
    }

    @Test
    void startShouldRejectNullStuckThreshold() {
        assertThrows(
                NullPointerException.class,
                () -> monitor.start(
                        Duration.ofMillis(10),
                        null,
                        report -> {
                        }
                )
        );
    }

    @Test
    void startShouldRejectZeroStuckThreshold() {
        assertThrows(
                IllegalArgumentException.class,
                () -> monitor.start(
                        Duration.ofMillis(10),
                        Duration.ZERO,
                        report -> {
                        }
                )
        );
    }

    @Test
    void startShouldRejectNullReportConsumer() {
        assertThrows(
                NullPointerException.class,
                () -> monitor.start(
                        Duration.ofMillis(10),
                        Duration.ofSeconds(30),
                        null
                )
        );
    }

    @Test
    void constructorShouldRejectNullGhostWork() {
        assertThrows(
                NullPointerException.class,
                () -> new GhostWorkMonitor(null, scheduler)
        );
    }

    @Test
    void constructorShouldRejectNullScheduler() {
        assertThrows(
                NullPointerException.class,
                () -> new GhostWorkMonitor(ghostWork, null)
        );
    }
}