package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.TranslationMetadata;
import com.cqlplatform.repository.CqlLibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

    @Mock
    private CqlTranslationService translationService;

    @Mock
    private CqlLibraryRepository libraryRepository;

    @InjectMocks
    private CqlLibraryService libraryService;

    private CqlTranslationResponse successResponse;
    private CqlTranslationResponse failureResponse;

    @BeforeEach
    void setUp() {
        successResponse = CqlTranslationResponse.builder()
                .success(true)
                .elmJson("{\"library\":{}}")
                .errors(List.of())
                .warnings(List.of())
                .metadata(TranslationMetadata.builder()
                        .libraryId("TestLib")
                        .libraryVersion("1.0")
                        .includes(List.of())
                        .build())
                .build();

        failureResponse = CqlTranslationResponse.builder()
                .success(false)
                .errors(List.of(CqlTranslationResponse.CqlError.builder()
                        .message("Syntax error").build()))
                .build();
    }

    private CqlLibraryEntity createTestEntity() {
        CqlLibraryEntity entity = CqlLibraryEntity.builder()
                .id(1L)
                .name("TestLib")
                .version("1.0")
                .cqlContent("library TestLib version '1.0'")
                .elmJson("{\"library\":{}}")
                .description("Test description")
                .status("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        entity.setDependencyList(List.of());
        return entity;
    }

    @Test
    void saveLibrary_validCql_shouldStoreAndReturn() {
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(successResponse);
        when(libraryRepository.findByNameAndVersion("TestLib", "1.0")).thenReturn(Optional.empty());
        when(libraryRepository.save(any())).thenAnswer(inv -> {
            CqlLibraryEntity e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });

        CqlLibrary library = libraryService.saveLibrary("library TestLib version '1.0'", "Test description");

        assertThat(library).isNotNull();
        assertThat(library.getName()).isEqualTo("TestLib");
        assertThat(library.getVersion()).isEqualTo("1.0");
        assertThat(library.getDescription()).isEqualTo("Test description");
        assertThat(library.getStatus()).isEqualTo("active");
        verify(libraryRepository).save(any());
    }

    @Test
    void saveLibrary_invalidCql_shouldThrow() {
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(failureResponse);

        assertThatThrownBy(() -> libraryService.saveLibrary("bad cql", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CQL");
    }

    @Test
    void getLibrary_existing_shouldReturnLibrary() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByNameAndVersion("TestLib", "1.0")).thenReturn(Optional.of(entity));

        Optional<CqlLibrary> result = libraryService.getLibrary("TestLib-1.0");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("TestLib");
    }

    @Test
    void getLibrary_nonExisting_shouldReturnEmpty() {
        when(libraryRepository.findByNameAndVersion("nonexistent", "1.0")).thenReturn(Optional.empty());

        Optional<CqlLibrary> result = libraryService.getLibrary("nonexistent-1.0");
        assertThat(result).isEmpty();
    }

    @Test
    void getLibraryByNameAndVersion_shouldFindCorrectLibrary() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByNameAndVersion("TestLib", "1.0")).thenReturn(Optional.of(entity));

        Optional<CqlLibrary> result = libraryService.getLibraryByNameAndVersion("TestLib", "1.0");
        assertThat(result).isPresent();
    }

    @Test
    void getAllLibraries_shouldReturnAllStored() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findAll()).thenReturn(List.of(entity));

        List<CqlLibrary> all = libraryService.getAllLibraries();
        assertThat(all).hasSize(1);
    }

    @Test
    void searchLibraries_byName_shouldFilter() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase("Test", "Test"))
                .thenReturn(List.of(entity));
        when(libraryRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase("Nonexistent", "Nonexistent"))
                .thenReturn(List.of());

        List<CqlLibrary> found = libraryService.searchLibraries("Test");
        assertThat(found).hasSize(1);

        List<CqlLibrary> notFound = libraryService.searchLibraries("Nonexistent");
        assertThat(notFound).isEmpty();
    }

    @Test
    void searchLibraries_nullSearch_shouldReturnAll() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findAll()).thenReturn(List.of(entity));

        List<CqlLibrary> result = libraryService.searchLibraries(null);
        assertThat(result).hasSize(1);
    }

    @Test
    void deleteLibrary_shouldRemoveFromStore() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByNameAndVersion("TestLib", "1.0")).thenReturn(Optional.of(entity));

        libraryService.deleteLibrary("TestLib-1.0");

        verify(libraryRepository).delete(entity);
    }

    @Test
    void updateLibrary_nonExisting_shouldThrow() {
        when(libraryRepository.findByNameAndVersion(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> libraryService.updateLibrary("nonexistent-1.0", "cql", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Library not found");
    }

    @Test
    void getLatestLibrary_shouldReturnHighestVersion() {
        CqlLibraryEntity v1 = createTestEntity();
        v1.setVersion("1.0");
        CqlLibraryEntity v2 = createTestEntity();
        v2.setVersion("2.0");
        v2.setId(2L);

        when(libraryRepository.findByName("TestLib")).thenReturn(List.of(v1, v2));

        Optional<CqlLibrary> latest = libraryService.getLatestLibrary("TestLib");
        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo("2.0");
    }

    @Test
    void getLibraryVersions_shouldReturnSortedDesc() {
        CqlLibraryEntity v1 = createTestEntity();
        v1.setVersion("1.0");
        CqlLibraryEntity v2 = createTestEntity();
        v2.setVersion("2.0");
        v2.setId(2L);

        when(libraryRepository.findByName("TestLib")).thenReturn(List.of(v1, v2));

        List<CqlLibrary> versions = libraryService.getLibraryVersions("TestLib");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo("2.0");
        assertThat(versions.get(1).getVersion()).isEqualTo("1.0");
    }
}
