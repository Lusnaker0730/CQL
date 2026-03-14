/**
 * QI-Core Common Patterns - Common retrieve and filter patterns for QI-Core profiles.
 * Provides reusable definitions for encounters, conditions, medications, and procedures.
 */
library QICoreCommon version '1.0.0'

using QICore version '4.1.1'

include FHIRHelpers version '4.0.1'

parameter "Measurement Period" Interval<DateTime>

context Patient

// Encounter patterns
define "All Encounters":
  [Encounter]

define "Completed Encounters":
  [Encounter] E where E.status = 'finished'

define "Encounters During Measurement Period":
  [Encounter] E
    where E.period overlaps "Measurement Period"
      and E.status = 'finished'

define "Inpatient Encounters":
  [Encounter] E
    where E.class ~ "IMP"
      and E.status = 'finished'

define "Emergency Department Encounters":
  [Encounter] E
    where E.class ~ "EMER"
      and E.status = 'finished'

// Condition patterns
define "Active Conditions":
  [Condition] C
    where C.clinicalStatus ~ "active"

define "Confirmed Conditions":
  [Condition] C
    where C.verificationStatus ~ "confirmed"

define "Active Confirmed Conditions":
  [Condition] C
    where C.clinicalStatus ~ "active"
      and C.verificationStatus ~ "confirmed"

// Medication patterns
define "Active Medications":
  [MedicationRequest] M
    where M.status = 'active'

define "Completed Medications":
  [MedicationRequest] M
    where M.status = 'completed'

// Procedure patterns
define "Completed Procedures":
  [Procedure] P
    where P.status = 'completed'

define "Procedures During Measurement Period":
  [Procedure] P
    where P.status = 'completed'
      and (P.performed as Period) overlaps "Measurement Period"

// Observation patterns
define "Final Observations":
  [Observation] O
    where O.status in { 'final', 'amended', 'corrected' }

define "Observations During Measurement Period":
  [Observation] O
    where O.status in { 'final', 'amended', 'corrected' }
      and (O.effective as Period) overlaps "Measurement Period"

// Code helpers
code "IMP": 'IMP' from "ActCode" display 'inpatient encounter'
code "EMER": 'EMER' from "ActCode" display 'emergency'
code "active": 'active' from "ConditionClinicalStatusCodes"
code "confirmed": 'confirmed' from "ConditionVerificationStatusCodes"

codesystem "ActCode": 'http://terminology.hl7.org/CodeSystem/v3-ActCode'
codesystem "ConditionClinicalStatusCodes": 'http://terminology.hl7.org/CodeSystem/condition-clinical'
codesystem "ConditionVerificationStatusCodes": 'http://terminology.hl7.org/CodeSystem/condition-ver-status'
