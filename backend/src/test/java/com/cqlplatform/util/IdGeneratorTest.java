package com.cqlplatform.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void uuid_shouldReturnValidUuidString() {
        String id = IdGenerator.uuid();
        // Parsing succeeds iff the format is a valid UUID
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void uuid_shouldProduceUniqueValuesAcrossManyCalls() {
        int n = 1000;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < n; i++) {
            ids.add(IdGenerator.uuid());
        }
        assertThat(ids).hasSize(n);  // all unique
    }

    @Test
    void uuid_shouldBe36Chars() {
        assertThat(IdGenerator.uuid()).hasSize(36);
    }
}
