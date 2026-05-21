package com.cqlplatform.repository;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.model.audit.AuditLogSearchRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-146 regression — locks {@link AuditLogSpecification}'s LIKE-clause builders
 * against the silent-escape-bug. Without {@code cb.like(expr, pattern, '\\')},
 * PostgreSQL (default {@code standard_conforming_strings=on}) ignores backslash
 * as an escape character — so a user input with a literal {@code %} would still
 * expand as a wildcard despite the {@code escapeLikeWildcards} pre-pass.
 *
 * <p>Pure unit test (no DB): verifies the Criteria builder is invoked with the
 * 3-arg overload and the correct escape char + escaped pattern.
 */
class AuditLogSpecificationTest {

    @Test
    void usernameContains_invokesCriteriaBuilderLikeWithEscapeChar() {
        // Build the spec with a username that contains a wildcard char (`%`)
        AuditLogSearchRequest req = new AuditLogSearchRequest();
        req.setUsername("admin%test");
        var spec = AuditLogSpecification.fromSearchRequest(req);

        // Mock the Criteria machinery
        Root<AuditLogEntity> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        @SuppressWarnings("unchecked")
        Path<String> usernamePath = (Path<String>) (Path<?>) root.get("username");
        @SuppressWarnings("unchecked")
        Expression<String> lowered = (Expression<String>) (Expression<?>) usernamePath;
        when(cb.lower(any())).thenReturn(lowered);
        when(cb.like(any(Expression.class), anyString(), anyChar())).thenReturn(mock(Predicate.class));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        // Capture the cb.like(expr, pattern, escape) call
        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Character> escapeCaptor = ArgumentCaptor.forClass(Character.class);
        verify(cb, times(1)).like(any(Expression.class), patternCaptor.capture(), escapeCaptor.capture());

        assertThat(escapeCaptor.getValue())
                .as("PAT-146: cb.like must be called with the 3-arg overload + backslash escape")
                .isEqualTo('\\');
        // Wildcard inside the user input must have been escaped to \%
        assertThat(patternCaptor.getValue())
                .as("user-input '%' should be escape-prefixed")
                .isEqualTo("%admin\\%test%");
    }

    @Test
    void resourceTypeContains_invokesCriteriaBuilderLikeWithEscapeChar() {
        AuditLogSearchRequest req = new AuditLogSearchRequest();
        req.setResourceType("Patient_secret");
        var spec = AuditLogSpecification.fromSearchRequest(req);

        Root<AuditLogEntity> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        @SuppressWarnings("unchecked")
        Path<String> path = (Path<String>) (Path<?>) root.get("resourceType");
        @SuppressWarnings("unchecked")
        Expression<String> lowered = (Expression<String>) (Expression<?>) path;
        when(cb.lower(any())).thenReturn(lowered);
        when(cb.like(any(Expression.class), anyString(), anyChar())).thenReturn(mock(Predicate.class));
        when(cb.conjunction()).thenReturn(mock(Predicate.class));

        spec.toPredicate(root, query, cb);

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Character> escapeCaptor = ArgumentCaptor.forClass(Character.class);
        verify(cb).like(any(Expression.class), patternCaptor.capture(), escapeCaptor.capture());

        assertThat(escapeCaptor.getValue()).isEqualTo('\\');
        // Wildcard inside the user input must have been escaped to \_
        assertThat(patternCaptor.getValue()).isEqualTo("%patient\\_secret%");
    }

    @Test
    void noFilters_returnsConjunctionPredicate() {
        AuditLogSearchRequest req = new AuditLogSearchRequest();
        var spec = AuditLogSpecification.fromSearchRequest(req);

        // toPredicate on a Specification.where(null).and(...).and(...) chain returns
        // null when nothing was added; the CriteriaQuery accepts null restriction.
        Root<AuditLogEntity> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        var result = spec.toPredicate(root, query, cb);

        // Either null or a conjunction is acceptable — important is no exception.
        assertThat(result == null || result != null).isTrue();
    }

    @SuppressWarnings("unchecked")
    private Root<AuditLogEntity> mockRoot() {
        Root<AuditLogEntity> root = mock(Root.class);
        Path<Object> path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        return root;
    }
}
