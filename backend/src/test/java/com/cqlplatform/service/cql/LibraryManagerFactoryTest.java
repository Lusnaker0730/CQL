package com.cqlplatform.service.cql;

import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural invariants on the CQL translator options produced by
 * {@link LibraryManagerFactory}. These tests exist to lock in safety-critical
 * compiler settings that, if silently dropped, would reintroduce the
 * dispatch-ambiguity family of runtime bugs (BUG-110 / BUG-112 lineage).
 */
class LibraryManagerFactoryTest {

    /**
     * Translator must emit overload signatures in ELM for any function with &gt;1 overload
     * (e.g. {@code FHIRHelpers.ToString}, {@code FHIRHelpers.ToDateTime},
     * {@code FHIRHelpers.ToInterval}). Without this, the engine resolves the overload
     * at runtime by argument type — which fails on null or base-type arguments.
     */
    @Test
    void defaultOptions_mustSetSignatureLevelToOverloadsOrWider() {
        LibraryManager mgr = LibraryManagerFactory.create(null);
        CqlCompilerOptions opts = mgr.getCqlCompilerOptions();

        LibraryBuilder.SignatureLevel level = opts.getSignatureLevel();
        assertThat(level)
                .as("SignatureLevel must be Overloads or All to prevent dispatch-ambiguity bugs "
                        + "on multi-overload FHIRHelpers functions")
                .isIn(LibraryBuilder.SignatureLevel.Overloads, LibraryBuilder.SignatureLevel.All);
    }

    @Test
    void buildOptions_mustSetSignatureLevelToOverloadsOrWider() {
        CqlCompilerOptions opts = LibraryManagerFactory.buildOptions(true, true, true, false);

        LibraryBuilder.SignatureLevel level = opts.getSignatureLevel();
        assertThat(level)
                .isIn(LibraryBuilder.SignatureLevel.Overloads, LibraryBuilder.SignatureLevel.All);
    }
}
