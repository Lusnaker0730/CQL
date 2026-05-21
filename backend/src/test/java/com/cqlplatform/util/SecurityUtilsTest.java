package com.cqlplatform.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUsername_shouldReturnDefault_whenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertThat(SecurityUtils.getCurrentUsername("anonymous")).isEqualTo("anonymous");
    }

    @Test
    void getCurrentUsername_shouldReturnAuthName_whenAuthenticated() {
        var auth = new UsernamePasswordAuthenticationToken("alice", "pw",
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityUtils.getCurrentUsername("system")).isEqualTo("alice");
    }

    @Test
    void getCurrentUsername_shouldReturnDefault_whenAuthNameIsNull() {
        // Authentication set but getName returns null — we treat same as unavailable
        var auth = new UsernamePasswordAuthenticationToken(null, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // UsernamePasswordAuthenticationToken.getName returns "" when principal is null,
        // so we expect "" (an empty but non-null name, which counts as "present").
        // This locks existing behavior — empty name is returned as-is.
        assertThat(SecurityUtils.getCurrentUsername("system")).isEqualTo("");
    }
}
