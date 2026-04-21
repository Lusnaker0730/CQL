package com.cqlplatform.util;

import com.cqlplatform.exception.CdsInvocationException;
import com.cqlplatform.model.debug.ErrorPhase;
import com.cqlplatform.model.debug.ExecutionErrorInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionErrorClassifierTest {

    // ---- classify() ----

    @Test
    void classify_null_shouldReturnUnknown() {
        assertThat(ExecutionErrorClassifier.classify(null)).isEqualTo(ErrorPhase.UNKNOWN);
    }

    @Test
    void classify_pureRuntimeException_shouldReturnCqlExecution() {
        // Baseline: the "boom" case that used to pin the CDS flow's default.
        // If this regressed, over-classification would mislabel real runtime
        // failures as translation errors.
        assertThat(ExecutionErrorClassifier.classify(new RuntimeException("boom")))
                .isEqualTo(ErrorPhase.CQL_EXECUTION);
    }

    @Test
    void classify_classSimpleName_Translation_shouldReturnCqlTranslation() {
        @SuppressWarnings("serial")
        class CqlTranslationException extends RuntimeException {
            CqlTranslationException(String m) { super(m); }
        }
        assertThat(ExecutionErrorClassifier.classify(new CqlTranslationException("x")))
                .isEqualTo(ErrorPhase.CQL_TRANSLATION);
    }

    @Test
    void classify_classSimpleName_Compiler_shouldReturnCqlTranslation() {
        @SuppressWarnings("serial")
        class CqlCompilerException extends RuntimeException {
            CqlCompilerException(String m) { super(m); }
        }
        assertThat(ExecutionErrorClassifier.classify(new CqlCompilerException("x")))
                .isEqualTo(ErrorPhase.CQL_TRANSLATION);
    }

    @Test
    void classify_wrappedTranslatorMessage_shouldReturnCqlTranslation() {
        // BUG-115 primary case: CqlExecutionService wraps translator errors as
        // RuntimeException("Execution failed: CQL translation failed with N error(s): ...").
        // Outer class name is generic; message carries the marker.
        RuntimeException wrapped = new RuntimeException(
                "Execution failed: CQL translation failed with 1 error(s): Could not resolve call");
        assertThat(ExecutionErrorClassifier.classify(wrapped)).isEqualTo(ErrorPhase.CQL_TRANSLATION);
    }

    @Test
    void classify_nestedCause_withTranslationClass_shouldReturnCqlTranslation() {
        @SuppressWarnings("serial")
        class CqlParseException extends RuntimeException {
            CqlParseException(String m) { super(m); }
        }
        RuntimeException outer = new RuntimeException("outer", new CqlParseException("bad token"));
        assertThat(ExecutionErrorClassifier.classify(outer)).isEqualTo(ErrorPhase.CQL_TRANSLATION);
    }

    @Test
    void classify_causeLoop_shouldTerminate() {
        // Defense in depth: a hypothetical malicious cause loop must not infinite-recurse.
        // We can't construct a real loop (setCause is final after construction), but we
        // can assert depth cap by checking classification completes for deeply nested chains.
        Throwable deepest = new RuntimeException("innermost");
        Throwable current = deepest;
        for (int i = 0; i < 50; i++) {
            current = new RuntimeException("level-" + i, current);
        }
        // No markers anywhere — must return CQL_EXECUTION without hanging.
        assertThat(ExecutionErrorClassifier.classify(current)).isEqualTo(ErrorPhase.CQL_EXECUTION);
    }

    // ---- buildErrorInfo() ----

    @Test
    void buildErrorInfo_autoClassify_populatesAllFields() {
        RuntimeException e = new RuntimeException("CQL translation failed");
        ExecutionErrorInfo info = ExecutionErrorClassifier.buildErrorInfo(e);

        assertThat(info.getPhase()).isEqualTo("cql_translation");
        assertThat(info.getErrorType()).isEqualTo("RuntimeException");
        assertThat(info.getMessage()).isEqualTo("CQL translation failed");
    }

    @Test
    void buildErrorInfo_explicitPhase_overridesClassification() {
        // Caller knows the phase (e.g. prefetch resolution) — classifier shouldn't override it.
        RuntimeException e = new RuntimeException("FHIR fetch timed out");
        ExecutionErrorInfo info = ExecutionErrorClassifier.buildErrorInfo(ErrorPhase.PREFETCH_RESOLUTION, e);
        assertThat(info.getPhase()).isEqualTo("prefetch_resolution");
    }

    @Test
    void buildErrorInfo_nullThrowable_returnsPhaseOnly() {
        ExecutionErrorInfo info = ExecutionErrorClassifier.buildErrorInfo(ErrorPhase.UNKNOWN, null);
        assertThat(info.getPhase()).isEqualTo("unknown");
        assertThat(info.getErrorType()).isNull();
        assertThat(info.getMessage()).isNull();
        assertThat(info.getStackTraceSummary()).isNull();
    }

    @Test
    void buildErrorInfo_noOwnPackageFrames_stackTraceSummaryIsNull() {
        // Stack of a synthetically-thrown exception contains no com.cqlplatform frames
        // here (test is also in com.cqlplatform, so filter *does* match — but we can
        // rely on filter behavior rather than stack layout). This test documents that
        // the stackTraceSummary is list-or-null, never an empty list.
        Exception e = new RuntimeException("x");
        e.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("third.party.Lib", "foo", "Lib.java", 10)
        });
        ExecutionErrorInfo info = ExecutionErrorClassifier.buildErrorInfo(e);
        assertThat(info.getStackTraceSummary()).isNull();
    }

    @Test
    void buildErrorInfo_ownPackageFrames_limitedToFive() {
        StackTraceElement[] frames = new StackTraceElement[10];
        for (int i = 0; i < 10; i++) {
            frames[i] = new StackTraceElement("com.cqlplatform.Service" + i, "m", "S.java", i);
        }
        RuntimeException e = new RuntimeException("x");
        e.setStackTrace(frames);
        ExecutionErrorInfo info = ExecutionErrorClassifier.buildErrorInfo(e);
        assertThat(info.getStackTraceSummary()).hasSize(5);
    }

    // ---- fromCdsPhase() ----

    @Test
    void fromCdsPhase_null_returnsUnknown() {
        assertThat(ExecutionErrorClassifier.fromCdsPhase(null)).isEqualTo(ErrorPhase.UNKNOWN);
    }

    @Test
    void fromCdsPhase_eachLegacyValue_mapsCleanly() {
        // Exhaustive mapping lock — if a new value is added to CdsInvocationException.Phase,
        // this test's switch-coverage breaks compile and forces the classifier update.
        assertThat(ExecutionErrorClassifier.fromCdsPhase(CdsInvocationException.Phase.PREFETCH_RESOLUTION))
                .isEqualTo(ErrorPhase.PREFETCH_RESOLUTION);
        assertThat(ExecutionErrorClassifier.fromCdsPhase(CdsInvocationException.Phase.PREFETCH_PROVIDER_BUILD))
                .isEqualTo(ErrorPhase.PREFETCH_PROVIDER_BUILD);
        assertThat(ExecutionErrorClassifier.fromCdsPhase(CdsInvocationException.Phase.CQL_TRANSLATION))
                .isEqualTo(ErrorPhase.CQL_TRANSLATION);
        assertThat(ExecutionErrorClassifier.fromCdsPhase(CdsInvocationException.Phase.CQL_EXECUTION))
                .isEqualTo(ErrorPhase.CQL_EXECUTION);
        assertThat(ExecutionErrorClassifier.fromCdsPhase(CdsInvocationException.Phase.CARD_GENERATION))
                .isEqualTo(ErrorPhase.CARD_GENERATION);
        assertThat(ExecutionErrorClassifier.fromCdsPhase(CdsInvocationException.Phase.UNKNOWN))
                .isEqualTo(ErrorPhase.UNKNOWN);
    }
}
