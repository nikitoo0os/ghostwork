import io.nikitoo0os.entity.Operation;
import io.nikitoo0os.entity.Registry;
import io.nikitoo0os.entity.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RegistryTest {

    @Test
    void registeredOperationShouldBeFoundById() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        assertSame(
                operation,
                registry.findOperation(operation.getId())
        );
    }

    @Test
    void duplicateOperationRegistrationShouldThrow() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        assertThrows(IllegalStateException.class, () -> registry.registerOperation(operation));
    }

    @Test
    void nullOperationShouldNotBeRegistered() {
        Registry registry = new Registry();
        assertThrows(NullPointerException.class, () -> registry.registerOperation(null));
    }

    @Test
    void unknownOperationIdShouldThrow() {
        Registry registry = new Registry();
        assertThrows(NoSuchElementException.class, () -> registry.findOperation(UUID.randomUUID()));
    }

    @Test
    void nullOperationIdShouldThrow() {
        Registry registry = new Registry();
        assertThrows(NullPointerException.class, () -> registry.findOperation(null));
    }

    @Test
    void registeredTaskShouldBeFoundById() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        Task task = new Task("TestTask", operation);
        registry.registerTask(task);

        assertSame(
                task,
                registry.findTask(task.getId())
        );
    }

    @Test
    void duplicateTaskRegistrationShouldThrow() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        Task task = new Task("TestTask", operation);
        registry.registerTask(task);
        assertThrows(IllegalStateException.class, () -> registry.registerTask(task));
    }

    @Test
    void nullTaskShouldNotBeRegistered() {
        Registry registry = new Registry();
        assertThrows(NullPointerException.class, () -> registry.registerTask(null));
    }

    @Test
    void unknownTaskIdShouldThrow() {
        Registry registry = new Registry();
        assertThrows(NoSuchElementException.class, () -> registry.findTask(UUID.randomUUID()));
    }

    @Test
    void nullTaskIdShouldThrow() {
        Registry registry = new Registry();
        assertThrows(NullPointerException.class, () -> registry.findTask(null));
    }

    @Test
    void onlyTheTasksOfTheRequiredOperationShouldBeReturned() {
        Registry registry = new Registry();
        Operation A = new Operation("A");
        registry.registerOperation(A);
        Operation B = new Operation("B");
        registry.registerOperation(B);

        Task A1 = new Task("A1", A);
        registry.registerTask(A1);
        Task A2 = new Task("A2", A);
        registry.registerTask(A2);
        Task B1 = new Task("B1", B);
        registry.registerTask(B1);

        List<Task> tasksA = registry.findTasksByOperation(A.getId());
        assertEquals(2, tasksA.size());

        assertTrue(tasksA.contains(A1));
        assertTrue(tasksA.contains(A2));
        assertFalse(tasksA.contains(B1));
    }

    @Test
    void operationWithoutTasksShouldReturnEmptyList() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        assertEquals(List.of(), registry.findTasksByOperation(operation.getId()));
    }

    @Test
    void allTasksOfTheSameOperationShouldBeReturned() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        Task task1 = new Task("TestTask1", operation);
        registry.registerTask(task1);
        Task task2 = new Task("TestTask2", operation);
        registry.registerTask(task2);

        assertEquals(
                2,
                registry.findTasksByOperation(operation.getId()).size()
        );
    }

    @Test
    void attemptToModifyReturnedListShouldThrow() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        Task task1 = new Task("TestTask1", operation);
        registry.registerTask(task1);
        Task task2 = new Task("TestTask2", operation);
        registry.registerTask(task2);

        assertThrows(UnsupportedOperationException.class, () -> registry.findTasksByOperation(operation.getId()).clear());
    }

    @Test
    void taskWithUnregisteredOperationShouldNotBeRegistered() {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        Task task = new Task("TestTask1", operation);

        assertThrows(
                NoSuchElementException.class,
                () -> registry.registerTask(task)
        );

        assertThrows(
                NoSuchElementException.class,
                () -> registry.findTask(task.getId())
        );
    }

    @Test
    void concurrentRegisterTaskShouldThrow() throws InterruptedException {
        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);
        Task task = new Task("TestTask1", operation);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger exceptions = new AtomicInteger();


        Runnable r = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                registry.registerTask(task);
            } catch (IllegalStateException e) {
                exceptions.incrementAndGet();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);

        t1.start();
        t2.start();

        readyLatch.await();
        startLatch.countDown();
        t1.join();
        t2.join();

        assertEquals(1, exceptions.get());
        assertEquals(1, registry.findTasksByOperation(operation.getId()).size());
    }


    @Test
    void concurrentRegistrationDifferentTasksSameOperation()
            throws InterruptedException {

        Registry registry = new Registry();
        Operation operation = new Operation("TestOperation");
        registry.registerOperation(operation);

        Task task1 = new Task("TestTask1", operation);
        Task task2 = new Task("TestTask2", operation);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            readyLatch.countDown();

            try {
                startLatch.await();
                registry.registerTask(task1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Thread t2 = new Thread(() -> {
            readyLatch.countDown();

            try {
                startLatch.await();
                registry.registerTask(task2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        t1.start();
        t2.start();

        readyLatch.await();
        startLatch.countDown();

        t1.join();
        t2.join();

        assertEquals(
                2,
                registry.findTasksByOperation(operation.getId()).size()
        );
    }

    @Test
    void concurrentRegistrationOfSameOperationShouldThrow() throws InterruptedException {
        Registry registry = new Registry();
        Operation operation1 = new Operation("TestOperation1");

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger exceptions = new AtomicInteger();

        Thread t1 = new Thread(() -> {
            readyLatch.countDown();

            try {
                startLatch.await();
                registry.registerOperation(operation1);
            } catch (IllegalStateException e) {
                exceptions.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exceptions.incrementAndGet();
                throw new RuntimeException(e);
            }
        });

        Thread t2 = new Thread(() -> {
            readyLatch.countDown();

            try {
                startLatch.await();
                registry.registerOperation(operation1);
            } catch (IllegalStateException e) {
                exceptions.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        t1.start();
        t2.start();

        readyLatch.await();
        startLatch.countDown();

        t1.join();
        t2.join();

        assertEquals(
                1,
                exceptions.get()
        );
    }
}