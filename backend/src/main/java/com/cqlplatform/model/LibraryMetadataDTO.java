package com.cqlplatform.model;

import com.fasterxml.jackson.core.type.TypeReference;
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
        List<String> expressions = new ArrayList<>();
        List<String> valueSets = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        List<String> functions = new ArrayList<>();

        if (library.getElmJson() != null) {
            try {
                JsonNode root = MAPPER.readTree(library.getElmJson());
                JsonNode lib = root.path("library");

                // Extract expressions
                JsonNode statements = lib.path("statements").path("def");
                if (statements.isArray()) {
                    for (JsonNode stmt : statements) {
                        String name = stmt.path("name").asText("");
                        if (!"Patient".equals(name)) {
                            if (stmt.has("operand")) {
                                functions.add(name);
                            } else {
                                expressions.add(name);
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
                .name(library.getName())
                .version(library.getVersion())
                .expressions(expressions)
                .valueSets(valueSets)
                .codes(codes)
                .functions(functions)
                .build();
    }
}
