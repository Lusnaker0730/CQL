package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FhirValidationService {

    private final FhirContext fhirContext;
    private final FhirValidator validator;
    private final FhirImplementationGuideService igService;

    public FhirValidationService(FhirContext fhirContext,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  FhirImplementationGuideService igService) {
        this.fhirContext = fhirContext;
        this.igService = igService;
        ValidationSupportChain validationSupportChain;

        if (igService != null && igService.isLoaded()) {
            log.info("Adding IG validation support to validation chain");
            validationSupportChain = new ValidationSupportChain(
                    igService.getValidationSupport(),
                    new DefaultProfileValidationSupport(fhirContext),
                    new InMemoryTerminologyServerValidationSupport(fhirContext),
                    new CommonCodeSystemsTerminologyService(fhirContext)
            );
        } else {
            validationSupportChain = new ValidationSupportChain(
                    new DefaultProfileValidationSupport(fhirContext),
                    new InMemoryTerminologyServerValidationSupport(fhirContext),
                    new CommonCodeSystemsTerminologyService(fhirContext)
            );
        }

        CachingValidationSupport cachingSupport = new CachingValidationSupport(validationSupportChain);

        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(cachingSupport);
        this.validator = fhirContext.newValidator();
        this.validator.registerValidatorModule(instanceValidator);
    }

    public ValidationResult validateResource(String resourceJson) {
        return validateResource(resourceJson, null);
    }

    public ValidationResult validateResource(String resourceJson, String profileUrl) {
        log.debug("Validating FHIR resource{}", profileUrl != null ? " against profile: " + profileUrl : "");
        try {
            String jsonToValidate = resourceJson;
            if (profileUrl != null && !profileUrl.isBlank()) {
                org.hl7.fhir.r4.model.Resource resource =
                        (org.hl7.fhir.r4.model.Resource) fhirContext.newJsonParser().parseResource(resourceJson);
                if (!resource.getMeta().hasProfile(profileUrl)) {
                    resource.getMeta().addProfile(profileUrl);
                }
                jsonToValidate = fhirContext.newJsonParser().encodeResourceToString(resource);
            }

            ca.uhn.fhir.validation.ValidationResult result = validator.validateWithResult(jsonToValidate);

            List<ValidationIssue> issues = result.getMessages().stream()
                    .map(msg -> new ValidationIssue(
                            mapSeverity(msg.getSeverity()),
                            msg.getLocationString(),
                            msg.getMessage()
                    ))
                    .toList();

            return new ValidationResult(result.isSuccessful(), issues);
        } catch (Exception e) {
            log.error("Resource validation failed", e);
            return new ValidationResult(false, List.of(
                    new ValidationIssue("error", "N/A", "Validation failed: " + e.getMessage())
            ));
        }
    }

    /**
     * Validate all resources in a FHIR Bundle against applicable TW Core profiles.
     * For each entry, the service attempts to auto-detect the best TW Core profile
     * based on resource type.
     */
    public BundleValidationResult validateBundle(String bundleJson) {
        log.debug("Validating FHIR Bundle");
        try {
            org.hl7.fhir.r4.model.Bundle bundle =
                    (org.hl7.fhir.r4.model.Bundle) fhirContext.newJsonParser().parseResource(bundleJson);

            List<ResourceValidationEntry> entries = new ArrayList<>();
            int validCount = 0;
            int invalidCount = 0;

            for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                org.hl7.fhir.r4.model.Resource resource = entry.getResource();
                if (resource == null) {
                    continue;
                }

                String resourceType = resource.fhirType();
                String resourceId = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;

                // Auto-detect profile from IG service
                String profileUrl = detectProfile(resource, resourceType);

                String resourceJson = fhirContext.newJsonParser().encodeResourceToString(resource);
                ValidationResult result = validateResource(resourceJson, profileUrl);

                if (result.valid()) {
                    validCount++;
                } else {
                    invalidCount++;
                }

                entries.add(new ResourceValidationEntry(
                        resourceType,
                        resourceId,
                        profileUrl,
                        result.valid(),
                        result.issues()
                ));
            }

            return new BundleValidationResult(entries.size(), validCount, invalidCount, entries);
        } catch (Exception e) {
            log.error("Bundle validation failed", e);
            return new BundleValidationResult(0, 0, 0, List.of());
        }
    }

    /**
     * Detect the appropriate profile URL for a resource. First checks if the resource
     * already declares a profile in meta, otherwise attempts to find a recommended
     * TW Core profile from the IG service.
     */
    private String detectProfile(org.hl7.fhir.r4.model.Resource resource, String resourceType) {
        // Check if resource already has a profile declared
        if (resource.getMeta() != null && resource.getMeta().hasProfile()) {
            return resource.getMeta().getProfile().get(0).getValue();
        }

        // Auto-detect from IG service
        if (igService != null && igService.isLoaded()) {
            return igService.findProfileUrlForResourceType(resourceType);
        }

        return null;
    }

    private String mapSeverity(ResultSeverityEnum severity) {
        return switch (severity) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFORMATION -> "information";
            case FATAL -> "fatal";
        };
    }

    public record ValidationResult(boolean valid, List<ValidationIssue> issues) {}
    public record ValidationIssue(String severity, String location, String message) {}

    public record BundleValidationResult(
            int totalResources,
            int validResources,
            int invalidResources,
            List<ResourceValidationEntry> entries
    ) {}

    public record ResourceValidationEntry(
            String resourceType,
            String resourceId,
            String profileUrl,
            boolean valid,
            List<ValidationIssue> issues
    ) {}
}
