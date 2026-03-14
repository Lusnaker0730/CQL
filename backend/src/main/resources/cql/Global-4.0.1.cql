/*
  Global Common Functions for FHIR v4.0.1
  Provides common terminology codes and helper definitions used across
  CDS Connect CQL artifacts and other clinical quality measures.
*/
library Global version '4.0.1'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1' called FHIRHelpers

// ======================== CODE SYSTEMS ========================

codesystem "ConditionClinicalStatusCodes": 'http://terminology.hl7.org/CodeSystem/condition-clinical'
codesystem "ConditionVerificationStatusCodes": 'http://terminology.hl7.org/CodeSystem/condition-ver-status'
codesystem "AllergyIntoleranceClinicalStatusCodes": 'http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical'
codesystem "AllergyIntoleranceVerificationStatusCodes": 'http://terminology.hl7.org/CodeSystem/allergyintolerance-verification'
codesystem "ConditionCategoryCodes": 'http://terminology.hl7.org/CodeSystem/condition-category'
codesystem "ObservationCategoryCodes": 'http://terminology.hl7.org/CodeSystem/observation-category'
codesystem "LOINC": 'http://loinc.org'
codesystem "SNOMEDCT": 'http://snomed.info/sct'
codesystem "RoleCode": 'http://terminology.hl7.org/CodeSystem/v3-RoleCode'

// ======================== CONDITION CLINICAL STATUS CODES ========================

code "active": 'active' from "ConditionClinicalStatusCodes" display 'Active'
code "recurrence": 'recurrence' from "ConditionClinicalStatusCodes" display 'Recurrence'
code "relapse": 'relapse' from "ConditionClinicalStatusCodes" display 'Relapse'
code "inactive": 'inactive' from "ConditionClinicalStatusCodes" display 'Inactive'
code "remission": 'remission' from "ConditionClinicalStatusCodes" display 'Remission'
code "resolved": 'resolved' from "ConditionClinicalStatusCodes" display 'Resolved'

// ======================== CONDITION VERIFICATION STATUS CODES ========================

code "unconfirmed": 'unconfirmed' from "ConditionVerificationStatusCodes" display 'Unconfirmed'
code "provisional": 'provisional' from "ConditionVerificationStatusCodes" display 'Provisional'
code "differential": 'differential' from "ConditionVerificationStatusCodes" display 'Differential'
code "confirmed": 'confirmed' from "ConditionVerificationStatusCodes" display 'Confirmed'
code "refuted": 'refuted' from "ConditionVerificationStatusCodes" display 'Refuted'
code "entered-in-error": 'entered-in-error' from "ConditionVerificationStatusCodes" display 'Entered in Error'

// ======================== ALLERGY INTOLERANCE STATUS CODES ========================

code "allergy-active": 'active' from "AllergyIntoleranceClinicalStatusCodes" display 'Active'
code "allergy-inactive": 'inactive' from "AllergyIntoleranceClinicalStatusCodes" display 'Inactive'
code "allergy-resolved": 'resolved' from "AllergyIntoleranceClinicalStatusCodes" display 'Resolved'
code "allergy-unconfirmed": 'unconfirmed' from "AllergyIntoleranceVerificationStatusCodes" display 'Unconfirmed'
code "allergy-confirmed": 'confirmed' from "AllergyIntoleranceVerificationStatusCodes" display 'Confirmed'
code "allergy-refuted": 'refuted' from "AllergyIntoleranceVerificationStatusCodes" display 'Refuted'

// ======================== CONDITION CATEGORY CODES ========================

code "problem-list-item": 'problem-list-item' from "ConditionCategoryCodes" display 'Problem List Item'
code "encounter-diagnosis": 'encounter-diagnosis' from "ConditionCategoryCodes" display 'Encounter Diagnosis'

// ======================== OBSERVATION CATEGORY CODES ========================

code "vital-signs": 'vital-signs' from "ObservationCategoryCodes" display 'Vital Signs'
code "laboratory": 'laboratory' from "ObservationCategoryCodes" display 'Laboratory'
code "social-history": 'social-history' from "ObservationCategoryCodes" display 'Social History'

// ======================== CONTEXT ========================

context Patient

// ======================== HELPER FUNCTIONS ========================

/**
 * Normalizes a value that could be a dateTime, a ## Quantity, or an Interval
 * to an Interval so that it can be used in  comparisons consistently.
 */
define function "Normalize Onset"(onset Choice<FHIR.dateTime, FHIR.Age, FHIR.Period, FHIR.Range, FHIR.string>):
  if onset is FHIR.dateTime then
    Interval[FHIRHelpers.ToDateTime(onset as FHIR.dateTime), FHIRHelpers.ToDateTime(onset as FHIR.dateTime)]
  else if onset is FHIR.Period then
    FHIRHelpers.ToInterval(onset as FHIR.Period)
  else if onset is FHIR.Age then
    Interval[FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity(onset as FHIR.Age),
      FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity(onset as FHIR.Age) + 1 year)
  else if onset is FHIR.Range then
    Interval[FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity((onset as FHIR.Range).low),
      FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity((onset as FHIR.Range).high) + 1 year)
  else null

/**
 * Normalizes a value that could be a dateTime, a Period, or a Timing
 * to an Interval so that it can be used in comparisons consistently.
 */
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
    when choice is FHIR.Range then
      Interval[FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity((choice as FHIR.Range).low),
        FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity((choice as FHIR.Range).high) + 1 year)
    when choice is FHIR.Timing then
      Message(null as Interval<DateTime>, true, '1', 'Error', 'Cannot compute a single interval from a Timing type')
    when choice is FHIR.string then
      Message(null as Interval<DateTime>, true, '1', 'Error', 'Cannot compute an interval from a String value')
    else null as Interval<DateTime>
  end

/**
 * Normalizes an abatement value to an Interval.
 */
define function "Normalize Abatement"(condition Condition):
  if condition.abatement is FHIR.dateTime then
    Interval[FHIRHelpers.ToDateTime(condition.abatement as FHIR.dateTime), FHIRHelpers.ToDateTime(condition.abatement as FHIR.dateTime)]
  else if condition.abatement is FHIR.Period then
    FHIRHelpers.ToInterval(condition.abatement as FHIR.Period)
  else if condition.abatement is FHIR.Age then
    Interval[FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity(condition.abatement as FHIR.Age),
      FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity(condition.abatement as FHIR.Age) + 1 year)
  else if condition.abatement is FHIR.Range then
    Interval[FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity((condition.abatement as FHIR.Range).low),
      FHIRHelpers.ToDate(Patient.birthDate) + FHIRHelpers.ToQuantity((condition.abatement as FHIR.Range).high) + 1 year)
  else if condition.abatement is FHIR.boolean then
    Interval[end of "Normalize Onset"(condition.onset), condition.recordedDate)
  else null

/**
 * Returns the prevalence period for a condition (onset to abatement).
 */
define function "Prevalence Period"(condition Condition):
  Interval[start of "Normalize Onset"(condition.onset), end of "Normalize Abatement"(condition))

/**
 * Returns the tail of a reference (the part after the last '/').
 */
define function GetId(uri String):
  Last(Split(uri, '/'))

/**
 * Calculates the difference in calendar days between two dates.
 */
define function "CalendarDayOfOrDayAfter"(dt DateTime):
  dt

/**
 * Get the base URL from a reference.
 */
define function GetBaseUrl(uri String):
  if uri is null then null
  else Last(Split(uri, '/'))
