/**
 * MAT Global Common Functions - Ported from MADiE/MAT.
 * Provides standard functions used across CMS electronic clinical quality measures.
 */
library MATGlobalCommonFunctions version '7.0.0'

using QICore version '4.1.1'

include FHIRHelpers version '4.0.1'

parameter "Measurement Period" Interval<DateTime>

context Patient

// Hospitalization helpers
define function "Hospitalization"(Encounter Encounter):
  Encounter E
    let dominated: [Encounter] X
      where X.status = 'finished'
        and X.period ends 1 hour or less before start of E.period
        and X.class ~ "EMER"
    return Interval[
      Coalesce(start of dominated.period, start of E.period),
      end of E.period
    ]

define function "Hospitalization Length of Stay"(Encounter Encounter):
  LengthInDays("Hospitalization"(Encounter))

define function "LengthInDays"(Value Interval<DateTime>):
  difference in days between start of Value and end of Value

// Emergency Department visit detection
define function "Emergency Department Visit"(Encounter Encounter):
  [Encounter] ED
    where ED.status = 'finished'
      and ED.class ~ "EMER"
      and ED.period ends 1 hour or less before start of Encounter.period

define function "Has Emergency Department Visit"(Encounter Encounter):
  exists "Emergency Department Visit"(Encounter)

// Measurement Period helpers
define "Inpatient Encounter During Measurement Period":
  [Encounter] E
    where E.class ~ "IMP"
      and E.status = 'finished'
      and E.period ends during day of "Measurement Period"
      and "LengthInDays"(E.period) <= 120

define "Qualifying Encounters":
  [Encounter] E
    where E.status = 'finished'
      and E.period during "Measurement Period"

// Diagnosis helpers
define function "Principal Diagnosis"(Encounter Encounter):
  singleton from (
    Encounter.diagnosis D
      where D.rank = 1
      return D.condition
  )

define function "Encounter Diagnosis"(Encounter Encounter):
  Encounter.diagnosis D
    return D.condition

// Latest value helpers
define function "Latest"(observations List<Observation>):
  Last(
    observations O
      sort by issued
  )

define function "Most Recent"(observations List<Observation>):
  Last(
    observations O
      sort by (effective as dateTime)
  )

// Normalize helpers
define function "Normalize Interval"(choice Choice<FHIR.dateTime, FHIR.Period, FHIR.Timing, FHIR.instant, FHIR.string, FHIR.Age, FHIR.Range>):
  case
    when choice is FHIR.dateTime then
      Interval[FHIRHelpers.ToDateTime(choice as FHIR.dateTime), FHIRHelpers.ToDateTime(choice as FHIR.dateTime)]
    when choice is FHIR.Period then
      FHIRHelpers.ToInterval(choice as FHIR.Period)
    when choice is FHIR.instant then
      Interval[FHIRHelpers.ToDateTime(choice as FHIR.instant), FHIRHelpers.ToDateTime(choice as FHIR.instant)]
    else null as Interval<DateTime>
  end

// Code references
code "IMP": 'IMP' from "ActCode" display 'inpatient encounter'
code "EMER": 'EMER' from "ActCode" display 'emergency'

codesystem "ActCode": 'http://terminology.hl7.org/CodeSystem/v3-ActCode'
