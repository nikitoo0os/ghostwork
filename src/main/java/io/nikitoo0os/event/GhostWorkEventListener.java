package io.nikitoo0os.event;

@FunctionalInterface
public interface GhostWorkEventListener {

    void onEvent(GhostWorkEvent event);
}