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
    void shortKey_shouldPad() {
        EncryptionConverter shortKeyConverter = new EncryptionConverter("short");
        String encrypted = shortKeyConverter.convertToDatabaseColumn("test");
        String decrypted = shortKeyConverter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo("test");
    }
}
