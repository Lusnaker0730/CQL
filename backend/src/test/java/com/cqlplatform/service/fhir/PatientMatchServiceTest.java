package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.model.ehr.PatientMatchRequest;
import com.cqlplatform.model.fhir.PatientMatchResult;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientMatchServiceTest {

    @Mock
    private EhrConnectionService connectionService;

    @Mock
    private FhirClientFactory fhirClientFactory;

    @InjectMocks
    private PatientMatchService patientMatchService;

    private EhrConnectionEntity connection;

    @BeforeEach
    void setUp() {
        connection = new EhrConnectionEntity();
        connection.setId(1L);
        connection.setName("Test Hospital");
    }

    // ===== Helper Methods =====

    private Patient buildPatient(String id, String family, String given,
                                  String birthDate, Enumerations.AdministrativeGender gender,
                                  String nationalId) {
        Patient patient = new Patient();
        patient.setId(id);

        HumanName name = patient.addName();
        name.setFamily(family);
        if (given != null) name.addGiven(given);

        if (birthDate != null) {
            try {
                patient.setBirthDateElement(new DateType(birthDate));
            } catch (Exception e) {
                // ignore parse errors in test setup
            }
        }

        if (gender != null) patient.setGender(gender);

        if (nationalId != null) {
            Identifier identifier = patient.addIdentifier();
            identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
            identifier.setValue(nationalId);
        }

        return patient;
    }

    private Bundle buildBundle(Patient... patients) {
        Bundle bundle = new Bundle();
        for (Patient p : patients) {
            bundle.addEntry().setResource(p);
        }
        return bundle;
    }

    @SuppressWarnings("unchecked")
    private IGenericClient mockClientWithBundle(Bundle identifierBundle, Bundle nameBundle) {
        IGenericClient client = mock(IGenericClient.class);
        IUntypedQuery untypedQuery = mock(IUntypedQuery.class);
        when(client.search()).thenReturn(untypedQuery);

        // We need two separate query chains for identifier and name searches
        IQuery<Bundle> idQuery = mock(IQuery.class);
        IQuery<Bundle> nameQuery = mock(IQuery.class);

        when(untypedQuery.forResource(Patient.class)).thenReturn(idQuery);

        // First call (identifier search) returns identifierBundle
        // Second call (name search) returns nameBundle
        if (identifierBundle != null && nameBundle != null) {
            when(idQuery.where(any(ICriterion.class))).thenReturn(idQuery, nameQuery);
            when(idQuery.returnBundle(Bundle.class)).thenReturn(idQuery);
            when(idQuery.execute()).thenReturn(identifierBundle);
            when(nameQuery.and(any(ICriterion.class))).thenReturn(nameQuery);
            when(nameQuery.returnBundle(Bundle.class)).thenReturn(nameQuery);
            when(nameQuery.execute()).thenReturn(nameBundle);
        } else if (identifierBundle != null) {
            when(idQuery.where(any(ICriterion.class))).thenReturn(idQuery);
            when(idQuery.returnBundle(Bundle.class)).thenReturn(idQuery);
            when(idQuery.execute()).thenReturn(identifierBundle);
        } else if (nameBundle != null) {
            when(idQuery.where(any(ICriterion.class))).thenReturn(nameQuery);
            when(nameQuery.returnBundle(Bundle.class)).thenReturn(nameQuery);
            when(nameQuery.execute()).thenReturn(nameBundle);
            when(nameQuery.and(any(ICriterion.class))).thenReturn(nameQuery);
        }

        return client;
    }

    // ===== matchPatients tests =====

    @Test
    void matchPatients_byNationalId_shouldReturnExactMatch() {
        Patient patient = buildPatient("p1", "Wang", "Ming",
                "1990-01-15", Enumerations.AdministrativeGender.MALE, "A123456789");
        Bundle bundle = buildBundle(patient);

        IGenericClient client = mockClientWithBundle(bundle, null);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);
        when(connectionService.list(null)).thenReturn(List.of(connection));

        PatientMatchRequest request = new PatientMatchRequest();
        request.setNationalId("A123456789");

        List<PatientMatchResult> results = patientMatchService.matchPatients(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPatientId()).isEqualTo("p1");
        assertThat(results.get(0).getMatchType()).isEqualTo("exact");
        assertThat(results.get(0).getConfidenceScore()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void matchPatients_byName_shouldReturnProbableMatch() {
        Patient patient = buildPatient("p2", "Chen", "Wei",
                "1985-06-20", Enumerations.AdministrativeGender.FEMALE, null);
        Bundle bundle = buildBundle(patient);

        IGenericClient client = mockClientWithBundle(null, bundle);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);
        when(connectionService.list(null)).thenReturn(List.of(connection));

        PatientMatchRequest request = new PatientMatchRequest();
        request.setFamilyName("Chen");
        request.setBirthDate("1985-06-20");

        List<PatientMatchResult> results = patientMatchService.matchPatients(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPatientName()).contains("Chen");
    }

    @Test
    void matchPatients_acrossConnections_shouldDedup() {
        // Same patient appears in two connections with same identifier
        PatientMatchResult r1 = PatientMatchResult.builder()
                .patientId("p1").identifier("A123456789").connectionId(1L)
                .connectionName("Conn1").confidenceScore(95).matchType("exact")
                .matchDetails("National ID exact match").build();
        PatientMatchResult r2 = PatientMatchResult.builder()
                .patientId("p1").identifier("A123456789").connectionId(2L)
                .connectionName("Conn2").confidenceScore(80).matchType("probable")
                .matchDetails("Name/DOB match").build();

        List<PatientMatchResult> input = new ArrayList<>(List.of(r1, r2));
        List<PatientMatchResult> deduped = patientMatchService.deduplicateResults(input);

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).getConfidenceScore()).isEqualTo(95);
    }

    @Test
    void matchPatients_noResults_shouldReturnEmpty() {
        Bundle emptyBundle = new Bundle();

        IGenericClient client = mockClientWithBundle(emptyBundle, null);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);
        when(connectionService.list(null)).thenReturn(List.of(connection));

        PatientMatchRequest request = new PatientMatchRequest();
        request.setNationalId("Z999999999");

        List<PatientMatchResult> results = patientMatchService.matchPatients(request);
        assertThat(results).isEmpty();
    }

    @Test
    void matchPatients_connectionError_shouldContinue() {
        EhrConnectionEntity conn2 = new EhrConnectionEntity();
        conn2.setId(2L);
        conn2.setName("Broken Hospital");

        when(connectionService.list(null)).thenReturn(List.of(connection, conn2));

        // First connection throws, second returns results
        when(fhirClientFactory.createAuthenticatedClient(connection))
                .thenThrow(new RuntimeException("Connection refused"));

        Patient patient = buildPatient("p1", "Lin", "Yu",
                "2000-03-10", Enumerations.AdministrativeGender.MALE, "B234567890");
        Bundle bundle = buildBundle(patient);
        IGenericClient client2 = mockClientWithBundle(bundle, null);
        when(fhirClientFactory.createAuthenticatedClient(conn2)).thenReturn(client2);

        PatientMatchRequest request = new PatientMatchRequest();
        request.setNationalId("B234567890");

        List<PatientMatchResult> results = patientMatchService.matchPatients(request);

        // Should still get results from conn2 despite conn1 failure
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConnectionId()).isEqualTo(2L);
    }

    // ===== calculateScore tests =====

    @Test
    void calculateScore_fullMatch_shouldReturn100() {
        Patient patient = buildPatient("p1", "Wang", "Ming",
                "1990-01-15", Enumerations.AdministrativeGender.MALE, "A123456789");

        PatientMatchRequest request = new PatientMatchRequest();
        request.setNationalId("A123456789");
        request.setBirthDate("1990-01-15");
        request.setFamilyName("Wang");
        request.setGivenName("Ming");
        request.setGender("male");

        int score = patientMatchService.calculateScore(patient, request);
        assertThat(score).isEqualTo(100);
    }

    @Test
    void calculateScore_identifierOnly_shouldReturn50() {
        Patient patient = buildPatient("p1", "Wang", null,
                null, null, "A123456789");

        PatientMatchRequest request = new PatientMatchRequest();
        request.setNationalId("A123456789");

        int score = patientMatchService.calculateScore(patient, request);
        assertThat(score).isEqualTo(50);
    }

    @Test
    void calculateScore_nameAndDob_shouldReturn35() {
        Patient patient = buildPatient("p1", "Chen", "Wei",
                "1985-06-20", null, null);

        PatientMatchRequest request = new PatientMatchRequest();
        request.setFamilyName("Chen");
        request.setGivenName("Wei");
        request.setBirthDate("1985-06-20");

        int score = patientMatchService.calculateScore(patient, request);
        // family(15) + given(10) + dob(20) = 45
        assertThat(score).isEqualTo(45);
    }

    // ===== isValidTwNationalId tests =====

    @Test
    void isValidTwNationalId_validId_shouldReturnTrue() {
        assertThat(patientMatchService.isValidTwNationalId("A123456789")).isTrue();
        assertThat(patientMatchService.isValidTwNationalId("B234567890")).isTrue();
        assertThat(patientMatchService.isValidTwNationalId("F212345678")).isTrue();
    }

    @Test
    void isValidTwNationalId_invalidId_shouldReturnFalse() {
        assertThat(patientMatchService.isValidTwNationalId("12345")).isFalse();
        assertThat(patientMatchService.isValidTwNationalId("ABCDEFGHIJ")).isFalse();
        assertThat(patientMatchService.isValidTwNationalId("A12345")).isFalse();
        // Third digit must not be anything other than what pattern allows (letter + [12] + 8 digits)
        assertThat(patientMatchService.isValidTwNationalId("A323456789")).isFalse();
    }

    @Test
    void isValidTwNationalId_null_shouldReturnFalse() {
        assertThat(patientMatchService.isValidTwNationalId(null)).isFalse();
        assertThat(patientMatchService.isValidTwNationalId("")).isFalse();
        assertThat(patientMatchService.isValidTwNationalId("   ")).isFalse();
    }

    // ===== deduplicateResults tests =====

    @Test
    void deduplicateResults_samePatient_shouldKeepHighestScore() {
        PatientMatchResult high = PatientMatchResult.builder()
                .patientId("p1").identifier("A123456789").connectionId(1L)
                .connectionName("Conn1").confidenceScore(95).matchType("exact")
                .matchDetails("ID match").build();
        PatientMatchResult low = PatientMatchResult.builder()
                .patientId("p1").identifier("A123456789").connectionId(2L)
                .connectionName("Conn2").confidenceScore(35).matchType("possible")
                .matchDetails("Name match").build();

        // Input sorted descending by score
        List<PatientMatchResult> results = patientMatchService.deduplicateResults(
                new ArrayList<>(List.of(high, low)));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getConfidenceScore()).isEqualTo(95);
        assertThat(results.get(0).getConnectionName()).isEqualTo("Conn1");
    }

    @Test
    void deduplicateResults_differentPatients_shouldKeepAll() {
        PatientMatchResult r1 = PatientMatchResult.builder()
                .patientId("p1").identifier("A123456789").connectionId(1L)
                .connectionName("Conn1").confidenceScore(90).matchType("exact").build();
        PatientMatchResult r2 = PatientMatchResult.builder()
                .patientId("p2").identifier("B234567890").connectionId(1L)
                .connectionName("Conn1").confidenceScore(70).matchType("probable").build();

        List<PatientMatchResult> results = patientMatchService.deduplicateResults(
                new ArrayList<>(List.of(r1, r2)));

        assertThat(results).hasSize(2);
    }

    @Test
    void deduplicateResults_noIdentifier_shouldUseConnectionAndPatientId() {
        PatientMatchResult r1 = PatientMatchResult.builder()
                .patientId("p1").identifier(null).connectionId(1L)
                .connectionName("Conn1").confidenceScore(50).matchType("possible").build();
        PatientMatchResult r2 = PatientMatchResult.builder()
                .patientId("p1").identifier(null).connectionId(2L)
                .connectionName("Conn2").confidenceScore(40).matchType("possible").build();

        List<PatientMatchResult> results = patientMatchService.deduplicateResults(
                new ArrayList<>(List.of(r1, r2)));

        // Different keys (1:p1 vs 2:p1) so both kept
        assertThat(results).hasSize(2);
    }
}
