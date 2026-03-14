/**
 * CQL Helpers - Common utility functions for eCQM development.
 * Provides age calculation, date utilities, and common type conversions.
 */
library CQLHelpers version '1.0.0'

using QICore version '4.1.1'

include FHIRHelpers version '4.0.1'

context Patient

// Age calculation helpers
define "Current Age in Years":
  AgeInYears()

define "Age at Start of Measurement Period":
  AgeInYearsAt(date from start of "Measurement Period")

define "Age at End of Measurement Period":
  AgeInYearsAt(date from end of "Measurement Period")

define "Is Adult":
  AgeInYearsAt(date from start of "Measurement Period") >= 18

define "Is Pediatric":
  AgeInYearsAt(date from start of "Measurement Period") < 18

// Date helpers
define "Measurement Period Start":
  start of "Measurement Period"

define "Measurement Period End":
  end of "Measurement Period"

define "During Measurement Period"(condition Interval<DateTime>):
  condition overlaps "Measurement Period"

// Common null-safe helpers
define function "ToString"(val System.Any):
  if val is null then null
  else ToString(val)

define function "Normalize Interval"(choice Choice<FHIR.dateTime, FHIR.Period, FHIR.Timing, FHIR.instant, FHIR.string, FHIR.Age, FHIR.Range>):
  case
    when choice is FHIR.dateTime then
      Interval[FHIRHelpers.ToDateTime(choice as FHIR.dateTime), FHIRHelpers.ToDateTime(choice as FHIR.dateTime)]
    when choice is FHIR.Period then
      FHIRHelpers.ToInterval(choice as FHIR.Period)
    when choice is FHIR.instant then
      Interval[FHIRHelpers.ToDateTime(choice as FHIR.instant), FHIRHelpers.ToDateTime(choice as FHIR.instant)]
    when choice is FHIR.Age then
      Interval[FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity(choice as FHIR.Age),
        FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity(choice as FHIR.Age) + 1 year)
    else null as Interval<DateTime>
  end

parameter "Measurement Period" Interval<DateTime>
