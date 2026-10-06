package com.cqlplatform.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** PAT-253 — the one edit-lock rule shared by measures, test cases and CQL libraries. */
class EditLockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 12, 0);

    @Test
    void noHolderOrNoTimestamp_isNotALock() {
        assertThat(EditLock.isActive(null, NOW, 30, NOW)).isFalse();
        assertThat(EditLock.isActive("alice", null, 30, NOW)).isFalse();
        assertThat(EditLock.isHeldByOther(null, null, "bob", 30)).isFalse();
        assertThat(EditLock.expiresAt(null, 30)).isNull();
    }

    @Test
    void activeUntilTheTimeoutPasses_thenReadsAsUnlocked() {
        LocalDateTime takenAt = NOW.minusMinutes(29);
        assertThat(EditLock.isActive("alice", takenAt, 30, NOW)).isTrue();
        assertThat(EditLock.isActive("alice", takenAt, 30, NOW.plusMinutes(1))).isTrue();   // exactly at expiry: still held
        assertThat(EditLock.isActive("alice", takenAt, 30, NOW.plusMinutes(2))).isFalse();  // past it: gone
        assertThat(EditLock.expiresAt(takenAt, 30)).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void heldByOther_isTrueOnlyForAnActiveLockOfSomeoneElse() {
        LocalDateTime fresh = LocalDateTime.now().minusMinutes(1);
        assertThat(EditLock.isHeldByOther("alice", fresh, "bob", 30)).isTrue();
        assertThat(EditLock.isHeldByOther("alice", fresh, "alice", 30)).isFalse();
        assertThat(EditLock.isHeldByOther("alice", fresh, null, 30)).isTrue();                 // an anonymous caller is never the holder
        assertThat(EditLock.isHeldByOther("alice", LocalDateTime.now().minusMinutes(31), "bob", 30)).isFalse(); // expired
    }
}
