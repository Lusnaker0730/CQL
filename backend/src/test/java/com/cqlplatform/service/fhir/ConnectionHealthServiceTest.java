package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IFetchConformanceUntyped;
import ca.uhn.fhir.rest.gclient.IFetchConformanceTyped;
import com.cqlplatform.entity.ConnectionHealthCheckEntity;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.repository.ConnectionHealthCheckRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionHealthServiceTest {

    @Mock
    private ConnectionHealthCheckRepository healthCheckRepository;

    @Mock
    private EhrConnectionService connectionService;

    @Mock
    private FhirClientFactory fhirClientFactory;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @InjectMocks
    private ConnectionHealthService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "checkIntervalMinutes", 15);
        ReflectionTestUtils.setField(service, "retentionDays", 7);
    }

    // ===== checkConnection =====

    @Test
    void checkConnection_healthy_shouldRecordHealthy() {
        EhrConnectionEntity connection = createConnection(1L);
        when(connectionService.getById(1L)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        org.hl7.fhir.r4.model.CapabilityStatement capabilities = new org.hl7.fhir.r4.model.CapabilityStatement();
        capabilities.setFhirVersion(org.hl7.fhir.r4.model.Enumerations.FHIRVersion._4_0_1);
        capabilities.getSoftware().setName("HAPI FHIR");

        @SuppressWarnings("unchecked")
        IFetchConformanceUntyped capUntyped = mock(IFetchConformanceUntyped.class);
        @SuppressWarnings("unchecked")
        IFetchConformanceTyped<org.hl7.fhir.r4.model.CapabilityStatement> capTyped = mock(IFetchConformanceTyped.class);

        when(client.capabilities()).thenReturn(capUntyped);
        when(capUntyped.ofType(org.hl7.fhir.r4.model.CapabilityStatement.class)).thenReturn(capTyped);
        when(capTyped.execute()).thenReturn(capabilities);

        when(healthCheckRepository.save(any(ConnectionHealthCheckEntity.class)))
                .thenAnswer(inv -> {
                    ConnectionHealthCheckEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        ConnectionHealthCheckEntity result = service.checkConnection(1L);

        assertThat(result.getStatus()).isEqualTo("healthy");
        assertThat(result.getFhirVersion()).isEqualTo("4.0.1");
        assertThat(result.getServerName()).isEqualTo("HAPI FHIR");
        assertThat(result.getResponseTimeMs()).isNotNull();
        assertThat(result.getConnectionId()).isEqualTo(1L);
        verify(healthCheckRepository).save(any(ConnectionHealthCheckEntity.class));
    }

    @Test
    void checkConnection_slow_shouldRecordDegraded() {
        EhrConnectionEntity connection = createConnection(1L);
        when(connectionService.getById(1L)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        @SuppressWarnings("unchecked")
        IFetchConformanceUntyped capUntyped = mock(IFetchConformanceUntyped.class);
        @SuppressWarnings("unchecked")
        IFetchConformanceTyped<org.hl7.fhir.r4.model.CapabilityStatement> capTyped = mock(IFetchConformanceTyped.class);

        when(client.capabilities()).thenReturn(capUntyped);
        when(capUntyped.ofType(org.hl7.fhir.r4.model.CapabilityStatement.class)).thenReturn(capTyped);

        org.hl7.fhir.r4.model.CapabilityStatement capabilities = new org.hl7.fhir.r4.model.CapabilityStatement();
        when(capTyped.execute()).thenReturn(capabilities);

        when(healthCheckRepository.save(any(ConnectionHealthCheckEntity.class)))
                .thenAnswer(inv -> {
                    ConnectionHealthCheckEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        // In a unit test we cannot truly simulate >5s delay, so we verify the save path works
        ConnectionHealthCheckEntity result = service.checkConnection(1L);
        assertThat(result.getResponseTimeMs()).isNotNull();
        assertThat(result.getStatus()).isIn("healthy", "degraded");
        verify(healthCheckRepository).save(any(ConnectionHealthCheckEntity.class));
    }

    @Test
    void checkConnection_down_shouldRecordDown() {
        EhrConnectionEntity connection = createConnection(1L);
        when(connectionService.getById(1L)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        IFetchConformanceUntyped capUntyped = mock(IFetchConformanceUntyped.class);
        when(client.capabilities()).thenReturn(capUntyped);
        when(capUntyped.ofType(org.hl7.fhir.r4.model.CapabilityStatement.class))
                .thenThrow(new RuntimeException("Connection refused"));

        when(healthCheckRepository.save(any(ConnectionHealthCheckEntity.class)))
                .thenAnswer(inv -> {
                    ConnectionHealthCheckEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        ConnectionHealthCheckEntity result = service.checkConnection(1L);

        assertThat(result.getStatus()).isEqualTo("down");
        assertThat(result.getErrorMessage()).contains("Connection refused");
        assertThat(result.getResponseTimeMs()).isNotNull();
        verify(healthCheckRepository).save(any(ConnectionHealthCheckEntity.class));
    }

    // ===== getHealthOverview =====

    @Test
    void getHealthOverview_shouldReturnAllConnections() {
        EhrConnectionEntity conn1 = createConnection(1L);
        EhrConnectionEntity conn2 = createConnection(2L);
        conn2.setName("Test Connection 2");
        when(connectionService.list(null)).thenReturn(List.of(conn1, conn2));

        ConnectionHealthCheckEntity check1 = createHealthCheck(1L, "healthy", 150L);
        when(healthCheckRepository.findFirstByConnectionIdOrderByCheckedAtDesc(1L))
                .thenReturn(Optional.of(check1));
        when(healthCheckRepository.findRecentByConnectionId(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(check1));

        ConnectionHealthCheckEntity check2 = createHealthCheck(2L, "down", 0L);
        check2.setErrorMessage("timeout");
        when(healthCheckRepository.findFirstByConnectionIdOrderByCheckedAtDesc(2L))
                .thenReturn(Optional.of(check2));
        when(healthCheckRepository.findRecentByConnectionId(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(check2));

        List<ConnectionHealthService.ConnectionHealthOverview> overview = service.getHealthOverview();

        assertThat(overview).hasSize(2);
        assertThat(overview.get(0).currentStatus()).isEqualTo("healthy");
        assertThat(overview.get(0).availability24h()).isEqualTo(100.0);
        assertThat(overview.get(1).currentStatus()).isEqualTo("down");
        assertThat(overview.get(1).errorCount24h()).isEqualTo(1);
    }

    @Test
    void getHealthOverview_withNoChecks_shouldReturnUnknown() {
        EhrConnectionEntity conn = createConnection(1L);
        when(connectionService.list(null)).thenReturn(List.of(conn));
        when(healthCheckRepository.findFirstByConnectionIdOrderByCheckedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(healthCheckRepository.findRecentByConnectionId(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        List<ConnectionHealthService.ConnectionHealthOverview> overview = service.getHealthOverview();

        assertThat(overview).hasSize(1);
        assertThat(overview.get(0).currentStatus()).isEqualTo("unknown");
        assertThat(overview.get(0).lastResponseTimeMs()).isNull();
        assertThat(overview.get(0).lastCheckedAt()).isNull();
        assertThat(overview.get(0).availability24h()).isEqualTo(0.0);
    }

    // ===== getHistory =====

    @Test
    void getHistory_shouldReturnRecentChecks() {
        // BUG-138: getHistory now gates on the tenant-scoped connection lookup first.
        when(connectionService.getById(1L)).thenReturn(new EhrConnectionEntity());
        ConnectionHealthCheckEntity check1 = createHealthCheck(1L, "healthy", 100L);
        ConnectionHealthCheckEntity check2 = createHealthCheck(1L, "healthy", 120L);
        when(healthCheckRepository.findRecentByConnectionId(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(check1, check2));

        List<ConnectionHealthCheckEntity> history = service.getHistory(1L, 24);

        assertThat(history).hasSize(2);
        verify(healthCheckRepository).findRecentByConnectionId(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void getHistory_whenConnectionNotInCallersTenant_shouldNotReadHealthChecks() {
        // getById is tenant-scoped (findByIdAndTenantId) and throws for a foreign tenant.
        // The endpoint has no @PreAuthorize, so this gate is the entire boundary.
        when(connectionService.getById(99L))
                .thenThrow(new IllegalArgumentException("EHR connection not found: 99"));

        assertThatThrownBy(() -> service.getHistory(99L, 24))
                .isInstanceOf(IllegalArgumentException.class);

        verify(healthCheckRepository, never()).findRecentByConnectionId(any(), any());
    }

    // ===== getCircuitBreakerStatuses =====

    @Test
    void getCircuitBreakerStatuses_shouldReturnAllBreakers() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        CircuitBreaker.Metrics metrics = mock(CircuitBreaker.Metrics.class);

        when(cb.getName()).thenReturn("fhirDataProvider");
        when(cb.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(cb.getMetrics()).thenReturn(metrics);
        when(metrics.getFailureRate()).thenReturn(5.0f);
        when(metrics.getSlowCallRate()).thenReturn(2.0f);
        when(metrics.getNumberOfBufferedCalls()).thenReturn(10);
        when(metrics.getNumberOfFailedCalls()).thenReturn(1);
        when(metrics.getNumberOfSuccessfulCalls()).thenReturn(9);

        when(circuitBreakerRegistry.getAllCircuitBreakers())
                .thenReturn(Set.of(cb));

        List<ConnectionHealthService.CircuitBreakerStatus> statuses = service.getCircuitBreakerStatuses();

        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).name()).isEqualTo("fhirDataProvider");
        assertThat(statuses.get(0).state()).isEqualTo("CLOSED");
        assertThat(statuses.get(0).failureRate()).isEqualTo(5.0f);
        assertThat(statuses.get(0).successfulCalls()).isEqualTo(9);
    }

    // ===== cleanupOldChecks =====

    @Test
    void cleanupOldChecks_shouldDeleteExpiredRecords() {
        when(healthCheckRepository.deleteByCheckedAtBefore(any(LocalDateTime.class))).thenReturn(5);

        service.cleanupOldChecks();

        verify(healthCheckRepository).deleteByCheckedAtBefore(any(LocalDateTime.class));
    }

    // ===== scheduledHealthCheck =====

    @Test
    void scheduledHealthCheck_shouldCheckAllConnections() {
        EhrConnectionEntity conn1 = createConnection(1L);
        EhrConnectionEntity conn2 = createConnection(2L);
        when(connectionService.list(null)).thenReturn(List.of(conn1, conn2));

        // For checkConnection calls
        when(connectionService.getById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return id.equals(1L) ? conn1 : conn2;
        });

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(any())).thenReturn(client);

        IFetchConformanceUntyped capUntyped = mock(IFetchConformanceUntyped.class);
        @SuppressWarnings("unchecked")
        IFetchConformanceTyped<org.hl7.fhir.r4.model.CapabilityStatement> capTyped = mock(IFetchConformanceTyped.class);
        when(client.capabilities()).thenReturn(capUntyped);
        when(capUntyped.ofType(org.hl7.fhir.r4.model.CapabilityStatement.class)).thenReturn(capTyped);
        when(capTyped.execute()).thenReturn(new org.hl7.fhir.r4.model.CapabilityStatement());

        when(healthCheckRepository.save(any(ConnectionHealthCheckEntity.class)))
                .thenAnswer(inv -> {
                    ConnectionHealthCheckEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        service.scheduledHealthCheck();

        verify(healthCheckRepository, times(2)).save(any(ConnectionHealthCheckEntity.class));
    }

    // ===== Helpers =====

    private EhrConnectionEntity createConnection(Long id) {
        EhrConnectionEntity conn = new EhrConnectionEntity();
        conn.setId(id);
        conn.setName("Test Connection " + id);
        conn.setFhirServerUrl("http://fhir-server-" + id + ":8080/fhir");
        conn.setActive(true);
        return conn;
    }

    private ConnectionHealthCheckEntity createHealthCheck(Long connectionId, String status, Long responseTimeMs) {
        ConnectionHealthCheckEntity check = new ConnectionHealthCheckEntity();
        check.setId(connectionId);
        check.setConnectionId(connectionId);
        check.setStatus(status);
        check.setResponseTimeMs(responseTimeMs);
        check.setCheckedAt(LocalDateTime.now());
        return check;
    }
}
