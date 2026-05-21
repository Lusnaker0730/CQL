package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmArtifactEntity;
import com.cqlplatform.model.ecqm.EcqmArtifactRequest;
import com.cqlplatform.model.ecqm.EcqmArtifactResponse;
import com.cqlplatform.model.ecqm.EcqmArtifactSummary;
import com.cqlplatform.repository.EcqmArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-level coverage for {@link EcqmArtifactService}: CRUD + ownership guard +
 * duplicate semantics. Repository is mocked so tests run without Spring context
 * or DB. PAT-139.
 */
@ExtendWith(MockitoExtension.class)
class EcqmArtifactServiceTest {

    @Mock
    private EcqmArtifactRepository repository;

    private EcqmArtifactService service;

    @BeforeEach
    void setUp() {
        service = new EcqmArtifactService(repository);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private EcqmArtifactEntity entity(Long id, String owner) {
        return EcqmArtifactEntity.builder()
                .id(id)
                .name("Test Measure")
                .version("1.0.0")
                .description("desc")
                .status("draft")
                .fhirVersion("4.0.1")
                .scoringType("proportion")
                .populationBasis("boolean")
                .improvementNotation("increase")
                .ownerUsername(owner)
                .build();
    }

    // ── listByOwner ─────────────────────────────────────────────────────

    @Test
    void listByOwner_shouldReturnMappedSummaries() {
        EcqmArtifactEntity e1 = entity(1L, "alice");
        EcqmArtifactEntity e2 = entity(2L, "alice");
        when(repository.findByOwnerUsername("alice")).thenReturn(List.of(e1, e2));

        List<EcqmArtifactSummary> result = service.listByOwner("alice");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EcqmArtifactSummary::getId).containsExactly(1L, 2L);
        assertThat(result).allMatch(s -> "alice".equals(s.getOwnerUsername()));
    }

    @Test
    void listByOwner_emptyResult() {
        when(repository.findByOwnerUsername("nobody")).thenReturn(List.of());

        assertThat(service.listByOwner("nobody")).isEmpty();
    }

    // ── getById ─────────────────────────────────────────────────────────

    @Test
    void getById_found_shouldReturnResponse() {
        when(repository.findById(42L)).thenReturn(Optional.of(entity(42L, "alice")));

        Optional<EcqmArtifactResponse> result = service.getById(42L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(42L);
        assertThat(result.get().getName()).isEqualTo("Test Measure");
    }

    @Test
    void getById_missing_shouldReturnEmpty() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThat(service.getById(999L)).isEmpty();
    }

    // ── create ──────────────────────────────────────────────────────────

    @Test
    void create_shouldAssignOwnerAndApplyDefaults() {
        EcqmArtifactRequest request = EcqmArtifactRequest.builder()
                .name("New Measure")
                .build();
        when(repository.save(any())).thenAnswer(inv -> {
            EcqmArtifactEntity e = inv.getArgument(0);
            e.setId(7L);
            return e;
        });

        EcqmArtifactResponse response = service.create(request, "alice");

        ArgumentCaptor<EcqmArtifactEntity> captor = ArgumentCaptor.forClass(EcqmArtifactEntity.class);
        verify(repository).save(captor.capture());
        EcqmArtifactEntity saved = captor.getValue();
        assertThat(saved.getOwnerUsername()).isEqualTo("alice");
        assertThat(saved.getName()).isEqualTo("New Measure");
        assertThat(saved.getVersion()).isEqualTo("1.0.0");           // default
        assertThat(saved.getStatus()).isEqualTo("draft");            // default
        assertThat(saved.getScoringType()).isEqualTo("proportion");  // default
        assertThat(saved.getPopulationBasis()).isEqualTo("boolean"); // default
        assertThat(response.getId()).isEqualTo(7L);
    }

    @Test
    void create_withCustomFields_shouldPreserveThem() {
        EcqmArtifactRequest request = EcqmArtifactRequest.builder()
                .name("CV Measure")
                .scoringType("continuous-variable")
                .populationBasis("Encounter")
                .version("2.5.0")
                .build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(request, "bob");

        ArgumentCaptor<EcqmArtifactEntity> captor = ArgumentCaptor.forClass(EcqmArtifactEntity.class);
        verify(repository).save(captor.capture());
        EcqmArtifactEntity saved = captor.getValue();
        assertThat(saved.getScoringType()).isEqualTo("continuous-variable");
        assertThat(saved.getPopulationBasis()).isEqualTo("Encounter");
        assertThat(saved.getVersion()).isEqualTo("2.5.0");
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    void update_partialFields_shouldOnlyChangeProvidedOnes() {
        // Lock the contract: only non-null request fields overwrite the entity.
        // Frontend relies on this to send incremental edits without clobbering
        // fields the user didn't touch.
        EcqmArtifactEntity existing = entity(5L, "alice");
        existing.setDescription("original description");
        existing.setRationale("original rationale");
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EcqmArtifactRequest req = EcqmArtifactRequest.builder()
                .description("new description")  // only this changes
                .build();
        EcqmArtifactResponse result = service.update(5L, req, "alice");

        assertThat(result.getDescription()).isEqualTo("new description");
        assertThat(result.getRationale()).isEqualTo("original rationale"); // untouched
    }

    @Test
    void update_listFields_shouldReplaceEntireList() {
        EcqmArtifactEntity existing = entity(5L, "alice");
        existing.setBaseElementsList(List.of(Map.of("name", "old")));
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> newBE = List.of(
                Map.of("name", "Element1"), Map.of("name", "Element2"));
        EcqmArtifactRequest req = EcqmArtifactRequest.builder()
                .baseElements(newBE)
                .build();
        EcqmArtifactResponse result = service.update(5L, req, "alice");

        assertThat(result.getBaseElements()).hasSize(2);
        assertThat(result.getBaseElements()).extracting(m -> m.get("name"))
                .containsExactly("Element1", "Element2");
    }

    @Test
    void update_byNonOwner_shouldThrow() {
        when(repository.findById(5L)).thenReturn(Optional.of(entity(5L, "alice")));

        EcqmArtifactRequest req = EcqmArtifactRequest.builder().description("x").build();

        assertThatThrownBy(() -> service.update(5L, req, "mallory"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
        verify(repository, never()).save(any());
    }

    @Test
    void update_missingArtifact_shouldThrow() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        EcqmArtifactRequest req = EcqmArtifactRequest.builder().build();

        assertThatThrownBy(() -> service.update(404L, req, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    void delete_byOwner_shouldDelete() {
        when(repository.findById(5L)).thenReturn(Optional.of(entity(5L, "alice")));

        service.delete(5L, "alice");

        verify(repository).deleteById(5L);
    }

    @Test
    void delete_byNonOwner_shouldThrow() {
        when(repository.findById(5L)).thenReturn(Optional.of(entity(5L, "alice")));

        assertThatThrownBy(() -> service.delete(5L, "mallory"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_missingArtifact_shouldThrow() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── duplicate ───────────────────────────────────────────────────────

    @Test
    void duplicate_shouldCopyFieldsAppendCopySuffixAndResetCmsId() {
        EcqmArtifactEntity original = entity(5L, "alice");
        original.setCmsMeasureId("CMS-123");
        original.setStatus("active");
        original.setBaseElementsList(List.of(Map.of("name", "be1")));
        when(repository.findById(5L)).thenReturn(Optional.of(original));
        when(repository.save(any())).thenAnswer(inv -> {
            EcqmArtifactEntity e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        EcqmArtifactResponse copy = service.duplicate(5L, "alice");

        ArgumentCaptor<EcqmArtifactEntity> captor = ArgumentCaptor.forClass(EcqmArtifactEntity.class);
        verify(repository).save(captor.capture());
        EcqmArtifactEntity saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Test Measure (Copy)");
        // cmsMeasureId is intentionally cleared on duplicate to avoid collisions
        // when the original is still active.
        assertThat(saved.getCmsMeasureId()).isNull();
        // Always reset to draft, regardless of original status.
        assertThat(saved.getStatus()).isEqualTo("draft");
        assertThat(saved.getOwnerUsername()).isEqualTo("alice");
        assertThat(saved.getBaseElementsList()).hasSize(1);
        assertThat(copy.getId()).isEqualTo(99L);
    }

    @Test
    void duplicate_byNonOwner_shouldThrow() {
        when(repository.findById(5L)).thenReturn(Optional.of(entity(5L, "alice")));

        assertThatThrownBy(() -> service.duplicate(5L, "mallory"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
        verify(repository, never()).save(any());
    }

    @Test
    void duplicate_listsAreCopiesNotReferences() {
        // The copy must not share the same list reference as the original — otherwise
        // editing one mutates the other in tests / batch operations.
        List<Map<String, Object>> originalBE = new java.util.ArrayList<>();
        originalBE.add(Map.of("name", "shared"));
        EcqmArtifactEntity original = entity(5L, "alice");
        original.setBaseElementsList(originalBE);
        when(repository.findById(5L)).thenReturn(Optional.of(original));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.duplicate(5L, "alice");

        ArgumentCaptor<EcqmArtifactEntity> captor = ArgumentCaptor.forClass(EcqmArtifactEntity.class);
        verify(repository).save(captor.capture());
        EcqmArtifactEntity saved = captor.getValue();
        assertThat(saved.getBaseElementsList()).isNotSameAs(originalBE);
    }

    @Test
    void duplicate_missingArtifact_shouldThrow() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicate(404L, "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
