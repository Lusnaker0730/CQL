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

import java.util.List;

@Service
@Slf4j
public class FhirValidationService {

    private final FhirValidator validator;

    public FhirValidationService(FhirContext fhirContext,
                                  @org.springframework.beans.factory.annotation.Autowired(required = false)
                                  FhirImplementationGuideService igService) {
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
        log.debug("Validating FHIR resource");
        try {
            ca.uhn.fhir.validation.ValidationResult result = validator.validateWithResult(resourceJson);

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
}
