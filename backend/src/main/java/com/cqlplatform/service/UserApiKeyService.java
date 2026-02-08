package com.cqlplatform.service;

import com.cqlplatform.entity.UserApiKeyEntity;
import com.cqlplatform.repository.UserApiKeyRepository;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public UserApiKeyEntity generateApiKey(String username, String name) {
        String key = generateSecureKey();

        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .username(username)
                .apiKey(key)
                .name(name)
                .active(true)
                .build();

        entity = repository.save(entity);
        log.info("Generated API key '{}' for user '{}'", name, username);
        return entity;
    }

    public Optional<String> validateApiKey(String apiKey) {
        Optional<UserApiKeyEntity> entity = repository.findByApiKeyAndActiveTrue(apiKey);
        if (entity.isPresent()) {
            // Update last used timestamp asynchronously
            UserApiKeyEntity key = entity.get();
            key.setLastUsedAt(LocalDateTime.now());
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

    private String generateSecureKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "cql_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
