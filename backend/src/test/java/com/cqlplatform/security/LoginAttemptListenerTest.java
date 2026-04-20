package com.cqlplatform.security;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptListenerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LoginAttemptListener listener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "maxAttempts", 5);
        ReflectionTestUtils.setField(listener, "lockoutDurationMinutes", 30L);
    }

    /** Builds an event shaped like what Spring Security fires on bad password. */
    private AuthenticationFailureBadCredentialsEvent failureEvent(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, "wrong");
        return new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("nope"));
    }

    private AuthenticationSuccessEvent successEvent(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, "correct");
        return new AuthenticationSuccessEvent(auth);
    }

    private UserEntity user(String username, int attempts, LocalDateTime lockoutUntil) {
        return UserEntity.builder()
                .id(1L)
                .username(username)
                .failedLoginAttempts(attempts)
                .lockoutUntil(lockoutUntil)
                .build();
    }

    @Test
    void firstFailure_shouldSetCounterToOne_noLockout() {
        var u = user("alice", 0, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        listener.onAuthenticationFailure(failureEvent("alice"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getLockoutUntil()).isNull();
    }

    @Test
    void failureBelowThreshold_incrementsWithoutLock() {
        var u = user("alice", 3, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        listener.onAuthenticationFailure(failureEvent("alice"));

        verify(userRepository).save(argThat(saved ->
                saved.getFailedLoginAttempts() == 4 && saved.getLockoutUntil() == null));
    }

    @Test
    void failureAtThreshold_setsLockoutUntil() {
        // After this failure, attempts becomes 5 — crosses threshold → lockout set.
        var u = user("alice", 4, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        LocalDateTime before = LocalDateTime.now();
        listener.onAuthenticationFailure(failureEvent("alice"));
        LocalDateTime after = LocalDateTime.now().plusMinutes(30).plusSeconds(1);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getLockoutUntil())
                .isNotNull()
                .isBetween(before.plusMinutes(30).minusSeconds(1), after);
    }

    @Test
    void failureOnUnknownUser_shouldNotTouchRepository() {
        // Guard against attackers sprinkling random usernames to create ghost rows.
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        listener.onAuthenticationFailure(failureEvent("ghost"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void successfulLogin_clearsPriorFailureState() {
        var u = user("alice", 3, LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        listener.onAuthenticationSuccess(successEvent("alice"));

        verify(userRepository).save(argThat(saved ->
                saved.getFailedLoginAttempts() == 0 && saved.getLockoutUntil() == null));
    }

    @Test
    void successfulLoginOnCleanRecord_shouldNotWriteAgain() {
        // Hot path optimization: most successful logins hit counter=0, no state to clear.
        var u = user("alice", 0, null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        listener.onAuthenticationSuccess(successEvent("alice"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void nullUsername_shouldBeNoop() {
        // Guard: some auth paths might fire an event without a principal (odd but
        // possible). Don't blow up, don't query.
        var auth = new UsernamePasswordAuthenticationToken(null, "x");
        var event = new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("nope"));
        listener.onAuthenticationFailure(event);
        verify(userRepository, never()).findByUsername(any());
    }
}
