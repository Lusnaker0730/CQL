package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * PAT-193 / #697: after Spring init wires the default-tenant resolver, a provider
     * created by the static factory must resolve null-TenantContext lookups within the
     * DEFAULT tenant (never unscoped), and the tenant lookup must be memoized.
     */
    @Test
    void createContext_wiresDefaultTenantResolver_scopedAndMemoized() {
        TenantRepository tenantRepo = mock(TenantRepository.class);
        when(tenantRepo.findByCode("default"))
                .thenReturn(Optional.of(TenantEntity.builder().id(42L).code("default").build()));
        CqlLibraryRepository libRepo = mock(CqlLibraryRepository.class);
        CqlLibraryEntity lib = new CqlLibraryEntity();
        lib.setName("MyLib");
        lib.setVersion("1.0.0");
        lib.setCqlContent("library MyLib version '1.0.0'\n");
        when(libRepo.findByTenantIdAndNameAndVersion(42L, "MyLib", "1.0.0"))
                .thenReturn(Optional.of(lib));

        new LibraryManagerFactory(tenantRepo).init();
        LibraryManagerFactory.LibraryContext ctx = LibraryManagerFactory.createContext(libRepo);

        VersionedIdentifier vid = new VersionedIdentifier().withId("MyLib").withVersion("1.0.0");
        assertThat(ctx.databaseProvider.getLibrarySource(vid)).isNotNull();
        assertThat(ctx.databaseProvider.getLibrarySource(vid)).isNotNull();

        verify(libRepo, times(2)).findByTenantIdAndNameAndVersion(42L, "MyLib", "1.0.0");
        verify(libRepo, never()).findByNameAndVersion("MyLib", "1.0.0");
        // Memoized: two lookups, but the tenant table is asked only once.
        verify(tenantRepo, times(1)).findByCode("default");
    }
}
