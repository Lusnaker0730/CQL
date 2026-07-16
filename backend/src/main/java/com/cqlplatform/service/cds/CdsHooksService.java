package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsFeedbackEntity;
import com.cqlplatform.entity.CdsServiceConfigEntity;
import com.cqlplatform.entity.CdsServicePrefetchEntity;
import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.repository.CdsFeedbackRepository;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.validation.HookContextRequirements;
import com.cqlplatform.validation.HookTypeValidator;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CDS Hooks service handling CRUD, discovery, feedback, and versioning.
 * Invocation logic is delegated to {@link CdsInvocationService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CdsHooksService {

    private final CdsServiceConfigRepository repository;
    private final ObjectMapper objectMapper;
    private final CdsInvocationService invocationService;
    private final CqlTupleCardStrategy tupleStrategy;
    /**
     * Optional dependencies — wrapped in {@link Optional} so Spring auto-injects
     * {@code Optional.empty()} when no bean exists (e.g. test slices). Constructor
     * injection replaces field-injection {@code @Autowired(required = false)}
     * which violated the project standard ("禁止 @Autowired 在欄位上") and made
     * test setup harder.
     */
    private final Optional<CdsFeedbackRepository> feedbackRepository;
    private final Optional<CqlLibraryRepository> cqlLibraryRepository;
    private final Optional<com.cqlplatform.repository.TenantRepository> tenantRepository;
    private final Map<String, CdsServiceConfig> serviceConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadServicesFromDatabase() {
        log.info("Loading CDS services from database...");
        List<CdsServiceConfigEntity> entities = repository.findAllEnabledWithPrefetch();

        // For versioning: group by serviceName, keep only latest enabled version
        Map<String, CdsServiceConfigEntity> latestByServiceName = new LinkedHashMap<>();
        for (CdsServiceConfigEntity entity : entities) {
            String key = entity.getServiceName() != null ? entity.getServiceName() : entity.getId();
            CdsServiceConfigEntity existing = latestByServiceName.get(key);
            if (existing == null || entity.getVersion() > existing.getVersion()) {
                latestByServiceName.put(key, entity);
            }
        }

        for (CdsServiceConfigEntity entity : latestByServiceName.values()) {
            CdsServiceConfig config = entityToConfig(entity);
            serviceConfigs.put(config.getId(), config);
            log.info("Loaded CDS service: {} (v{})", config.getId(), entity.getVersion());
        }
        log.info("Loaded {} CDS services from database", latestByServiceName.size());
    }

    // --- CRUD ---

    @Transactional
    public CdsServiceConfigResponse createService(CdsServiceConfigRequest request) {
        return createService(request, null);
    }

    @Transactional
    public CdsServiceConfigResponse createService(CdsServiceConfigRequest request, String ownerUsername) {
        HookTypeValidator.validate(request.getHook());

        String serviceName = request.getId();
        Optional<Integer> maxVersion = repository.findMaxVersionByServiceName(serviceName);
        int newVersion = maxVersion.map(v -> v + 1).orElse(1);

        String actualId = newVersion > 1 ? serviceName + "-v" + newVersion : serviceName;

        if (repository.existsById(actualId)) {
            throw new IllegalArgumentException("Service with ID '" + actualId + "' already exists");
        }

        CdsServiceConfigEntity entity = requestToEntity(request);
        entity.setId(actualId);
        entity.setServiceName(serviceName);
        entity.setVersion(newVersion);
        if (ownerUsername != null) {
            entity.setOwnerUsername(ownerUsername);
        }
        entity.setTenantId(effectiveTenantId());
        entity = repository.save(entity);

        syncCqlLibrary(entity.getCqlContent());

        CdsServiceConfig config = entityToConfig(entity);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            // Multi-step cache mutation (remove old versions + add new) is NOT
            // atomic on a ConcurrentHashMap — two concurrent createService calls
            // for the same serviceName could leave stale entries. Wrap in a
            // synchronized block so the cache reflects exactly one version per
            // serviceName at any observation point.
            synchronized (serviceConfigs) {
                serviceConfigs.entrySet().removeIf(e -> {
                    CdsServiceConfig c = e.getValue();
                    return serviceName.equals(c.getServiceName());
                });
                serviceConfigs.put(config.getId(), config);
            }
        }

        log.info("Created CDS service: {} (v{})", entity.getId(), newVersion);
        return entityToResponse(entity);
    }

    /**
     * Verifies that {@code username} is the owner of {@code entity} or {@code isAdmin}.
     * Throws {@link AccessDeniedException} (mapped to HTTP 403 by the global
     * exception handler) when neither condition holds. The check accepts a
     * {@code null} {@code ownerUsername} as legacy / system-owned and skips the
     * comparison so existing un-owned rows aren't accidentally locked out.
     */
    private void verifyOwnership(CdsServiceConfigEntity entity, String username, boolean isAdmin, String action) {
        if (isAdmin) {
            // Admin bypass is bounded to the admin's own clinic (Phase 2 — #698 PR-C2).
            // A null entity tenant is legacy data (pre-V64 rows are backfilled, so this
            // only occurs in non-Spring test wiring) and keeps the old behaviour.
            Long entityTenant = entity.getTenantId();
            if (entityTenant == null || entityTenant.equals(effectiveTenantId())) {
                return;
            }
            throw new AccessDeniedException("You can only " + action + " services in your own clinic");
        }
        String owner = entity.getOwnerUsername();
        if (owner == null || owner.equals(username)) return;
        throw new AccessDeniedException("You can only " + action + " your own services");
    }

    @Transactional
    public CdsServiceConfigResponse toggleShared(String id, boolean shared) {
        // BUG-137: strictly own-tenant. The controller only checks isAdmin(), and a clinic's
        // ADMIN is not a platform operator — without this scope they could publish another
        // tenant's private service onto the anonymous discovery surface, or unshare theirs.
        CdsServiceConfigEntity entity = repository.findByIdAndTenantIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        entity.setShared(shared);
        entity = repository.save(entity);
        log.info("Set service {} shared={}", id, shared);
        return entityToResponse(entity);
    }

    /**
     * Update a CDS service after verifying caller ownership. Throws
     * {@link AccessDeniedException} (→ 403) when the caller is neither the
     * owner nor an admin. Throws {@link IllegalArgumentException} (→ 404) when
     * the service doesn't exist. Prefer this over the bare {@link #updateService}
     * unless you're in a server-internal context where ownership is implicit.
     */
    @Transactional
    public CdsServiceConfigResponse updateServiceIfOwnedBy(
            String id, CdsServiceConfigRequest request, String username, boolean isAdmin) {
        CdsServiceConfigEntity entity = repository.findByIdAndTenantIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        verifyOwnership(entity, username, isAdmin, "modify");
        return updateService(id, request);
    }

    /** Same as {@link #deleteService(String)} but enforces ownership in the service layer. */
    @Transactional
    public void deleteServiceIfOwnedBy(String id, String username, boolean isAdmin) {
        CdsServiceConfigEntity entity = repository.findByIdAndTenantIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        verifyOwnership(entity, username, isAdmin, "delete");
        deleteService(id);
    }

    /** Same as {@link #toggleServiceEnabled(String, boolean)} but enforces ownership. */
    @Transactional
    public CdsServiceConfigResponse toggleServiceEnabledIfOwnedBy(
            String id, boolean enabled, String username, boolean isAdmin) {
        CdsServiceConfigEntity entity = repository.findByIdAndTenantIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        verifyOwnership(entity, username, isAdmin, enabled ? "enable" : "disable");
        return toggleServiceEnabled(id, enabled);
    }

    @Transactional
    public CdsServiceConfigResponse updateService(String id, CdsServiceConfigRequest request) {
        HookTypeValidator.validate(request.getHook());

        CdsServiceConfigEntity entity = repository.findByIdAndTenantIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));

        entity.setHook(request.getHook());
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setCqlContent(request.getCqlContent());
        entity.setCqlLibraryId(request.getCqlLibraryId());
        entity.setDefaultIndicator(request.getDefaultIndicator());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setPlanDefinitionJson(request.getPlanDefinitionJson());
        entity.setCardGenerationMode(request.getCardGenerationMode() != null
                ? request.getCardGenerationMode() : "cql_tuple");

        entity.clearPrefetchItems();
        if (request.getPrefetch() != null) {
            for (Map.Entry<String, String> entry : request.getPrefetch().entrySet()) {
                CdsServicePrefetchEntity prefetch = CdsServicePrefetchEntity.builder()
                        .prefetchKey(entry.getKey())
                        .query(entry.getValue())
                        .build();
                entity.addPrefetchItem(prefetch);
            }
        }

        entity = repository.save(entity);

        syncCqlLibrary(entity.getCqlContent());

        // Two-step cache update (decide presence + put-or-remove) wrapped in
        // the same lock as createService so concurrent writers see consistent
        // state. ConcurrentHashMap individual op is thread-safe; the combination
        // is not.
        synchronized (serviceConfigs) {
            if (Boolean.TRUE.equals(entity.getEnabled())) {
                serviceConfigs.put(id, entityToConfig(entity));
            } else {
                serviceConfigs.remove(id);
            }
        }

        log.info("Updated CDS service: {}", id);
        return entityToResponse(entity);
    }

    @Transactional
    public void deleteService(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Service not found: " + id);
        }
        repository.deleteById(id);
        synchronized (serviceConfigs) {
            serviceConfigs.remove(id);
        }
        log.info("Deleted CDS service: {}", id);
    }

    @Transactional(readOnly = true)
    /**
     * BUG-137 — tenant-scoped read: the caller's own tenant, plus any tenant's shared
     * service (the shared surface is deliberately tenant-agnostic, Option A #698, so the
     * detail view agrees with getServicesForUser's list). Not usable as an authorisation
     * gate for mutations — see rollbackService / toggleShared, which scope strictly.
     */
    public CdsServiceConfigResponse getService(String id) {
        CdsServiceConfigEntity entity = repository.findReadableByIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        return entityToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getAllServices() {
        return repository.findAllByTenantIdWithPrefetch(effectiveTenantId()).stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getServicesForUser(String username) {
        return repository.findByTenantIdAndOwnerUsernameOrSharedTrue(effectiveTenantId(), username).stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getServicesByOwner(String username) {
        return repository.findByOwnerUsernameWithPrefetch(username).stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    // --- Discovery ---

    @Transactional(readOnly = true)
    public List<CdsServiceDefinition> getServiceDefinitionsForUser(String username) {
        List<CdsServiceDefinition> definitions = new ArrayList<>();
        // Tenant-scoped since PR-C2; the API-key auth path sets TenantContext (PAT-198).
        List<CdsServiceConfigEntity> entities =
                repository.findByTenantIdAndOwnerUsernameAndEnabledTrue(effectiveTenantId(), username);

        for (CdsServiceConfigEntity entity : entities) {
            definitions.add(toDefinition(entityToConfig(entity)));
        }

        return definitions;
    }

    @Transactional(readOnly = true)
    public List<CdsServiceDefinition> getSharedServiceDefinitions() {
        List<CdsServiceDefinition> definitions = new ArrayList<>();

        // Option A (#698 PR-C2): the anonymous discovery surface lists ONLY services a
        // clinic explicitly published (shared=true) — private services are no longer
        // enumerable across tenants. The shared surface is deliberately tenant-agnostic.
        List<CdsServiceConfigEntity> entities = repository.findBySharedTrueAndEnabledTrue();

        Map<String, CdsServiceConfigEntity> latestByServiceName = new LinkedHashMap<>();
        for (CdsServiceConfigEntity entity : entities) {
            String key = entity.getServiceName() != null ? entity.getServiceName() : entity.getId();
            CdsServiceConfigEntity existing = latestByServiceName.get(key);
            if (existing == null || entity.getVersion() > existing.getVersion()) {
                latestByServiceName.put(key, entity);
            }
        }

        for (CdsServiceConfigEntity entity : latestByServiceName.values()) {
            definitions.add(toDefinition(entityToConfig(entity)));
        }

        return definitions;
    }

    @Transactional
    public CdsServiceConfigResponse toggleServiceEnabled(String id, boolean enabled) {
        CdsServiceConfigEntity entity = repository.findByIdAndTenantIdWithPrefetch(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));

        entity.setEnabled(enabled);
        entity = repository.save(entity);

        synchronized (serviceConfigs) {
            if (enabled) {
                serviceConfigs.put(id, entityToConfig(entity));
            } else {
                serviceConfigs.remove(id);
            }
        }

        log.info("Toggled CDS service {} enabled: {}", id, enabled);
        return entityToResponse(entity);
    }

    public void registerService(CdsServiceConfig config) {
        serviceConfigs.put(config.getId(), config);
        log.info("Registered CDS service: {}", config.getId());
    }

    public void unregisterService(String serviceId) {
        serviceConfigs.remove(serviceId);
        log.info("Unregistered CDS service: {}", serviceId);
    }

    public List<CdsServiceDefinition> getServiceDefinitions() {
        List<CdsServiceDefinition> definitions = new ArrayList<>();

        for (CdsServiceConfig config : serviceConfigs.values()) {
            definitions.add(toDefinition(config));
        }

        return definitions;
    }

    // --- Invocation (delegated) ---

    public CdsResponse invokeService(String serviceId, CdsRequest request) {
        CdsServiceConfig config = serviceConfigs.get(serviceId);

        if (config == null) {
            return CdsResponse.builder()
                    .cards(List.of(tupleStrategy.createInfoCard("Service not found",
                            "The requested CDS service '" + serviceId + "' is not available.")))
                    .build();
        }

        // Option A (#698 PR-C2): only shared services are invocable anonymously. A
        // private service may be invoked by its owner (per-user API-key path or the
        // authenticated builder UI). Legacy un-owned rows require any authenticated
        // caller — they were platform-global before ownership existed. Unauthorized
        // callers get the same not-found card as a missing service, so private
        // service ids are not confirmable by probing.
        if (!Boolean.TRUE.equals(config.getShared())) {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            String caller = (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;
            String owner = config.getOwnerUsername();
            boolean allowed = caller != null && (owner == null || owner.equals(caller));
            if (!allowed) {
                log.info("CDS invoke denied for non-shared service {} (caller={})", serviceId, caller);
                return CdsResponse.builder()
                        .cards(List.of(tupleStrategy.createInfoCard("Service not found",
                                "The requested CDS service '" + serviceId + "' is not available.")))
                        .build();
            }
        }

        // Validate hook type matches config
        if (request.getHook() != null && !request.getHook().equals(config.getHook())) {
            throw new IllegalArgumentException(
                    "Hook type mismatch: request hook '" + request.getHook() +
                            "' does not match service hook '" + config.getHook() + "'");
        }

        // Validate required context fields for the hook type
        HookContextRequirements.validateContext(config.getHook(), request.getContext());

        return invocationService.invoke(config, request);
    }

    // --- Feedback ---

    @Transactional
    public void processFeedback(String serviceId, CdsFeedbackRequest request) {
        if (!repository.existsById(serviceId) && !serviceConfigs.containsKey(serviceId)) {
            throw new IllegalArgumentException("Service not found: " + serviceId);
        }

        if (request.getFeedback() == null || request.getFeedback().isEmpty()) {
            return;
        }

        if (feedbackRepository.isEmpty()) {
            log.warn("Feedback repository not available, skipping feedback persistence");
            return;
        }
        CdsFeedbackRepository repo = feedbackRepository.get();

        for (CdsFeedbackRequest.FeedbackItem item : request.getFeedback()) {
            // Defense-in-depth: HTML-escape all free-text fields before persistence
            // to prevent stored XSS even if @NoXss validation is bypassed
            CdsFeedbackEntity entity = CdsFeedbackEntity.builder()
                    .serviceId(serviceId)
                    .cardUuid(escapeHtml(item.getCard()))
                    .outcome(item.getOutcome())
                    .outcomeTimestamp(item.getOutcomeTimestamp() != null
                            ? LocalDateTime.parse(item.getOutcomeTimestamp())
                            : null)
                    .acceptedSuggestions(item.getAcceptedSuggestions() != null
                            ? serializeToJson(item.getAcceptedSuggestions())
                            : null)
                    .build();

            if (item.getOverrideReason() != null) {
                entity.setOverrideReasonCode(escapeHtml(item.getOverrideReason().getCode()));
                entity.setOverrideReasonDisplay(escapeHtml(item.getOverrideReason().getDisplay()));
            }

            repo.save(entity);
        }

        log.info("Processed {} feedback items for service {}", request.getFeedback().size(), serviceId);
    }

    @Transactional(readOnly = true)
    public List<CdsFeedbackEntity> getFeedback(String serviceId) {
        // BUG-137: cds_feedback has no tenant_id — its tenant is its parent service's
        // (service_id is NOT NULL REFERENCES cds_service_config ON DELETE CASCADE), so the
        // parent gate IS the boundary. Strictly own-tenant, not findReadableById: publishing
        // a service to the shared surface does not publish who overrode its cards and why.
        repository.findByIdAndTenantIdWithPrefetch(serviceId, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));
        return feedbackRepository
                .map(repo -> repo.findByServiceIdOrderByCreatedAtDesc(serviceId))
                .orElseGet(List::of);
    }

    private static String escapeHtml(String value) {
        return value == null ? null : HtmlUtils.htmlEscape(value);
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }

    // --- Versioning ---

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getServiceVersions(String serviceName) {
        return repository.findByTenantIdAndServiceNameOrderByVersionDesc(effectiveTenantId(), serviceName).stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CdsServiceConfigResponse rollbackService(String serviceName, int targetVersion) {
        // BUG-137: strictly own-tenant — a rollback rewrites the live service, so seeing a
        // shared service must not imply being able to roll it back.
        List<CdsServiceConfigEntity> versions =
                repository.findByTenantIdAndServiceNameOrderByVersionDesc(effectiveTenantId(), serviceName);

        if (versions.isEmpty()) {
            throw new IllegalArgumentException("No service found with name: " + serviceName);
        }

        CdsServiceConfigEntity targetEntity = versions.stream()
                .filter(v -> v.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + targetVersion + " not found for service: " + serviceName));

        for (CdsServiceConfigEntity v : versions) {
            v.setEnabled(false);
            repository.save(v);
            serviceConfigs.remove(v.getId());
        }

        targetEntity.setEnabled(true);
        targetEntity = repository.save(targetEntity);

        CdsServiceConfig config = entityToConfig(targetEntity);
        serviceConfigs.put(config.getId(), config);

        log.info("Rolled back service {} to version {}", serviceName, targetVersion);
        return entityToResponse(targetEntity);
    }

    // --- Entity mapping ---

    private CdsServiceDefinition toDefinition(CdsServiceConfig config) {
        return CdsServiceDefinition.builder()
                .id(config.getId())
                .hook(config.getHook())
                .title(config.getTitle())
                .description(config.getDescription())
                .version(config.getVersion())
                .prefetch(config.getPrefetch())
                .contextFields(HookContextRequirements.getContextFieldDefinitions(config.getHook()))
                .build();
    }

    private CdsServiceConfigEntity requestToEntity(CdsServiceConfigRequest request) {
        CdsServiceConfigEntity entity = CdsServiceConfigEntity.builder()
                .id(request.getId())
                .hook(request.getHook())
                .title(request.getTitle())
                .description(request.getDescription())
                .cqlContent(request.getCqlContent())
                .cqlLibraryId(request.getCqlLibraryId())
                .defaultIndicator(request.getDefaultIndicator())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .planDefinitionJson(request.getPlanDefinitionJson())
                .cardGenerationMode(request.getCardGenerationMode() != null
                        ? request.getCardGenerationMode() : "cql_tuple")
                .serviceName(request.getId())
                .version(1)
                .prefetchItems(new ArrayList<>())
                .build();

        if (request.getPrefetch() != null) {
            for (Map.Entry<String, String> entry : request.getPrefetch().entrySet()) {
                CdsServicePrefetchEntity prefetch = CdsServicePrefetchEntity.builder()
                        .prefetchKey(entry.getKey())
                        .query(entry.getValue())
                        .build();
                entity.addPrefetchItem(prefetch);
            }
        }

        return entity;
    }

    private CdsServiceConfig entityToConfig(CdsServiceConfigEntity entity) {
        Map<String, CdsServiceDefinition.PrefetchTemplate> prefetch = new HashMap<>();
        if (entity.getPrefetchItems() != null) {
            for (CdsServicePrefetchEntity p : entity.getPrefetchItems()) {
                prefetch.put(p.getPrefetchKey(), CdsServiceDefinition.PrefetchTemplate.builder()
                        .query(p.getQuery())
                        .build());
            }
        }

        return CdsServiceConfig.builder()
                .id(entity.getId())
                .hook(entity.getHook())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .cqlContent(entity.getCqlContent())
                .cqlLibraryId(entity.getCqlLibraryId())
                .defaultIndicator(entity.getDefaultIndicator())
                .version(entity.getVersion())
                .serviceName(entity.getServiceName())
                .planDefinitionJson(entity.getPlanDefinitionJson())
                .cardGenerationMode(entity.getCardGenerationMode())
                .prefetch(prefetch.isEmpty() ? null : prefetch)
                .shared(entity.getShared())
                .ownerUsername(entity.getOwnerUsername())
                .tenantId(entity.getTenantId())
                .build();
    }

    private CdsServiceConfigResponse entityToResponse(CdsServiceConfigEntity entity) {
        Map<String, String> prefetch = new HashMap<>();
        if (entity.getPrefetchItems() != null) {
            for (CdsServicePrefetchEntity p : entity.getPrefetchItems()) {
                prefetch.put(p.getPrefetchKey(), p.getQuery());
            }
        }

        return CdsServiceConfigResponse.builder()
                .id(entity.getId())
                .hook(entity.getHook())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .cqlContent(entity.getCqlContent())
                .cqlLibraryId(entity.getCqlLibraryId())
                .defaultIndicator(entity.getDefaultIndicator())
                .enabled(entity.getEnabled())
                .version(entity.getVersion())
                .serviceName(entity.getServiceName())
                .ownerUsername(entity.getOwnerUsername())
                .shared(entity.getShared())
                .planDefinitionJson(entity.getPlanDefinitionJson())
                .cardGenerationMode(entity.getCardGenerationMode())
                .prefetch(prefetch.isEmpty() ? null : prefetch)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Sync CQL content from CDS service config to the {@code cql_library} table.
     *
     * <p>Two failure modes are deliberately treated differently:
     * <ul>
     * <li><b>Parse failure</b> (no {@code library X version 'Y'} declaration) —
     *   logged at WARN and silently returned. Lots of test/sandbox CQL has no
     *   library declaration and shouldn't break service create/update.</li>
     * <li><b>DB failure</b> — propagated so the surrounding {@code @Transactional}
     *   rolls back the service create/update. Previously this was silently
     *   caught (loss of CDS service ↔ cql_library consistency); the user saw
     *   "service created" but later "library missing" when the service ran.</li>
     * </ul>
     *
     * <p>If you call this from a non-transactional context, wrap in a try/catch
     * and decide explicitly whether the caller should see DB failures.
     */
    private void syncCqlLibrary(String cqlContent) {
        if (cqlContent == null || cqlContent.isBlank())
            return;
        if (cqlLibraryRepository.isEmpty())
            return;
        CqlLibraryRepository repo = cqlLibraryRepository.get();

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("library\\s+\"?([^\"\\s]+)\"?\\s+version\\s+'([^']+)'")
                .matcher(cqlContent);

        if (!matcher.find()) {
            log.warn("Could not parse library name/version from CQL content, skipping sync");
            return;
        }

        String libName = matcher.group(1);
        String libVersion = matcher.group(2);

        // Phase 2 (V62): cql_library is tenant-scoped. This runs on the request thread
        // (CDS service create/update), so the tenant comes straight off TenantContext,
        // defaulting to the default tenant for legacy callers with none. Both the lookup
        // and the insert are scoped so a CDS sync never overwrites or collides with another
        // clinic's same-named library.
        Long tenantId = effectiveTenantId();
        Optional<CqlLibraryEntity> existing =
                repo.findByTenantIdAndNameAndVersion(tenantId, libName, libVersion);
        if (existing.isPresent()) {
            CqlLibraryEntity entity = existing.get();
            entity.setCqlContent(cqlContent);
            entity.setElmJson(null);
            repo.save(entity);
            log.info("Synced cql_library '{}' version '{}' with updated CQL content", libName, libVersion);
        } else {
            CqlLibraryEntity newLib = CqlLibraryEntity.builder()
                    .name(libName)
                    .version(libVersion)
                    .cqlContent(cqlContent)
                    .status("active")
                    .tenantId(tenantId)
                    .build();
            repo.save(newLib);
            log.info("Created cql_library '{}' version '{}' from CDS service CQL", libName, libVersion);
        }
    }

    /**
     * Effective tenant for CDS→cql_library sync: the caller's, or the default tenant for
     * legacy callers with none. {@code tenantRepository} is Optional (absent in trimmed test
     * slices); when it and the tenant context are both absent this returns null, which only
     * happens in tests that also lack a cqlLibraryRepository — so no tenant-less row is ever
     * persisted (see {@link #syncCqlLibrary} early-return guard).
     */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository
                .flatMap(r -> r.findByCode("default"))
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElse(null);
    }

    @lombok.Data
    @lombok.Builder
    public static class CdsServiceConfig {
        private String id;
        private String hook;
        private String title;
        private String description;
        private String cqlLibraryId;
        private String cqlContent;
        private String defaultIndicator;
        private Integer version;
        private String serviceName;
        private String planDefinitionJson;
        private String cardGenerationMode;
        private Map<String, CdsServiceDefinition.PrefetchTemplate> prefetch;
        // Phase 2 (#698 PR-C2): carried so invokeService can authorize from the cache
        // without a DB round-trip — shared services are the anonymous surface.
        private Boolean shared;
        private String ownerUsername;
        private Long tenantId;
    }
}
