package io.github.nikitoo0os.examples;

import io.nikitoo0os.GhostWork;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@SpringBootApplication
public class GhostWorkConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GhostWorkConsumerApplication.class, args);
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService ghostWorkExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    GhostWork ghostWork(ExecutorService ghostWorkExecutor) {
        return GhostWork.create(ghostWorkExecutor);
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("consumer-scheduler-");
        return scheduler;
    }

    @Bean
    ScheduledProbe scheduledProbe() {
        return new ScheduledProbe();
    }

    @RestController
    static class ConsumerController {
        @GetMapping("/consumer/ping")
        String ping() {
            return "pong";
        }
    }

    static final class ScheduledProbe {
        private final CountDownLatch executions = new CountDownLatch(2);

        @Scheduled(fixedDelay = 50, timeUnit = TimeUnit.MILLISECONDS)
        void refreshMarketplaceCatalog() {
            executions.countDown();
        }

        boolean awaitExecutions() throws InterruptedException {
            return executions.await(5, TimeUnit.SECONDS);
        }
    }
}
