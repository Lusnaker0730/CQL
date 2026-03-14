package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.cql.DependencyAnalysisService.DependencyAnalysisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyAnalysisServiceTest {

    @Mock
    private CqlLibraryRepository libraryRepository;

    @InjectMocks
    private DependencyAnalysisService service;

    private CqlLibraryEntity createLibrary(String name, String version, List<String> deps) {
        CqlLibraryEntity entity = new CqlLibraryEntity();
        entity.setName(name);
        entity.setVersion(version);
        entity.setDependencyList(deps);
        return entity;
    }

    // ===== Invalid input =====

    @Test
    void analyze_invalidLibraryId_noDash_shouldReturnEmpty() {
        DependencyAnalysisResult result = service.analyze("nodash");
        assertThat(result.getDependencies()).isEmpty();
        assertThat(result.isHasIssues()).isFalse();
    }

    @Test
    void analyze_libraryNotFound_shouldReturnEmpty() {
        when(libraryRepository.findByNameAndVersion("TestLib", "1.0.0")).thenReturn(Optional.empty());

        DependencyAnalysisResult result = service.analyze("TestLib-1.0.0");
        assertThat(result.getDependencies()).isEmpty();
    }

    // ===== No dependencies =====

    @Test
    void analyze_noDependencies_shouldReturnEmpty() {
        CqlLibraryEntity lib = createLibrary("TestLib", "1.0.0", List.of());
        when(libraryRepository.findByNameAndVersion("TestLib", "1.0.0")).thenReturn(Optional.of(lib));

        DependencyAnalysisResult result = service.analyze("TestLib-1.0.0");
        assertThat(result.getDependencies()).isEmpty();
        assertThat(result.isHasIssues()).isFalse();
    }

    // ===== Version match =====

    @Test
    void analyze_versionMatch_shouldResolveCorrectly() {
        CqlLibraryEntity main = createLibrary("MainLib", "1.0.0",
                List.of("FHIRHelpers version '4.0.1'"));
        CqlLibraryEntity dep = createLibrary("FHIRHelpers", "4.0.1", List.of());

        when(libraryRepository.findByNameAndVersion("MainLib", "1.0.0")).thenReturn(Optional.of(main));
        when(libraryRepository.findByNameAndVersion("FHIRHelpers", "4.0.1")).thenReturn(Optional.of(dep));

        DependencyAnalysisResult result = service.analyze("MainLib-1.0.0");

        assertThat(result.getDependencies()).hasSize(1);
        assertThat(result.getDependencies().get(0).getName()).isEqualTo("FHIRHelpers");
        assertThat(result.getDependencies().get(0).isAvailable()).isTrue();
        assertThat(result.getDependencies().get(0).isVersionMatch()).isTrue();
        assertThat(result.isHasIssues()).isFalse();
    }

    // ===== Version mismatch =====

    @Test
    void analyze_versionMismatch_shouldReportMismatch() {
        CqlLibraryEntity main = createLibrary("MainLib", "1.0.0",
                List.of("FHIRHelpers version '4.0.1'"));
        CqlLibraryEntity depDifferentVersion = createLibrary("FHIRHelpers", "4.0.2", List.of());

        when(libraryRepository.findByNameAndVersion("MainLib", "1.0.0")).thenReturn(Optional.of(main));
        when(libraryRepository.findByNameAndVersion("FHIRHelpers", "4.0.1")).thenReturn(Optional.empty());
        when(libraryRepository.findByName("FHIRHelpers")).thenReturn(List.of(depDifferentVersion));

        DependencyAnalysisResult result = service.analyze("MainLib-1.0.0");

        assertThat(result.getDependencies()).hasSize(1);
        assertThat(result.getDependencies().get(0).isVersionMatch()).isFalse();
        assertThat(result.getMismatches()).isNotEmpty();
        assertThat(result.isHasIssues()).isTrue();
    }

    // ===== Transitive dependencies =====

    @Test
    void analyze_transitiveDependencies_shouldResolve() {
        CqlLibraryEntity main = createLibrary("MainLib", "1.0.0",
                List.of("HelperLib version '2.0.0'"));
        CqlLibraryEntity helper = createLibrary("HelperLib", "2.0.0",
                List.of("FHIRHelpers version '4.0.1'"));
        CqlLibraryEntity fhirHelpers = createLibrary("FHIRHelpers", "4.0.1", List.of());

        when(libraryRepository.findByNameAndVersion("MainLib", "1.0.0")).thenReturn(Optional.of(main));
        when(libraryRepository.findByNameAndVersion("HelperLib", "2.0.0")).thenReturn(Optional.of(helper));
        when(libraryRepository.findByNameAndVersion("FHIRHelpers", "4.0.1")).thenReturn(Optional.of(fhirHelpers));

        DependencyAnalysisResult result = service.analyze("MainLib-1.0.0");

        assertThat(result.getDependencies()).hasSize(1);
        assertThat(result.getDependencies().get(0).getTransitiveDeps()).hasSize(1);
        assertThat(result.getDependencies().get(0).getTransitiveDeps().get(0).getName()).isEqualTo("FHIRHelpers");
    }

    // ===== Conflict detection =====

    @Test
    void analyze_conflictingVersions_shouldDetectConflict() {
        CqlLibraryEntity main = createLibrary("MainLib", "1.0.0",
                List.of("LibA version '1.0.0'", "LibB version '1.0.0'"));
        CqlLibraryEntity libA = createLibrary("LibA", "1.0.0",
                List.of("FHIRHelpers version '4.0.1'"));
        CqlLibraryEntity libB = createLibrary("LibB", "1.0.0",
                List.of("FHIRHelpers version '3.0.0'"));
        CqlLibraryEntity fh401 = createLibrary("FHIRHelpers", "4.0.1", List.of());
        CqlLibraryEntity fh300 = createLibrary("FHIRHelpers", "3.0.0", List.of());

        when(libraryRepository.findByNameAndVersion("MainLib", "1.0.0")).thenReturn(Optional.of(main));
        when(libraryRepository.findByNameAndVersion("LibA", "1.0.0")).thenReturn(Optional.of(libA));
        when(libraryRepository.findByNameAndVersion("LibB", "1.0.0")).thenReturn(Optional.of(libB));
        when(libraryRepository.findByNameAndVersion("FHIRHelpers", "4.0.1")).thenReturn(Optional.of(fh401));
        when(libraryRepository.findByNameAndVersion("FHIRHelpers", "3.0.0")).thenReturn(Optional.of(fh300));

        DependencyAnalysisResult result = service.analyze("MainLib-1.0.0");

        assertThat(result.getConflicts()).isNotEmpty();
        assertThat(result.getConflicts().get(0).getLibraryName()).isEqualTo("FHIRHelpers");
        assertThat(result.getConflicts().get(0).getSeverity()).isEqualTo("error"); // major version diff
        assertThat(result.isHasIssues()).isTrue();
    }

    // ===== Circular reference protection =====

    @Test
    void analyze_circularDependency_shouldNotInfiniteLoop() {
        CqlLibraryEntity main = createLibrary("LibA", "1.0.0",
                List.of("LibB version '1.0.0'"));
        CqlLibraryEntity libB = createLibrary("LibB", "1.0.0",
                List.of("LibA version '1.0.0'"));

        when(libraryRepository.findByNameAndVersion("LibA", "1.0.0")).thenReturn(Optional.of(main));
        when(libraryRepository.findByNameAndVersion("LibB", "1.0.0")).thenReturn(Optional.of(libB));

        // Should not throw or infinite loop
        DependencyAnalysisResult result = service.analyze("LibA-1.0.0");

        assertThat(result.getDependencies()).hasSize(1);
    }
}
