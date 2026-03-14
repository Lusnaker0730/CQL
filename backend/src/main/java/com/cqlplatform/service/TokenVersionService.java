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
        int newVersion = loadFromDb(username);
        log.info("Bumped token version for user {} to {}", username, newVersion);
        return newVersion;
    }

    /**
     * Evicts a single user from the cache (e.g. after external DB change).
     */
    public void evict(String username) {
        cache.invalidate(username);
    }

    private int loadFromDb(String username) {
        return userRepository.findTokenVersionByUsername(username).orElse(-1);
    }
}
