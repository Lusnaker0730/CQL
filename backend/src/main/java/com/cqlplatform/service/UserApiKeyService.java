package com.cqlplatform.service;

import com.cqlplatform.entity.UserApiKeyEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserApiKeyRepository;
import com.cqlplatform.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserApiKeyService {

    private final UserApiKeyRepository repository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int LAST_USED_UPDATE_MINUTES = 15;

    /**
     * Generates a new API key. Returns the entity with the plaintext key temporarily
     * set in apiKey for the one-time response. The DB stores the SHA-256 hash.
     */
    @Transactional
    public UserApiKeyEntity generateApiKey(String username, String name) {
        String plaintextKey = generateSecureKey();
        String keyHash = hashApiKey(plaintextKey);
        String prefix = plaintextKey.substring(0, Math.min(8, plaintextKey.length()));

        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .username(username)
                .apiKey(keyHash)
                .keyPrefix(prefix)
                .name(name)
                .active(true)
                .build();

        entity = repository.save(entity);
        log.info("Generated API key '{}' for user '{}'", name, username);

        // Detach so the plaintext override below won't be flushed back to DB
        entityManager.detach(entity);
        entity.setApiKey(plaintextKey);
        return entity;
    }

    @Transactional
    public Optional<String> validateApiKey(String apiKey) {
        // Try hash lookup first (new keys)
        String keyHash = hashApiKey(apiKey);
        Optional<UserApiKeyEntity> entity = repository.findByApiKeyAndActiveTrue(keyHash);

        if (entity.isEmpty()) {
            // Fallback: try plaintext lookup (legacy unhashed keys)
            entity = repository.findByApiKeyAndActiveTrue(apiKey);
            if (entity.isPresent()) {
                // Upgrade legacy key to hashed (sets will be saved below)
                UserApiKeyEntity legacyKey = entity.get();
                legacyKey.setKeyPrefix(apiKey.substring(0, Math.min(8, apiKey.length())));
                legacyKey.setApiKey(keyHash);
                log.info("Upgraded legacy API key to hashed for user '{}'", legacyKey.getUsername());
            }
        }

        if (entity.isPresent()) {
            UserApiKeyEntity key = entity.get();

            // Defense-in-depth: reject key if owning user is disabled
            Optional<UserEntity> user = userRepository.findByUsername(key.getUsername());
            if (user.isEmpty() || !Boolean.TRUE.equals(user.get().getEnabled())) {
                log.warn("API key rejected: user '{}' is disabled or deleted", key.getUsername());
                return Optional.empty();
            }

            // Debounce lastUsedAt writes to reduce DB churn on hot auth path
            if (key.getLastUsedAt() == null ||
                    key.getLastUsedAt().plusMinutes(LAST_USED_UPDATE_MINUTES).isBefore(LocalDateTime.now())) {
                key.setLastUsedAt(LocalDateTime.now());
            }
            repository.save(key);
            return Optional.of(key.getUsername());
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<UserApiKeyEntity> listKeys(String username) {
        return repository.findByUsername(username);
    }

    @Transactional
    public boolean revokeKey(Long id, String username) {
        Optional<UserApiKeyEntity> entity = repository.findById(id);
        if (entity.isPresent() && entity.get().getUsername().equals(username)) {
            UserApiKeyEntity key = entity.get();
            key.setActive(false);
            repository.save(key);
            log.info("Revoked API key id={} for user '{}'", id, username);
            return true;
        }
        return false;
    }

    /**
     * Deactivates all active API keys for the given user (called when admin disables a user).
     */
    @Transactional
    public int deactivateAllKeys(String username) {
        int count = repository.deactivateAllByUsername(username);
        if (count > 0) {
            log.info("Deactivated {} API key(s) for disabled user '{}'", count, username);
        }
        return count;
    }

    private String generateSecureKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "cql_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashApiKey(String apiKey) {
        return com.cqlplatform.util.DigestUtils.sha256Hex(apiKey);
    }
}
