package com.example.aideveloper;

import java.util.concurrent.atomic.AtomicBoolean;

/** NEW: observes actual surfaced failures only; no polling loop and no silent repair loop. */
public final class ErrorMonitor {
    public interface Listener {
        void onRepairableError(String message);
        void onCleared();
    }

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Listener listener;

    public ErrorMonitor(Listener listener) {
        this.listener = listener;
    }

    public void recordActualError(Throwable error) {
        if (error == null || !active.compareAndSet(false, true)) return;
        listener.onRepairableError(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    public void clearWhenResolved() {
        if (active.compareAndSet(true, false)) listener.onCleared();
    }
}