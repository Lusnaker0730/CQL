package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.TranslationMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

    @Mock
    private CqlTranslationService translationService;

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

    @Test
    void saveLibrary_validCql_shouldStoreAndReturn() {
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(successResponse);

        CqlLibrary library = libraryService.saveLibrary("library TestLib version '1.0'", "Test description");

        assertThat(library).isNotNull();
        assertThat(library.getName()).isEqualTo("TestLib");
        assertThat(library.getVersion()).isEqualTo("1.0");
        assertThat(library.getDescription()).isEqualTo("Test description");
        assertThat(library.getStatus()).isEqualTo("active");
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
        when(translationService.translate(any())).thenReturn(successResponse);
        libraryService.saveLibrary("cql", "desc");

        Optional<CqlLibrary> result = libraryService.getLibrary("TestLib-1.0");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("TestLib");
    }

    @Test
    void getLibrary_nonExisting_shouldReturnEmpty() {
        Optional<CqlLibrary> result = libraryService.getLibrary("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void getLibraryByNameAndVersion_shouldFindCorrectLibrary() {
        when(translationService.translate(any())).thenReturn(successResponse);
        libraryService.saveLibrary("cql", "desc");

        Optional<CqlLibrary> result = libraryService.getLibraryByNameAndVersion("TestLib", "1.0");
        assertThat(result).isPresent();
    }

    @Test
    void getAllLibraries_shouldReturnAllStored() {
        when(translationService.translate(any())).thenReturn(successResponse);
        libraryService.saveLibrary("cql", "desc");

        List<CqlLibrary> all = libraryService.getAllLibraries();
        assertThat(all).hasSize(1);
    }

    @Test
    void searchLibraries_byName_shouldFilter() {
        when(translationService.translate(any())).thenReturn(successResponse);
        libraryService.saveLibrary("cql", "desc");

        List<CqlLibrary> found = libraryService.searchLibraries("Test");
        assertThat(found).hasSize(1);

        List<CqlLibrary> notFound = libraryService.searchLibraries("Nonexistent");
        assertThat(notFound).isEmpty();
    }

    @Test
    void searchLibraries_nullSearch_shouldReturnAll() {
        when(translationService.translate(any())).thenReturn(successResponse);
        libraryService.saveLibrary("cql", "desc");

        List<CqlLibrary> result = libraryService.searchLibraries(null);
        assertThat(result).hasSize(1);
    }

    @Test
    void deleteLibrary_shouldRemoveFromStore() {
        when(translationService.translate(any())).thenReturn(successResponse);
        libraryService.saveLibrary("cql", "desc");

        libraryService.deleteLibrary("TestLib-1.0");

        assertThat(libraryService.getLibrary("TestLib-1.0")).isEmpty();
    }

    @Test
    void updateLibrary_nonExisting_shouldThrow() {
        assertThatThrownBy(() -> libraryService.updateLibrary("nonexistent", "cql", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Library not found");
    }
}
