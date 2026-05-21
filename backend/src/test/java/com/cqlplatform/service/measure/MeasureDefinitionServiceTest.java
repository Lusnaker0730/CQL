package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.CqlTranslationException;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.service.NotificationService;
import com.cqlplatform.service.cql.CqlTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasureDefinitionServiceTest {

    @Mock
    private MeasureDefinitionRepository repository;

    @Mock
    private MeasureAuditRepository auditRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CqlTranslationService cqlTranslationService;

    @InjectMocks
    private MeasureDefinitionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 30);
    }

    private MeasureDefinitionEntity createEntity(Long id, String name, String version) {
        return MeasureDefinitionEntity.builder()
                .id(id)
                .name(name)
                .version(version)
                .status("draft")
                .scoringType("proportion")
                .ownerUsername("owner")
                .build();
    }

    // ===== CRUD =====

    @Test
    void create_newMeasure_shouldSucceed() {
        MeasureDefinition definition = MeasureDefinition.builder()
                .name("TestMeasure")
                .version("1.0.0")
                .build();

        when(repository.existsByNameAndVersion("TestMeasure", "1.0.0")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.create(definition);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("TestMeasure");
        verify(repository).save(any());
        verify(auditRepository).save(any());
    }

    @Test
    void create_duplicateMeasure_shouldThrow() {
        MeasureDefinition definition = MeasureDefinition.builder()
                .name("Existing")
                .version("1.0.0")
                .build();

        when(repository.existsByNameAndVersion("Existing", "1.0.0")).thenReturn(true);

        assertThatThrownBy(() -> service.create(definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getById_found_shouldReturnMeasure() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        Optional<MeasureDefinition> result = service.getById(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test");
    }

    @Test
    void getById_notFound_shouldReturnEmpty() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThat(service.getById(999L)).isEmpty();
    }

    @Test
    void delete_shouldRecordAudit() {
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());
        service.delete(1L);
        verify(repository).deleteById(1L);
        verify(auditRepository).save(any());
    }

    // ===== Versioning =====

    @Test
    void createVersion_major_shouldBump() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.2.3");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.existsByNameAndVersion("Test", "2.0.0")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(2L);
            return e;
        });

        MeasureDefinition result = service.createVersion(1L, "major");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
        assertThat(result.getStatus()).isEqualTo("draft");
    }

    @Test
    void createVersion_minor_shouldBump() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.2.3");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.existsByNameAndVersion("Test", "1.3.0")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(2L);
            return e;
        });

        MeasureDefinition result = service.createVersion(1L, "minor");
        assertThat(result.getVersion()).isEqualTo("1.3.0");
    }

    @Test
    void createVersion_patch_shouldBump() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.2.3");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.existsByNameAndVersion("Test", "1.2.4")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(2L);
            return e;
        });

        MeasureDefinition result = service.createVersion(1L, "patch");
        assertThat(result.getVersion()).isEqualTo("1.2.4");
    }

    @Test
    void createVersion_notFound_shouldThrow() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVersion(999L, "major"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ===== Locking =====

    @Test
    void lockMeasure_unlocked_shouldSucceed() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setLockedBy(null);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.lockMeasure(1L, "user1");
        assertThat(result.getLockedBy()).isEqualTo("user1");
    }

    @Test
    void lockMeasure_lockedByAnother_shouldThrow() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setLockedBy("otherUser");
        entity.setLockedAt(LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.lockMeasure(1L, "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already locked");
    }

    @Test
    void lockMeasure_expiredLock_shouldSucceed() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setLockedBy("otherUser");
        entity.setLockedAt(LocalDateTime.now().minusMinutes(60)); // expired
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.lockMeasure(1L, "user1");
        assertThat(result.getLockedBy()).isEqualTo("user1");
    }

    @Test
    void unlockMeasure_byLockHolder_shouldSucceed() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setLockedBy("user1");
        entity.setLockedAt(LocalDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.unlockMeasure(1L, "user1");
        assertThat(result.getLockedBy()).isNull();
    }

    @Test
    void unlockMeasure_byWrongUser_shouldThrow() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setLockedBy("user1");
        entity.setLockedAt(LocalDateTime.now());
        entity.setOwnerUsername("user1");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.unlockMeasure(1L, "wrongUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the lock holder or owner");
    }

    // ===== Sharing =====

    @Test
    void shareMeasure_shouldAddUser() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setOwnerUsername("owner");
        entity.setSharedWithList(new ArrayList<>());
        entity.setAccessLevel("private");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.shareMeasure(1L, "collaborator", "owner");
        assertThat(result.getSharedWith()).contains("collaborator");
        assertThat(result.getAccessLevel()).isEqualTo("shared");
    }

    @Test
    void shareMeasure_nonOwner_shouldThrow() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setOwnerUsername("owner");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.shareMeasure(1L, "collaborator", "notOwner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only the owner");
    }

    // ===== Workflow Transitions =====

    @Test
    void submitForReview_fromDraft_shouldSucceed() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setStatus("draft");
        entity.setOwnerUsername("owner");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.submitForReview(1L, "owner");
        assertThat(result.getStatus()).isEqualTo("in-review");
    }

    @Test
    void submitForReview_fromActive_shouldThrow() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setStatus("active");
        entity.setOwnerUsername("owner");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.submitForReview(1L, "owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void approveMeasure_fromInReview_shouldSucceed() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setStatus("in-review");
        entity.setOwnerUsername("owner");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.approveMeasure(1L, "owner");
        assertThat(result.getStatus()).isEqualTo("active");
    }

    @Test
    void rejectMeasure_fromInReview_shouldRevertToDraft() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setStatus("in-review");
        entity.setOwnerUsername("owner");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.rejectMeasure(1L, "Needs work", "owner");
        assertThat(result.getStatus()).isEqualTo("draft");
    }

    @Test
    void retireMeasure_fromActive_shouldSucceed() {
        MeasureDefinitionEntity entity = createEntity(1L, "Test", "1.0.0");
        entity.setStatus("active");
        entity.setOwnerUsername("owner");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition result = service.retireMeasure(1L, "owner");
        assertThat(result.getStatus()).isEqualTo("retired");
    }

    // ===== Audit =====

    @Test
    void getAuditTrail_shouldDelegateToRepository() {
        List<MeasureAuditEntity> audits = List.of(
                MeasureAuditEntity.builder().measureId(1L).action("CREATE").build());
        when(auditRepository.findByMeasureIdOrderByCreatedAtDesc(1L)).thenReturn(audits);

        List<MeasureAuditEntity> result = service.getAuditTrail(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("CREATE");
    }

    // =====================================================================
    // PAT-140 — preCompileElm error propagation (regression for silent-null bug)
    // =====================================================================

    @Test
    void create_withInvalidCql_shouldThrowAndNotPersist() {
        // Previously: translation failure silently logged a warn and stored null
        // elmJson, so the measure looked saved but evaluation failed later with
        // a cryptic error. PAT-140 propagates the error so the user sees it
        // immediately and the @Transactional save rolls back.
        CqlTranslationResponse failed = CqlTranslationResponse.builder()
                .success(false)
                .errors(List.of(CqlTranslationResponse.CqlError.builder()
                        .severity("ERROR").message("Syntax error")
                        .startLine(1).startColumn(1).endLine(1).endColumn(1).build()))
                .build();
        when(cqlTranslationService.translate(any())).thenReturn(failed);
        when(repository.existsByNameAndVersion(any(), any())).thenReturn(false);

        MeasureDefinition definition = MeasureDefinition.builder()
                .name("Bad")
                .version("1.0.0")
                .cqlContent("library Bad version '1.0.0'\ndefine \"X\": broken")
                .build();

        assertThatThrownBy(() -> service.create(definition))
                .isInstanceOf(CqlTranslationException.class)
                .hasMessageContaining("CQL translation failed")
                .satisfies(ex -> {
                    CqlTranslationException te = (CqlTranslationException) ex;
                    assertThat(te.getErrors()).hasSize(1);
                    assertThat(te.getErrors().get(0).getMessage()).isEqualTo("Syntax error");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void create_withValidCql_shouldStoreElmJson() {
        CqlTranslationResponse ok = CqlTranslationResponse.builder()
                .success(true).elmJson("{\"library\":{}}").build();
        when(cqlTranslationService.translate(any())).thenReturn(ok);
        when(repository.existsByNameAndVersion(any(), any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            MeasureDefinitionEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition definition = MeasureDefinition.builder()
                .name("Good")
                .version("1.0.0")
                .cqlContent("library Good version '1.0.0'\ndefine \"X\": true")
                .build();

        MeasureDefinition result = service.create(definition);
        assertThat(result.getElmJson()).isEqualTo("{\"library\":{}}");
    }

    @Test
    void update_withChangedCqlThatFailsTranslation_shouldThrowAndRollBack() {
        MeasureDefinitionEntity existing = createEntity(1L, "Test", "1.0.0");
        existing.setCqlContent("library old");
        existing.setElmJson("{\"old\":{}}");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        CqlTranslationResponse failed = CqlTranslationResponse.builder()
                .success(false)
                .errors(List.of(CqlTranslationResponse.CqlError.builder()
                        .severity("ERROR").message("Cannot resolve identifier")
                        .startLine(5).startColumn(1).endLine(5).endColumn(20).build()))
                .build();
        when(cqlTranslationService.translate(any())).thenReturn(failed);

        MeasureDefinition update = MeasureDefinition.builder()
                .id(1L)
                .name("Test")
                .version("1.0.0")
                .cqlContent("library Test version '1.0.0'\ndefine \"X\": missingThing")
                .build();

        assertThatThrownBy(() -> service.update(1L, update))
                .isInstanceOf(CqlTranslationException.class)
                .satisfies(ex -> assertThat(((CqlTranslationException) ex).getErrors())
                        .anyMatch(e -> "Cannot resolve identifier".equals(e.getMessage())));
        verify(repository, never()).save(any());
    }

    @Test
    void update_withUnchangedCql_shouldNotRetranslate() {
        // Avoid wasting a translation cycle when the user only edits metadata.
        MeasureDefinitionEntity existing = createEntity(1L, "Test", "1.0.0");
        existing.setCqlContent("library Test version '1.0.0'\ndefine \"X\": true");
        existing.setElmJson("{\"cached\":{}}");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MeasureDefinition update = MeasureDefinition.builder()
                .id(1L)
                .name("Test")
                .version("1.0.0")
                .description("only the description changed")
                .cqlContent("library Test version '1.0.0'\ndefine \"X\": true")
                .build();

        service.update(1L, update);

        verify(cqlTranslationService, never()).translate(any());
    }

    @Test
    void preCompileElm_translatorThrows_shouldWrapAsCqlTranslationException() {
        // If the translator itself crashes (NPE etc.), we still surface a
        // structured 400 error instead of a 500 stack trace.
        when(cqlTranslationService.translate(any()))
                .thenThrow(new RuntimeException("translator crashed"));
        when(repository.existsByNameAndVersion(any(), any())).thenReturn(false);

        MeasureDefinition definition = MeasureDefinition.builder()
                .name("Crashy")
                .version("1.0.0")
                .cqlContent("library Crashy version '1.0.0'")
                .build();

        assertThatThrownBy(() -> service.create(definition))
                .isInstanceOf(CqlTranslationException.class)
                .hasMessageContaining("translator crashed");
    }
}
