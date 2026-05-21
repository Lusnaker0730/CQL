package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-163: unit tests for V56 migration's JSON transform. Tests
 * {@link V56__migrate_arithmetic_to_nary#migrateBaseElementsJson(String)}
 * directly without a real DB so we exercise the transform shape rapidly.
 *
 * <p>The full DB-touching path (read row -> migrate -> write back) is
 * already exercised by Flyway every time the integration test runs Spring
 * up with H2; here we lock the JSON shape contract.
 */
class V56MigrationTest {

    private final V56__migrate_arithmetic_to_nary migration = new V56__migrate_arithmetic_to_nary();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacy2ary_convertsToNary() throws Exception {
        String legacy = "["
                + "{\"type\":\"arithmeticExpression\",\"fields\":["
                + "  {\"id\":\"left_mode\",\"value\":\"element\"},"
                + "  {\"id\":\"left_operand_id\",\"value\":\"be-1\"},"
                + "  {\"id\":\"operator\",\"value\":\"+\"},"
                + "  {\"id\":\"right_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"right_literal\",\"value\":\"5\"}"
                + "]}"
                + "]";
        String migrated = migration.migrateBaseElementsJson(legacy);
        assertThat(migrated).isNotNull();

        JsonNode root = mapper.readTree(migrated);
        JsonNode fields = root.get(0).get("fields");
        // Should now have exactly 2 fields: operands + operators
        assertThat(fields.size()).isEqualTo(2);
        assertThat(fields.get(0).get("id").asText()).isEqualTo("operands");
        assertThat(fields.get(1).get("id").asText()).isEqualTo("operators");

        JsonNode operands = fields.get(0).get("value");
        assertThat(operands.isArray()).isTrue();
        assertThat(operands.size()).isEqualTo(2);
        assertThat(operands.get(0).get("mode").asText()).isEqualTo("element");
        assertThat(operands.get(0).get("operand_id").asText()).isEqualTo("be-1");
        assertThat(operands.get(1).get("mode").asText()).isEqualTo("literal");
        assertThat(operands.get(1).get("operand_literal").asText()).isEqualTo("5");

        JsonNode operators = fields.get(1).get("value");
        assertThat(operators.isArray()).isTrue();
        assertThat(operators.size()).isEqualTo(1);
        assertThat(operators.get(0).asText()).isEqualTo("+");
    }

    @Test
    void alreadyNary_isNoOp() throws Exception {
        String nary = "["
                + "{\"type\":\"arithmeticExpression\",\"fields\":["
                + "  {\"id\":\"operands\",\"type\":\"json\",\"name\":\"operands\",\"value\":[]},"
                + "  {\"id\":\"operators\",\"type\":\"json\",\"name\":\"operators\",\"value\":[]}"
                + "]}"
                + "]";
        String migrated = migration.migrateBaseElementsJson(nary);
        // No-op path returns the same input string unchanged
        assertThat(migrated).isEqualTo(nary);
    }

    @Test
    void runTwice_isIdempotent() throws Exception {
        String legacy = "["
                + "{\"type\":\"arithmeticExpression\",\"fields\":["
                + "  {\"id\":\"left_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"left_literal\",\"value\":\"3\"},"
                + "  {\"id\":\"operator\",\"value\":\"*\"},"
                + "  {\"id\":\"right_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"right_literal\",\"value\":\"4\"}"
                + "]}"
                + "]";
        String firstPass = migration.migrateBaseElementsJson(legacy);
        String secondPass = migration.migrateBaseElementsJson(firstPass);
        assertThat(secondPass).isEqualTo(firstPass);
    }

    @Test
    void mixedBaseElements_onlyArithmeticTouched() throws Exception {
        String mixed = "["
                + "{\"type\":\"baseElement\",\"name\":\"Logic\",\"fields\":[]},"
                + "{\"type\":\"arithmeticUnary\",\"fields\":["
                + "  {\"id\":\"function\",\"value\":\"Abs\"},"
                + "  {\"id\":\"operand_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"operand_literal\",\"value\":\"-5\"}"
                + "]},"
                + "{\"type\":\"arithmeticExpression\",\"fields\":["
                + "  {\"id\":\"left_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"left_literal\",\"value\":\"1\"},"
                + "  {\"id\":\"operator\",\"value\":\"+\"},"
                + "  {\"id\":\"right_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"right_literal\",\"value\":\"2\"}"
                + "]}"
                + "]";
        String migrated = migration.migrateBaseElementsJson(mixed);
        JsonNode root = mapper.readTree(migrated);

        // First element (baseElement) untouched
        assertThat(root.get(0).get("type").asText()).isEqualTo("baseElement");
        // Second element (arithmeticUnary) untouched -- still has 'function' field
        JsonNode unaryFields = root.get(1).get("fields");
        boolean hasFunction = false;
        for (JsonNode f : unaryFields) {
            if ("function".equals(f.get("id").asText())) hasFunction = true;
        }
        assertThat(hasFunction).isTrue();
        // Third element (arithmeticExpression) converted
        JsonNode arithFields = root.get(2).get("fields");
        assertThat(arithFields.size()).isEqualTo(2);
        assertThat(arithFields.get(0).get("id").asText()).isEqualTo("operands");
    }

    @Test
    void quantityOperand_preservedThroughMigration() throws Exception {
        String legacy = "["
                + "{\"type\":\"arithmeticExpression\",\"fields\":["
                + "  {\"id\":\"left_mode\",\"value\":\"quantity\"},"
                + "  {\"id\":\"left_literal_value\",\"value\":\"5\"},"
                + "  {\"id\":\"left_literal_unit\",\"value\":\"mg/dL\"},"
                + "  {\"id\":\"operator\",\"value\":\"-\"},"
                + "  {\"id\":\"right_mode\",\"value\":\"literal\"},"
                + "  {\"id\":\"right_literal\",\"value\":\"1\"}"
                + "]}"
                + "]";
        String migrated = migration.migrateBaseElementsJson(legacy);
        JsonNode operands = mapper.readTree(migrated).get(0).get("fields").get(0).get("value");
        assertThat(operands.get(0).get("mode").asText()).isEqualTo("quantity");
        assertThat(operands.get(0).get("operand_literal_value").asText()).isEqualTo("5");
        assertThat(operands.get(0).get("operand_literal_unit").asText()).isEqualTo("mg/dL");
    }

    @Test
    void emptyArray_returnsUnchanged() throws Exception {
        String empty = "[]";
        String migrated = migration.migrateBaseElementsJson(empty);
        assertThat(migrated).isEqualTo(empty);
    }

    @Test
    void notAnArray_returnsNull() throws Exception {
        String notArray = "{\"type\":\"something\"}";
        String migrated = migration.migrateBaseElementsJson(notArray);
        // Defensive: when input is not the expected base_elements array shape,
        // returning null lets the caller fall through to "no update needed".
        assertThat(migrated).isNull();
    }
}
