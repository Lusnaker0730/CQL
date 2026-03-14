package com.cqlplatform.service.cds;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CdsTupleAccessorTest {

    /**
     * Fake Tuple class that mimics the CQL engine's Tuple with a getElements() method.
     */
    static class FakeTuple {
        private final Map<String, Object> elements;

        FakeTuple(Map<String, Object> elements) {
            this.elements = elements;
        }

        public Map<String, Object> getElements() {
            return elements;
        }
    }

    @Test
    void isTuple_withTupleObject_shouldReturnTrue() {
        FakeTuple tuple = new FakeTuple(Map.of("key", "value"));
        assertThat(CdsTupleAccessor.isTuple(tuple)).isTrue();
    }

    @Test
    void isTuple_withNull_shouldReturnFalse() {
        assertThat(CdsTupleAccessor.isTuple(null)).isFalse();
    }

    @Test
    void isTuple_withNonTupleObject_shouldReturnFalse() {
        assertThat(CdsTupleAccessor.isTuple("just a string")).isFalse();
        assertThat(CdsTupleAccessor.isTuple(42)).isFalse();
    }

    @Test
    void getElements_shouldReturnMap() {
        Map<String, Object> elements = new HashMap<>();
        elements.put("summary", "Test Summary");
        elements.put("detail", "Test Detail");
        FakeTuple tuple = new FakeTuple(elements);

        Map<String, Object> result = CdsTupleAccessor.getElements(tuple);
        assertThat(result).containsEntry("summary", "Test Summary");
        assertThat(result).containsEntry("detail", "Test Detail");
    }

    @Test
    void getElements_withNonTuple_shouldReturnEmptyMap() {
        Map<String, Object> result = CdsTupleAccessor.getElements("not a tuple");
        assertThat(result).isEmpty();
    }

    @Test
    void getString_shouldReturnStringValue() {
        FakeTuple tuple = new FakeTuple(Map.of("summary", "Hello"));
        assertThat(CdsTupleAccessor.getString(tuple, "summary")).isEqualTo("Hello");
    }

    @Test
    void getString_missingField_shouldReturnNull() {
        FakeTuple tuple = new FakeTuple(Map.of("summary", "Hello"));
        assertThat(CdsTupleAccessor.getString(tuple, "nonexistent")).isNull();
    }

    @Test
    void getObject_shouldReturnRawValue() {
        List<String> list = List.of("a", "b");
        FakeTuple tuple = new FakeTuple(Map.of("items", list));
        assertThat(CdsTupleAccessor.getObject(tuple, "items")).isEqualTo(list);
    }

    @Test
    void getList_shouldReturnListValue() {
        List<Object> list = List.of("item1", "item2");
        FakeTuple tuple = new FakeTuple(Map.of("suggestions", list));
        assertThat(CdsTupleAccessor.getList(tuple, "suggestions")).containsExactly("item1", "item2");
    }

    @Test
    void getList_nonListValue_shouldReturnEmptyList() {
        FakeTuple tuple = new FakeTuple(Map.of("summary", "not a list"));
        assertThat(CdsTupleAccessor.getList(tuple, "summary")).isEmpty();
    }

    @Test
    void getList_missingField_shouldReturnEmptyList() {
        FakeTuple tuple = new FakeTuple(Map.of());
        assertThat(CdsTupleAccessor.getList(tuple, "items")).isEmpty();
    }

    @Test
    void getBoolean_shouldReturnBooleanValue() {
        FakeTuple tuple = new FakeTuple(Map.of("isRecommended", true));
        assertThat(CdsTupleAccessor.getBoolean(tuple, "isRecommended")).isTrue();
    }

    @Test
    void getBoolean_nonBooleanValue_shouldReturnNull() {
        FakeTuple tuple = new FakeTuple(Map.of("summary", "text"));
        assertThat(CdsTupleAccessor.getBoolean(tuple, "summary")).isNull();
    }

    @Test
    void getBoolean_missingField_shouldReturnNull() {
        FakeTuple tuple = new FakeTuple(Map.of());
        assertThat(CdsTupleAccessor.getBoolean(tuple, "missing")).isNull();
    }
}
