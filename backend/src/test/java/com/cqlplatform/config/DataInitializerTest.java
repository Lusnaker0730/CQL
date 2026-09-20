package com.cqlplatform.config;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BUG-144 — the first boot of a fresh database. The demo measure was saved without a tenant while
 * {@code measure_definition.tenant_id} has been NOT NULL since V61: the insert failed, startup
 * failed with it, and the container's restart skipped seeding because the users already existed.
 * H2 tests could not see it (this runner is {@code @Profile({"dev","docker"})} and Flyway is off
 * there); the smoke harness's boot check is the integration-level lock, this is the unit-level one.
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MeasureDefinitionRepository measureDefinitionRepository;
    @Mock private TenantRepository tenantRepository;
    @InjectMocks private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        lenient().when(tenantRepository.findByCode("default"))
                .thenReturn(Optional.of(TenantEntity.builder().id(7L).code("default").build()));
    }

    @Test
    void firstBoot_seedsTheDemoMeasureIntoTheDefaultTenant() {
        when(userRepository.count()).thenReturn(0L);

        initializer.run();

        verify(userRepository, times(2)).save(any(UserEntity.class));
        ArgumentCaptor<MeasureDefinitionEntity> saved = ArgumentCaptor.forClass(MeasureDefinitionEntity.class);
        verify(measureDefinitionRepository).save(saved.capture());
        // The column is NOT NULL: a row without a tenant cannot be inserted at all.
        assertThat(saved.getValue().getTenantId()).isEqualTo(7L);
        assertThat(saved.getValue().getName()).isEqualTo("DiabetesHbA1cRate");
        assertThat(saved.getValue().getOwnerUsername()).isEqualTo("demo");
        assertThat(saved.getValue().getStatus()).isEqualTo("active");
    }

    @Test
    void aFailingDemoMeasure_doesNotTakeStartupDown_andTheUsersAreStillThere() {
        when(userRepository.count()).thenReturn(0L);
        when(measureDefinitionRepository.save(any(MeasureDefinitionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("null value in column \"tenant_id\""));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verify(userRepository, times(2)).save(any(UserEntity.class));
    }

    @Test
    void withoutADefaultTenant_theDemoMeasureIsSkipped() {
        when(userRepository.count()).thenReturn(0L);
        when(tenantRepository.findByCode("default")).thenReturn(Optional.empty());

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verify(measureDefinitionRepository, never()).save(any());
    }

    @Test
    void anInstallationThatAlreadyHasUsers_isLeftAlone() {
        when(userRepository.count()).thenReturn(3L);

        initializer.run();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(measureDefinitionRepository, tenantRepository);
    }
}
