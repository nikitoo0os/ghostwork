package io.nikitoo0os.scheduling;

@FunctionalInterface
public interface ScheduleEventListener {
    void onEvent(ScheduleEvent event);
}
