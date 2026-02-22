package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsFeedbackEntity;
import com.cqlplatform.entity.CdsServiceConfigEntity;
import com.cqlplatform.entity.CdsServicePrefetchEntity;
import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.repository.CdsFeedbackRepository;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.validation.HookTypeValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Map<String, CdsServiceConfig> serviceConfigs = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CdsFeedbackRepository feedbackRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CqlLibraryRepository cqlLibraryRepository;

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
        entity = repository.save(entity);

        syncCqlLibrary(entity.getCqlContent());

        CdsServiceConfig config = entityToConfig(entity);
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            serviceConfigs.entrySet().removeIf(e -> {
                CdsServiceConfig c = e.getValue();
                return serviceName.equals(c.getServiceName());
            });
            serviceConfigs.put(config.getId(), config);
        }

        log.info("Created CDS service: {} (v{})", entity.getId(), newVersion);
        return entityToResponse(entity);
    }

    @Transactional
    public CdsServiceConfigResponse toggleShared(String id, boolean shared) {
        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        entity.setShared(shared);
        entity = repository.save(entity);
        log.info("Set service {} shared={}", id, shared);
        return entityToResponse(entity);
    }

    @Transactional
    public CdsServiceConfigResponse updateService(String id, CdsServiceConfigRequest request) {
        HookTypeValidator.validate(request.getHook());

        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
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

        if (Boolean.TRUE.equals(entity.getEnabled())) {
            serviceConfigs.put(id, entityToConfig(entity));
        } else {
            serviceConfigs.remove(id);
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
        serviceConfigs.remove(id);
        log.info("Deleted CDS service: {}", id);
    }

    @Transactional(readOnly = true)
    public CdsServiceConfigResponse getService(String id) {
        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        return entityToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getAllServices() {
        return repository.findAllWithPrefetch().stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CdsServiceConfigResponse> getServicesForUser(String username) {
        return repository.findByOwnerUsernameOrSharedTrue(username).stream()
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

    public List<CdsServiceDefinition> getServiceDefinitionsForUser(String username) {
        List<CdsServiceDefinition> definitions = new ArrayList<>();
        List<CdsServiceConfigEntity> entities = repository.findByOwnerUsernameAndEnabledTrue(username);

        for (CdsServiceConfigEntity entity : entities) {
            CdsServiceConfig config = entityToConfig(entity);
            definitions.add(CdsServiceDefinition.builder()
                    .id(config.getId())
                    .hook(config.getHook())
                    .title(config.getTitle())
                    .description(config.getDescription())
                    .version(config.getVersion())
                    .prefetch(config.getPrefetch())
                    .build());
        }

        return definitions;
    }

    public List<CdsServiceDefinition> getSharedServiceDefinitions() {
        List<CdsServiceDefinition> definitions = new ArrayList<>();

        List<CdsServiceConfigEntity> entities = repository.findAllEnabledWithPrefetch();

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
            definitions.add(CdsServiceDefinition.builder()
                    .id(config.getId())
                    .hook(config.getHook())
                    .title(config.getTitle())
                    .description(config.getDescription())
                    .version(config.getVersion())
                    .prefetch(config.getPrefetch())
                    .build());
        }

        return definitions;
    }

    @Transactional
    public CdsServiceConfigResponse toggleServiceEnabled(String id, boolean enabled) {
        CdsServiceConfigEntity entity = repository.findByIdWithPrefetch(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));

        entity.setEnabled(enabled);
        entity = repository.save(entity);

        if (enabled) {
            serviceConfigs.put(id, entityToConfig(entity));
        } else {
            serviceConfigs.remove(id);
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
            definitions.add(CdsServiceDefinition.builder()
                    .id(config.getId())
                    .hook(config.getHook())
                    .title(config.getTitle())
                    .description(config.getDescription())
                    .version(config.getVersion())
                    .prefetch(config.getPrefetch())
                    .build());
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

        // Validate hook type matches config
        if (request.getHook() != null && !request.getHook().equals(config.getHook())) {
            throw new IllegalArgumentException(
                    "Hook type mismatch: request hook '" + request.getHook() +
                            "' does not match service hook '" + config.getHook() + "'");
        }

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

        if (feedbackRepository == null) {
            log.warn("Feedback repository not available, skipping feedback persistence");
            return;
        }

        for (CdsFeedbackRequest.FeedbackItem item : request.getFeedback()) {
            CdsFeedbackEntity entity = CdsFeedbackEntity.builder()
                    .serviceId(serviceId)
                    .cardUuid(item.getCard())
                    .outcome(item.getOutcome())
                    .outcomeTimestamp(item.getOutcomeTimestamp() != null
                            ? LocalDateTime.parse(item.getOutcomeTimestamp())
                            : null)
                    .acceptedSuggestions(item.getAcceptedSuggestions() != null
                            ? serializeToJson(item.getAcceptedSuggestions())
                            : null)
                    .build();

            if (item.getOverrideReason() != null) {
                entity.setOverrideReasonCode(item.getOverrideReason().getCode());
                entity.setOverrideReasonDisplay(item.getOverrideReason().getDisplay());
            }

            feedbackRepository.save(entity);
        }

        log.info("Processed {} feedback items for service {}", request.getFeedback().size(), serviceId);
    }

    @Transactional(readOnly = true)
    public List<CdsFeedbackEntity> getFeedback(String serviceId) {
        if (feedbackRepository == null) {
            return List.of();
        }
        return feedbackRepository.findByServiceIdOrderByCreatedAtDesc(serviceId);
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
        return repository.findByServiceNameOrderByVersionDesc(serviceName).stream()
                .map(this::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CdsServiceConfigResponse rollbackService(String serviceName, int targetVersion) {
        List<CdsServiceConfigEntity> versions = repository.findByServiceNameOrderByVersionDesc(serviceName);

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
     * Sync CQL content from CDS service config to the cql_library table.
     */
    private void syncCqlLibrary(String cqlContent) {
        if (cqlContent == null || cqlContent.isBlank())
            return;
        if (cqlLibraryRepository == null)
            return;

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("library\\s+\"?([^\"\\s]+)\"?\\s+version\\s+'([^']+)'")
                .matcher(cqlContent);

        if (!matcher.find()) {
            log.warn("Could not parse library name/version from CQL content, skipping sync");
            return;
        }

        String libName = matcher.group(1);
        String libVersion = matcher.group(2);

        try {
            Optional<CqlLibraryEntity> existing = cqlLibraryRepository.findByNameAndVersion(libName, libVersion);
            if (existing.isPresent()) {
                CqlLibraryEntity entity = existing.get();
                entity.setCqlContent(cqlContent);
                entity.setElmJson(null);
                cqlLibraryRepository.save(entity);
                log.info("Synced cql_library '{}' version '{}' with updated CQL content", libName, libVersion);
            } else {
                CqlLibraryEntity newLib = CqlLibraryEntity.builder()
                        .name(libName)
                        .version(libVersion)
                        .cqlContent(cqlContent)
                        .status("active")
                        .build();
                cqlLibraryRepository.save(newLib);
                log.info("Created cql_library '{}' version '{}' from CDS service CQL", libName, libVersion);
            }
        } catch (Exception e) {
            log.warn("Failed to sync CQL library: {}", e.getMessage());
        }
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
    }
}
