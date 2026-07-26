package io.nikitoo0os.event;

@FunctionalInterface
public interface GhostWorkLifecycleEventListener {
    void onEvent(GhostWorkLifecycleEvent event);
}
