package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.config.CqlConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(CqlConfig.class)
@ActiveProfiles("test")
public class CqlTranslationServiceTest {

    @Autowired
    private CqlTranslationService cqlTranslationService;

    @Test
    public void testTranslateWithFHIRHelpers() {
        String cql = "library TestLibrary version '1.0.0'\n" +
                "using FHIR version '4.0.1'\n" +
                "include FHIRHelpers version '4.0.1'\n" +
                "context Patient\n" +
                "define \"Test\": true";

        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);

        CqlTranslationResponse response = cqlTranslationService.translate(request);

        Assertions.assertTrue(response.isSuccess(), "Translation should be successful: "
                + (response.getErrors() != null ? response.getErrors().toString() : ""));
        Assertions.assertTrue(response.getErrors().isEmpty(), "There should be no errors");
    }
}
