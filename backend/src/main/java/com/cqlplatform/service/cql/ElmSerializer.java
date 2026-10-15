package com.cqlplatform.service.cql;

import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.elm.serializing.ElmJsonLibraryReader;
import org.cqframework.cql.elm.serializing.ElmXmlLibraryWriter;
import org.hl7.elm.r1.Library;
import org.springframework.stereotype.Component;

/**
 * Re-serialises ELM between the two wire formats the CQL IG knows (PAT-261).
 *
 * <p>The platform stores and executes ELM JSON ({@code translator.toJson()}); the exchange package
 * also carries ELM XML ({@code application/elm+xml}, the format MADiE and older engines expect).
 * The XML is produced from the stored JSON — no second translation, so both attachments describe
 * exactly the same compiled library.
 */
@Component
@Slf4j
public class ElmSerializer {

    public static final String ELM_JSON = "application/elm+json";
    public static final String ELM_XML = "application/elm+xml";

    /**
     * @return the ELM XML for {@code elmJson}, or {@code null} when the input is blank or cannot
     *         be read back as an ELM library (logged; the caller decides whether that is an error)
     */
    public String toXml(String elmJson) {
        if (elmJson == null || elmJson.isBlank()) return null;
        try {
            Library library = new ElmJsonLibraryReader().read(elmJson);
            return new ElmXmlLibraryWriter().writeAsString(library);
        } catch (Exception e) {
            log.warn("ELM JSON could not be re-serialised as XML: {}", e.getMessage());
            return null;
        }
    }
}
