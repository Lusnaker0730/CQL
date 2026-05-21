package com.cqlplatform.service;

import com.cqlplatform.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Maintains a short-lived local cache of user token versions so that
 * the JWT authentication filter can reject revoked access tokens without
 * hitting the database on every request.
 *
 * <p>Cache TTL (30 s) defines the maximum staleness window — after an admin
 * bumps a user's token version, existing access tokens become invalid within
 * at most 30 seconds.</p>
 */
@Service
@Slf4j
public class TokenVersionService {

    private final UserRepository userRepository;

    /** username → current token_version in DB */
    private final Cache<String, Integer> cache;

    public TokenVersionService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Returns the current token version for the given user.
     * Uses the local cache; falls back to DB on miss.
     */
    public int getCurrentVersion(String username) {
        return cache.get(username, this::loadFromDb);
    }

    /**
     * Atomically increments the user's token version in the DB and evicts
     * the cached value so subsequent checks pick up the new version.
     *
     * @return the new token version after increment
     */
    @Transactional
    public int bumpVersion(String username) {
        int updated = userRepository.incrementTokenVersion(username);
        if (updated == 0) {
            log.warn("Token version bump for unknown user: {}", username);
            return -1;
        }
        cache.invalidate(username);
        // Bypass the loadFromDb sentinel here — if user was hard-deleted in the tiny
        // window between increment and this read, MAX_VALUE would log misleadingly
        // as the new version number. Read the raw Optional and report -1 on miss.
        int newVersion = userRepository.findTokenVersionByUsername(username).orElse(-1);
        log.info("Bumped token version for user {} to {}", username, newVersion);
        return newVersion;
    }

    /**
     * Evicts a single user from the cache (e.g. after external DB change).
     */
    public void evict(String username) {
        cache.invalidate(username);
    }

    /**
     * Resolve the user's token version from the DB.
     *
     * <p>Returns {@link Integer#MAX_VALUE} when the user is missing — PAT-143 fix
     * for an auth-bypass on user deletion. {@link JwtAuthenticationFilter} compares
     * {@code claimVersion < currentVersion}; the previous sentinel of {@code -1} made
     * that check {@code claimVersion < -1 = false} for any normal claim, so a deleted
     * user's still-unexpired JWT continued to authenticate. {@code MAX_VALUE} flips
     * the comparison so any token from a missing user is treated as stale and rejected.
     */
    private int loadFromDb(String username) {
        return userRepository.findTokenVersionByUsername(username).orElse(Integer.MAX_VALUE);
    }
}
