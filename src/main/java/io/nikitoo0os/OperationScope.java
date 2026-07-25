package io.nikitoo0os;

@FunctionalInterface
public interface OperationScope extends AutoCloseable {
    @Override
    void close();
}
