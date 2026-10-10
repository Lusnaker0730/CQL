package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.exception.ResourceLockedException;
import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.TranslationMetadata;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-253 — the CQL library edit lock: a save that lands on the locked name + version, an update
 * and a delete are holder-only while the lock is active; the holder's lock follows a header edit
 * to the new name-version row; the holder or the owner releases it.
 */
@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceLockTest {

    @Mock private CqlTranslationService translationService;
    @Mock private CqlLibraryRepository libraryRepository;

    @InjectMocks private CqlLibraryService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(7L);
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 30);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static CqlLibraryEntity library(String version, String lockedBy, LocalDateTime lockedAt) {
        CqlLibraryEntity e = CqlLibraryEntity.builder().id(1L).name("Lib").version(version).cqlContent("library Lib version '" + version + "'")
                .ownerUsername("owner").tenantId(7L).build();
        e.setLockedBy(lockedBy);
        e.setLockedAt(lockedAt);
        return e;
    }

    private static CqlTranslationResponse translated(String version) {
        return CqlTranslationResponse.builder().success(true).elmJson("{}").errors(List.of()).warnings(List.of())
                .metadata(TranslationMetadata.builder().libraryId("Lib").libraryVersion(version).includes(List.of()).build())
                .build();
    }

    @Test
    void lock_thenWritesByOthersAre409_andTheDtoReportsTheActiveLock() {
        CqlLibraryEntity lib = library("1.0", null, null);
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "Lib", "1.0")).thenReturn(Optional.of(lib));
        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqlLibrary locked = service.lockLibrary("Lib-1.0", "alice");
        assertThat(locked.getLockedBy()).isEqualTo("alice");
        assertThat(locked.getLockExpiresAt()).isEqualTo(locked.getLockedAt().plusMinutes(30));

        assertThatThrownBy(() -> service.updateLibrary("Lib-1.0", "library Lib version '1.0'", null, "bob"))
                .isInstanceOf(ResourceLockedException.class).hasMessageContaining("Library Lib-1.0 is locked by alice");
        assertThatThrownBy(() -> service.deleteLibrary("Lib-1.0", "bob")).isInstanceOf(ResourceLockedException.class);
        assertThatThrownBy(() -> service.deleteLibrary("Lib-1.0")).isInstanceOf(ResourceLockedException.class); // anonymous
        verify(libraryRepository, never()).delete(any());
    }

    @Test
    void saveThatLandsOnALockedNameVersion_isRefusedForOthers_allowedForTheHolder() {
        CqlLibraryEntity lib = library("1.0", "alice", LocalDateTime.now());
        when(translationService.translate(any())).thenReturn(translated("1.0"));
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "Lib", "1.0")).thenReturn(Optional.of(lib));

        assertThatThrownBy(() -> service.saveLibrary("library Lib version '1.0'", "d", "bob")).isInstanceOf(ResourceLockedException.class);
        assertThatThrownBy(() -> service.saveLibrary("library Lib version '1.0'", "d")).isInstanceOf(ResourceLockedException.class);
        verify(libraryRepository, never()).save(any());

        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.saveLibrary("library Lib version '1.0'", "d", "alice").getLockedBy()).isEqualTo("alice");
    }

    @Test
    void expiredLock_doesNotBlock_andIsNotReported() {
        CqlLibraryEntity lib = library("1.0", "alice", LocalDateTime.now().minusMinutes(31));
        when(translationService.translate(any())).thenReturn(translated("1.0"));
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "Lib", "1.0")).thenReturn(Optional.of(lib));
        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqlLibrary saved = service.updateLibrary("Lib-1.0", "library Lib version '1.0'", null, "bob");
        assertThat(saved.getLockedBy()).isNull();
    }

    @Test
    void headerEditByTheHolder_movesTheLockToTheNewNameVersionRow() {
        CqlLibraryEntity old = library("1.0", "alice", LocalDateTime.now());
        CqlLibraryEntity fresh = library("2.0", null, null);
        fresh.setId(2L);
        when(translationService.translate(any())).thenReturn(translated("2.0"));
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "Lib", "1.0")).thenReturn(Optional.of(old));
        // the save creates the 2.0 row; the follow-up lookup finds it
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "Lib", "2.0")).thenReturn(Optional.empty(), Optional.of(fresh));
        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CqlLibrary moved = service.updateLibrary("Lib-1.0", "library Lib version '2.0'", null, "alice");

        assertThat(moved.getId()).isEqualTo("Lib-2.0");
        assertThat(moved.getLockedBy()).isEqualTo("alice");
        verify(libraryRepository).delete(old);
    }

    @Test
    void unlock_byHolderOrOwner_releases_anyoneElseIsRefused() {
        CqlLibraryEntity lib = library("1.0", "alice", LocalDateTime.now());
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "Lib", "1.0")).thenReturn(Optional.of(lib));

        assertThatThrownBy(() -> service.unlockLibrary("Lib-1.0", "bob"))
                .isInstanceOf(ResourceLockedException.class).hasMessageContaining("only the lock holder or the owner");

        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.unlockLibrary("Lib-1.0", "owner").getLockedBy()).isNull();
        lib.setLockedBy("alice");
        lib.setLockedAt(LocalDateTime.now());
        assertThat(service.unlockLibrary("Lib-1.0", "alice").getLockedBy()).isNull();
    }
}
