package com.cqlplatform.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class LibraryMetadataDTO {
    private String name;
    private String version;
    private List<String> expressions;
    private List<String> valueSets;
    private List<String> codes;
    private List<String> functions;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static LibraryMetadataDTO fromLibrary(CqlLibrary library) {
        return fromElm(library.getName(), library.getVersion(), library.getElmJson());
    }

    /**
     * Build metadata from just the fields this DTO actually needs (name, version, and
     * the ELM JSON it parses). Lets callers use a lightweight repository projection
     * that skips the heavy {@code cql_content} TEXT column — the metadata endpoint
     * never reads it — instead of loading full library entities.
     */
    public static LibraryMetadataDTO fromElm(String name, String version, String elmJson) {
        List<String> expressions = new ArrayList<>();
        List<String> valueSets = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        List<String> functions = new ArrayList<>();

        if (elmJson != null) {
            try {
                JsonNode root = MAPPER.readTree(elmJson);
                JsonNode lib = root.path("library");

                // Extract expressions
                JsonNode statements = lib.path("statements").path("def");
                if (statements.isArray()) {
                    for (JsonNode stmt : statements) {
                        String stmtName = stmt.path("name").asText("");
                        if (!"Patient".equals(stmtName)) {
                            if (stmt.has("operand")) {
                                functions.add(stmtName);
                            } else {
                                expressions.add(stmtName);
                            }
                        }
                    }
                }

                // Extract value sets
                JsonNode vs = lib.path("valueSets").path("def");
                if (vs.isArray()) {
                    for (JsonNode v : vs) {
                        valueSets.add(v.path("name").asText(""));
                    }
                }

                // Extract codes
                JsonNode cds = lib.path("codes").path("def");
                if (cds.isArray()) {
                    for (JsonNode c : cds) {
                        codes.add(c.path("name").asText(""));
                    }
                }
            } catch (Exception ignored) {
                // Fall back to empty lists
            }
        }

        return LibraryMetadataDTO.builder()
                .name(name)
                .version(version)
                .expressions(expressions)
                .valueSets(valueSets)
                .codes(codes)
                .functions(functions)
                .build();
    }
}
