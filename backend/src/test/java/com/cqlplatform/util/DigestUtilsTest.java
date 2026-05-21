package com.cqlplatform.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigestUtilsTest {

    @Test
    void sha256Hex_shouldMatchKnownVector_emptyString() {
        // SHA-256 of empty string is a well-known fixed value
        assertThat(DigestUtils.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256Hex_shouldMatchKnownVector_abc() {
        // SHA-256("abc") from RFC / NIST test vectors
        assertThat(DigestUtils.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_shouldBeDeterministic() {
        String input = "the quick brown fox jumps over the lazy dog";
        assertThat(DigestUtils.sha256Hex(input)).isEqualTo(DigestUtils.sha256Hex(input));
    }

    @Test
    void sha256Hex_shouldHandleUtf8MultiByteChars() {
        // Should not throw and produce a 64-char hex string
        String out = DigestUtils.sha256Hex("繁體中文測試 🔐");
        assertThat(out).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void sha256Hex_shouldProduceDifferentHashesForDifferentInputs() {
        assertThat(DigestUtils.sha256Hex("foo")).isNotEqualTo(DigestUtils.sha256Hex("bar"));
    }

    @Test
    void sha256Hex_shouldReturnLowercaseHex() {
        String out = DigestUtils.sha256Hex("test");
        assertThat(out).isEqualTo(out.toLowerCase());
        assertThat(out).hasSize(64);
    }
}
