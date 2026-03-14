package com.cqlplatform.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnershipVerifierTest {

    private final OwnershipVerifier verifier = new OwnershipVerifier();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setUser(String username, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new TestingAuthenticationToken(username, "password", authorities);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setUserWithDepartment(String username, String department, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new TestingAuthenticationToken(username, "password", authorities);
        auth.setAuthenticated(true);
        auth.setDetails(department);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ===== getCurrentUsername =====

    @Test
    void getCurrentUsername_shouldReturnAuthenticatedUsername() {
        setUser("testuser", "ROLE_USER");

        assertThat(verifier.getCurrentUsername()).isEqualTo("testuser");
    }

    @Test
    void getCurrentUsername_notAuthenticated_shouldThrowAccessDenied() {
        // No authentication set
        assertThatThrownBy(() -> verifier.getCurrentUsername())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Not authenticated");
    }

    // ===== isAdmin =====

    @Test
    void isAdmin_withAdminRole_shouldReturnTrue() {
        setUser("admin", "ROLE_ADMIN");

        assertThat(verifier.isAdmin()).isTrue();
    }

    @Test
    void isAdmin_withUserRole_shouldReturnFalse() {
        setUser("user", "ROLE_USER");

        assertThat(verifier.isAdmin()).isFalse();
    }

    // ===== isDepartmentAdmin =====

    @Test
    void isDepartmentAdmin_withDeptAdminRole_shouldReturnTrue() {
        setUser("deptadmin", "ROLE_DEPARTMENT_ADMIN");

        assertThat(verifier.isDepartmentAdmin()).isTrue();
    }

    // ===== verifyOwnership =====

    @Test
    void verifyOwnership_ownResource_shouldPass() {
        setUser("alice", "ROLE_USER");

        // Should not throw
        verifier.verifyOwnership("alice");
    }

    @Test
    void verifyOwnership_admin_shouldBypass() {
        setUser("admin", "ROLE_ADMIN");

        // Admin can access anyone's resource
        verifier.verifyOwnership("other-user");
    }

    @Test
    void verifyOwnership_otherUser_shouldThrowAccessDenied() {
        setUser("alice", "ROLE_USER");

        assertThatThrownBy(() -> verifier.verifyOwnership("bob"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have permission");
    }

    // ===== verifySameDepartment =====

    @Test
    void verifySameDepartment_matchingDepartment_shouldPass() {
        setUserWithDepartment("deptadmin", "cardiology", "ROLE_DEPARTMENT_ADMIN");

        // Should not throw
        verifier.verifySameDepartment("cardiology");
    }

    @Test
    void verifySameDepartment_mismatchedDepartment_shouldThrow() {
        setUserWithDepartment("deptadmin", "cardiology", "ROLE_DEPARTMENT_ADMIN");

        assertThatThrownBy(() -> verifier.verifySameDepartment("oncology"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have permission");
    }

    @Test
    void verifySameDepartment_admin_shouldBypass() {
        setUser("admin", "ROLE_ADMIN");

        // Admin bypasses department check
        verifier.verifySameDepartment("any-department");
    }
}
