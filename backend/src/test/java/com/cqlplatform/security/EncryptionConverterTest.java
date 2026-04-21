package com.cqlplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EncryptionConverterTest {

    private EncryptionConverter converter;
    private static final String SECRET_KEY = "TestEncryptionKeyMustBeExact32B!";

    @BeforeEach
    void setUp() {
        converter = new EncryptionConverter(SECRET_KEY);
    }

    @Test
    void roundTrip_shouldEncryptAndDecrypt() {
        String original = "patient@example.com";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void convertToDatabaseColumn_shouldReturnNullForNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_shouldReturnNullForNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_shouldReturnLegacyDataAsIs() {
        String legacyData = "plaintext-not-encrypted";
        assertThat(converter.convertToEntityAttribute(legacyData)).isEqualTo(legacyData);
    }

    @Test
    void convertToDatabaseColumn_shouldProduceEncPrefix() {
        String encrypted = converter.convertToDatabaseColumn("test");
        assertThat(encrypted).startsWith("ENC:");
    }

    @Test
    void roundTrip_specialCharacters() {
        String original = "émàîl@hëalth.org!#$%^&*()";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void roundTrip_emptyString() {
        String encrypted = converter.convertToDatabaseColumn("");
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo("");
    }

    @Test
    void uniqueIVs_sameInputDifferentCiphertext() {
        String input = "same-input";
        String encrypted1 = converter.convertToDatabaseColumn(input);
        String encrypted2 = converter.convertToDatabaseColumn(input);
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void wrongKey_shouldFailToDecrypt() {
        String encrypted = converter.convertToDatabaseColumn("secret data");
        EncryptionConverter otherConverter = new EncryptionConverter("OtherEncryptionKeyMustBeExact32!");
        assertThatThrownBy(() -> otherConverter.convertToEntityAttribute(encrypted))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void roundTrip_longString() {
        String original = "a".repeat(10000);
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void roundTrip_largeJsonPayload_100kb() {
        // measure_report.result_json can exceed 100KB for patient-list reports;
        // BUG-N risk is that cipher operations or Base64 inflation break those writes.
        // ~100k chars of realistic JSON-like content (mixed ASCII + control chars).
        StringBuilder sb = new StringBuilder(100_000);
        for (int i = 0; i < 1000; i++) {
            sb.append("{\"patientId\":\"p-").append(i)
                    .append("\",\"populations\":{\"ip\":1,\"denom\":1,\"numer\":")
                    .append(i % 2).append("},\"date\":\"2026-01-").append(i % 28 + 1)
                    .append("\",\"notes\":\"Lorem ipsum dolor sit amet consectetur adipiscing elit.\"},");
        }
        String original = sb.toString();
        assertThat(original.length()).isGreaterThan(100_000);

        String encrypted = converter.convertToDatabaseColumn(original);
        assertThat(encrypted).startsWith("ENC:");
        // Base64 + GCM tag inflates ~1.33× — sanity check we didn't truncate.
        assertThat(encrypted.length()).isGreaterThan(original.length());

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void roundTrip_utf8MultibytePayload() {
        // CQL result payloads can contain Traditional Chinese comments + mixed
        // scripts from patient demographics. UTF-8 re-encoding roundtrip must
        // preserve byte-exact content.
        String original = "患者 王小明 | HbA1c 7.2% | 診斷: 第2型糖尿病 ✓\n就診日期: 2026-04-21";
        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void shortKey_shouldPad() {
        EncryptionConverter shortKeyConverter = new EncryptionConverter("short");
        String encrypted = shortKeyConverter.convertToDatabaseColumn("test");
        String decrypted = shortKeyConverter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo("test");
    }
}
