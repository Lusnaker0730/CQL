package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.repository.CdsArtifactRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqlGenerationServiceTest {

    @Mock
    private CdsArtifactRepository artifactRepository;

    @Mock
    private CqlArtifactBuilder cqlBuilder;

    @Mock
    private CqlTranslationService translationService;

    @InjectMocks
    private CqlGenerationService service;

    private CdsArtifactEntity createEntity(Long id, String name) {
        return CdsArtifactEntity.builder()
                .id(id)
                .name(name)
                .version("1.0.0")
                .fhirVersion("R4")
                .status("Draft")
                .ownerUsername("owner")
                .build();
    }

    @Test
    void generateCql_notFound_shouldThrow() {
        when(artifactRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateCql(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void generateCql_found_shouldReturnBuiltCql() {
        CdsArtifactEntity entity = createEntity(1L, "TestArtifact");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(cqlBuilder.buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("R4")))
                .thenReturn("library Test version '1.0'");

        String cql = service.generateCql(1L);

        assertThat(cql).contains("library Test");
    }

    @Test
    void generateCql_withExplicitFhirVersion_shouldUseIt() {
        CdsArtifactEntity entity = createEntity(1L, "TestArtifact");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(cqlBuilder.buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("DSTU2")))
                .thenReturn("library Test version '1.0'");

        service.generateCql(1L, "DSTU2");

        verify(cqlBuilder).buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("DSTU2"));
    }

    @Test
    void generateCql_nullFhirVersion_shouldDefaultToEntityVersion() {
        CdsArtifactEntity entity = createEntity(1L, "TestArtifact");
        entity.setFhirVersion("R4");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(cqlBuilder.buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("R4")))
                .thenReturn("library Test version '1.0'");

        service.generateCql(1L, null);

        verify(cqlBuilder).buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("R4"));
    }

    @Test
    void generateAndTranslate_shouldReturnTranslationResponse() {
        CdsArtifactEntity entity = createEntity(1L, "TestArtifact");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(cqlBuilder.buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("R4")))
                .thenReturn("library Test version '1.0'");

        CqlTranslationResponse mockResponse = CqlTranslationResponse.builder()
                .success(true)
                .build();
        when(translationService.translate(any())).thenReturn(mockResponse);

        CqlTranslationResponse result = service.generateAndTranslate(1L);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateArtifactCql_shouldReturnValidationResult() {
        CdsArtifactEntity entity = createEntity(1L, "TestArtifact");
        when(artifactRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(cqlBuilder.buildCql(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("R4")))
                .thenReturn("library Test version '1.0'");

        CqlTranslationResponse mockResponse = CqlTranslationResponse.builder()
                .success(false)
                .build();
        when(translationService.translate(any())).thenReturn(mockResponse);

        CqlTranslationResponse result = service.validateArtifactCql(1L);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
    }
}
