package com.cqlplatform.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class OwnershipVerifier {

    public String getCurrentUsername() {
        Authentication auth = getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        return auth.getName();
    }

    public boolean isAdmin() {
        Authentication auth = getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    public void verifyOwnership(String ownerUsername) {
        if (ownerUsername == null) return;
        String currentUser = getCurrentUsername();
        if (!ownerUsername.equals(currentUser) && !isAdmin()) {
            throw new AccessDeniedException("You do not have permission to modify this resource");
        }
    }

    public boolean isDepartmentAdmin() {
        Authentication auth = getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEPARTMENT_ADMIN".equals(a.getAuthority()));
    }

    public String getCurrentDepartment() {
        Authentication auth = getAuthentication();
        if (auth != null && auth.getDetails() instanceof String) {
            return (String) auth.getDetails();
        }
        return null;
    }

    public void verifySameDepartment(String resourceDepartment) {
        if (isAdmin()) return;
        if (isDepartmentAdmin()) {
            String userDept = getCurrentDepartment();
            if (userDept != null && userDept.equals(resourceDepartment)) {
                return;
            }
        }
        throw new AccessDeniedException("You do not have permission to access resources in this department");
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
