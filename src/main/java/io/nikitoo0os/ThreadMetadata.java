package io.nikitoo0os;

public record ThreadMetadata(
        String name,
        long id,
        boolean virtual
) {
    public static ThreadMetadata current() {
        Thread thread = Thread.currentThread();
        return new ThreadMetadata(
                thread.getName(),
                thread.threadId(),
                thread.isVirtual()
        );
    }
}
