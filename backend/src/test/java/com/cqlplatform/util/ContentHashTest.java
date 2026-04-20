package com.cqlplatform.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashTest {

    /** Known SHA-256 vector: hash of empty string. */
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void nullInput_shouldReturnNull() {
        // null propagates — distinguishes "never hashed" from "hashed empty" in DB.
        assertThat(ContentHash.sha256Hex(null)).isNull();
    }

    @Test
    void emptyString_shouldReturnKnownSha256() {
        // Empty string hashes to a well-defined SHA-256 constant. Any future
        // implementation change must keep this exact output.
        assertThat(ContentHash.sha256Hex("")).isEqualTo(EMPTY_SHA256);
    }

    @Test
    void knownStringVector_shouldMatch() {
        // Reference vector verifiable via `echo -n "abc" | sha256sum`.
        assertThat(ContentHash.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sameInput_sameOutput() {
        // Purity check — required for provenance use (same CQL → same hash → audit
        // can say "these reports ran the identical measure").
        String cql = "define \"Initial Population\": true";
        assertThat(ContentHash.sha256Hex(cql)).isEqualTo(ContentHash.sha256Hex(cql));
    }

    @Test
    void byteLevelChange_producesDifferentHash() {
        // Even whitespace-only changes must bump the hash — otherwise "cosmetic edit"
        // to production CQL looks identical on audit. elm_hash fills the "semantically
        // equivalent" role separately.
        assertThat(ContentHash.sha256Hex("abc ")).isNotEqualTo(ContentHash.sha256Hex("abc"));
        assertThat(ContentHash.sha256Hex("ABC")).isNotEqualTo(ContentHash.sha256Hex("abc"));
    }

    @Test
    void utf8ChineseInput_shouldHashConsistently() {
        // Character set sanity — CQL can contain Chinese comments in our project.
        String cql = "define \"人口\": true";
        String hash1 = ContentHash.sha256Hex(cql);
        String hash2 = ContentHash.sha256Hex(cql);
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
    }
}
