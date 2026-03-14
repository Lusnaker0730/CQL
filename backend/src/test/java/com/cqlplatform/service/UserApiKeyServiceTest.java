package com.cqlplatform.service;

import com.cqlplatform.entity.UserApiKeyEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserApiKeyRepository;
import com.cqlplatform.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApiKeyServiceTest {

    @Mock
    private UserApiKeyRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserApiKeyService service;

    @Test
    void generateApiKey_shouldReturnEntityWithCqlPrefix() {
        when(repository.save(any())).thenAnswer(inv -> {
            UserApiKeyEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        UserApiKeyEntity result = service.generateApiKey("testuser", "My Key");

        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).startsWith("cql_");
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getName()).isEqualTo("My Key");
        assertThat(result.getActive()).isTrue();
        verify(repository).save(any());
    }

    @Test
    void generateApiKey_shouldGenerateUniqueKeys() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserApiKeyEntity key1 = service.generateApiKey("user", "Key 1");
        UserApiKeyEntity key2 = service.generateApiKey("user", "Key 2");

        assertThat(key1.getApiKey()).isNotEqualTo(key2.getApiKey());
    }

    @Test
    void validateApiKey_validKey_enabledUser_shouldReturnUsername() {
        String plainKey = "cql_abc123";
        String keyHash = UserApiKeyService.hashApiKey(plainKey);
        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .id(1L)
                .username("testuser")
                .apiKey(keyHash)
                .active(true)
                .build();
        UserEntity user = UserEntity.builder().username("testuser").enabled(true).build();
        when(repository.findByApiKeyAndActiveTrue(keyHash)).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(repository.save(any())).thenReturn(entity);

        Optional<String> result = service.validateApiKey(plainKey);

        assertThat(result).isPresent().contains("testuser");
        verify(repository).save(any()); // lastUsedAt updated
    }

    @Test
    void validateApiKey_validKey_disabledUser_shouldReturnEmpty() {
        String plainKey = "cql_abc123";
        String keyHash = UserApiKeyService.hashApiKey(plainKey);
        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .id(1L)
                .username("disableduser")
                .apiKey(keyHash)
                .active(true)
                .build();
        UserEntity user = UserEntity.builder().username("disableduser").enabled(false).build();
        when(repository.findByApiKeyAndActiveTrue(keyHash)).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("disableduser")).thenReturn(Optional.of(user));

        Optional<String> result = service.validateApiKey(plainKey);

        assertThat(result).isEmpty();
        verify(repository, never()).save(any()); // should not update lastUsedAt
    }

    @Test
    void validateApiKey_validKey_deletedUser_shouldReturnEmpty() {
        String plainKey = "cql_abc123";
        String keyHash = UserApiKeyService.hashApiKey(plainKey);
        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .id(1L)
                .username("ghost")
                .apiKey(keyHash)
                .active(true)
                .build();
        when(repository.findByApiKeyAndActiveTrue(keyHash)).thenReturn(Optional.of(entity));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<String> result = service.validateApiKey(plainKey);

        assertThat(result).isEmpty();
    }

    @Test
    void validateApiKey_invalidKey_shouldReturnEmpty() {
        String plainKey = "bad_key";
        String keyHash = UserApiKeyService.hashApiKey(plainKey);
        // Hash lookup returns empty, then legacy plaintext fallback also returns empty
        when(repository.findByApiKeyAndActiveTrue(keyHash)).thenReturn(Optional.empty());
        when(repository.findByApiKeyAndActiveTrue(plainKey)).thenReturn(Optional.empty());

        Optional<String> result = service.validateApiKey(plainKey);

        assertThat(result).isEmpty();
    }

    @Test
    void listKeys_shouldDelegateToRepository() {
        List<UserApiKeyEntity> keys = List.of(
                UserApiKeyEntity.builder().id(1L).username("user").build());
        when(repository.findByUsername("user")).thenReturn(keys);

        List<UserApiKeyEntity> result = service.listKeys("user");
        assertThat(result).hasSize(1);
    }

    @Test
    void revokeKey_owner_shouldDeactivate() {
        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .id(1L)
                .username("user1")
                .apiKey("cql_key")
                .active(true)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenReturn(entity);

        boolean result = service.revokeKey(1L, "user1");

        assertThat(result).isTrue();
        assertThat(entity.getActive()).isFalse();
    }

    @Test
    void revokeKey_nonOwner_shouldReturnFalse() {
        UserApiKeyEntity entity = UserApiKeyEntity.builder()
                .id(1L)
                .username("user1")
                .apiKey("cql_key")
                .active(true)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        boolean result = service.revokeKey(1L, "otherUser");

        assertThat(result).isFalse();
    }

    @Test
    void revokeKey_notFound_shouldReturnFalse() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        boolean result = service.revokeKey(999L, "user");

        assertThat(result).isFalse();
    }

    @Test
    void deactivateAllKeys_shouldDelegateToRepository() {
        when(repository.deactivateAllByUsername("disableduser")).thenReturn(3);

        int count = service.deactivateAllKeys("disableduser");

        assertThat(count).isEqualTo(3);
        verify(repository).deactivateAllByUsername("disableduser");
    }

    @Test
    void deactivateAllKeys_noKeys_shouldReturnZero() {
        when(repository.deactivateAllByUsername("nokeys")).thenReturn(0);

        int count = service.deactivateAllKeys("nokeys");

        assertThat(count).isZero();
    }
}
