package com.cqlplatform.util;

import com.cqlplatform.exception.CdsInvocationException;
import com.cqlplatform.model.debug.ErrorPhase;
import com.cqlplatform.model.debug.ExecutionErrorInfo;

import java.util.Arrays;
import java.util.List;

/**
 * Single classifier for all execution-failure responses (CDS, CQL editor,
 * eCQM). Walks the cause chain and inspects exception class simple names
 * plus messages to decide which {@link ErrorPhase} applies.
 *
 * <p>Previously this logic lived inside {@code CdsInvocationService} only;
 * the CQL editor and eCQM flows dumped failures into flat string lists
 * without classification, leaving EHR integrators and measure authors
 * without phase info. Consolidating here lets all three paths produce
 * identically-shaped debug output.
 *
 * <p>BUG-115 fix preserved: classifier walks the cause chain (depth-capped)
 * and scans message text because translator failures get wrapped in
 * {@code CqlExecutionException} and the outer simple name alone doesn't
 * reveal them.
 */
public final class ExecutionErrorClassifier {

    private static final int MAX_CAUSE_DEPTH = 10;
    private static final int STACK_FRAME_LIMIT = 5;
    private static final String OWN_PACKAGE_PREFIX = "com.cqlplatform";

    private static final List<String> TRANSLATION_CLASS_MARKERS =
            List.of("Translation", "Parse", "Syntax", "Compiler", "Lexer");
    private static final List<String> TRANSLATION_MESSAGE_MARKERS =
            List.of("CQL translation failed", "translation error", "parse error");

    private ExecutionErrorClassifier() {}

    /**
     * Classify a throwable into an {@link ErrorPhase}. Heuristic order:
     * <ol>
     *   <li>Translation markers on class name or message anywhere in the cause chain
     *       → {@link ErrorPhase#CQL_TRANSLATION}
     *   <li>Otherwise {@link ErrorPhase#CQL_EXECUTION} (the conservative default for
     *       failures escaping the CQL engine). Callers with richer context — e.g. a
     *       CDS prefetch failure caught before engine invocation — should pass an
     *       explicit phase to {@link #buildErrorInfo(ErrorPhase, Throwable)}.
     * </ol>
     */
    public static ErrorPhase classify(Throwable t) {
        if (t == null) {
            return ErrorPhase.UNKNOWN;
        }
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            String cls = current.getClass().getSimpleName();
            for (String marker : TRANSLATION_CLASS_MARKERS) {
                if (cls.contains(marker)) {
                    return ErrorPhase.CQL_TRANSLATION;
                }
            }
            String msg = current.getMessage();
            if (msg != null) {
                for (String marker : TRANSLATION_MESSAGE_MARKERS) {
                    if (msg.contains(marker)) {
                        return ErrorPhase.CQL_TRANSLATION;
                    }
                }
            }
            current = current.getCause();
            depth++;
        }
        return ErrorPhase.CQL_EXECUTION;
    }

    /** Classify + build. Convenience for the common case where the caller has no phase hint. */
    public static ExecutionErrorInfo buildErrorInfo(Throwable t) {
        return buildErrorInfo(classify(t), t);
    }

    /**
     * Build with an explicit phase — use this when the catch site knows the
     * phase directly (e.g. CDS prefetch resolution, card generation) and
     * doesn't want the default classify() heuristic to second-guess it.
     */
    public static ExecutionErrorInfo buildErrorInfo(ErrorPhase phase, Throwable t) {
        if (t == null) {
            return ExecutionErrorInfo.builder()
                    .phase(phase != null ? phase.wireName() : ErrorPhase.UNKNOWN.wireName())
                    .build();
        }
        List<String> frames = Arrays.stream(t.getStackTrace())
                .filter(f -> f.getClassName().startsWith(OWN_PACKAGE_PREFIX))
                .limit(STACK_FRAME_LIMIT)
                .map(StackTraceElement::toString)
                .toList();
        return ExecutionErrorInfo.builder()
                .phase((phase != null ? phase : ErrorPhase.UNKNOWN).wireName())
                .errorType(t.getClass().getSimpleName())
                .message(t.getMessage())
                .stackTraceSummary(frames.isEmpty() ? null : frames)
                .build();
    }

    /**
     * Map the legacy {@link CdsInvocationException.Phase} enum onto the shared
     * {@link ErrorPhase} enum. Keeps CDS code paths that still use the inner
     * enum wire-compatible during the transition.
     */
    public static ErrorPhase fromCdsPhase(CdsInvocationException.Phase p) {
        if (p == null) {
            return ErrorPhase.UNKNOWN;
        }
        return switch (p) {
            case PREFETCH_RESOLUTION -> ErrorPhase.PREFETCH_RESOLUTION;
            case PREFETCH_PROVIDER_BUILD -> ErrorPhase.PREFETCH_PROVIDER_BUILD;
            case CQL_TRANSLATION -> ErrorPhase.CQL_TRANSLATION;
            case CQL_EXECUTION -> ErrorPhase.CQL_EXECUTION;
            case CARD_GENERATION -> ErrorPhase.CARD_GENERATION;
            case UNKNOWN -> ErrorPhase.UNKNOWN;
        };
    }
}
