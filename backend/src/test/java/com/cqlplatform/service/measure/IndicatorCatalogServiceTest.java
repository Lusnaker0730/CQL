package com.cqlplatform.service.measure;

import com.cqlplatform.entity.IndicatorCatalogEntity;
import com.cqlplatform.repository.IndicatorCatalogRepository;
import com.cqlplatform.security.PlatformOperatorGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BUG-138 — indicator_catalog is a PLATFORM-level catalog (national / TFDA quality
 * indicators, no tenant_id): reads are open to everyone, but a write changes what EVERY
 * tenant sees, so writes are restricted to the platform operator. This whole service had
 * NO unit test before — which is how the missing guard shipped.
 */
@ExtendWith(MockitoExtension.class)
class IndicatorCatalogServiceTest {

    @Mock
    private IndicatorCatalogRepository repository;

    @Mock
    private PlatformOperatorGuard platformOperatorGuard;

    @InjectMocks
    private IndicatorCatalogService service;

    private IndicatorCatalogEntity entity() {
        IndicatorCatalogEntity e = new IndicatorCatalogEntity();
        e.setCode("IND-1");
        e.setSource("TFDA");
        e.setName("Test Indicator");
        return e;
    }

    // ===== reads stay open (no guard) =====

    @Test
    void search_shouldNotRequirePlatformOperator() {
        when(repository.findByActiveTrue()).thenReturn(List.of(entity()));

        service.search(null, null, null);

        verify(platformOperatorGuard, never()).require();
    }

    // ===== writes require the platform operator, BEFORE touching the repository =====

    @Test
    void create_whenNotPlatformOperator_shouldDenyBeforeWriting() {
        doThrow(new AccessDeniedException("restricted")).when(platformOperatorGuard).require();

        assertThatThrownBy(() -> service.create(entity()))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).existsByCodeAndSource(any(), any());
    }

    @Test
    void update_whenNotPlatformOperator_shouldDenyBeforeWriting() {
        doThrow(new AccessDeniedException("restricted")).when(platformOperatorGuard).require();

        assertThatThrownBy(() -> service.update("IND-1", "TFDA", entity()))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findByCodeAndSource(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void delete_whenNotPlatformOperator_shouldDenyBeforeWriting() {
        doThrow(new AccessDeniedException("restricted")).when(platformOperatorGuard).require();

        assertThatThrownBy(() -> service.delete("IND-1", "TFDA"))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void bulkImport_whenNotPlatformOperator_shouldDenyBeforeWriting() {
        doThrow(new AccessDeniedException("restricted")).when(platformOperatorGuard).require();

        assertThatThrownBy(() -> service.bulkImport(List.of(Map.of("code", "X"))))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void create_asPlatformOperator_shouldSave() {
        when(repository.existsByCodeAndSource("IND-1", "TFDA")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.create(entity())).doesNotThrowAnyException();

        verify(platformOperatorGuard).require();
        verify(repository).save(any());
    }
}
