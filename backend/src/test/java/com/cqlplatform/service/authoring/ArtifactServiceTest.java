package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.model.authoring.ArtifactRequest;
import com.cqlplatform.model.authoring.ArtifactResponse;
import com.cqlplatform.model.authoring.ArtifactSummary;
import com.cqlplatform.repository.CdsArtifactRepository;
import com.cqlplatform.repository.CdsExternalCqlLibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock
    private CdsArtifactRepository artifactRepository;

    @Mock
    private CdsExternalCqlLibraryRepository externalCqlRepository;

    @Mock
    private ExpressionTreeValidator expressionTreeValidator;

    @InjectMocks
    private ArtifactService service;

    private CdsArtifactEntity createEntity(Long id, String name, String owner) {
        return CdsArtifactEntity.builder()
                .id(id)
                .name(name)
                .version("1.0.0")
                .status("Draft")
                .fhirVersion("R4")
                .ownerUsername(owner)
                .build();
    }

    // ===== create =====

    @Test
    void create_shouldSetOwnerAndSave() {
        ArtifactRequest request = new ArtifactRequest();
        request.setName("New Artifact");

        when(artifactRepository.save(any())).thenAnswer(inv -> {
            CdsArtifactEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        ArtifactResponse result = service.create(request, "testuser");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Artifact");
        assertThat(result.getOwnerUsername()).isEqualTo("testuser");
        verify(artifactRepository).save(any());
    }

    // ===== getById =====

    @Test
    void getById_found_shouldReturnResponse() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));

        Optional<ArtifactResponse> result = service.getById(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test");
    }

    @Test
    void getById_notFound_shouldReturnEmpty() {
        when(artifactRepository.findById(999L)).thenReturn(Optional.empty());
        assertThat(service.getById(999L)).isEmpty();
    }

    // ===== update =====

    @Test
    void update_asOwner_shouldSucceed() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArtifactRequest request = new ArtifactRequest();
        request.setName("Updated");

        ArtifactResponse result = service.update(1L, request, "owner");
        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void update_asNonOwner_shouldThrow() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));

        ArtifactRequest request = new ArtifactRequest();
        request.setName("Updated");

        assertThatThrownBy(() -> service.update(1L, request, "otherUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== delete =====

    @Test
    void delete_asOwner_shouldCascadeExternalCql() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L, "owner");

        verify(externalCqlRepository).deleteByArtifactId(1L);
        verify(artifactRepository).deleteById(1L);
    }

    @Test
    void delete_asNonOwner_shouldThrow() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(1L, "otherUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== duplicate =====

    @Test
    void duplicate_shouldCreateCopyWithSuffix() {
        CdsArtifactEntity entity = createEntity(1L, "Original", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(artifactRepository.save(any())).thenAnswer(inv -> {
            CdsArtifactEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });

        ArtifactResponse result = service.duplicate(1L, "owner");

        assertThat(result.getName()).isEqualTo("Original (Copy)");
        assertThat(result.getOwnerUsername()).isEqualTo("owner");
        assertThat(result.getId()).isEqualTo(2L);
    }

    @Test
    void duplicate_asNonOwner_shouldThrow() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.duplicate(1L, "otherUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== listByOwner =====

    @Test
    void listByOwner_shouldReturnSummaries() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findByOwnerUsername("owner")).thenReturn(List.of(entity));

        List<ArtifactSummary> result = service.listByOwner("owner");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test");
    }
}
