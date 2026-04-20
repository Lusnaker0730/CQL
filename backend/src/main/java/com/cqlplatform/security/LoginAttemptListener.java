package com.cqlplatform.security;

import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Tracks consecutive failed login attempts per user and locks the account after a
 * configurable threshold. Listens to Spring Security's built-in authentication events
 * rather than hooking into {@code AuthController.login} — this way SSO / OAuth /
 * any future alternative login path gets the same protection for free.
 *
 * <p>Key design choices:
 * <ul>
 *   <li><b>Per-account counter, not per-IP</b>: IP-based rate limiting is complementary
 *       (see RateLimitFilter) but doesn't protect against password spraying across
 *       many accounts from the same IP. Per-account also gives us the "locked for 30
 *       min" UX feedback users expect.</li>
 *   <li><b>Counter stored, not derived from logs</b>: log scraping would miss rotations
 *       and doesn't survive pod restarts; DB column is the single source of truth.</li>
 *   <li><b>Successful login always clears</b>: no partial reset; one valid login
 *       wipes the history so a legitimate user who mistyped 4 times doesn't carry
 *       that state forward.</li>
 *   <li><b>Expired lockouts stay in DB until next login</b>: we don't run cleanup
 *       cron. Reading the column is cheap (partial index), and keeping the expired
 *       row is useful forensically ("this account was locked at T, unlocked itself
 *       at T+30min, legitimate login at T+45min").</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptListener {

    private final UserRepository userRepository;

    /** Fail count at which we lock the account. Default 5. */
    @Value("${app.security.lockout.max-attempts:5}")
    private int maxAttempts;

    /** Duration of the lockout after hitting max-attempts. Default 30 minutes. */
    @Value("${app.security.lockout.duration-minutes:30}")
    private long lockoutDurationMinutes;

    @EventListener
    @Transactional
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication() != null
                ? event.getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            return;
        }

        // findByUsername — if the username doesn't exist we do nothing. Deliberately:
        // (1) we don't create a ghost row for unknown usernames (DoS by making us
        //     insert garbage rows for random inputs), and (2) Spring Security already
        //     hides user-existence via hide-user-not-found-exceptions.
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxAttempts) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockoutDurationMinutes);
                user.setLockoutUntil(lockUntil);
                log.warn("Account '{}' locked until {} after {} failed login attempts",
                        username, lockUntil, attempts);
            } else {
                log.info("Failed login for '{}' (attempt {} of {})", username, attempts, maxAttempts);
            }
            userRepository.save(user);
        });
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication() != null
                ? event.getAuthentication().getName()
                : null;
        if (username == null || username.isBlank()) {
            return;
        }

        userRepository.findByUsername(username).ifPresent(user -> {
            // Only write if state actually needs clearing — avoids pointless UPDATE
            // on every login (hot path). Most successful logins hit this branch with
            // counter=0 already.
            if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                    || user.getLockoutUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockoutUntil(null);
                userRepository.save(user);
            }
        });
    }
}
