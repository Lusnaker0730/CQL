package com.cqlplatform.security;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Locks in that the lockout_until column is honored by CustomUserDetailsService —
 * this is the critical "DaoAuthenticationProvider never even reaches password check"
 * path for a currently-locked account.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceLockoutTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    private UserEntity user(LocalDateTime lockoutUntil) {
        return UserEntity.builder()
                .id(1L)
                .username("alice")
                .password("{noop}x")
                .role(UserEntity.Role.USER)
                .enabled(true)
                .lockoutUntil(lockoutUntil)
                .build();
    }

    @Test
    void lockoutInFuture_shouldReturnAccountNonLockedFalse() {
        // Future lockout → Spring Security should throw LockedException before the
        // password compare. We assert the flag here; the throw is Spring's job.
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(LocalDateTime.now().plusMinutes(15))));
        var details = service.loadUserByUsername("alice");
        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void lockoutInPast_shouldTreatAsUnlocked() {
        // Expired lockouts allow login; the stale timestamp sits in DB until cleared
        // by a successful login (LoginAttemptListener). Acceptable state for the read path.
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(LocalDateTime.now().minusMinutes(1))));
        var details = service.loadUserByUsername("alice");
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void nullLockout_shouldBeUnlocked() {
        // Default state for a fresh / never-locked user. No state access needed.
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(null)));
        var details = service.loadUserByUsername("alice");
        assertThat(details.isAccountNonLocked()).isTrue();
    }
}
