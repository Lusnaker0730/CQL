package com.cqlplatform.service.cql;

import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.service.fhir.EhrConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase 1 — the fail-closed resolution of a request's connectionId to a stored,
 * authenticated EhrConnection. The safety property: when a connectionId IS given we must
 * NEVER silently fall back to an unauthenticated / default FHIR server.
 */
@ExtendWith(MockitoExtension.class)
class CqlExecutionServiceConnectionTest {

    @Mock
    private EhrConnectionService ehrConnectionService;

    private CqlExecutionService service;

    @BeforeEach
    void setUp() {
        // resolveConnection only needs the field-injected ehrConnectionService.
        service = new CqlExecutionService(null, null, null, null);
        ReflectionTestUtils.setField(service, "ehrConnectionService", ehrConnectionService);
    }

    private CqlExecutionRequest req(Long connectionId) {
        CqlExecutionRequest r = new CqlExecutionRequest();
        r.setConnectionId(connectionId);
        return r;
    }

    private Object resolve(CqlExecutionRequest r) {
        return ReflectionTestUtils.invokeMethod(service, "resolveConnection", r);
    }

    @Test
    void nullConnectionIdResolvesToNull() {
        assertThat(resolve(req(null))).isNull();
        verifyNoInteractions(ehrConnectionService);
    }

    @Test
    void activeConnectionIsReturned() {
        EhrConnectionEntity c = new EhrConnectionEntity();
        c.setId(7L);
        c.setActive(true);
        c.setFhirServerUrl("https://clinic.example/fhir");
        when(ehrConnectionService.getById(7L)).thenReturn(c);

        assertThat(resolve(req(7L))).isSameAs(c);
    }

    @Test
    void inactiveConnectionIsRejected() {
        EhrConnectionEntity c = new EhrConnectionEntity();
        c.setId(8L);
        c.setActive(false);
        when(ehrConnectionService.getById(8L)).thenReturn(c);

        assertThatThrownBy(() -> resolve(req(8L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void missingConnectionPropagatesAndDoesNotFallBack() {
        when(ehrConnectionService.getById(9L))
                .thenThrow(new IllegalArgumentException("EHR connection not found: 9"));

        assertThatThrownBy(() -> resolve(req(9L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectionIdWithoutServiceFailsClosed() {
        ReflectionTestUtils.setField(service, "ehrConnectionService", null);

        assertThatThrownBy(() -> resolve(req(5L)))
                .isInstanceOf(IllegalStateException.class);
    }
}
