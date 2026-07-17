//package io.nikitoo0os;
//
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public final class WrappedExecutorService {
//    private final ExecutorService executorService;
//    public WrappedExecutorService(Runnable runnable) {
//         this.executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "Thread"));
//    }
//}
