package com.cqlplatform.util;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Elapsed time on the JVM's monotonic clock ({@link System#nanoTime()}).
 *
 * <p>{@code System.currentTimeMillis() - start} measures the distance between two readings of the
 * WALL clock, and the wall clock is adjusted while the program runs — NTP corrections, a VM
 * resyncing with its host after a pause, a manual change. When it steps backwards between the two
 * readings the "duration" is negative; when it steps forwards it is inflated. BUG-145 was exactly
 * that: a negative evaluation duration violated the {@code >= 0} CHECK on
 * {@code measure_report.evaluation_duration_ms} and the whole report was rejected. Anything that is
 * a DURATION — especially one that is stored — should come from here.
 */
public final class Stopwatch {

    private final LongSupplier nanoClock;
    private final long startNanos;

    private Stopwatch(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        this.startNanos = nanoClock.getAsLong();
    }

    public static Stopwatch start() {
        return new Stopwatch(System::nanoTime);
    }

    /** For tests: a stopwatch over a controllable nanosecond source. */
    static Stopwatch start(LongSupplier nanoClock) {
        return new Stopwatch(nanoClock);
    }

    /** Whole milliseconds since {@link #start()}. nanoTime may be negative or wrap; only differences mean anything. */
    public long elapsedMs() {
        return TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - startNanos);
    }
}
