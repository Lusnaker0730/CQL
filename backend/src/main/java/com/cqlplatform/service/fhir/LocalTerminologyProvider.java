package com.cqlplatform.service.fhir;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.terminology.CodeSystemInfo;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.ValueSetInfo;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LocalTerminologyProvider implements TerminologyProvider {

    private final FhirImplementationGuideService igService;
    private final TerminologyProvider remoteProvider;
    private final VsacService vsacService;

    public LocalTerminologyProvider(FhirImplementationGuideService igService, TerminologyProvider remoteProvider) {
        this(igService, remoteProvider, null);
    }

    public LocalTerminologyProvider(FhirImplementationGuideService igService, TerminologyProvider remoteProvider, VsacService vsacService) {
        this.igService = igService;
        this.remoteProvider = remoteProvider;
        this.vsacService = vsacService;
    }

    private static boolean isVsacUrl(String url) {
        return url != null && url.contains("cts.nlm.nih.gov");
    }

    private static String extractVsacOid(String url) {
        // e.g. http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }

    @Override
    public boolean in(Code code, ValueSetInfo valueSet) {
        if (igService != null && igService.isLoaded() && valueSet.getId() != null) {
            ValueSet vs = igService.getValueSetByUrl(valueSet.getId());
            if (vs != null) {
                log.debug("Checking code {} membership in local ValueSet {}", code.getCode(), valueSet.getId());
                return isCodeInValueSet(code, vs);
            }
        }
        // Try VSAC for cts.nlm.nih.gov ValueSet URLs
        if (vsacService != null && isVsacUrl(valueSet.getId())) {
            try {
                String oid = extractVsacOid(valueSet.getId());
                log.info("Resolving VSAC ValueSet for membership check: {} (OID: {})", valueSet.getId(), oid);
                ValueSet vs = vsacService.expandValueSetByOid(oid);
                if (vs != null) {
                    return isCodeInValueSet(code, vs);
                }
            } catch (Exception e) {
                log.warn("VSAC ValueSet resolution failed for {}: {}", valueSet.getId(), e.getMessage());
            }
        }
        // Fallback to remote
        if (remoteProvider != null) {
            return remoteProvider.in(code, valueSet);
        }
        return false;
    }

    @Override
    public Iterable<Code> expand(ValueSetInfo valueSet) {
        if (igService != null && igService.isLoaded() && valueSet.getId() != null) {
            ValueSet vs = igService.getValueSetByUrl(valueSet.getId());
            if (vs != null) {
                log.debug("Expanding local ValueSet: {}", valueSet.getId());
                return expandValueSet(vs);
            }
        }
        // Try VSAC for cts.nlm.nih.gov ValueSet URLs
        if (vsacService != null && isVsacUrl(valueSet.getId())) {
            try {
                String oid = extractVsacOid(valueSet.getId());
                log.info("Expanding VSAC ValueSet: {} (OID: {})", valueSet.getId(), oid);
                ValueSet vs = vsacService.expandValueSetByOid(oid);
                if (vs != null) {
                    return expandValueSet(vs);
                }
            } catch (Exception e) {
                log.warn("VSAC ValueSet expansion failed for {}: {}", valueSet.getId(), e.getMessage());
            }
        }
        // Fallback to remote
        if (remoteProvider != null) {
            return remoteProvider.expand(valueSet);
        }
        return new ArrayList<>();
    }

    @Override
    public Code lookup(Code code, CodeSystemInfo codeSystem) {
        if (igService != null && igService.isLoaded() && codeSystem.getId() != null) {
            CodeSystem cs = igService.getCodeSystemByUrl(codeSystem.getId());
            if (cs != null) {
                log.debug("Looking up code {} in local CodeSystem {}", code.getCode(), codeSystem.getId());
                Code result = lookupInCodeSystem(code, cs);
                if (result != null) {
                    return result;
                }
            }
        }
        // Fallback to remote
        if (remoteProvider != null) {
            return remoteProvider.lookup(code, codeSystem);
        }
        return code;
    }

    private boolean isCodeInValueSet(Code code, ValueSet vs) {
        // Check expansion first
        if (vs.hasExpansion() && vs.getExpansion().hasContains()) {
            for (ValueSet.ValueSetExpansionContainsComponent contains : vs.getExpansion().getContains()) {
                if (matchesCode(code, contains.getSystem(), contains.getCode())) {
                    return true;
                }
            }
        }

        // Check compose includes
        if (vs.hasCompose()) {
            for (ValueSet.ConceptSetComponent include : vs.getCompose().getInclude()) {
                if (include.hasConcept()) {
                    for (ValueSet.ConceptReferenceComponent concept : include.getConcept()) {
                        if (matchesCode(code, include.getSystem(), concept.getCode())) {
                            return true;
                        }
                    }
                } else if (include.hasSystem()) {
                    // Include all codes from system — check if code's system matches
                    if (code.getSystem() != null && code.getSystem().equals(include.getSystem())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesCode(Code code, String system, String codeValue) {
        boolean codeMatches = code.getCode() != null && code.getCode().equals(codeValue);
        boolean systemMatches = code.getSystem() == null || system == null || code.getSystem().equals(system);
        return codeMatches && systemMatches;
    }

    private List<Code> expandValueSet(ValueSet vs) {
        List<Code> codes = new ArrayList<>();

        // From expansion
        if (vs.hasExpansion() && vs.getExpansion().hasContains()) {
            for (ValueSet.ValueSetExpansionContainsComponent contains : vs.getExpansion().getContains()) {
                Code code = new Code()
                        .withCode(contains.getCode())
                        .withSystem(contains.getSystem())
                        .withDisplay(contains.getDisplay());
                codes.add(code);
            }
            return codes;
        }

        // From compose
        if (vs.hasCompose()) {
            for (ValueSet.ConceptSetComponent include : vs.getCompose().getInclude()) {
                if (include.hasConcept()) {
                    for (ValueSet.ConceptReferenceComponent concept : include.getConcept()) {
                        Code code = new Code()
                                .withCode(concept.getCode())
                                .withSystem(include.getSystem())
                                .withDisplay(concept.getDisplay());
                        codes.add(code);
                    }
                }
            }
        }

        return codes;
    }

    private Code lookupInCodeSystem(Code code, CodeSystem cs) {
        if (cs.hasConcept()) {
            for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
                Code found = findInConcept(code, concept, cs.getUrl());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Code findInConcept(Code code, CodeSystem.ConceptDefinitionComponent concept, String system) {
        if (code.getCode() != null && code.getCode().equals(concept.getCode())) {
            return new Code()
                    .withCode(concept.getCode())
                    .withSystem(system)
                    .withDisplay(concept.getDisplay());
        }
        // Recurse into child concepts
        if (concept.hasConcept()) {
            for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
                Code found = findInConcept(code, child, system);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
