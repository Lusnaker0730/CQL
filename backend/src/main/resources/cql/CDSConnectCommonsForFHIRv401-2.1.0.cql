/*
  CDS Connect Commons for FHIR v4.0.1, version 2.1.0
  Based on the AHRQ CDS Connect project.
  Provides common helper functions for clinical decision support artifacts.
*/
library CDSConnectCommonsForFHIRv401 version '2.1.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1' called FHIRHelpers
include Global version '4.0.1' called Global

codesystem "CONDVERSTAT": 'http://terminology.hl7.org/CodeSystem/condition-ver-status'
codesystem "ALLERGYINTOLVERSTAT": 'http://terminology.hl7.org/CodeSystem/allergyintolerance-verification'

code "Condition Confirmed code": 'confirmed' from "CONDVERSTAT" display 'Confirmed'
code "AllergyIntolerance Confirmed code": 'confirmed' from "ALLERGYINTOLVERSTAT" display 'Confirmed'

concept "Condition Confirmed": { "Condition Confirmed code" } display 'Confirmed'

// -------------------------------------------------- OBSERVATIONS -----------------------------------------------------

/**
 * Observations that are complete and verified. For lab results, only
 * those with a final, amended, or corrected status are included.
 * This also excludes entries-in-error.
 */
define function Verified(ObsList List<Observation>):
  ObsList O
    where O.status in { 'final', 'amended', 'corrected' }

/**
 * Observations that have quantity values recorded in the value field.
 */
define function WithQuantityValue(ObsList List<Observation>):
  ObsList O
    where O.value is FHIR.Quantity

/**
 * Observations with values that use units specified in the unitsFilter.
 */
define function WithUnit(ObsList List<Observation>, Unit String):
  ObsList O
    where (O.value as FHIR.Quantity).unit.value = Unit
      or (O.value as FHIR.Quantity).code.value = Unit

/**
 * Observations performed in the specified "look back" period.
 * Checks effective[x] (dateTime or Period) and issued date.
 */
define function ObservationLookBack(ObsList List<Observation>, LookBack System.Quantity):
  ObsList O
    let LookBackInterval: Interval[Now() - LookBack, Now()]
    where (O.effective as FHIR.dateTime).value in LookBackInterval
      or NullSafeToInterval(O.effective as FHIR.Period) overlaps LookBackInterval
      or (O.issued as FHIR.instant).value in LookBackInterval

/**
 * The most recent observation based on effective date/time or issued date.
 * For observations with more than one status update, the most recent is used.
 */
define function MostRecent(ObsList List<Observation>):
  Last(
    ObsList O sort by Coalesce(
      (effective as FHIR.dateTime).value,
      (effective as FHIR.instant).value,
      (effective as FHIR.Period)."end".value,
      (effective as FHIR.Period)."start".value,
      issued.value
    )
  )

/**
 * Extracts the quantity value from an Observation, returning it as a CQL Quantity.
 */
define function QuantityValue(Obs Observation):
  FHIRHelpers.ToQuantity(Obs.value as FHIR.Quantity)

/**
 * Extracts the CodeableConcept value from an Observation, returning it as a CQL Concept.
 */
define function ConceptValue(Obs Observation):
  FHIRHelpers.ToConcept(Obs.value as FHIR.CodeableConcept)

/**
 * Finds the first non-null meaningful date value from an Observation.
 */
define function FindDate(Obs Observation):
  Coalesce(
    (Obs.effective as FHIR.dateTime).value,
    (Obs.effective as FHIR.instant).value,
    (Obs.effective as FHIR.Period)."start".value,
    Obs.issued.value
  )

/**
 * The highest observation based on quantity value.
 */
define function HighestObservation(ObsList List<Observation>):
  Max(ObsList O return QuantityValue(O))

// -------------------------------------------------- CONDITIONS -------------------------------------------------------

/**
 * Conditions that are active. This accounts for conditions with a
 * clinicalStatus of active, recurrence, or relapse.
 */
define function ActiveCondition(CondList List<Condition>):
  CondList C
    where C.clinicalStatus ~ ToConcept(Global."active")
      or C.clinicalStatus ~ ToConcept(Global."recurrence")
      or C.clinicalStatus ~ ToConcept(Global."relapse")

/**
 * Conditions that are active, recurring, or in remission.
 */
define function ActiveOrRecurring(CondList List<Condition>):
  CondList C
    where C.clinicalStatus ~ ToConcept(Global."active")
      or C.clinicalStatus ~ ToConcept(Global."recurrence")
      or C.clinicalStatus ~ ToConcept(Global."relapse")
      or C.clinicalStatus ~ ToConcept(Global."remission")

/**
 * Conditions that are confirmed. Specifically, conditions with a
 * verificationStatus of 'confirmed' or null (to account for clinicians
 * that don't set verification status).
 */
define function Confirmed(CondList List<Condition>):
  CondList C
    where C.verificationStatus ~ "Condition Confirmed"
      or C.verificationStatus is null

/**
 * Conditions that are not refuted or entered-in-error.
 */
define function NotRefutedOrError(CondList List<Condition>):
  CondList C
    where C.verificationStatus is null
      or not (C.verificationStatus ~ ToConcept(Global."refuted"))

/**
 * Conditions performed in the specified "look back" period.
 * Checks onset (dateTime or Period) and recordedDate.
 */
define function ConditionLookBack(CondList List<Condition>, LookBack System.Quantity):
  CondList C
    let LookBackInterval: Interval[Now() - LookBack, Now()]
    where (C.onset as FHIR.dateTime).value in LookBackInterval
      or NullSafeToInterval(C.onset as FHIR.Period) overlaps LookBackInterval
      or C.recordedDate.value in LookBackInterval

// -------------------------------------------------- PROCEDURES -------------------------------------------------------

/**
 * Procedures that are completed.
 */
define function Completed(ProcList List<Procedure>):
  ProcList P where P.status = 'completed'

/**
 * Procedures performed in the specified "look back" period.
 * Checks performed (dateTime or Period).
 */
define function ProcedureLookBack(ProcList List<Procedure>, LookBack System.Quantity):
  ProcList P
    let LookBackInterval: Interval[Now() - LookBack, Now()]
    where (P.performed as FHIR.dateTime).value in LookBackInterval
      or NullSafeToInterval(P.performed as FHIR.Period) overlaps LookBackInterval

// ------------------------------------------ MEDICATION REQUESTS ------------------------------------------------------

/**
 * Medication requests that are active.
 */
define function ActiveMedicationRequest(MedReqList List<MedicationRequest>):
  MedReqList M where M.status = 'active'

/**
 * Medication requests performed in the specified "look back" period.
 */
define function MedicationRequestLookBack(MedReqList List<MedicationRequest>, LookBack System.Quantity):
  MedReqList M
    let LookBackInterval: Interval[Now() - LookBack, Now()]
    where M.authoredOn.value in LookBackInterval

// ----------------------------------------- MEDICATION STATEMENTS -----------------------------------------------------

/**
 * Medication statements that are active.
 */
define function ActiveMedicationStatement(MedStatList List<MedicationStatement>):
  MedStatList M where M.status = 'active'

// -------------------------------------------- ALLERGY INTOLERANCES --------------------------------------------------

/**
 * Allergy Intolerances that are active.
 */
define function ActiveAllergyIntolerance(AllergyIntolList List<AllergyIntolerance>):
  AllergyIntolList A
    where A.clinicalStatus ~ ToConcept(Global."allergy-active")

/**
 * Allergy Intolerances that are confirmed. Specifically, allergy intolerances
 * with a verificationStatus of confirmed or null.
 */
define function ConfirmedAllergyIntolerance(AllergyIntolList List<AllergyIntolerance>):
  AllergyIntolList A
    where A.verificationStatus ~ ToConcept(Global."allergy-confirmed")
      or A.verificationStatus is null

// --------------------------------------------------- ENCOUNTERS -----------------------------------------------------

/**
 * Encounters that are finished.
 */
define function FinishedEncounter(EncList List<Encounter>):
  EncList E where E.status = 'finished'

/**
 * Encounters performed in the specified "look back" period.
 */
define function EncounterLookBack(EncList List<Encounter>, LookBack System.Quantity):
  EncList E
    let LookBackInterval: Interval[Now() - LookBack, Now()]
    where NullSafeToInterval(E.period) overlaps LookBackInterval

// --------------------------------------------------- UTILITIES -------------------------------------------------------

/**
 * Converts a FHIR Period to a CQL Interval<DateTime>, handling possible null values.
 */
define function NullSafeToInterval(Pd FHIR.Period):
  if Pd is not null then Interval[Pd."start".value, Pd."end".value] else null as Interval<DateTime>

/**
 * Converts a FHIR Coding to a CQL Concept for comparison.
 */
define function ToConcept(Coding FHIR.Coding):
  if Coding is not null then
    System.Concept {
      codes: { FHIRHelpers.ToCode(Coding) },
      display: Coding.display.value
    }
  else null
