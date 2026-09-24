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

    // BUG-126: a repeat translate of identical CQL+options must hit the
    // cqlTranslation cache and return the same cached instance (proves @Cacheable
    // is wired without depending on the request's default option values).
    @Test
    public void testTranslateIsCached() {
        String cql = "library CacheTest version '1.0.0'\n" +
                "using FHIR version '4.0.1'\n" +
                "context Patient\n" +
                "define \"X\": true";

        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);

        CqlTranslationResponse first = cqlTranslationService.translate(request);
        CqlTranslationResponse second = cqlTranslationService.translate(request);

        Assertions.assertTrue(first.isSuccess());
        Assertions.assertSame(first, second,
                "second identical translate should return the cached instance");
    }

    // PAT-237: a `define function` is reported as kind=function with its operand signature in
    // author-facing type names; a plain define is kind=expression with no operands. Before,
    // both looked the same and the builder emitted a function reference without arguments.
    @Test
    public void metadata_distinguishesFunctionsFromExpressions_andRendersOperandTypes() {
        String cql = "library FnLib version '1.0.0'\n" +
                "using FHIR version '4.0.1'\n" +
                "include FHIRHelpers version '4.0.1'\n" +
                "context Patient\n" +
                "define \"Adult\": AgeInYears() >= 18\n" +
                "define function \"AtLeast\"(value Integer, threshold Integer): value >= threshold\n" +
                "define function \"MostRecent\"(observations List<FHIR.Observation>): " +
                "First(observations O sort by (effective as FHIR.dateTime).value desc)\n" +
                "define function \"Within\"(period Interval<DateTime>, moment DateTime): moment in period\n" +
                "define function \"Label\"(code Code, notes List<String>): 'x'\n" +
                "define function \"Any\"(): true\n";

        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);
        CqlTranslationResponse response = cqlTranslationService.translate(request);
        Assertions.assertTrue(response.isSuccess(), String.valueOf(response.getErrors()));

        java.util.Map<String, CqlTranslationResponse.ExpressionInfo> byName = new java.util.HashMap<>();
        for (CqlTranslationResponse.ExpressionInfo info : response.getMetadata().getExpressions()) byName.put(info.getName(), info);

        CqlTranslationResponse.ExpressionInfo adult = byName.get("Adult");
        Assertions.assertEquals("expression", adult.getKind());
        Assertions.assertNull(adult.getOperands());
        Assertions.assertFalse(adult.isFunction());

        CqlTranslationResponse.ExpressionInfo atLeast = byName.get("AtLeast");
        Assertions.assertTrue(atLeast.isFunction());
        Assertions.assertEquals(java.util.List.of("value Integer", "threshold Integer"),
                atLeast.getOperands().stream().map(o -> o.getName() + " " + o.getType()).toList());
        Assertions.assertEquals("Boolean", atLeast.getResultType());

        Assertions.assertEquals(java.util.List.of("observations List<FHIR.Observation>"),
                byName.get("MostRecent").getOperands().stream().map(o -> o.getName() + " " + o.getType()).toList());
        Assertions.assertEquals("Observation", byName.get("MostRecent").getResultType());
        Assertions.assertEquals(java.util.List.of("period Interval<DateTime>", "moment DateTime"),
                byName.get("Within").getOperands().stream().map(o -> o.getName() + " " + o.getType()).toList());
        Assertions.assertEquals(java.util.List.of("code Code", "notes List<String>"),
                byName.get("Label").getOperands().stream().map(o -> o.getName() + " " + o.getType()).toList());
        Assertions.assertTrue(byName.get("Any").isFunction());
        Assertions.assertTrue(byName.get("Any").getOperands().isEmpty());
    }
}
