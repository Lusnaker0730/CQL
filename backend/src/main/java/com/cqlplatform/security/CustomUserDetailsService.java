package com.cqlplatform.security;

import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String password = user.getPassword() != null ? user.getPassword() : "{noop}SSO_USER_NO_PASSWORD";

        // Password lockout: if lockout_until is set and still in the future, mark the
        // account as LOCKED so Spring Security's DaoAuthenticationProvider throws
        // LockedException BEFORE running the password comparison. This means an
        // attacker can't even observe timing differences between "wrong password" and
        // "correct password on locked account" — both short-circuit equivalently.
        //
        // Expired lockouts are treated as unlocked here; the counter/timestamp stay
        // in the row until a successful login clears them (LoginAttemptListener).
        boolean accountNonLocked = user.getLockoutUntil() == null
                || user.getLockoutUntil().isBefore(LocalDateTime.now());

        return new User(
                user.getUsername(),
                password,
                user.getEnabled(),
                true,
                true,
                accountNonLocked,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
