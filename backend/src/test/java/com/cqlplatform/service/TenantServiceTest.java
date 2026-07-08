package com.cqlplatform.service;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository repo;

    @InjectMocks
    private TenantService service;

    @Test
    void createTenant_savesNewWhenCodeFree() {
        when(repo.existsByCode("clinic-a")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TenantEntity t = service.createTenant("clinic-a", "Clinic A");

        assertThat(t.getCode()).isEqualTo("clinic-a");
        assertThat(t.getName()).isEqualTo("Clinic A");
        assertThat(t.getActive()).isTrue();
        verify(repo).save(any());
    }

    @Test
    void createTenant_rejectsDuplicateCode() {
        when(repo.existsByCode("dup")).thenReturn(true);

        assertThatThrownBy(() -> service.createTenant("dup", "X"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void getByCode_notFound_throws() {
        when(repo.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCode("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
