package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-253 — the measure set is the lineage: create opens one, a new version keeps it, a rename
 * follows into it, history / version uniqueness / supersede-on-approve and the access settings
 * (sharing, owner, access level) all work across the set rather than the name.
 */
@ExtendWith(MockitoExtension.class)
class MeasureDefinitionServiceMeasureSetTest {

    @Mock private MeasureDefinitionRepository repository;
    @Mock private MeasureAuditRepository auditRepository;
    @Mock private NotificationService notificationService;
    @Mock private OwnershipVerifier ownershipVerifier;
    @Mock private com.cqlplatform.repository.MeasureScheduleRepository scheduleRepository;
    @Mock private com.cqlplatform.repository.TestCaseRepository testCaseRepository;
    @Mock private ApprovalReadinessService readinessService;
    @Mock private MeasureSetService measureSetService;

    @InjectMocks private MeasureDefinitionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 30);
        TenantContext.setCurrentTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static MeasureDefinitionEntity entity(Long id, String name, String version, Long setId, String status) {
        return MeasureDefinitionEntity.builder().id(id).name(name).version(version).status(status).scoringType("cohort")
                .ownerUsername("owner").tenantId(7L).measureSetId(setId).accessLevel("private")
                .sharedWithList(new ArrayList<>()).build();
    }

    @Test
    void create_opensANewSetForTheMeasure() {
        when(ownershipVerifier.getCurrentUsername()).thenReturn("owner");
        when(repository.existsByTenantIdAndNameAndVersion(7L, "Fresh", "1.0.0")).thenReturn(false);
        when(measureSetService.createFor(7L, "Fresh")).thenReturn(99L);
        when(repository.save(any())).thenAnswer(inv -> { MeasureDefinitionEntity e = inv.getArgument(0); e.setId(1L); return e; });
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        MeasureDefinition created = service.create(MeasureDefinition.builder().name("Fresh").version("1.0.0")
                .scoringType("cohort").measureSetId(123L) /* body value is ignored */ .build());

        assertThat(created.getMeasureSetId()).isEqualTo(99L);
    }

    @Test
    void createVersionAs_keepsTheSet_andChecksTheVersionWithinTheSetNotTheName() {
        MeasureDefinitionEntity v1 = entity(1L, "Renamed", "1.0.0", 5L, "active");
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(v1));
        when(repository.existsByMeasureSetIdAndVersion(5L, "1.1.0")).thenReturn(true);

        assertThatThrownBy(() -> service.createVersionAs(1L, "1.1.0"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Version already exists");
        verify(repository, never()).existsByTenantIdAndNameAndVersion(any(), anyString(), anyString());

        when(repository.existsByMeasureSetIdAndVersion(5L, "2.0.0")).thenReturn(false);
        ArgumentCaptor<MeasureDefinitionEntity> saved = ArgumentCaptor.forClass(MeasureDefinitionEntity.class);
        when(repository.save(saved.capture())).thenAnswer(inv -> { MeasureDefinitionEntity e = inv.getArgument(0); e.setId(2L); return e; });

        MeasureDefinition v2 = service.createVersionAs(1L, "2.0.0");

        assertThat(v2.getMeasureSetId()).isEqualTo(5L);
        assertThat(saved.getValue().getMeasureSetId()).isEqualTo(5L);
        assertThat(v2.getStatus()).isEqualTo("draft");
    }

    @Test
    void update_renamingAVersion_renamesTheSet_butNotWhenTheNameIsUnchanged() {
        MeasureDefinitionEntity draft = entity(1L, "Old", "1.0.0", 5L, "draft");
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(draft));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        service.update(1L, MeasureDefinition.builder().name("Old").version("1.0.0").scoringType("cohort").build(), "owner");
        verify(measureSetService, never()).rename(any(), any(), any());

        service.update(1L, MeasureDefinition.builder().name("New").version("1.0.0").scoringType("cohort").build(), "owner");
        verify(measureSetService).rename(5L, 7L, "New");
    }

    @Test
    void history_isTheSet_soARenamedVersionStaysInIt_andANamesakeOutsideItDoesNot() {
        MeasureDefinitionEntity v1 = entity(1L, "A", "1.0.0", 5L, "retired");
        MeasureDefinitionEntity v2 = entity(2L, "B", "2.0.0", 5L, "active");
        when(repository.findByTenantIdAndMeasureSetId(7L, 5L)).thenReturn(List.of(v1, v2));

        List<MeasureDefinition> history = service.getHistory(MeasureDefinition.builder().id(2L).name("B").measureSetId(5L).build());

        assertThat(history).extracting(MeasureDefinition::getVersion).containsExactly("2.0.0", "1.0.0");
        verify(repository, never()).findByTenantIdAndName(any(), anyString());
    }

    @Test
    void history_fallsBackToTheName_forRowsWithoutASet() {
        when(repository.findByTenantIdAndName(7L, "Legacy")).thenReturn(List.of(entity(1L, "Legacy", "1.0.0", null, "active")));

        assertThat(service.getHistory(MeasureDefinition.builder().id(1L).name("Legacy").build())).hasSize(1);
    }

    @Test
    void share_unshare_transfer_access_applyToEveryVersionOfTheSet() {
        MeasureDefinitionEntity v1 = entity(1L, "A", "1.0.0", 5L, "active");
        MeasureDefinitionEntity v2 = entity(2L, "A", "2.0.0", 5L, "draft");
        when(repository.findByIdAndTenantId(1L, 7L)).thenReturn(Optional.of(v1));
        when(repository.findByTenantIdAndMeasureSetId(7L, 5L)).thenReturn(List.of(v1, v2));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        service.shareMeasure(1L, "reviewer", "owner");
        assertThat(v2.getSharedWithList()).containsExactly("reviewer");
        assertThat(v2.getAccessLevel()).isEqualTo("shared");

        service.setAccessLevel(1L, "public", "owner");
        assertThat(v2.getAccessLevel()).isEqualTo("public");

        service.transferOwnership(1L, "newOwner", "owner");
        assertThat(v2.getOwnerUsername()).isEqualTo("newOwner");

        service.unshareMeasure(1L, "reviewer", "newOwner");
        assertThat(v2.getSharedWithList()).isEmpty();
    }

    @Test
    void approve_retiresTheOtherActiveVersionOfTheSet_evenWhenItWasRenamed() {
        MeasureDefinitionEntity old = entity(1L, "OldName", "1.0.0", 5L, "active");
        MeasureDefinitionEntity next = entity(2L, "NewName", "2.0.0", 5L, "in-review");
        next.setSharedWithList(new ArrayList<>(List.of("reviewer")));
        when(repository.findByIdAndTenantId(2L, 7L)).thenReturn(Optional.of(next));
        when(repository.findByTenantIdAndMeasureSetId(7L, 5L)).thenReturn(List.of(old, next));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any())).thenReturn(MeasureAuditEntity.builder().build());

        service.approveMeasure(2L, "reviewer");

        assertThat(next.getStatus()).isEqualTo("active");
        assertThat(old.getStatus()).isEqualTo("retired");
        verify(repository, never()).findByTenantIdAndName(any(), anyString());
    }
}
