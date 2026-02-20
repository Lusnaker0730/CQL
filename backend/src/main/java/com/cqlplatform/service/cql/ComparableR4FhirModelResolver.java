package com.cqlplatform.service.cql;

import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.TimeType;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;
import org.opencds.cqf.cql.engine.runtime.DateTime;
import org.opencds.cqf.cql.engine.runtime.Time;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Extended R4FhirModelResolver that converts FHIR date/time types to CQL engine
 * DateTime types so they are Comparable during sort operations.
 *
 * Fixes: "Type org.hl7.fhir.r4.model.DateTimeType is not comparable"
 *
 * Note: FHIR Coding→CQL Code conversion is NOT done here because the CQL
 * translator already bakes FHIRHelpers.ToCode() calls into the ELM. Converting
 * early would cause "Could not resolve ToCode(Code)" signature mismatch errors.
 */
public class ComparableR4FhirModelResolver extends R4FhirModelResolver {

    public ComparableR4FhirModelResolver() {
        super();
    }

    @Override
    public Object resolvePath(Object target, String path) {
        Object result = super.resolvePath(target, path);
        return convertIfDateTimeType(result);
    }

    private Object convertIfDateTimeType(Object value) {
        if (value instanceof BaseDateTimeType fhirDate) {
            return toEngineDateTime(fhirDate);
        }
        if (value instanceof TimeType timeType) {
            String timeStr = timeType.getValue();
            if (timeStr != null) {
                return new Time(timeStr);
            }
        }
        return value;
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
