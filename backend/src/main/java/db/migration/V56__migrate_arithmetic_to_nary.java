package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * PAT-163 Phase 3: migrate {@code arithmeticExpression} base elements from the
 * PAT-161 2-ary shape (left_x / right_x / operator scalar fields) to the new
 * N-ary shape (operands[] and operators[] array fields).
 *
 * <p>Touches the {@code base_elements} TEXT (JSON) column on
 * {@code cds_artifact} and {@code ecqm_artifact} tables.
 *
 * <p><b>Idempotency</b>: each element is skipped if it already has an
 * {@code operands} field -- re-running the migration is a no-op.
 *
 * <p><b>Safety</b>: parsing failures on any single row are caught + logged so
 * one corrupt artifact doesn't abort the whole migration. Errors during write
 * are propagated to fail the migration.
 *
 * <p>See {@code rollback_V56__migrate_arithmetic_to_nary.sql} for the inverse
 * (N=2 only -- wider N can't be expressed in the legacy 2-ary shape).
 */
public class V56__migrate_arithmetic_to_nary extends BaseJavaMigration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        migrateTable(context, "cds_artifact");
        migrateTable(context, "ecqm_artifact");
    }

    private void migrateTable(Context context, String tableName) throws Exception {
        // Pull all rows that have a non-null/non-empty base_elements value.
        List<long[]> idsToUpdate = new ArrayList<>();
        List<String> newJsonValues = new ArrayList<>();

        try (Statement st = context.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, base_elements FROM " + tableName
                             + " WHERE base_elements IS NOT NULL AND base_elements <> ''")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String json = rs.getString("base_elements");
                String migrated;
                try {
                    migrated = migrateBaseElementsJson(json);
                } catch (Exception ex) {
                    // Log + skip -- one bad row shouldn't abort the migration.
                    // The defensive read-side adapter in ExpressionCqlEngine will
                    // still handle legacy shape on this row.
                    System.err.println("[V56] skipping " + tableName + " id=" + id
                            + " due to JSON parse error: " + ex.getMessage());
                    continue;
                }
                if (migrated != null && !migrated.equals(json)) {
                    idsToUpdate.add(new long[]{id});
                    newJsonValues.add(migrated);
                }
            }
        }

        if (idsToUpdate.isEmpty()) {
            return;
        }

        try (PreparedStatement ps = context.getConnection().prepareStatement(
                "UPDATE " + tableName + " SET base_elements = ? WHERE id = ?")) {
            for (int i = 0; i < idsToUpdate.size(); i++) {
                ps.setString(1, newJsonValues.get(i));
                ps.setLong(2, idsToUpdate.get(i)[0]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Migrate the base_elements JSON string. Returns {@code null} if the input
     * isn't a JSON array (defensive -- should be filtered upstream but cheap to
     * check); returns the original input string when no element needed
     * conversion (idempotent path).
     */
    String migrateBaseElementsJson(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        if (!root.isArray()) return null;

        boolean anyChanged = false;
        for (JsonNode element : root) {
            if (!element.isObject()) continue;
            JsonNode typeNode = element.get("type");
            if (typeNode == null || !"arithmeticExpression".equals(typeNode.asText())) continue;

            JsonNode fields = element.get("fields");
            if (fields == null || !fields.isArray()) continue;

            // Idempotency: skip if operands field already present.
            if (hasFieldId(fields, "operands")) continue;

            // Build new fields[] from legacy left_*/right_*/operator scalars.
            ObjectNode operand0 = extractLegacyOperand((ArrayNode) fields, "left");
            ObjectNode operand1 = extractLegacyOperand((ArrayNode) fields, "right");
            String operator = legacyFieldValue((ArrayNode) fields, "operator", "+");

            ArrayNode operands = MAPPER.createArrayNode();
            operands.add(operand0);
            operands.add(operand1);
            ArrayNode operators = MAPPER.createArrayNode();
            operators.add(operator);

            ArrayNode newFields = MAPPER.createArrayNode();
            newFields.add(buildJsonField("operands", "json", "operands", operands));
            newFields.add(buildJsonField("operators", "json", "operators", operators));
            ((ObjectNode) element).set("fields", newFields);
            anyChanged = true;
        }

        return anyChanged ? MAPPER.writeValueAsString(root) : json;
    }

    private boolean hasFieldId(JsonNode fields, String id) {
        for (JsonNode f : fields) {
            JsonNode fid = f.get("id");
            if (fid != null && id.equals(fid.asText())) return true;
        }
        return false;
    }

    private String legacyFieldValue(ArrayNode fields, String id, String defaultVal) {
        for (JsonNode f : fields) {
            JsonNode fid = f.get("id");
            if (fid != null && id.equals(fid.asText())) {
                JsonNode v = f.get("value");
                return v == null || v.isNull() ? defaultVal : v.asText();
            }
        }
        return defaultVal;
    }

    /**
     * Pull the 5 possible legacy keys ({@code <side>_mode},
     * {@code <side>_operand_id}, {@code <side>_literal},
     * {@code <side>_literal_value}, {@code <side>_literal_unit}) into a single
     * operand-shape object (keys without the side prefix).
     */
    private ObjectNode extractLegacyOperand(ArrayNode fields, String side) {
        ObjectNode operand = MAPPER.createObjectNode();
        operand.put("mode", legacyFieldValue(fields, side + "_mode", "element"));
        operand.put("operand_id", legacyFieldValue(fields, side + "_operand_id", ""));
        operand.put("operand_literal", legacyFieldValue(fields, side + "_literal", ""));
        operand.put("operand_literal_value", legacyFieldValue(fields, side + "_literal_value", ""));
        operand.put("operand_literal_unit", legacyFieldValue(fields, side + "_literal_unit", ""));
        return operand;
    }

    private ObjectNode buildJsonField(String id, String type, String name, JsonNode value) {
        ObjectNode f = MAPPER.createObjectNode();
        f.put("id", id);
        f.put("type", type);
        f.put("name", name);
        f.set("value", value);
        return f;
    }
}
