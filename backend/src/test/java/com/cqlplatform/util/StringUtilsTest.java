package com.cqlplatform.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilsTest {

    @Test
    void truncate_shouldReturnNull_whenInputIsNull() {
        assertThat(StringUtils.truncate(null, 10)).isNull();
    }

    @Test
    void truncate_shouldReturnEmpty_whenInputIsEmpty() {
        assertThat(StringUtils.truncate("", 10)).isEmpty();
    }

    @Test
    void truncate_shouldReturnSameString_whenShorterThanMax() {
        assertThat(StringUtils.truncate("short", 100)).isEqualTo("short");
    }

    @Test
    void truncate_shouldReturnSameString_whenExactlyAtMax() {
        assertThat(StringUtils.truncate("exact", 5)).isEqualTo("exact");
    }

    @Test
    void truncate_shouldCut_whenLongerThanMax() {
        assertThat(StringUtils.truncate("abcdefghij", 4)).isEqualTo("abcd");
    }

    @Test
    void truncate_shouldHandleZeroMax() {
        assertThat(StringUtils.truncate("anything", 0)).isEmpty();
    }
}
