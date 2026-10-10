package com.cqlplatform.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** BUG-145 — elapsed time comes from the monotonic clock, so it does not care what the wall clock does. */
class StopwatchTest {

    @Test
    void elapsedIsTheDistanceOnTheNanoClock_inWholeMilliseconds() {
        AtomicLong nanos = new AtomicLong(5_000_000_000L);
        Stopwatch stopwatch = Stopwatch.start(nanos::get);

        assertThat(stopwatch.elapsedMs()).isZero();
        nanos.addAndGet(1_234_999_999L);
        assertThat(stopwatch.elapsedMs()).isEqualTo(1234L); // truncated, never rounded up
        nanos.addAndGet(1L);
        assertThat(stopwatch.elapsedMs()).isEqualTo(1235L);
    }

    @Test
    void aNegativeOrWrappingNanoOrigin_isFine_onlyDifferencesMatter() {
        // System.nanoTime() has an arbitrary origin: it may be negative, and it may overflow.
        AtomicLong nanos = new AtomicLong(-7_000_000L);
        Stopwatch negativeOrigin = Stopwatch.start(nanos::get);
        nanos.set(3_000_000L);
        assertThat(negativeOrigin.elapsedMs()).isEqualTo(10L);

        nanos.set(Long.MAX_VALUE - 1_000_000L);
        Stopwatch acrossOverflow = Stopwatch.start(nanos::get);
        nanos.set(Long.MIN_VALUE + 1_000_000L); // wrapped past Long.MAX_VALUE
        assertThat(acrossOverflow.elapsedMs()).isEqualTo(2L);
    }

    @Test
    void theRealClock_neverGoesBackwards() {
        Stopwatch stopwatch = Stopwatch.start();
        long previous = stopwatch.elapsedMs();
        for (int i = 0; i < 10_000; i++) {
            long now = stopwatch.elapsedMs();
            assertThat(now).isGreaterThanOrEqualTo(previous);
            previous = now;
        }
        assertThat(previous).isGreaterThanOrEqualTo(0L);
    }
}
