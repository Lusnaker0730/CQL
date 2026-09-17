package com.cqlplatform.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryMetadataDTOTest {

    @Test
    void fromElmParsesDefsExcludesPatientAndSeparatesFunctions() {
        String elm = "{\"library\":{"
                + "\"statements\":{\"def\":["
                + "  {\"name\":\"Patient\"},"
                + "  {\"name\":\"Has Diabetes\"},"
                + "  {\"name\":\"CalcAge\",\"operand\":[{}]}"
                + "]},"
                + "\"valueSets\":{\"def\":[{\"name\":\"Diabetes VS\"}]},"
                + "\"codes\":{\"def\":[{\"name\":\"HbA1c Code\"}]}"
                + "}}";

        LibraryMetadataDTO dto = LibraryMetadataDTO.fromElm("Lib", "1.0.0", elm);

        assertThat(dto.getName()).isEqualTo("Lib");
        assertThat(dto.getVersion()).isEqualTo("1.0.0");
        assertThat(dto.getExpressions()).containsExactly("Has Diabetes"); // Patient excluded
        assertThat(dto.getFunctions()).containsExactly("CalcAge");        // has operand → function
        assertThat(dto.getValueSets()).containsExactly("Diabetes VS");
        assertThat(dto.getCodes()).containsExactly("HbA1c Code");
    }

    @Test
    void fromElmWithNullElmReturnsEmptyLists() {
        LibraryMetadataDTO dto = LibraryMetadataDTO.fromElm("Lib", "1.0.0", null);
        assertThat(dto.getName()).isEqualTo("Lib");
        assertThat(dto.getVersion()).isEqualTo("1.0.0");
        assertThat(dto.getExpressions()).isEmpty();
        assertThat(dto.getValueSets()).isEmpty();
        assertThat(dto.getCodes()).isEmpty();
        assertThat(dto.getFunctions()).isEmpty();
    }

    @Test
    void fromElmWithMalformedElmFallsBackToEmptyLists() {
        LibraryMetadataDTO dto = LibraryMetadataDTO.fromElm("Lib", "1.0.0", "{not valid json");
        assertThat(dto.getName()).isEqualTo("Lib");
        assertThat(dto.getExpressions()).isEmpty();
    }

    @Test
    void fromLibraryDelegatesToFromElm() {
        CqlLibrary lib = CqlLibrary.builder()
                .name("Lib").version("2.0")
                .elmJson("{\"library\":{\"statements\":{\"def\":[{\"name\":\"A\"}]}}}")
                .build();

        LibraryMetadataDTO dto = LibraryMetadataDTO.fromLibrary(lib);

        assertThat(dto.getName()).isEqualTo("Lib");
        assertThat(dto.getVersion()).isEqualTo("2.0");
        assertThat(dto.getExpressions()).containsExactly("A");
    }
}
