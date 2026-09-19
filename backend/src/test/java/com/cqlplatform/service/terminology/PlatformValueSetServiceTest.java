package com.cqlplatform.service.terminology;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.ValueSetEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.model.terminology.PlatformValueSet.Concept;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.ValueSetRepository;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.terminology.PlatformValueSetService.ImportOutcome;
import com.cqlplatform.service.terminology.PlatformValueSetService.Resolved;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-230 — the value set store against a real repository (H2): life cycle, version resolution,
 * tenant isolation, FHIR import / export.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "alice", roles = {"USER"})
class PlatformValueSetServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NHI = "https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw";
    private static final String URL = "https://hospital.example.tw/fhir/ValueSet/HbA1cOrders";

    @Autowired private PlatformValueSetService service;
    @Autowired private ValueSetRepository repository;
    @Autowired private TenantRepository tenantRepository;

    private Long tenantA;
    private Long tenantB;

    @BeforeEach
    void setUp() {
        tenantA = tenant("vs-test-a");
        tenantB = tenant("vs-test-b");
        TenantContext.setCurrentTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Long tenant(String code) {
        return tenantRepository.findByCode(code)
                .orElseGet(() -> tenantRepository.save(TenantEntity.builder().code(code).name(code).active(true).build()))
                .getId();
    }

    private static Concept code(String code, String display) {
        return Concept.builder().system(NHI).code(code).display(display).build();
    }

    private PlatformValueSet draft(String version, Concept... concepts) {
        return service.create(PlatformValueSet.builder().url(URL).version(version).name("HbA1cOrders")
                .title("HbA1c order codes").concepts(List.of(concepts)).build());
    }

    // ------------------------------------------------------------ create / edit

    @Test
    void create_startsAsTheCallersDraft_andDerivesUrlAndVersion() {
        PlatformValueSet created = service.create(PlatformValueSet.builder().name("Local Drug List")
                .concepts(List.of(code("A10BA02", "metformin"))).build());

        assertThat(created.getStatus()).isEqualTo("draft");
        assertThat(created.getOwnerUsername()).isEqualTo("alice");
        assertThat(created.getVersion()).isEqualTo("1.0.0");
        assertThat(created.getUrl()).endsWith("/ValueSet/Local-Drug-List");
        assertThat(created.getOrigin()).isEqualTo("authored");
        assertThat(repository.findByIdAndTenantId(created.getId(), tenantA)).isPresent();
    }

    @Test
    void create_rejectsWhatCouldNeverResolve_andListsEveryProblem() {
        assertThatThrownBy(() -> service.create(PlatformValueSet.builder().name(" ").url("not a url")
                .concepts(List.of(Concept.builder().code("09006C").build())).build()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getDetails())
                        .anyMatch(d -> d.contains("name is required"))
                        .anyMatch(d -> d.contains("url must be"))
                        .anyMatch(d -> d.contains("code 1: system and code are both required")));
    }

    @Test
    void concepts_areTrimmedAndDeduplicated_keepingAuthorOrder() {
        PlatformValueSet created = draft("1.0.0", code(" 09006C ", "HbA1c"), code("09139C", "Glycated albumin"),
                code("09006C", "duplicate"));

        assertThat(created.getConcepts()).extracting(Concept::getCode).containsExactly("09006C", "09139C");
        assertThat(created.getConcepts().get(0).getDisplay()).isEqualTo("HbA1c");
        assertThat(created.getConceptCount()).isEqualTo(2);
    }

    @Test
    void sameUrlAndVersionTwice_isADuplicate() {
        draft("1.0.0", code("09006C", "HbA1c"));
        assertThatThrownBy(() -> draft("1.0.0", code("09006C", "HbA1c"))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void activeVersionIsFrozen_changingCodesMeansANewVersion() {
        PlatformValueSet v1 = draft("1.0.0", code("09006C", "HbA1c"));
        service.activate(v1.getId());

        assertThatThrownBy(() -> service.update(v1.getId(), PlatformValueSet.builder().name("HbA1cOrders")
                .concepts(List.of(code("XXXX", "sneaked in"))).build()))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Create a new version");
        assertThatThrownBy(() -> service.delete(v1.getId())).isInstanceOf(ValidationException.class);

        PlatformValueSet v2 = service.createVersion(v1.getId(), "1.1.0");
        assertThat(v2.getStatus()).isEqualTo("draft");
        assertThat(v2.getConcepts()).extracting(Concept::getCode).containsExactly("09006C");
        service.update(v2.getId(), PlatformValueSet.builder().name("HbA1cOrders")
                .concepts(List.of(code("09006C", "HbA1c"), code("09139C", "Glycated albumin"))).build());

        // v1 still means exactly what it meant.
        assertThat(service.get(v1.getId()).getConcepts()).hasSize(1);
        assertThat(service.versions(v1.getId())).extracting(PlatformValueSet::getVersion).containsExactly("1.1.0", "1.0.0");
    }

    @Test
    void anEmptyValueSetCannotBeActivated() {
        PlatformValueSet empty = draft("1.0.0");
        assertThatThrownBy(() -> service.activate(empty.getId()))
                .isInstanceOf(ValidationException.class).hasMessageContaining("empty");
    }

    // ------------------------------------------------------------ resolution

    @Test
    void unversionedReference_isTheNewestActive_aPinnedOneIsExact() {
        PlatformValueSet v1 = draft("1.0.0", code("09006C", "HbA1c"));
        service.activate(v1.getId());
        PlatformValueSet v2 = service.createVersion(v1.getId(), "2.0.0"); // still a draft

        Resolved unpinned = service.resolve(tenantA, URL, null).orElseThrow();
        assertThat(unpinned.valueSet().getVersion()).isEqualTo("1.0.0"); // a newer DRAFT does not take over
        assertThat(unpinned.pinned()).isFalse();
        assertThat(unpinned.isDraft()).isFalse();

        Resolved pinned = service.resolve(tenantA, URL, "2.0.0").orElseThrow();
        assertThat(pinned.valueSet().getId()).isEqualTo(v2.getId());
        assertThat(pinned.pinned()).isTrue();
        assertThat(pinned.isDraft()).isTrue();

        assertThat(service.resolve(tenantA, URL, "9.9.9")).isEmpty();
    }

    @Test
    void beforeAnythingIsActive_theDraftResolves_soTheAuthorCanTest_butRetiredNeverDoes() {
        PlatformValueSet v1 = draft("1.0.0", code("09006C", "HbA1c"));
        assertThat(service.resolve(tenantA, URL, null).orElseThrow().isDraft()).isTrue();

        service.activate(v1.getId());
        service.retire(v1.getId());
        assertThat(service.resolve(tenantA, URL, null)).isEmpty();
        // Pinning a retired version still works: a stored report may have been computed with it.
        assertThat(service.resolve(tenantA, URL, "1.0.0")).isPresent();
    }

    @Test
    void anotherTenantsValueSet_doesNotExist() {
        PlatformValueSet mine = draft("1.0.0", code("09006C", "HbA1c"));
        service.activate(mine.getId());

        assertThat(service.resolve(tenantB, URL, null)).isEmpty();
        TenantContext.setCurrentTenantId(tenantB);
        assertThat(service.list(null)).isEmpty();
        assertThatThrownBy(() -> service.get(mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.exportFhir(mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        // …and the same URL + version is free to use there.
        assertThat(draft("1.0.0", code("OTHER", "other hospital's meaning")).getId()).isNotEqualTo(mine.getId());
    }

    @Test
    @WithMockUser(username = "bob", roles = {"USER"})
    void someoneElsesValueSet_canBeReadButNotChanged() {
        ValueSetEntity alices = repository.save(ValueSetEntity.builder().tenantId(tenantA).url(URL).version("1.0.0")
                .name("HbA1cOrders").status("draft").origin("authored").concepts("[]").conceptCount(0)
                .ownerUsername("alice").build());

        assertThat(service.get(alices.getId()).getName()).isEqualTo("HbA1cOrders");
        assertThatThrownBy(() -> service.delete(alices.getId())).isNotInstanceOf(ResourceNotFoundException.class)
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findById(alices.getId())).isPresent();
    }

    @Test
    void storedConceptsThatCannotBeRead_failLoudly_neverAsAnEmptyList() {
        ValueSetEntity broken = repository.save(ValueSetEntity.builder().tenantId(tenantA).url(URL).version("1.0.0")
                .name("HbA1cOrders").status("active").origin("authored").concepts("{not json").conceptCount(3)
                .ownerUsername("alice").build());

        assertThatThrownBy(() -> service.resolve(tenantA, URL, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("#" + broken.getId());
    }

    @Test
    void lookupsWithoutAnyResolvableTenant_findNothing_insteadOfFailing() {
        // An unseeded database (H2 tests, or Flyway not yet run): no tenant in context, no `default` row.
        // The terminology endpoints that existed before this store must keep working.
        TenantContext.clear();
        tenantRepository.findByCode("default").ifPresent(tenantRepository::delete);

        assertThat(service.lookupTenantId()).isEmpty();
        assertThat(service.resolveForCaller(URL, null)).isEmpty();
        assertThat(service.searchForCaller("hba1c")).isEmpty();
        // Writes still refuse: a value set must belong to a tenant.
        assertThatThrownBy(() -> draft("1.0.0", code("09006C", "HbA1c"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void searchForCaller_isOneEntryPerUrl_theVersionAnUnpinnedReferenceWouldGet() {
        PlatformValueSet v1 = draft("1.0.0", code("09006C", "HbA1c"));
        service.activate(v1.getId());
        service.createVersion(v1.getId(), "2.0.0");

        assertThat(service.searchForCaller("hba1c")).singleElement()
                .satisfies(vs -> assertThat(vs.getVersion()).isEqualTo("1.0.0"));
        service.retire(v1.getId());
        assertThat(service.searchForCaller("hba1c")).singleElement()
                .satisfies(vs -> assertThat(vs.getStatus()).isEqualTo("draft")); // only the 2.0.0 draft is left
    }

    // ------------------------------------------------------------ FHIR

    @Test
    void export_hasTheDefinitionGroupedBySystem_andTheSameCodesAsAnExpansion() {
        PlatformValueSet vs = service.create(PlatformValueSet.builder().url(URL).name("Mixed").concepts(List.of(
                code("09006C", "HbA1c"),
                Concept.builder().system("http://loinc.org").version("2.77").code("4548-4").display("HbA1c/Hb.total").build(),
                code("09139C", "Glycated albumin"))).build());

        ObjectNode fhir = service.exportFhir(vs.getId());

        assertThat(fhir.path("url").asText()).isEqualTo(URL);
        assertThat(fhir.path("status").asText()).isEqualTo("draft");
        JsonNode include = fhir.path("compose").path("include");
        assertThat(include).hasSize(2);
        assertThat(include.get(0).path("system").asText()).isEqualTo(NHI);
        assertThat(include.get(0).path("concept")).hasSize(2);
        assertThat(include.get(1).path("version").asText()).isEqualTo("2.77");
        assertThat(fhir.path("expansion").path("total").asInt()).isEqualTo(3);
        assertThat(StreamSupport.stream(fhir.path("expansion").path("contains").spliterator(), false)
                .map(c -> c.path("code").asText())).containsExactly("09006C", "4548-4", "09139C");
    }

    private static JsonNode fhirValueSet(String body) throws Exception {
        return MAPPER.readTree("{\"resourceType\":\"ValueSet\"," + body + "}");
    }

    @Test
    void import_readsExplicitCodes_arrivesAsADraft_andGoesBackOutUnchanged() throws Exception {
        JsonNode source = fhirValueSet("""
                "url":"https://other.example.org/fhir/ValueSet/dm","version":"3.0.0","name":"DiabetesDx","status":"active",
                "identifier":[{"system":"urn:ietf:rfc:3986","value":"urn:oid:1.2.3"}],
                "extension":[{"url":"http://example.org/unknown","valueString":"kept"}],
                "compose":{"include":[{"system":"http://hl7.org/fhir/sid/icd-10-cm","concept":[
                  {"code":"E11.9","display":"Type 2 DM"},{"code":"E10.9"}]}]}""");

        PlatformValueSet imported = service.importFhir(source);

        assertThat(imported.getStatus()).isEqualTo("draft"); // it arrived active; here it is a draft until activated here
        assertThat(imported.getOrigin()).isEqualTo("imported");
        assertThat(imported.getConcepts()).extracting(Concept::getCode).containsExactly("E11.9", "E10.9");

        ObjectNode exported = service.exportFhir(imported.getId());
        assertThat(exported.path("extension").get(0).path("valueString").asText()).isEqualTo("kept");
        assertThat(exported.path("identifier").get(0).path("value").asText()).isEqualTo("urn:oid:1.2.3");
        assertThat(exported.path("status").asText()).isEqualTo("draft");

        // Once edited it is this installation's content, and is rebuilt from what is stored.
        service.update(imported.getId(), PlatformValueSet.builder().name("DiabetesDx").concepts(List.of(
                Concept.builder().system("http://hl7.org/fhir/sid/icd-10-cm").code("E11.9").build())).build());
        ObjectNode rebuilt = service.exportFhir(imported.getId());
        assertThat(rebuilt.has("extension")).isFalse();
        assertThat(rebuilt.path("compose").path("include").get(0).path("concept")).hasSize(1);
    }

    @Test
    void import_ofARuleBasedDefinition_usesItsExpansion_orIsRefused() throws Exception {
        String ruleBased = "\"url\":\"https://other.example.org/fhir/ValueSet/all-loinc-hba1c\","
                + "\"compose\":{\"include\":[{\"system\":\"http://loinc.org\",\"filter\":[{\"property\":\"parent\",\"op\":\"=\",\"value\":\"LP16413-4\"}]}]}";

        assertThatThrownBy(() -> service.importFhir(fhirValueSet(ruleBased)))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getDetails()).anyMatch(d -> d.contains("rule-based")));

        PlatformValueSet expanded = service.importFhir(fhirValueSet(ruleBased + """
                ,"expansion":{"contains":[{"system":"http://loinc.org","code":"4548-4","display":"HbA1c"},
                  {"abstract":true,"display":"grouper","contains":[{"system":"http://loinc.org","code":"17856-6"}]}]}"""));
        assertThat(expanded.getConcepts()).extracting(Concept::getCode).containsExactly("4548-4", "17856-6");
    }

    @Test
    void import_needsAUrl_andNeverOverwritesWhatIsAlreadyHere() throws Exception {
        assertThatThrownBy(() -> service.importFhir(fhirValueSet("\"name\":\"NoUrl\"")))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getDetails()).anyMatch(d -> d.contains("ValueSet.url is required")));

        PlatformValueSet local = draft("1.0.0", code("09006C", "HbA1c"));
        JsonNode theirs = fhirValueSet("\"url\":\"" + URL + "\",\"version\":\"1.0.0\",\"compose\":{\"include\":[{\"system\":\""
                + NHI + "\",\"concept\":[{\"code\":\"DIFFERENT\"}]}]}");

        ImportOutcome outcome = service.importFhirIfAbsent(theirs);
        assertThat(outcome.created()).isFalse();
        assertThat(outcome.valueSet().getId()).isEqualTo(local.getId());
        assertThat(service.get(local.getId()).getConcepts()).extracting(Concept::getCode).containsExactly("09006C");
        assertThatThrownBy(() -> service.importFhir(theirs)).isInstanceOf(DuplicateResourceException.class);
        assertThat(Optional.of(service.list("hba1c")).orElseThrow()).hasSize(1);
    }
}
