package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.LibraryMetadataDTO;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.CqlTranslationResponse.TranslationMetadata;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

    @Mock
    private CqlTranslationService translationService;

    @Mock
    private CqlLibraryRepository libraryRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private CqlLibraryService libraryService;

    private CqlTranslationResponse successResponse;
    private CqlTranslationResponse failureResponse;

    @BeforeEach
    void setUp() {
        // Phase 2: create paths derive tenant from TenantContext; set one so existing
        // saveLibrary/create tests exercise the effectiveTenantId path without a repo stub.
        TenantContext.setCurrentTenantId(7L);
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

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void saveLibrary_setsTenantFromContext() {
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(successResponse);
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("TestLib"), eq("1.0"))).thenReturn(Optional.empty());
        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        libraryService.saveLibrary("library TestLib version '1.0'", "d");

        ArgumentCaptor<CqlLibraryEntity> captor = ArgumentCaptor.forClass(CqlLibraryEntity.class);
        verify(libraryRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(7L); // from TenantContext (setUp)
    }

    @Test
    void saveLibrary_fallsBackToDefaultTenantWhenNoContext() {
        TenantContext.clear(); // legacy caller: no tenant in context
        TenantEntity def = TenantEntity.builder().code("default").name("Default Tenant").build();
        def.setId(99L);
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(def));
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(successResponse);
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("TestLib"), eq("1.0"))).thenReturn(Optional.empty());
        when(libraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        libraryService.saveLibrary("library TestLib version '1.0'", "d");

        ArgumentCaptor<CqlLibraryEntity> captor = ArgumentCaptor.forClass(CqlLibraryEntity.class);
        verify(libraryRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(99L); // default tenant
    }

    @Test
    void getAllLibraries_scopesToCallerTenant() {
        when(libraryRepository.findByTenantId(7L)).thenReturn(List.of()); // 7L from setUp
        libraryService.getAllLibraries();
        verify(libraryRepository).findByTenantId(7L);        // exact caller tenant, not findAll
        verify(libraryRepository, never()).findAll();
    }

    @Test
    void getLibrary_scopesToCallerTenant() {
        when(libraryRepository.findByTenantIdAndNameAndVersion(7L, "TestLib", "1.0"))
                .thenReturn(Optional.empty());
        libraryService.getLibrary("TestLib-1.0");
        verify(libraryRepository).findByTenantIdAndNameAndVersion(7L, "TestLib", "1.0");
    }

    @Test
    void getLibrariesMetadata_usesProjectionNotFindAll_andParsesElm() {
        String elm = "{\"library\":{\"statements\":{\"def\":["
                + "{\"name\":\"Has Diabetes\"},{\"name\":\"Patient\"}]},"
                + "\"valueSets\":{\"def\":[{\"name\":\"Diabetes VS\"}]}}}";
        CqlLibraryRepository.LibraryMetadataView view = new CqlLibraryRepository.LibraryMetadataView() {
            public String getName() { return "DiabetesLib"; }
            public String getVersion() { return "1.0.0"; }
            public String getElmJson() { return elm; }
        };
        when(libraryRepository.findMetadataByTenantId(anyLong())).thenReturn(List.of(view));

        List<LibraryMetadataDTO> result = libraryService.getLibrariesMetadata();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("DiabetesLib");
        assertThat(result.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(result.get(0).getExpressions()).containsExactly("Has Diabetes"); // Patient excluded
        assertThat(result.get(0).getValueSets()).containsExactly("Diabetes VS");
        // The whole point: the metadata path must use the lightweight projection,
        // never findAll() (which would load the heavy cql_content TEXT).
        verify(libraryRepository).findMetadataByTenantId(anyLong());
        verify(libraryRepository, never()).findAll();
        verify(libraryRepository, never()).findByTenantId(anyLong());
    }

    @Test
    void saveLibrary_validCql_shouldStoreAndReturn() {
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(successResponse);
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("TestLib"), eq("1.0"))).thenReturn(Optional.empty());
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
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("TestLib"), eq("1.0"))).thenReturn(Optional.of(entity));

        Optional<CqlLibrary> result = libraryService.getLibrary("TestLib-1.0");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("TestLib");
    }

    @Test
    void getLibrary_nonExisting_shouldReturnEmpty() {
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("nonexistent"), eq("1.0"))).thenReturn(Optional.empty());

        Optional<CqlLibrary> result = libraryService.getLibrary("nonexistent-1.0");
        assertThat(result).isEmpty();
    }

    @Test
    void getLibraryByNameAndVersion_shouldFindCorrectLibrary() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("TestLib"), eq("1.0"))).thenReturn(Optional.of(entity));

        Optional<CqlLibrary> result = libraryService.getLibraryByNameAndVersion("TestLib", "1.0");
        assertThat(result).isPresent();
    }

    @Test
    void getAllLibraries_shouldReturnAllStored() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByTenantId(anyLong())).thenReturn(List.of(entity));

        List<CqlLibrary> all = libraryService.getAllLibraries();
        assertThat(all).hasSize(1);
    }

    @Test
    void searchLibraries_byName_shouldFilter() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.searchByTenant(anyLong(), eq("Test")))
                .thenReturn(List.of(entity));
        when(libraryRepository.searchByTenant(anyLong(), eq("Nonexistent")))
                .thenReturn(List.of());

        List<CqlLibrary> found = libraryService.searchLibraries("Test");
        assertThat(found).hasSize(1);

        List<CqlLibrary> notFound = libraryService.searchLibraries("Nonexistent");
        assertThat(notFound).isEmpty();
    }

    @Test
    void searchLibraries_nullSearch_shouldReturnAll() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByTenantId(anyLong())).thenReturn(List.of(entity));

        List<CqlLibrary> result = libraryService.searchLibraries(null);
        assertThat(result).hasSize(1);
    }

    @Test
    void deleteLibrary_shouldRemoveFromStore() {
        CqlLibraryEntity entity = createTestEntity();
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), eq("TestLib"), eq("1.0"))).thenReturn(Optional.of(entity));

        libraryService.deleteLibrary("TestLib-1.0");

        verify(libraryRepository).delete(entity);
    }

    @Test
    void updateLibrary_nonExisting_shouldThrow() {
        when(libraryRepository.findByTenantIdAndNameAndVersion(anyLong(), any(), any())).thenReturn(Optional.empty());

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

        when(libraryRepository.findByTenantIdAndName(anyLong(), eq("TestLib"))).thenReturn(List.of(v1, v2));

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

        when(libraryRepository.findByTenantIdAndName(anyLong(), eq("TestLib"))).thenReturn(List.of(v1, v2));

        List<CqlLibrary> versions = libraryService.getLibraryVersions("TestLib");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo("2.0");
        assertThat(versions.get(1).getVersion()).isEqualTo("1.0");
    }

    // ===== Tenant boundary (BUG-135) =====
    //
    // All three reads had NO test coverage before, which is how they shipped unscoped.
    // /libraries/owner and /shared let a ROLE_ADMIN pass an arbitrary username, and
    // /dependents had no role check at all — any authenticated user of any tenant could
    // enumerate library names and read cqlContent. Sharing is a within-tenant concept.

    @Test
    void getLibrariesByOwner_shouldScopeToCallersTenant() {
        when(libraryRepository.findByTenantIdAndOwnerUsername(7L, "someone-else"))
                .thenReturn(List.of(createTestEntity()));

        assertThat(libraryService.getLibrariesByOwner("someone-else")).hasSize(1);

        verify(libraryRepository).findByTenantIdAndOwnerUsername(7L, "someone-else");
    }

    @Test
    void getSharedLibraries_shouldScopeToCallersTenant() {
        when(libraryRepository.findSharedWithUser(eq(7L), anyString()))
                .thenReturn(List.of(createTestEntity()));

        assertThat(libraryService.getSharedLibraries("bob")).hasSize(1);

        verify(libraryRepository).findSharedWithUser(eq(7L), anyString());
    }

    @Test
    void getDependents_shouldScopeToCallersTenant() {
        when(libraryRepository.findByTenantIdAndDependenciesContaining(7L, "SharedLogic"))
                .thenReturn(List.of(createTestEntity()));

        assertThat(libraryService.getDependents("SharedLogic")).hasSize(1);

        verify(libraryRepository).findByTenantIdAndDependenciesContaining(7L, "SharedLogic");
    }
}
