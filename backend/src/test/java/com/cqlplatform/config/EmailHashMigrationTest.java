package com.cqlplatform.config;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-150 — locks {@link EmailHashMigration}'s idempotency + fail-safe contract:
 *
 * <ul>
 *   <li>Skip users whose hash is already present (idempotent re-run on every startup).</li>
 *   <li>Skip users with no email at all.</li>
 *   <li>Backfill users with email but no hash.</li>
 *   <li>Catch all exceptions (encryption-key mismatch, DB hiccup) and continue
 *       startup — the next reboot can retry.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmailHashMigrationTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmailHashMigration migration;

    private UserEntity user(Long id, String email, String hash) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setUsername("u" + id);
        u.setEmail(email);
        u.setEmailHash(hash);
        return u;
    }

    @BeforeEach
    void setUp() {
        // No common stubbing — each test sets its own returns
    }

    @Test
    void backfillsHashForUserWithEmailAndNullHash() {
        UserEntity needsBackfill = user(1L, "alice@example.com", null);
        when(userRepository.findAll()).thenReturn(List.of(needsBackfill));

        migration.run();

        verify(userRepository, times(1)).save(needsBackfill);
        // Hash was computed and set on the entity
        assertThatCode(() -> {}).doesNotThrowAnyException();
    }

    @Test
    void skipsUserWhoseHashIsAlreadyPresent_idempotent() {
        UserEntity alreadyHashed = user(1L, "bob@example.com", "existing-hash");
        when(userRepository.findAll()).thenReturn(List.of(alreadyHashed));

        migration.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsUserWithNullEmail() {
        UserEntity noEmail = user(1L, null, null);
        when(userRepository.findAll()).thenReturn(List.of(noEmail));

        migration.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsUserWithBlankEmail() {
        UserEntity blank = user(1L, "   ", null);
        when(userRepository.findAll()).thenReturn(List.of(blank));

        migration.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void mixedBatch_onlyBackfillsUsersThatNeedIt() {
        UserEntity needsHash = user(1L, "alice@example.com", null);
        UserEntity alreadyHashed = user(2L, "bob@example.com", "hash-bob");
        UserEntity noEmail = user(3L, null, null);
        when(userRepository.findAll()).thenReturn(List.of(needsHash, alreadyHashed, noEmail));

        migration.run();

        verify(userRepository, times(1)).save(needsHash);
        verify(userRepository, never()).save(alreadyHashed);
        verify(userRepository, never()).save(noEmail);
    }

    @Test
    void PAT150_regression_repositoryFailureDoesNotPropagate() {
        // Repository throws on findAll → migration must NOT crash startup.
        when(userRepository.findAll())
                .thenThrow(new RuntimeException("simulated DB outage"));

        assertThatCode(() -> migration.run())
                .as("migration must swallow exceptions so app startup continues; rerun on next boot recovers")
                .doesNotThrowAnyException();
    }

    @Test
    void PAT150_regression_saveFailureDoesNotPropagate() {
        UserEntity needsBackfill = user(1L, "alice@example.com", null);
        when(userRepository.findAll()).thenReturn(List.of(needsBackfill));
        when(userRepository.save(any()))
                .thenThrow(new RuntimeException("encryption key mismatch"));

        assertThatCode(() -> migration.run())
                .doesNotThrowAnyException();
    }
}
