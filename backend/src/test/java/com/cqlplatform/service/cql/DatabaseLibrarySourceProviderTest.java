package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.repository.CqlLibraryRepository;
import kotlinx.io.Source;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("DatabaseLibrarySourceProvider — exclusion + silent-fallback guards")
class DatabaseLibrarySourceProviderTest {

    private CqlLibraryRepository repo;
    private DatabaseLibrarySourceProvider provider;

    @BeforeEach
    void setUp() {
        repo = mock(CqlLibraryRepository.class);
        provider = new DatabaseLibrarySourceProvider(repo);
    }

    // ── Helpers ──
    private static VersionedIdentifier vid(String id, String version) {
        return new VersionedIdentifier().withId(id).withVersion(version);
    }

    private static CqlLibraryEntity entity(String name, String version, String content) {
        CqlLibraryEntity e = new CqlLibraryEntity();
        e.setName(name);
        e.setVersion(version);
        e.setCqlContent(content);
        return e;
    }

    @Test
    @DisplayName("returns source for a known library when not excluded")
    void returnsKnownLibrary() {
        when(repo.findByNameAndVersion("MyLib", "1.0.0"))
                .thenReturn(Optional.of(entity("MyLib", "1.0.0", "library MyLib version '1.0.0'\n")));

        Source out = provider.getLibrarySource(vid("MyLib", "1.0.0"));

        assertThat(out).isNotNull();
    }

    @Test
    @DisplayName("returns null for excluded identifier — no DB call happens")
    void excludedIdentifier_returnsNullAndDoesNotQueryRepo() {
        provider.excludeIdentifier(vid("MyLib", "1.0.0"));

        Source out = provider.getLibrarySource(vid("MyLib", "1.0.0"));

        assertThat(out).isNull();
        // Critical invariant: the repo is never asked; we don't even know what's in the DB
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("exclusion is identity-specific — other libraries still resolve")
    void exclusionIsPerIdentifier() {
        provider.excludeIdentifier(vid("LibA", "1.0.0"));
        when(repo.findByNameAndVersion("LibB", "1.0.0"))
                .thenReturn(Optional.of(entity("LibB", "1.0.0", "library LibB version '1.0.0'\n")));

        assertThat(provider.getLibrarySource(vid("LibA", "1.0.0"))).isNull();
        assertThat(provider.getLibrarySource(vid("LibB", "1.0.0"))).isNotNull();
    }

    @Test
    @DisplayName("exclusion is version-specific — other versions of the same name still resolve")
    void exclusionIsPerVersion() {
        provider.excludeIdentifier(vid("MyLib", "1.0.0"));
        when(repo.findByNameAndVersion("MyLib", "2.0.0"))
                .thenReturn(Optional.of(entity("MyLib", "2.0.0", "library MyLib version '2.0.0'\n")));

        assertThat(provider.getLibrarySource(vid("MyLib", "1.0.0"))).isNull();
        assertThat(provider.getLibrarySource(vid("MyLib", "2.0.0"))).isNotNull();
    }

    @Test
    @DisplayName("version-less lookup for an excluded name is also suppressed (defensive)")
    void versionLessLookupAgainstExcludedName() {
        provider.excludeIdentifier(vid("MyLib", "1.0.0"));

        // Even though we're asking for "any version of MyLib", we refuse — exclusion
        // lives at the name level when no version is specified.
        Source out = provider.getLibrarySource(vid("MyLib", null));
        assertThat(out).isNull();
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("clearExclusion removes exclusion; DB resolution resumes")
    void clearExclusion() {
        provider.excludeIdentifier(vid("MyLib", "1.0.0"));
        assertThat(provider.getLibrarySource(vid("MyLib", "1.0.0"))).isNull();

        provider.clearExclusion(vid("MyLib", "1.0.0"));
        when(repo.findByNameAndVersion("MyLib", "1.0.0"))
                .thenReturn(Optional.of(entity("MyLib", "1.0.0", "library MyLib version '1.0.0'\n")));

        assertThat(provider.getLibrarySource(vid("MyLib", "1.0.0"))).isNotNull();
    }

    @Test
    @DisplayName("returns null + skips DB for unknown + returns null for missing entity")
    void unknownLibrary_returnsNull() {
        when(repo.findByNameAndVersion("Missing", "1.0.0")).thenReturn(Optional.empty());

        assertThat(provider.getLibrarySource(vid("Missing", "1.0.0"))).isNull();
        verify(repo).findByNameAndVersion("Missing", "1.0.0");
    }

    @Test
    @DisplayName("latest-version resolution when no version specified")
    void latestVersionResolution() {
        when(repo.findByName("MyLib")).thenReturn(List.of(
                entity("MyLib", "1.0.0", "library MyLib version '1.0.0'\n"),
                entity("MyLib", "1.2.0", "library MyLib version '1.2.0'\n"),
                entity("MyLib", "1.1.5", "library MyLib version '1.1.5'\n")
        ));

        Source out = provider.getLibrarySource(vid("MyLib", null));
        assertThat(out).isNotNull();
    }
}
