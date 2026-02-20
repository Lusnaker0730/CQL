package com.cqlplatform.service.cql;

import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.TimeType;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Time;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Extended R4FhirModelResolver that converts FHIR types to CQL engine types
 * so that CQL operations (equality, sort, contains) work correctly.
 *
 * Conversions:
 * - FHIR DateTimeType → CQL DateTime (fixes "not comparable" errors in sort)
 * - FHIR Coding → CQL Code (fixes "contains" on clinicalStatus.coding, etc.)
 */
public class ComparableR4FhirModelResolver extends R4FhirModelResolver {

    public ComparableR4FhirModelResolver() {
        super();
    }

    @Override
    public Object resolvePath(Object target, String path) {
        // Handle path resolution on CQL Code objects (produced by our Coding→Code conversion)
        if (target instanceof Code cqlCode) {
            return resolveCodePath(cqlCode, path);
        }
        Object result = super.resolvePath(target, path);
        return convertFhirTypes(result);
    }

    private Object resolveCodePath(Code code, String path) {
        return switch (path) {
            case "code" -> code.getCode();
            case "system" -> code.getSystem();
            case "version" -> code.getVersion();
            case "display" -> code.getDisplay();
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Object convertFhirTypes(Object value) {
        if (value instanceof BaseDateTimeType fhirDate) {
            return toEngineDateTime(fhirDate);
        }
        if (value instanceof TimeType timeType) {
            String timeStr = timeType.getValue();
            if (timeStr != null) {
                return new Time(timeStr);
            }
        }
        if (value instanceof Coding coding) {
            return toCqlCode(coding);
        }
        // Convert List<Coding> to List<Code> for correct equality in CQL contains/in
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Coding) {
            List<Code> codes = new ArrayList<>(list.size());
            for (Object item : list) {
                codes.add(toCqlCode((Coding) item));
            }
            return codes;
        }
        return value;
    }

    private Code toCqlCode(Coding coding) {
        if (coding == null) return null;
        Code code = new Code();
        if (coding.hasCode()) code.setCode(coding.getCode());
        if (coding.hasSystem()) code.setSystem(coding.getSystem());
        if (coding.hasVersion()) code.setVersion(coding.getVersion());
        if (coding.hasDisplay()) code.setDisplay(coding.getDisplay());
        return code;
    }

    private DateTime toEngineDateTime(BaseDateTimeType fhirDate) {
        Date date = fhirDate.getValue();
        if (date == null) return null;

        TimeZone tz = fhirDate.getTimeZone();
        ZoneOffset offset = tz != null
                ? ZoneOffset.ofTotalSeconds(tz.getRawOffset() / 1000)
                : ZoneOffset.UTC;

        Calendar cal = fhirDate.getValueAsCalendar();
        OffsetDateTime odt = OffsetDateTime.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND),
                cal.get(Calendar.MILLISECOND) * 1_000_000,
                offset);

        return new DateTime(odt);
    }
}
