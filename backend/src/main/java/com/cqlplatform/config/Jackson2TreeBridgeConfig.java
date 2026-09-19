package com.cqlplatform.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.module.SimpleModule;

/**
 * Lets Jackson 2 trees cross the HTTP boundary.
 *
 * <p>Spring Boot 4 converts request / response bodies with Jackson 3 ({@code tools.jackson}); the
 * FHIR services build and read Jackson 2 trees ({@code com.fasterxml.jackson.databind.JsonNode}).
 * Jackson 3 knows nothing about the Jackson 2 tree model, so without this bridge
 * <ul>
 *   <li>{@code @RequestBody JsonNode} fails with 500 "Cannot construct instance of JsonNode" — every
 *       FHIR Measure / Library / Bundle import;</li>
 *   <li>a returned {@code ObjectNode} is written as a bean
 *       ({@code {"array":false,"bigDecimal":false,…,"nodeType":"OBJECT"}}) instead of its content —
 *       {@code GET /measures/{id}/fhir} and {@code GET /cql/libraries/{id}/fhir}.</li>
 * </ul>
 * Found by smoke scenario 33 (PAT-229); locked by {@code FhirJsonBodyEndpointsTest}. Spring Boot
 * registers every {@link JacksonModule} bean with the auto-configured {@code JsonMapper}.
 */
@Configuration
public class Jackson2TreeBridgeConfig {

    private static final ObjectMapper JACKSON2 = new ObjectMapper();

    @Bean
    public JacksonModule jackson2TreeBridgeModule() {
        SimpleModule module = new SimpleModule("jackson2-tree-bridge");
        // Registered for the base type: SimpleSerializers also matches subclasses (ObjectNode, ArrayNode, …).
        module.addSerializer(JsonNode.class, new Jackson2TreeSerializer());
        // Deserializers match the declared type exactly, so each tree type a controller may declare is listed.
        module.addDeserializer(JsonNode.class, new Jackson2TreeDeserializer<>(JsonNode.class));
        module.addDeserializer(ObjectNode.class, new Jackson2TreeDeserializer<>(ObjectNode.class));
        module.addDeserializer(ArrayNode.class, new Jackson2TreeDeserializer<>(ArrayNode.class));
        return module;
    }

    private static final class Jackson2TreeSerializer extends ValueSerializer<JsonNode> {
        @Override
        public void serialize(JsonNode value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            // JsonNode#toString() is the node's JSON (guaranteed since Jackson 2.10).
            gen.writeRawValue(value.toString());
        }
    }

    private static final class Jackson2TreeDeserializer<T extends JsonNode> extends ValueDeserializer<T> {
        private final Class<T> type;

        private Jackson2TreeDeserializer(Class<T> type) {
            this.type = type;
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            String json = ctxt.readTree(p).toString();
            JsonNode node;
            try {
                node = JACKSON2.readTree(json);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Jackson 3 just parsed the same text, so this is not reachable with well-formed input.
                throw MismatchedInputException.from(p, type, "Not a JSON value: " + e.getOriginalMessage());
            }
            if (!type.isInstance(node)) {
                // e.g. a JSON array posted where a FHIR resource (object) is expected → 400, not a ClassCastException.
                throw MismatchedInputException.from(p, type,
                        "Expected JSON " + (type == ArrayNode.class ? "array" : "object") + " but got " + node.getNodeType());
            }
            return type.cast(node);
        }
    }
}
