package com.cqlplatform.testsupport;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * PAT-265 — a small seeded property / fuzz harness on plain JUnit 5.
 *
 * <p>A property test here is one {@code @Test} that generates many random inputs and checks an
 * invariant for each. {@link #forAll} runs {@code tries} inputs; every try has its own
 * {@link Random} seeded from the base seed plus the try index, so a failure names exactly one
 * seed and the input it produced. Re-run that single input with
 * <pre>
 *   mvn -f backend/pom.xml test -Dtest=XxxFuzzTest -Dfuzz.seed=&lt;seed&gt; -Dfuzz.tries=1
 * </pre>
 * and search harder than the default with a bigger {@code -Dfuzz.tries}.
 *
 * <p>Why not jqwik: jqwik 1.10.x is built on JUnit Platform 1.14 and its release notes announce the
 * next line will move to JUnit Platform 6; this project runs Platform 6.0 (Spring Boot 4.1), so the
 * jqwik engine would run against a platform it was not compiled for. Reproducible seeds and a plain
 * failure message are what the fuzz tests need, and they need no dependency.
 */
public final class Fuzz {

    /** System property: run only this seed (plus {@code fuzz.tries} − 1 following ones). */
    public static final String SEED_PROPERTY = "fuzz.seed";
    /** System property: how many tries to run; defaults to the test's own number, 1 with a fixed seed. */
    public static final String TRIES_PROPERTY = "fuzz.tries";

    private static final int MAX_DESCRIPTION = 4000;

    private Fuzz() {
    }

    @FunctionalInterface
    public interface Property<T> {
        void check(T input, Random random) throws Exception;
    }

    /**
     * Generates {@code tries} inputs from {@code generator} and checks {@code property} on each one.
     *
     * @param baseSeed     the seed of try #0 (try #i uses {@code baseSeed + i}); fixed per property so
     *                     a plain run is reproducible, overridden by {@code -Dfuzz.seed}
     * @param defaultTries how many inputs a plain run checks, overridden by {@code -Dfuzz.tries}
     */
    public static <T> void forAll(long baseSeed, int defaultTries, Function<Random, T> generator, Property<T> property) {
        Long fixedSeed = longProperty(SEED_PROPERTY);
        int tries = intProperty(TRIES_PROPERTY, fixedSeed != null ? 1 : defaultTries);
        long firstSeed = fixedSeed != null ? fixedSeed : baseSeed;
        for (int i = 0; i < tries; i++) {
            long seed = firstSeed + i;
            Random random = new Random(seed);
            T input = null;
            try {
                input = generator.apply(random);
                property.check(input, random);
            } catch (Throwable t) {
                throw new AssertionError(String.format(
                        "fuzz try #%d of %d failed (seed=%d); re-run this input alone with -D%s=%d -D%s=1%ninput: %s%ncause: %s",
                        i, tries, seed, SEED_PROPERTY, seed, TRIES_PROPERTY, describe(input), t), t);
            }
        }
    }

    /** A uniformly random integer in {@code [lo, hiInclusive]}. */
    public static int between(Random random, int lo, int hiInclusive) {
        if (hiInclusive < lo) throw new IllegalArgumentException("empty range " + lo + ".." + hiInclusive);
        return lo + random.nextInt(hiInclusive - lo + 1);
    }

    /** True with probability {@code p}. */
    public static boolean chance(Random random, double p) {
        return random.nextDouble() < p;
    }

    /** One of the given items (repeat an item to weight it; {@code null} is allowed). */
    @SafeVarargs
    public static <T> T pick(Random random, T... items) {
        return items[random.nextInt(items.length)];
    }

    public static <T> T pick(Random random, List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    /** A short printable description of a generated input, for the failure message. */
    public static String describe(Object input) {
        String text = String.valueOf(input);
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) + "… (" + text.length() + " chars)" : text;
    }

    private static Long longProperty(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) return defaultValue;
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 1) throw new IllegalArgumentException(name + " must be >= 1, got " + parsed);
        return parsed;
    }
}
