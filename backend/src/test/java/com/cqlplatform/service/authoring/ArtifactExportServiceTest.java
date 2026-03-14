package com.cqlplatform.service.authoring;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.ArtifactResponse;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactExportServiceTest {

    @Mock
    private CqlGenerationService cqlGenerationService;

    @Mock
    private CqlTranslationService translationService;

    @InjectMocks
    private ArtifactExportService service;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ArtifactResponse createArtifact() {
        return ArtifactResponse.builder()
                .id(1L)
                .name("TestArtifact")
                .version("1.0.0")
                .status("Draft")
                .description("Test description")
                .url("https://example.org/Library/TestArtifact")
                .publisher("Test Publisher")
                .purpose("Testing")
                .copyright("(c) 2026")
                .experimental(true)
                .approvalDate(LocalDate.of(2026, 1, 1))
                .lastReviewDate(LocalDate.of(2026, 3, 1))
                .effectivePeriodStart(LocalDate.of(2026, 1, 1))
                .effectivePeriodEnd(LocalDate.of(2026, 12, 31))
                .author(List.of(Map.of("name", "Author1")))
                .reviewer(List.of(Map.of("name", "Reviewer1")))
                .endorser(List.of(Map.of("name", "Endorser1")))
                .relatedArtifact(List.of(Map.of("type", "citation", "display", "Ref1", "url", "https://example.org")))
                .build();
    }

    @Test
    void exportAsZip_shouldContainAllFiles() throws Exception {
        ArtifactResponse artifact = createArtifact();
        when(cqlGenerationService.generateCql(1L)).thenReturn("library TestArtifact version '1.0.0'");

        CqlTranslationResponse translation = CqlTranslationResponse.builder()
                .success(true)
                .elmJson("{\"library\":{}}")
                .build();
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(translation);

        // Seed the cached FHIRHelpers field via init()
        service.init();

        byte[] zip = service.exportAsZip(artifact);

        assertThat(zip).isNotEmpty();

        // Verify ZIP contains 4 entries
        java.util.Set<String> entryNames = new java.util.HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        assertThat(entryNames).containsExactlyInAnyOrder(
                "TestArtifact.cql",
                "TestArtifact-elm.json",
                "FHIRHelpers-4.0.1.cql",
                "TestArtifact-library.json"
        );
    }

    @Test
    void exportAsZip_translationFails_shouldThrow() {
        ArtifactResponse artifact = createArtifact();
        when(cqlGenerationService.generateCql(1L)).thenReturn("library TestArtifact version '1.0.0'");

        CqlTranslationResponse translation = CqlTranslationResponse.builder()
                .success(false)
                .build();
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(translation);

        assertThatThrownBy(() -> service.exportAsZip(artifact))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("translation failed");
    }

    @Test
    void exportAsZip_libraryJsonContainsCpgMetadata() throws Exception {
        ArtifactResponse artifact = createArtifact();
        when(cqlGenerationService.generateCql(1L)).thenReturn("library TestArtifact version '1.0.0'");

        CqlTranslationResponse translation = CqlTranslationResponse.builder()
                .success(true)
                .elmJson("{\"library\":{}}")
                .build();
        when(translationService.translate(any(CqlTranslationRequest.class))).thenReturn(translation);

        service.init();

        byte[] zip = service.exportAsZip(artifact);

        // Extract the library JSON from the ZIP
        String libraryJson = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith("-library.json")) {
                    libraryJson = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        assertThat(libraryJson).isNotNull();

        JsonNode root = MAPPER.readTree(libraryJson);

        assertThat(root.get("resourceType").asText()).isEqualTo("Library");
        assertThat(root.get("name").asText()).isEqualTo("TestArtifact");
        assertThat(root.get("version").asText()).isEqualTo("1.0.0");
        assertThat(root.get("url").asText()).isEqualTo("https://example.org/Library/TestArtifact");
        assertThat(root.get("publisher").asText()).isEqualTo("Test Publisher");
        assertThat(root.get("purpose").asText()).isEqualTo("Testing");
        assertThat(root.get("copyright").asText()).isEqualTo("(c) 2026");
        assertThat(root.get("experimental").asBoolean()).isTrue();
        assertThat(root.get("approvalDate").asText()).isEqualTo("2026-01-01");
        assertThat(root.get("lastReviewDate").asText()).isEqualTo("2026-03-01");
        assertThat(root.get("effectivePeriod").get("start").asText()).isEqualTo("2026-01-01");
        assertThat(root.get("effectivePeriod").get("end").asText()).isEqualTo("2026-12-31");
        assertThat(root.get("author")).hasSize(1);
        assertThat(root.get("author").get(0).get("name").asText()).isEqualTo("Author1");
        assertThat(root.get("reviewer")).hasSize(1);
        assertThat(root.get("endorser")).hasSize(1);
        assertThat(root.get("relatedArtifact")).hasSize(1);
        assertThat(root.get("relatedArtifact").get(0).get("type").asText()).isEqualTo("citation");

        // Verify content attachments
        assertThat(root.get("content")).hasSize(2);
        assertThat(root.get("content").get(0).get("contentType").asText()).isEqualTo("text/cql");
        assertThat(root.get("content").get(1).get("contentType").asText()).isEqualTo("application/elm+json");

        // Verify type coding
        assertThat(root.get("type").get("coding").get(0).get("code").asText()).isEqualTo("logic-library");
    }
}
