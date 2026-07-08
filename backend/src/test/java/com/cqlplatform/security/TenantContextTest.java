package com.cqlplatform.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setGetClear() {
        assertThat(TenantContext.getCurrentTenantId()).isNull();

        TenantContext.setCurrentTenantId(42L);
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo(42L);

        TenantContext.clear();
        assertThat(TenantContext.getCurrentTenantId()).isNull();
    }
}
