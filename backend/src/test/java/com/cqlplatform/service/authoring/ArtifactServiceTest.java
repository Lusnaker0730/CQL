package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.model.authoring.ArtifactRequest;
import com.cqlplatform.model.authoring.ArtifactResponse;
import com.cqlplatform.model.authoring.ArtifactSummary;
import com.cqlplatform.repository.CdsArtifactRepository;
import com.cqlplatform.repository.CdsExternalCqlLibraryRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private ArtifactService service;

    // BUG-134: every artifact lookup is tenant-scoped. With TenantContext set,
    // effectiveTenantId() returns early and never touches tenantRepository.
    private static final Long TENANT = 7L;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

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
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));

        Optional<ArtifactResponse> result = service.getById(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test");
    }

    @Test
    void getById_notFound_shouldReturnEmpty() {
        when(artifactRepository.findByIdAndTenantId(999L, TENANT)).thenReturn(Optional.empty());
        assertThat(service.getById(999L)).isEmpty();
    }

    // ===== update =====

    @Test
    void update_asOwner_shouldSucceed() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));
        when(artifactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArtifactRequest request = new ArtifactRequest();
        request.setName("Updated");

        ArtifactResponse result = service.update(1L, request, "owner");
        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void update_asNonOwner_shouldThrow() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));

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
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));

        service.delete(1L, "owner");

        verify(externalCqlRepository).deleteByArtifactId(1L);
        verify(artifactRepository).deleteById(1L);
    }

    @Test
    void delete_asNonOwner_shouldThrow() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(1L, "otherUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== duplicate =====

    @Test
    void duplicate_shouldCreateCopyWithSuffix() {
        CdsArtifactEntity entity = createEntity(1L, "Original", "owner");
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));
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
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.duplicate(1L, "otherUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== listByOwner =====

    @Test
    void listByOwner_shouldReturnSummaries() {
        CdsArtifactEntity entity = createEntity(1L, "Test", "owner");
        when(artifactRepository.findByOwnerUsernameAndTenantId("owner", TENANT)).thenReturn(List.of(entity));

        List<ArtifactSummary> result = service.listByOwner("owner");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test");
    }

    // ===== Tenant boundary (BUG-134) =====
    //
    // These lock the actual fix: every lookup must carry the caller's tenant, so an artifact
    // owned by another tenant is simply invisible. That invisibility is what confines
    // OwnershipVerifier's ROLE_ADMIN bypass (which never consults TenantContext) to the
    // caller's own tenant — the bypass itself is deliberately left intact so that a clinic
    // ADMIN can still administer their own clinic's artifacts.

    @Test
    void getById_shouldScopeLookupToCallersTenant() {
        // A foreign tenant's artifact is not found — the id-only findById is never used.
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.empty());

        assertThat(service.getById(1L)).isEmpty();
        verify(artifactRepository, never()).findById(any());
    }

    @Test
    void create_shouldStampCallersTenant() {
        ArtifactRequest request = new ArtifactRequest();
        request.setName("New");
        when(artifactRepository.save(any(CdsArtifactEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(request, "owner");

        ArgumentCaptor<CdsArtifactEntity> saved = ArgumentCaptor.forClass(CdsArtifactEntity.class);
        verify(artifactRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void duplicate_shouldKeepCopyInTheSameTenant() {
        CdsArtifactEntity original = createEntity(1L, "Test", "owner");
        original.setTenantId(TENANT);
        when(artifactRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(original));
        when(artifactRepository.save(any(CdsArtifactEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.duplicate(1L, "owner");

        ArgumentCaptor<CdsArtifactEntity> copy = ArgumentCaptor.forClass(CdsArtifactEntity.class);
        verify(artifactRepository).save(copy.capture());
        assertThat(copy.getValue().getTenantId()).isEqualTo(TENANT);
    }
}
