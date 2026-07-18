package io.nikitoo0os;

import io.nikitoo0os.entity.*;
import io.nikitoo0os.factory.TrackingRunnableFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        TrackingRunnableFactory factory = new TrackingRunnableFactory(registry);

        CountDownLatch latch = new CountDownLatch(1);
        Runnable r = () -> {
            latch.countDown();
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Runnable wrapped = factory.wrap(
                operation,
                "task-name",
                r
        );

        executorService.submit(wrapped);
        Task t = registry.findTasksByOperation(operation.getId()).getFirst();

        executorService.shutdown();
        latch.await();
        System.out.println(t.getState());
        operation.timeout();
        Detector detector = new Detector(registry);
        detector.detectGhostTasks(operation.getId());

    }
}