package com.cqlplatform.integration;

import com.cqlplatform.entity.CdsServiceConfigEntity;
import com.cqlplatform.entity.CdsServicePrefetchEntity;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CdsServicePersistenceIntegrationTest {

    @Autowired
    private CdsServiceConfigRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @Transactional
    void createAndRetrieve_shouldPersist() {
        CdsServiceConfigEntity entity = CdsServiceConfigEntity.builder()
                .id("test-svc")
                .hook("patient-view")
                .title("Test Service")
                .description("A test CDS service")
                .enabled(true)
                .prefetchItems(new ArrayList<>())
                .build();

        CdsServicePrefetchEntity prefetch = CdsServicePrefetchEntity.builder()
                .prefetchKey("patient")
                .query("Patient/{{context.patientId}}")
                .build();
        entity.addPrefetchItem(prefetch);

        repository.save(entity);

        Optional<CdsServiceConfigEntity> found = repository.findByIdWithPrefetch("test-svc");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Service");
        assertThat(found.get().getPrefetchItems()).hasSize(1);
        assertThat(found.get().getPrefetchItems().get(0).getPrefetchKey()).isEqualTo("patient");
    }

    @Test
    @Transactional
    void updateEntity_shouldPersistChanges() {
        CdsServiceConfigEntity entity = CdsServiceConfigEntity.builder()
                .id("update-svc")
                .hook("patient-view")
                .title("Original")
                .enabled(true)
                .prefetchItems(new ArrayList<>())
                .build();
        repository.save(entity);

        entity.setTitle("Updated Title");
        repository.save(entity);

        Optional<CdsServiceConfigEntity> found = repository.findByIdWithPrefetch("update-svc");
        assertThat(found.get().getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void deleteEntity_shouldRemove() {
        CdsServiceConfigEntity entity = CdsServiceConfigEntity.builder()
                .id("delete-svc")
                .hook("patient-view")
                .title("To Delete")
                .enabled(true)
                .prefetchItems(new ArrayList<>())
                .build();
        repository.save(entity);

        repository.deleteById("delete-svc");

        assertThat(repository.existsById("delete-svc")).isFalse();
    }

    @Test
    void findAllEnabled_shouldFilterCorrectly() {
        CdsServiceConfigEntity enabled = CdsServiceConfigEntity.builder()
                .id("enabled-svc")
                .hook("patient-view")
                .title("Enabled")
                .enabled(true)
                .prefetchItems(new ArrayList<>())
                .build();
        CdsServiceConfigEntity disabled = CdsServiceConfigEntity.builder()
                .id("disabled-svc")
                .hook("patient-view")
                .title("Disabled")
                .enabled(false)
                .prefetchItems(new ArrayList<>())
                .build();

        repository.save(enabled);
        repository.save(disabled);

        var enabledList = repository.findAllEnabledWithPrefetch();
        assertThat(enabledList).extracting("id").contains("enabled-svc");
        assertThat(enabledList).extracting("id").doesNotContain("disabled-svc");
    }
}
