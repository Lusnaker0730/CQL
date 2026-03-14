package com.cqlplatform.service.cql;

import com.cqlplatform.exception.CqlTranslationException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CqlTranslationServiceUnitTest {

    private CqlTranslationService translationService;

    private static final String VALID_CQL = """
            library TestLibrary version '1.0'
            using FHIR version '4.0.1'
            context Patient
            define IsAlive: true
            """;

    private static final String INVALID_CQL = """
            library TestLibrary version '1.0'
            using FHIR version '4.0.1'
            context Patient
            define IsAlive: @#$%
            """;

    private static final String SIMPLE_CQL = """
            library SimpleTest version '1.0'
            define X: 1 + 1
            """;

    @BeforeEach
    void setUp() {
        translationService = new CqlTranslationService();
        // Set translation timeout (normally injected by Spring)
        try {
            java.lang.reflect.Field f = CqlTranslationService.class.getDeclaredField("translationTimeoutSeconds");
            f.setAccessible(true);
            f.set(translationService, 30);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void translate_validCql_shouldSucceed() {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(VALID_CQL);

        CqlTranslationResponse response = translationService.translate(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getElmJson()).isNotNull().isNotBlank();
        assertThat(response.getErrors()).isEmpty();
    }

    @Test
    void translate_invalidCql_shouldReturnErrors() {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(INVALID_CQL);

        CqlTranslationResponse response = translationService.translate(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrors()).isNotEmpty();
    }

    @Test
    void translate_nullCql_shouldThrowIllegalArgument() {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(null);

        assertThatThrownBy(() -> translationService.translate(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void translate_blankCql_shouldThrowIllegalArgument() {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql("   ");

        assertThatThrownBy(() -> translationService.translate(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_validCql_shouldSucceed() {
        CqlTranslationResponse response = translationService.validate(VALID_CQL);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void validate_invalidCql_shouldReturnErrors() {
        CqlTranslationResponse response = translationService.validate(INVALID_CQL);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void compile_validCql_shouldReturnCompiledLibrary() {
        var compiled = translationService.compile(VALID_CQL);
        assertThat(compiled).isNotNull();
        assertThat(compiled.getIdentifier().getId()).isEqualTo("TestLibrary");
    }

    @Test
    void compile_invalidCql_shouldThrowTranslationException() {
        assertThatThrownBy(() -> translationService.compile(INVALID_CQL))
                .isInstanceOf(CqlTranslationException.class);
    }

    @Test
    void translate_shouldExtractMetadata() {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(VALID_CQL);

        CqlTranslationResponse response = translationService.translate(request);

        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getLibraryId()).isEqualTo("TestLibrary");
        assertThat(response.getMetadata().getLibraryVersion()).isEqualTo("1.0");
        assertThat(response.getMetadata().getExpressions()).isNotEmpty();
    }

    @Test
    void translate_simpleCqlWithoutFhir_shouldSucceed() {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(SIMPLE_CQL);

        CqlTranslationResponse response = translationService.translate(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMetadata().getLibraryId()).isEqualTo("SimpleTest");
    }
}
