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

    @Test
    void callWith_setsTenantDuringActionAndRestoresAfter() {
        assertThat(TenantContext.getCurrentTenantId()).isNull();

        Long seen = TenantContext.callWith(9L, TenantContext::getCurrentTenantId);

        assertThat(seen).isEqualTo(9L);                          // visible inside the action
        assertThat(TenantContext.getCurrentTenantId()).isNull(); // restored (was null) afterwards
    }

    @Test
    void callWith_restoresPreviousTenantWhenNested() {
        TenantContext.setCurrentTenantId(1L);

        Long inner = TenantContext.callWith(2L, TenantContext::getCurrentTenantId);

        assertThat(inner).isEqualTo(2L);
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo(1L); // outer value restored
    }
}
