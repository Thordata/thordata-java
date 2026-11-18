package com.thordata.sdk;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation signal used by asynchronous Web Scraper operations. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Object monitor = new Object();

    public boolean cancel() {
        boolean changed = cancelled.compareAndSet(false, true);
        if (changed) {
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }
        return changed;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    void await(long milliseconds) throws InterruptedException {
        if (milliseconds <= 0 || cancelled.get()) {
            return;
        }
        synchronized (monitor) {
            if (!cancelled.get()) {
                monitor.wait(milliseconds);
            }
        }
    }
}
