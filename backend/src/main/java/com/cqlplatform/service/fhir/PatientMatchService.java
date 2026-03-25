package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.model.ehr.PatientMatchRequest;
import com.cqlplatform.model.fhir.PatientMatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientMatchService {

    private final EhrConnectionService connectionService;
    private final FhirClientFactory fhirClientFactory;

    // TW National ID pattern: 1 letter + 9 digits
    private static final Pattern TW_NATIONAL_ID_PATTERN = Pattern.compile("^[A-Z][12]\\d{8}$");

    /**
     * Match patients across one or more EHR connections.
     */
    public List<PatientMatchResult> matchPatients(PatientMatchRequest request) {
        List<EhrConnectionEntity> connections;
        if (request.getConnectionIds() != null && !request.getConnectionIds().isEmpty()) {
            connections = request.getConnectionIds().stream()
                    .map(connectionService::getById)
                    .toList();
        } else {
            connections = connectionService.list(null);
        }

        List<PatientMatchResult> allResults = new ArrayList<>();
        for (EhrConnectionEntity connection : connections) {
            try {
                List<PatientMatchResult> results = searchAndScore(connection, request);
                allResults.addAll(results);
            } catch (Exception e) {
                log.warn("Patient match failed on connection '{}': {}", connection.getName(), e.getMessage());
            }
        }

        // Sort by confidence score descending, deduplicate
        allResults.sort(Comparator.comparingInt(PatientMatchResult::getConfidenceScore).reversed());
        return deduplicateResults(allResults);
    }

    /**
     * Validate a TW National ID format.
     */
    public boolean isValidTwNationalId(String id) {
        if (id == null || id.isBlank()) return false;
        return TW_NATIONAL_ID_PATTERN.matcher(id.toUpperCase()).matches();
    }

    private List<PatientMatchResult> searchAndScore(EhrConnectionEntity connection, PatientMatchRequest request) {
        IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);
        List<PatientMatchResult> results = new ArrayList<>();

        // Strategy 1: Exact identifier match (highest confidence)
        if (request.getNationalId() != null && !request.getNationalId().isBlank()) {
            Bundle idBundle = client.search()
                    .forResource(Patient.class)
                    .where(Patient.IDENTIFIER.exactly().identifier(request.getNationalId()))
                    .returnBundle(Bundle.class)
                    .execute();
            for (Bundle.BundleEntryComponent entry : idBundle.getEntry()) {
                if (entry.getResource() instanceof Patient patient) {
                    int score = calculateScore(patient, request);
                    results.add(buildResult(patient, connection, score, "exact", "National ID exact match"));
                }
            }
        }

        // Strategy 2: Name + birthDate search (moderate confidence)
        if (request.getFamilyName() != null && !request.getFamilyName().isBlank()) {
            var searchQuery = client.search().forResource(Patient.class)
                    .where(Patient.FAMILY.matches().value(request.getFamilyName()));

            if (request.getBirthDate() != null && !request.getBirthDate().isBlank()) {
                searchQuery = searchQuery.and(Patient.BIRTHDATE.exactly().day(request.getBirthDate()));
            }

            Bundle nameBundle = searchQuery.returnBundle(Bundle.class).execute();
            for (Bundle.BundleEntryComponent entry : nameBundle.getEntry()) {
                if (entry.getResource() instanceof Patient patient) {
                    // Skip if already matched by identifier
                    String patientId = patient.getIdElement().getIdPart();
                    boolean alreadyMatched = results.stream()
                            .anyMatch(r -> r.getPatientId().equals(patientId)
                                    && r.getConnectionId().equals(connection.getId()));
                    if (!alreadyMatched) {
                        int score = calculateScore(patient, request);
                        String matchType = score >= 80 ? "probable" : "possible";
                        results.add(buildResult(patient, connection, score, matchType, "Name/DOB match"));
                    }
                }
            }
        }

        return results;
    }

    /**
     * Calculate a confidence score (0-100) based on matching criteria.
     */
    int calculateScore(Patient patient, PatientMatchRequest request) {
        int score = 0;

        // National ID match: +50 points
        if (request.getNationalId() != null && !request.getNationalId().isBlank()) {
            for (Identifier id : patient.getIdentifier()) {
                if (id.hasValue() && id.getValue().equalsIgnoreCase(request.getNationalId())) {
                    score += 50;
                    break;
                }
            }
        }

        // Birth date match: +20 points
        if (request.getBirthDate() != null && patient.hasBirthDate()) {
            String patientDob = patient.getBirthDateElement().getValueAsString();
            if (request.getBirthDate().equals(patientDob)) {
                score += 20;
            }
        }

        // Family name match: +15 points
        if (request.getFamilyName() != null && patient.hasName()) {
            for (HumanName name : patient.getName()) {
                if (name.hasFamily() && name.getFamily().equalsIgnoreCase(request.getFamilyName())) {
                    score += 15;
                    break;
                }
            }
        }

        // Given name match: +10 points
        if (request.getGivenName() != null && patient.hasName()) {
            for (HumanName name : patient.getName()) {
                for (StringType given : name.getGiven()) {
                    if (given.getValue().equalsIgnoreCase(request.getGivenName())) {
                        score += 10;
                        break;
                    }
                }
            }
        }

        // Gender match: +5 points
        if (request.getGender() != null && patient.hasGender()) {
            if (request.getGender().equalsIgnoreCase(patient.getGender().toCode())) {
                score += 5;
            }
        }

        return Math.min(score, 100);
    }

    private PatientMatchResult buildResult(Patient patient, EhrConnectionEntity connection,
                                            int score, String matchType, String matchDetails) {
        String name = "Unknown";
        if (patient.hasName()) {
            HumanName hn = patient.getNameFirstRep();
            StringBuilder sb = new StringBuilder();
            if (hn.hasFamily()) sb.append(hn.getFamily());
            if (hn.hasGiven()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(hn.getGiven().stream().map(StringType::getValue).collect(Collectors.joining(" ")));
            }
            if (!sb.isEmpty()) name = sb.toString();
        }

        String identifier = null;
        if (patient.hasIdentifier()) {
            for (Identifier id : patient.getIdentifier()) {
                if (id.hasUse() && id.getUse() == Identifier.IdentifierUse.OFFICIAL && id.hasValue()) {
                    identifier = id.getValue();
                    break;
                }
            }
            if (identifier == null) {
                for (Identifier id : patient.getIdentifier()) {
                    if (id.hasValue()) { identifier = id.getValue(); break; }
                }
            }
        }

        return PatientMatchResult.builder()
                .patientId(patient.getIdElement().getIdPart())
                .patientName(name)
                .birthDate(patient.hasBirthDate() ? patient.getBirthDateElement().getValueAsString() : null)
                .gender(patient.hasGender() ? patient.getGender().toCode() : null)
                .identifier(identifier)
                .connectionId(connection.getId())
                .connectionName(connection.getName())
                .confidenceScore(score)
                .matchType(matchType)
                .matchDetails(matchDetails)
                .build();
    }

    /**
     * Deduplicate results across connections - keep highest scoring entry per unique patient identifier.
     */
    List<PatientMatchResult> deduplicateResults(List<PatientMatchResult> results) {
        Map<String, PatientMatchResult> seen = new LinkedHashMap<>();
        for (PatientMatchResult result : results) {
            String key = result.getIdentifier() != null
                    ? result.getIdentifier()
                    : result.getConnectionId() + ":" + result.getPatientId();
            seen.putIfAbsent(key, result); // Already sorted by score desc, keep first
        }
        return new ArrayList<>(seen.values());
    }
}
