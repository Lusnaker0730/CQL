package com.cqlplatform.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class XssStringDeserializerTest {

    private XssStringDeserializer deserializer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        deserializer = new XssStringDeserializer();
        mapper = new ObjectMapper();
    }

    private String deserialize(String input) throws Exception {
        String json = mapper.writeValueAsString(input);
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            parser.nextToken();
            DeserializationContext ctxt = mapper.getDeserializationContext();
            return deserializer.deserialize(parser, ctxt);
        }
    }

    @Test
    void shouldReturnNullForNull() throws Exception {
        String json = "null";
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            parser.nextToken();
            String result = deserializer.deserialize(parser, mapper.getDeserializationContext());
            assertThat(result).isNull();
        }
    }

    @Test
    void shouldPassSafeStrings() throws Exception {
        assertThat(deserialize("Hello World")).isEqualTo("Hello World");
        assertThat(deserialize("normal text 123")).isEqualTo("normal text 123");
    }

    @Test
    void shouldStripScriptTagWithAttributes() throws Exception {
        String result = deserialize("<script type=\"text/javascript\">alert(1)</script>");
        assertThat(result).doesNotContain("<script");
        assertThat(result).doesNotContain("</script>");
    }

    @Test
    void shouldStripUnclosedScriptTag() throws Exception {
        String result = deserialize("<script src=evil.js>");
        assertThat(result).doesNotContain("<script");
    }

    @Test
    void shouldStripClosingScriptTag() throws Exception {
        String result = deserialize("text</script>");
        assertThat(result).doesNotContain("</script>");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<svg onload=alert(1)>",
            "<SVG/ONLOAD=alert(1)>",
            "<svg>",
    })
    void shouldStripSvgTags(String input) throws Exception {
        String result = deserialize(input);
        assertThat(result.toLowerCase()).doesNotContain("<svg");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<math><mtext>xss</mtext></math>",
            "<MATH>",
    })
    void shouldStripMathTags(String input) throws Exception {
        String result = deserialize(input);
        assertThat(result.toLowerCase()).doesNotContain("<math");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<object data=evil>",
            "<OBJECT type=text/html data=evil>",
    })
    void shouldStripObjectTags(String input) throws Exception {
        String result = deserialize(input);
        assertThat(result.toLowerCase()).doesNotContain("<object");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<embed src=evil>",
            "<EMBED type=text/html>",
    })
    void shouldStripEmbedTags(String input) throws Exception {
        String result = deserialize(input);
        assertThat(result.toLowerCase()).doesNotContain("<embed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<base href=evil>",
            "<BASE href=//evil.com>",
    })
    void shouldStripBaseTags(String input) throws Exception {
        String result = deserialize(input);
        assertThat(result.toLowerCase()).doesNotContain("<base");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<form action=evil>",
            "<FORM method=POST>",
    })
    void shouldStripFormTags(String input) throws Exception {
        String result = deserialize(input);
        assertThat(result.toLowerCase()).doesNotContain("<form");
    }

    @Test
    void shouldStripJavascriptProtocol() throws Exception {
        String result = deserialize("javascript:alert(1)");
        assertThat(result.toLowerCase()).doesNotContain("javascript:");
    }

    @Test
    void shouldStripEventHandlers() throws Exception {
        String result = deserialize("<img onerror=alert(1)>");
        assertThat(result).doesNotContain("onerror=");
    }

    @Test
    void shouldStripIframeTags() throws Exception {
        String result = deserialize("<iframe src=evil></iframe>");
        assertThat(result.toLowerCase()).doesNotContain("<iframe");
    }

    @Test
    void shouldStripEvalCalls() throws Exception {
        String result = deserialize("eval(document.cookie)");
        assertThat(result).doesNotContain("eval(");
    }

    @Test
    void shouldStripDataTextHtmlUri() throws Exception {
        String result = deserialize("data:text/html,<script>alert(1)</script>");
        assertThat(result.toLowerCase()).doesNotContain("data:text/html");
    }

    @Test
    void shouldStripVbscriptProtocol() throws Exception {
        String result = deserialize("vbscript:MsgBox(1)");
        assertThat(result.toLowerCase()).doesNotContain("vbscript:");
    }
}
