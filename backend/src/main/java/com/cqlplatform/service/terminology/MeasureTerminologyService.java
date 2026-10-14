package com.cqlplatform.service.terminology;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureStatusConstants;
import com.cqlplatform.model.measure.MeasureTerminology;
import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.measure.CqfmLibraryBuilder;
import com.cqlplatform.service.measure.CqfmLibraryBuilder.ElmDependencies;
import com.cqlplatform.service.measure.MeasureDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PAT-260 — what a measure's CQL references and how it resolves, plus the pin / unpin operations.
 *
 * <p>The list comes from the measure's stored ELM (translated on demand when there is none). For every value set
 * reference it says whether the platform owns the value set, which version evaluation resolves today (the CQL
 * version, else the measure's pin, else the newest active) and which versions exist. Pins are written through
 * {@link MeasureDefinitionService#updateValueSetVersions}, which applies the edit lock and the draft-only rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureTerminologyService {

    private final MeasureDefinitionRepository repository;
    private final MeasureDefinitionService definitionService;
    private final CqfmLibraryBuilder libraryBuilder;
    private final CqlTranslationService translationService;
    private final PlatformValueSetService platformValueSets;

    @Transactional(readOnly = true)
    public MeasureTerminology describe(Long measureId) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(measureId, definitionService.effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        return describe(entity);
    }

    private MeasureTerminology describe(MeasureDefinitionEntity entity) {
        Map<String, String> pins = entity.getValueSetVersionMap() == null ? Map.of() : entity.getValueSetVersionMap();
        List<MeasureTerminology.Item> items = new ArrayList<>();
        for (TerminologyPins.ValueSetUse use : TerminologyPins.valueSetsOf(dependenciesOf(entity))) {
            String pinned = pins.get(use.url());
            String effective = TerminologyPins.effectiveVersion(use, pins);
            Optional<PlatformValueSetService.Resolved> resolved = platformValueSets.resolve(entity.getTenantId(), use.url(), effective);
            List<MeasureTerminology.Version> versions = resolved
                    .map(r -> platformValueSets.versions(r.valueSet().getId()).stream()
                            .map(v -> MeasureTerminology.Version.builder().id(v.getId()).version(v.getVersion())
                                    .status(v.getStatus()).conceptCount(v.getConceptCount()).build())
                            .toList())
                    .orElse(List.of());
            items.add(MeasureTerminology.Item.builder()
                    .name(use.name()).url(use.url())
                    .cqlVersion(use.cqlVersion()).pinnedVersion(pinned)
                    .source(resolved.isPresent() ? "platform" : TerminologyPins.EXTERNAL)
                    .resolvedVersion(resolved.map(r -> r.valueSet().getVersion()).orElse(null))
                    .resolvedStatus(resolved.map(r -> r.valueSet().getStatus()).orElse(null))
                    .pinned(use.cqlVersion() != null || pinned != null)
                    .versions(versions)
                    .build());
        }
        return MeasureTerminology.builder()
                .measureId(entity.getId())
                .status(entity.getStatus())
                .pinnable(MeasureStatusConstants.DRAFT.equals(entity.getStatus()))
                .items(items)
                .build();
    }

    /**
     * Pins. {@code url == null}: every platform value set the CQL references without a version is pinned to the
     * version it resolves right now. Otherwise the one URL is pinned to {@code version} (or, when that is blank,
     * to what it resolves now); the version must exist on the platform.
     */
    @Transactional
    public MeasureTerminology pin(Long measureId, String url, String version, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(measureId, definitionService.effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        Map<String, String> next = new LinkedHashMap<>(entity.getValueSetVersionMap() == null ? Map.of() : entity.getValueSetVersionMap());
        List<TerminologyPins.ValueSetUse> uses = TerminologyPins.valueSetsOf(dependenciesOf(entity));
        if (url == null || url.isBlank()) {
            for (TerminologyPins.ValueSetUse use : uses) {
                if (use.cqlVersion() != null || next.containsKey(use.url())) continue;
                platformValueSets.resolve(entity.getTenantId(), use.url(), null)
                        .ifPresent(r -> next.put(use.url(), r.valueSet().getVersion()));
            }
        } else {
            if (uses.stream().noneMatch(u -> url.equals(u.url()))) {
                throw new ValidationException("The measure's CQL does not reference value set " + url);
            }
            String target = version == null || version.isBlank() ? null : version.trim();
            Optional<PlatformValueSetService.Resolved> resolved = platformValueSets.resolve(entity.getTenantId(), url, target);
            if (resolved.isEmpty()) {
                throw new ValidationException(target == null
                        ? "Value set " + url + " is not stored on this platform; only platform value sets can be pinned"
                        : "Value set " + url + " has no version " + target + " on this platform");
            }
            next.put(url, resolved.get().valueSet().getVersion());
        }
        MeasureDefinition updated = definitionService.updateValueSetVersions(measureId, next, currentUser);
        log.info("Measure {} terminology pins now {}", measureId, updated.getValueSetVersions());
        return describe(repository.findByIdAndTenantId(measureId, entity.getTenantId()).orElse(entity));
    }

    /** Removes one pin ({@code url}) or all of them. */
    @Transactional
    public MeasureTerminology unpin(Long measureId, String url, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(measureId, definitionService.effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));
        Map<String, String> next = new LinkedHashMap<>();
        if (url != null && !url.isBlank() && entity.getValueSetVersionMap() != null) {
            next.putAll(entity.getValueSetVersionMap());
            next.remove(url);
        }
        definitionService.updateValueSetVersions(measureId, next, currentUser);
        return describe(repository.findByIdAndTenantId(measureId, entity.getTenantId()).orElse(entity));
    }

    /** The measure's dependencies from its stored ELM, translating the CQL when no ELM is stored yet. */
    private ElmDependencies dependenciesOf(MeasureDefinitionEntity entity) {
        String elm = entity.getElmJson();
        if ((elm == null || elm.isBlank()) && entity.getCqlContent() != null && !entity.getCqlContent().isBlank()) {
            try {
                CqlTranslationRequest request = new CqlTranslationRequest();
                request.setCql(entity.getCqlContent());
                CqlTranslationResponse response = translationService.translate(request);
                elm = response != null ? response.getElmJson() : null;
            } catch (Exception e) {
                log.warn("Could not translate measure {} to list its value sets: {}", entity.getId(), e.getMessage());
            }
        }
        return libraryBuilder.readDependencies(elm);
    }
}
