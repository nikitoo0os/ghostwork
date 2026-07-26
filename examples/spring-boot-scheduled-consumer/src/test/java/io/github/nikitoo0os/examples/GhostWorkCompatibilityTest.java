package io.github.nikitoo0os.examples;

import io.nikitoo0os.GhostWork;
import io.nikitoo0os.scheduling.ScheduleExecutionState;
import io.nikitoo0os.scheduling.ScheduleExecutionView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ghostwork.dashboard.enabled=true",
                "ghostwork.scheduling.long-running-threshold=5s"
        }
)
class GhostWorkCompatibilityTest {

    @Autowired
    private GhostWork ghostWork;

    @Autowired
    private GhostWorkConsumerApplication.ScheduledProbe scheduledProbe;

    @Autowired
    private ApplicationContext applicationContext;

    @LocalServerPort
    private int port;

    @Test
    void allModulesShouldTrackScheduledExecutionsAndExposeDashboard()
            throws Exception {
        assertThat(scheduledProbe.awaitExecutions()).isTrue();
        await(() -> !ghostWork.schedules().isEmpty());

        var schedule = ghostWork.schedules().getFirst();
        ScheduleExecutionView execution = awaitExecution(schedule.id());

        assertThat(execution.state())
                .isEqualTo(ScheduleExecutionState.COMPLETED);
        assertThat(execution.operationId()).isNotNull();
        assertThat(execution.rootTaskId()).isNotNull();
        assertThat(ghostWork.tasks(execution.operationId()))
                .anySatisfy(task ->
                        assertThat(task.id()).isEqualTo(execution.rootTaskId())
                );

        assertThat(applicationContext.containsBean(
                "ghostWorkRequestInterceptor"
        )).isTrue();
        assertThat(applicationContext.containsBean(
                "ghostWorkDashboardController"
        )).isTrue();

        HttpClient client = HttpClient.newHttpClient();
        assertResponse(client, "/consumer/ping", "pong");
        assertResponse(client, "/ghostwork/", "GhostWork");
        assertResponse(client, "/ghostwork/api/report", "\"operations\"");
        assertResponse(client, "/ghostwork/api/schedules", "\"schedules\"");
    }

    private ScheduleExecutionView awaitExecution(
            io.nikitoo0os.scheduling.ScheduleId scheduleId
    ) throws InterruptedException {
        await(() -> ghostWork.scheduleExecutions(scheduleId).stream()
                .anyMatch(execution ->
                        execution.state()
                                == ScheduleExecutionState.COMPLETED
                                && execution.operationId() != null
                                && execution.rootTaskId() != null
                ));
        return ghostWork.scheduleExecutions(scheduleId).stream()
                .filter(execution ->
                        execution.state() == ScheduleExecutionState.COMPLETED
                                && execution.operationId() != null
                                && execution.rootTaskId() != null
                )
                .findFirst()
                .orElseThrow();
    }

    private void assertResponse(
            HttpClient client,
            String path,
            String expectedContent
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(expectedContent);
    }

    private static void await(BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
