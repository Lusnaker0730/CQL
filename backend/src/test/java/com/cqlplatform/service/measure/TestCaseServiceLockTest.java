package com.cqlplatform.service.measure;

import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.exception.ResourceLockedException;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-253 — the test case edit lock: holder-only writes (update / delete / date shift) while the
 * lock is active, expiry lifts it, the holder or the measure owner releases it, and the DTO only
 * ever reports an active lock.
 */
@ExtendWith(MockitoExtension.class)
class TestCaseServiceLockTest {

    @Mock private TestCaseRepository repository;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private TestCaseValidationService validationService;

    @InjectMocks private TestCaseService service;

    @BeforeEach
    void timeout() {
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 30);
    }

    private static TestCaseEntity testCase(String lockedBy, LocalDateTime lockedAt) {
        TestCaseEntity e = TestCaseEntity.builder().id(5L).measureDefinitionId(10L).title("TC")
                .patientBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[]}").build();
        e.setLockedBy(lockedBy);
        e.setLockedAt(lockedAt);
        return e;
    }

    @Test
    void lock_takesTheLockForTheCaller_andTheDtoReportsIt() {
        when(repository.findById(5L)).thenReturn(Optional.of(testCase(null, null)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestCase locked = service.lock(5L, "alice");

        assertThat(locked.getLockedBy()).isEqualTo("alice");
        assertThat(locked.getLockedAt()).isNotNull();
        assertThat(locked.getLockExpiresAt()).isEqualTo(locked.getLockedAt().plusMinutes(30));
    }

    @Test
    void lock_whileSomeoneElseHoldsIt_is409_andTheHolderMayRefresh() {
        when(repository.findById(5L)).thenReturn(Optional.of(testCase("alice", LocalDateTime.now().minusMinutes(5))));

        assertThatThrownBy(() -> service.lock(5L, "bob"))
                .isInstanceOf(ResourceLockedException.class)
                .hasMessageContaining("Test case 5 is locked by alice");
        verify(repository, never()).save(any());

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TestCase refreshed = service.lock(5L, "alice");
        assertThat(refreshed.getLockedBy()).isEqualTo("alice");
    }

    @Test
    void update_delete_shift_byAnotherUser_areRefusedWhileLocked_andAllowedOnceExpired() {
        TestCaseEntity held = testCase("alice", LocalDateTime.now().minusMinutes(1));
        when(repository.findById(5L)).thenReturn(Optional.of(held));
        TestCase body = TestCase.builder().title("TC").patientBundleJson(held.getPatientBundleJson()).build();

        assertThatThrownBy(() -> service.update(5L, body, "bob")).isInstanceOf(ResourceLockedException.class);
        assertThatThrownBy(() -> service.update(5L, body)).isInstanceOf(ResourceLockedException.class); // anonymous = not the holder
        assertThatThrownBy(() -> service.delete(5L, "bob")).isInstanceOf(ResourceLockedException.class);
        assertThatThrownBy(() -> service.shiftDates(5L, 1, "bob")).isInstanceOf(ResourceLockedException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(anyLong());

        // 31 minutes later the lock has lapsed: the write goes through
        held.setLockedAt(LocalDateTime.now().minusMinutes(31));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TestCase updated = service.update(5L, body, "bob");
        assertThat(updated.getTitle()).isEqualTo("TC");
        assertThat(updated.getLockedBy()).isNull(); // an expired lock is not reported
    }

    @Test
    void shiftAllDates_isAllOrNothing_whenOneCaseIsLockedByAnotherUser() {
        TestCaseEntity free = testCase(null, null);
        free.setId(6L);
        TestCaseEntity held = testCase("alice", LocalDateTime.now());
        when(repository.findByMeasureDefinitionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(free, held));

        assertThatThrownBy(() -> service.shiftAllDates(10L, 1, "bob")).isInstanceOf(ResourceLockedException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void unlock_byTheHolderOrTheMeasureOwner_releases_anyoneElseIsRefused() {
        TestCaseEntity held = testCase("alice", LocalDateTime.now());
        when(repository.findById(5L)).thenReturn(Optional.of(held));
        when(definitionService.getById(10L)).thenReturn(Optional.of(MeasureDefinition.builder().id(10L).ownerUsername("owner").build()));

        assertThatThrownBy(() -> service.unlock(5L, "bob"))
                .isInstanceOf(ResourceLockedException.class)
                .hasMessageContaining("only the lock holder or the measure owner");

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.unlock(5L, "owner").getLockedBy()).isNull();

        held.setLockedBy("alice");
        held.setLockedAt(LocalDateTime.now());
        assertThat(service.unlock(5L, "alice").getLockedBy()).isNull();
    }
}
