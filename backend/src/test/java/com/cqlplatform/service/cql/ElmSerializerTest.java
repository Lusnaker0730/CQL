package com.cqlplatform.service.cql;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PAT-261: ELM JSON → ELM XML without a second translation. */
class ElmSerializerTest {

    private final ElmSerializer serializer = new ElmSerializer();

    @Test
    void translatedElmJson_isReSerialisedAsTheTranslatorsOwnXml() {
        CqlTranslator translator = CqlTranslator.fromText(
                "library SmokeElm version '1.2.3'\n\ndefine \"Answer\": 40 + 2\n",
                LibraryManagerFactory.create(null));
        assertThat(translator.getErrors()).isEmpty();

        String xml = serializer.toXml(translator.toJson());

        assertThat(xml).isNotNull()
                .contains("<library")
                .contains("id=\"SmokeElm\"")
                .contains("version=\"1.2.3\"")
                .contains("Answer");
        // Same compiled library, same XML: the package's two attachments cannot drift apart.
        assertThat(xml).isEqualTo(translator.toXml());
    }

    @Test
    void blankOrUnreadableInput_isNull_notAnException() {
        assertThat(serializer.toXml(null)).isNull();
        assertThat(serializer.toXml("   ")).isNull();
        assertThat(serializer.toXml("this is not ELM")).isNull();
        assertThat(serializer.toXml("{\"library\":")).isNull();
    }
}
