package com.cqlplatform.service.terminology;

import com.cqlplatform.entity.ValueSetEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.model.terminology.PlatformValueSet.Concept;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.ValueSetRepository;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.measure.FhirCanonicalResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PAT-230 — value sets this installation owns.
 *
 * <p>Life cycle: {@code draft} (editable) → {@code active} (content frozen) → {@code retired}.
 * Content never changes under an active version, so a measure that pins
 * {@code valueset "X": 'url' version '1.2.0'} keeps meaning the same codes; changing the codes
 * means a new version. An unversioned reference resolves to the newest active version, or — so an
 * author can test before activating — the newest draft when nothing is active yet.
 *
 * <p>Only explicit code lists are stored. A rule-based definition (compose filter, nested value
 * set) can be imported only when the resource also carries its expansion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformValueSetService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Concept>> CONCEPT_LIST = new TypeReference<>() {};
    private static final int MAX_CONCEPTS = 20_000;

    private final ValueSetRepository repository;
    private final TenantRepository tenantRepository;
    private final OwnershipVerifier ownershipVerifier;
    private final FhirCanonicalResolver canonical;

    /** What a reference resolved to; {@code draft} tells callers the content can still change. */
    public record Resolved(PlatformValueSet valueSet, boolean pinned) {
        public boolean isDraft() {
            return PlatformValueSet.STATUS_DRAFT.equals(valueSet.getStatus());
        }
    }

    /** Caller's tenant ?? default — see EhrConnectionService for the canonical pattern. */
    public Long effectiveTenantId() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) return tenantId;
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    /**
     * The caller's tenant for LOOKUPS, or empty when none can be determined (no tenant in context
     * and no {@code default} tenant row — a database Flyway has not seeded). Lookups sit in front of
     * terminology endpoints that existed before this store and need no tenant at all; they must
     * degrade to "this installation owns no such value set", not fail. Writes keep using
     * {@link #effectiveTenantId()}, which does fail.
     */
    public Optional<Long> lookupTenantId() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) return Optional.of(tenantId);
        return tenantRepository.findByCode("default").map(com.cqlplatform.entity.TenantEntity::getId);
    }

    /** {@link #resolve} for the caller's tenant. */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolveForCaller(String url, String version) {
        return lookupTenantId().flatMap(tenantId -> resolve(tenantId, url, version));
    }

    /** One entry per URL the caller's tenant owns (newest active, else newest draft), for search results. */
    @Transactional(readOnly = true)
    public List<PlatformValueSet> searchForCaller(String search) {
        Optional<Long> tenantId = lookupTenantId();
        if (tenantId.isEmpty()) return List.of();
        List<ValueSetEntity> rows = search == null || search.isBlank()
                ? repository.findByTenantIdOrderByUpdatedAtDesc(tenantId.get())
                : repository.searchByTenant(tenantId.get(), search.trim());
        Set<String> urls = new LinkedHashSet<>();
        for (ValueSetEntity row : rows) {
            if (!PlatformValueSet.STATUS_RETIRED.equals(row.getStatus())) urls.add(row.getUrl());
        }
        List<PlatformValueSet> result = new ArrayList<>();
        for (String url : urls) {
            resolve(tenantId.get(), url, null).ifPresent(r -> result.add(r.valueSet()));
        }
        return result;
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<PlatformValueSet> list(String search) {
        Long tenantId = effectiveTenantId();
        List<ValueSetEntity> rows = search == null || search.isBlank()
                ? repository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                : repository.searchByTenant(tenantId, search.trim());
        return rows.stream().map(e -> toModel(e, false)).toList();
    }

    @Transactional(readOnly = true)
    public PlatformValueSet get(Long id) {
        return toModel(require(id), true);
    }

    @Transactional(readOnly = true)
    public List<PlatformValueSet> versions(Long id) {
        ValueSetEntity entity = require(id);
        return repository.findByTenantIdAndUrlOrderByCreatedAtDescIdDesc(entity.getTenantId(), entity.getUrl())
                .stream().map(e -> toModel(e, false)).toList();
    }

    /**
     * Resolve a CQL / FHIR reference inside one tenant. {@code tenantId} is explicit because the
     * CQL engine may call from a thread that has no {@link TenantContext}.
     */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolve(Long tenantId, String url, String version) {
        if (tenantId == null || url == null || url.isBlank()) return Optional.empty();
        if (version != null && !version.isBlank()) {
            return repository.findByTenantIdAndUrlAndVersion(tenantId, url, version)
                    .map(e -> new Resolved(toModel(e, true), true));
        }
        List<ValueSetEntity> all = repository.findByTenantIdAndUrlOrderByCreatedAtDescIdDesc(tenantId, url);
        return all.stream().filter(e -> PlatformValueSet.STATUS_ACTIVE.equals(e.getStatus())).findFirst()
                .or(() -> all.stream().filter(e -> PlatformValueSet.STATUS_DRAFT.equals(e.getStatus())).findFirst())
                .map(e -> new Resolved(toModel(e, true), false));
    }

    // ---------------------------------------------------------------- write

    @Transactional
    public PlatformValueSet create(PlatformValueSet request) {
        Long tenantId = effectiveTenantId();
        String name = trimToNull(request.getName());
        List<String> problems = new ArrayList<>();
        if (name == null) problems.add("name is required");
        String url = trimToNull(request.getUrl());
        if (url == null && name != null) url = canonical.valueSetUrl(name);
        if (url != null && !url.matches("^(https?://|urn:)\\S+$")) {
            problems.add("url must be an absolute http(s) URL or a urn: '" + url + "'");
        }
        String version = Optional.ofNullable(trimToNull(request.getVersion())).orElse("1.0.0");
        List<Concept> concepts = normalise(request.getConcepts(), problems);
        if (!problems.isEmpty()) throw new ValidationException("Value set is not valid", problems);
        if (repository.existsByTenantIdAndUrlAndVersion(tenantId, url, version)) {
            throw new DuplicateResourceException("Value set " + url + " version " + version + " already exists");
        }

        ValueSetEntity entity = ValueSetEntity.builder()
                .tenantId(tenantId)
                .url(url).version(version).name(name)
                .title(trimToNull(request.getTitle()))
                .description(trimToNull(request.getDescription()))
                .publisher(trimToNull(request.getPublisher()))
                .status(PlatformValueSet.STATUS_DRAFT)
                .origin(PlatformValueSet.ORIGIN_AUTHORED)
                .concepts(write(concepts)).conceptCount(concepts.size())
                .ownerUsername(ownershipVerifier.getCurrentUsername())
                .build();
        return toModel(repository.save(entity), true);
    }

    /** Only a draft can be edited: an active version is what measures have been approved against. */
    @Transactional
    public PlatformValueSet update(Long id, PlatformValueSet request) {
        ValueSetEntity entity = requireOwned(id);
        if (!PlatformValueSet.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ValidationException("Version " + entity.getVersion() + " is " + entity.getStatus()
                    + " and can no longer be edited. Create a new version to change the codes.");
        }
        List<String> problems = new ArrayList<>();
        String name = trimToNull(request.getName());
        if (name == null) problems.add("name is required");
        List<Concept> concepts = normalise(request.getConcepts(), problems);
        if (!problems.isEmpty()) throw new ValidationException("Value set is not valid", problems);

        // url and version identify the row in CQL references; they change through createVersion only.
        entity.setName(name);
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setPublisher(trimToNull(request.getPublisher()));
        entity.setConcepts(write(concepts));
        entity.setConceptCount(concepts.size());
        return toModel(repository.save(entity), true);
    }

    @Transactional
    public PlatformValueSet createVersion(Long id, String newVersion) {
        ValueSetEntity source = requireOwned(id);
        String version = trimToNull(newVersion);
        if (version == null) throw new ValidationException("version is required");
        if (repository.existsByTenantIdAndUrlAndVersion(source.getTenantId(), source.getUrl(), version)) {
            throw new DuplicateResourceException("Value set " + source.getUrl() + " version " + version + " already exists");
        }
        ValueSetEntity copy = ValueSetEntity.builder()
                .tenantId(source.getTenantId())
                .url(source.getUrl()).version(version).name(source.getName())
                .title(source.getTitle()).description(source.getDescription()).publisher(source.getPublisher())
                .status(PlatformValueSet.STATUS_DRAFT)
                .origin(PlatformValueSet.ORIGIN_AUTHORED)
                .concepts(source.getConcepts()).conceptCount(source.getConceptCount())
                .ownerUsername(ownershipVerifier.getCurrentUsername())
                .build();
        return toModel(repository.save(copy), true);
    }

    @Transactional
    public PlatformValueSet activate(Long id) {
        ValueSetEntity entity = requireOwned(id);
        if (!PlatformValueSet.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ValidationException("Only a draft can be activated; this version is " + entity.getStatus() + ".");
        }
        if (entity.getConceptCount() == 0) {
            throw new ValidationException("An empty value set cannot be activated: every membership test against it would be false.");
        }
        entity.setStatus(PlatformValueSet.STATUS_ACTIVE);
        return toModel(repository.save(entity), true);
    }

    @Transactional
    public PlatformValueSet retire(Long id) {
        ValueSetEntity entity = requireOwned(id);
        if (!PlatformValueSet.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ValidationException("Only an active version can be retired; this version is " + entity.getStatus() + ".");
        }
        entity.setStatus(PlatformValueSet.STATUS_RETIRED);
        return toModel(repository.save(entity), true);
    }

    /** Drafts only — an active or retired version may be what a stored measure report was computed with. */
    @Transactional
    public void delete(Long id) {
        ValueSetEntity entity = requireOwned(id);
        if (!PlatformValueSet.STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ValidationException("Only a draft can be deleted; retire this version instead.");
        }
        repository.delete(entity);
    }

    // ---------------------------------------------------------------- FHIR

    /** What happened to one ValueSet of an import. */
    public record ImportOutcome(PlatformValueSet valueSet, boolean created) {}

    @Transactional
    public PlatformValueSet importFhir(JsonNode resource) {
        ImportOutcome outcome = importFhirIfAbsent(resource);
        if (!outcome.created()) {
            throw new DuplicateResourceException("Value set " + outcome.valueSet().getUrl() + " version "
                    + outcome.valueSet().getVersion() + " already exists");
        }
        return outcome.valueSet();
    }

    /**
     * Import unless this tenant already has the same url + version (then the stored one is
     * returned untouched — a package must not silently overwrite local content).
     */
    @Transactional
    public ImportOutcome importFhirIfAbsent(JsonNode resource) {
        if (resource == null || !"ValueSet".equals(resource.path("resourceType").asText())) {
            throw new ValidationException("Not a FHIR ValueSet resource");
        }
        Long tenantId = effectiveTenantId();
        List<String> problems = new ArrayList<>();
        String url = trimToNull(resource.path("url").asText(null));
        if (url == null) problems.add("ValueSet.url is required: it is how CQL refers to the value set");
        String version = Optional.ofNullable(trimToNull(resource.path("version").asText(null))).orElse("1.0.0");
        List<Concept> concepts = normalise(readConcepts(resource, problems), problems);
        if (!problems.isEmpty()) throw new ValidationException("ValueSet cannot be imported", problems);

        Optional<ValueSetEntity> existing = repository.findByTenantIdAndUrlAndVersion(tenantId, url, version);
        if (existing.isPresent()) {
            return new ImportOutcome(toModel(existing.get(), true), false);
        }
        String name = importedName(resource, url);
        ValueSetEntity entity = ValueSetEntity.builder()
                .tenantId(tenantId)
                .url(url).version(version).name(name)
                .title(trimToNull(resource.path("title").asText(null)))
                .description(trimToNull(resource.path("description").asText(null)))
                .publisher(trimToNull(resource.path("publisher").asText(null)))
                // Like an imported measure: it arrives as a draft, this installation decides when it is active.
                .status(PlatformValueSet.STATUS_DRAFT)
                .origin(PlatformValueSet.ORIGIN_IMPORTED)
                .concepts(write(concepts)).conceptCount(concepts.size())
                .sourceJson(resource.toString())
                .ownerUsername(ownershipVerifier.getCurrentUsername())
                .build();
        return new ImportOutcome(toModel(repository.save(entity), true), true);
    }

    @Transactional(readOnly = true)
    public ObjectNode exportFhir(Long id) {
        return toFhir(get(id));
    }

    /**
     * The value set as a FHIR R4 ValueSet: compose (the definition) and expansion (the same codes,
     * flat). One that was imported and not edited since goes out as the resource that came in —
     * identifiers, copyright, extensions this platform does not model — with only the status
     * replaced by this installation's.
     */
    public ObjectNode toFhir(PlatformValueSet vs) {
        ObjectNode original = unchangedOriginal(vs);
        if (original != null) {
            original.put("status", vs.getStatus());
            return original;
        }
        ObjectNode resource = MAPPER.createObjectNode();
        resource.put("resourceType", "ValueSet");
        resource.put("id", FhirCanonicalResolver.idPart(vs.getName()));
        resource.put("url", vs.getUrl());
        resource.put("version", vs.getVersion());
        resource.put("name", vs.getName());
        if (vs.getTitle() != null) resource.put("title", vs.getTitle());
        resource.put("status", vs.getStatus());
        if (vs.getUpdatedAt() != null) resource.put("date", vs.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (vs.getPublisher() != null) resource.put("publisher", vs.getPublisher());
        if (vs.getDescription() != null) resource.put("description", vs.getDescription());

        Map<String, ArrayNode> bySystem = new LinkedHashMap<>();
        ArrayNode include = resource.putObject("compose").putArray("include");
        ObjectNode expansion = MAPPER.createObjectNode();
        ArrayNode contains = expansion.putArray("contains");
        for (Concept c : Optional.ofNullable(vs.getConcepts()).orElse(List.of())) {
            String key = c.getSystem() + "|" + Optional.ofNullable(c.getVersion()).orElse("");
            ArrayNode concepts = bySystem.computeIfAbsent(key, k -> {
                ObjectNode inc = include.addObject();
                inc.put("system", c.getSystem());
                if (c.getVersion() != null) inc.put("version", c.getVersion());
                return inc.putArray("concept");
            });
            ObjectNode concept = concepts.addObject().put("code", c.getCode());
            ObjectNode flat = contains.addObject().put("system", c.getSystem());
            if (c.getVersion() != null) flat.put("version", c.getVersion());
            flat.put("code", c.getCode());
            if (c.getDisplay() != null) {
                concept.put("display", c.getDisplay());
                flat.put("display", c.getDisplay());
            }
        }
        if (vs.getUpdatedAt() != null) {
            expansion.put("timestamp", vs.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        expansion.put("total", contains.size());
        resource.set("expansion", expansion);
        return resource;
    }

    // ---------------------------------------------------------------- helpers

    private static String importedName(JsonNode resource, String url) {
        return Optional.ofNullable(trimToNull(resource.path("name").asText(null)))
                .or(() -> Optional.ofNullable(trimToNull(resource.path("title").asText(null))))
                .orElse(FhirCanonicalResolver.idPart(url.substring(url.lastIndexOf('/') + 1)));
    }

    private static ObjectNode unchangedOriginal(PlatformValueSet vs) {
        if (!PlatformValueSet.ORIGIN_IMPORTED.equals(vs.getOrigin()) || vs.getSourceJson() == null) return null;
        try {
            JsonNode source = MAPPER.readTree(vs.getSourceJson());
            if (!(source instanceof ObjectNode original)) return null;
            List<String> ignored = new ArrayList<>();
            boolean same = normalise(readConcepts(source, ignored), ignored).equals(vs.getConcepts())
                    && java.util.Objects.equals(importedName(source, vs.getUrl()), vs.getName())
                    && java.util.Objects.equals(trimToNull(source.path("title").asText(null)), vs.getTitle())
                    && java.util.Objects.equals(trimToNull(source.path("description").asText(null)), vs.getDescription())
                    && java.util.Objects.equals(trimToNull(source.path("publisher").asText(null)), vs.getPublisher());
            return same ? original : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ValueSetEntity require(Long id) {
        // Another tenant's id is indistinguishable from a missing one.
        return repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("ValueSet", id));
    }

    private ValueSetEntity requireOwned(Long id) {
        ValueSetEntity entity = require(id);
        ownershipVerifier.verifyOwnership(entity.getOwnerUsername());
        return entity;
    }

    /** compose.include[].concept first; the expansion only when the compose cannot be enumerated. */
    private static List<Concept> readConcepts(JsonNode resource, List<String> problems) {
        List<Concept> concepts = new ArrayList<>();
        boolean ruleBased = false;
        for (JsonNode include : resource.path("compose").path("include")) {
            if (include.path("concept").isArray() && !include.path("concept").isEmpty()) {
                for (JsonNode c : include.path("concept")) {
                    concepts.add(Concept.builder()
                            .system(include.path("system").asText(null))
                            .version(include.path("version").asText(null))
                            .code(c.path("code").asText(null))
                            .display(c.path("display").asText(null)).build());
                }
            } else {
                ruleBased = true; // whole code system, filter, or nested value set
            }
        }
        if (resource.path("compose").path("exclude").isArray() && !resource.path("compose").path("exclude").isEmpty()) {
            ruleBased = true;
        }
        if (!ruleBased) return concepts;

        JsonNode contains = resource.path("expansion").path("contains");
        if (!contains.isArray() || contains.isEmpty()) {
            problems.add("The definition is rule-based (whole code system, filter, exclude or nested value set) and the "
                    + "resource carries no expansion. Expand it on a terminology server first and import the expanded resource.");
            return List.of();
        }
        List<Concept> expanded = new ArrayList<>();
        flatten(contains, expanded);
        return expanded;
    }

    private static void flatten(JsonNode contains, List<Concept> out) {
        for (JsonNode c : contains) {
            if (!c.path("abstract").asBoolean(false) && c.hasNonNull("code")) {
                out.add(Concept.builder()
                        .system(c.path("system").asText(null)).version(c.path("version").asText(null))
                        .code(c.path("code").asText(null)).display(c.path("display").asText(null)).build());
            }
            if (c.path("contains").isArray()) flatten(c.path("contains"), out);
        }
    }

    /** Trim, require system + code, drop exact duplicates (first display wins), keep author order. */
    private static List<Concept> normalise(List<Concept> input, List<String> problems) {
        List<Concept> result = new ArrayList<>();
        if (input == null) return result;
        if (input.size() > MAX_CONCEPTS) {
            problems.add("A value set can hold at most " + MAX_CONCEPTS + " codes (got " + input.size() + ")");
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        int row = 0;
        for (Concept c : input) {
            row++;
            String system = c == null ? null : trimToNull(c.getSystem());
            String code = c == null ? null : trimToNull(c.getCode());
            if (system == null || code == null) {
                if (problems.size() < 20) problems.add("code " + row + ": system and code are both required");
                continue;
            }
            String version = trimToNull(c.getVersion());
            if (!seen.add(system + "|" + Optional.ofNullable(version).orElse("") + "|" + code)) continue;
            result.add(Concept.builder().system(system).version(version).code(code).display(trimToNull(c.getDisplay())).build());
        }
        return result;
    }

    private static String write(List<Concept> concepts) {
        try {
            return MAPPER.writeValueAsString(concepts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise value set concepts", e);
        }
    }

    private PlatformValueSet toModel(ValueSetEntity e, boolean withConcepts) {
        List<Concept> concepts = null;
        if (withConcepts) {
            try {
                concepts = MAPPER.readValue(e.getConcepts(), CONCEPT_LIST);
            } catch (JsonProcessingException ex) {
                // Never evaluate against a silently empty list: that turns every membership test false.
                throw new IllegalStateException("Stored concepts of value set #" + e.getId() + " cannot be read", ex);
            }
        }
        return PlatformValueSet.builder()
                .id(e.getId()).url(e.getUrl()).version(e.getVersion()).name(e.getName())
                .title(e.getTitle()).description(e.getDescription()).status(e.getStatus())
                .publisher(e.getPublisher()).concepts(concepts).conceptCount(e.getConceptCount())
                .origin(e.getOrigin()).ownerUsername(e.getOwnerUsername())
                .sourceJson(withConcepts ? e.getSourceJson() : null)
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
