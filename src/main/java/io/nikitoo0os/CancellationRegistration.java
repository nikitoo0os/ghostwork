package io.nikitoo0os;

@FunctionalInterface
public interface CancellationRegistration extends AutoCloseable {
    @Override
    void close();
}
