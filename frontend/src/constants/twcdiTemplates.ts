export interface TwcdiTemplate {
  name: string
  category: string
  description: string
  cql: string
}

export const TWCDI_TEMPLATES: TwcdiTemplate[] = [
  {
    name: 'TWCDI Overview',
    category: 'General',
    description: 'Query all 20 TWCDI data classes from TW Core',
    cql: `library TWCDIOverview version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// Taiwan Core Data for Interoperability - All 20 Data Classes

context Patient

// 1. Demographics
define "Patient Info":
  Patient

// 2. Encounters
define "All Encounters":
  [Encounter] E
    sort by period.start desc

// 3. Conditions / Diagnoses
define "All Conditions":
  [Condition] C
    sort by recordedDate desc

// 4. Vital Signs
define "Vital Signs":
  [Observation: category in "vital-signs"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

// 5. Laboratory Results
define "Lab Results":
  [Observation: category in "laboratory"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

// 6. Medication Requests
define "Medication Orders":
  [MedicationRequest] M
    sort by authoredOn desc

// 7. Medication Statements
define "Medication Usage":
  [MedicationStatement] M

// 8. Medications
define "Medications":
  [Medication]

// 9. Procedures
define "All Procedures":
  [Procedure] P
    sort by performed desc

// 10. Diagnostic Reports
define "Diagnostic Reports":
  [DiagnosticReport] D
    sort by issued desc

// 11. Allergies
define "Allergies":
  [AllergyIntolerance] A

// 12. Immunizations
define "Immunizations":
  [Immunization] I
    where I.status = 'completed'
    sort by occurrence desc

// 13-20. Other Resources
define "Organizations":
  [Organization]

define "Practitioners":
  [Practitioner]

define "Imaging Studies":
  [ImagingStudy]

define "Locations":
  [Location]

define "Compositions":
  [Composition]

define "Document References":
  [DocumentReference]
`,
  },
  {
    name: 'TWCDI Vital Signs',
    category: 'Vital Signs',
    description: 'Blood pressure, heart rate, temperature, SpO2, weight, height, BMI',
    cql: `library TWCDIVitalSigns version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Vital Signs Queries
codesystem "LOINC": 'http://loinc.org'

code "BP Code": '85354-9' from "LOINC" display 'Blood Pressure Panel'
code "HR Code": '8867-4' from "LOINC" display 'Heart Rate'
code "Temp Code": '8310-5' from "LOINC" display 'Body Temperature'
code "RR Code": '9279-1' from "LOINC" display 'Respiratory Rate'
code "SpO2 Code": '2708-6' from "LOINC" display 'Oxygen Saturation'
code "Weight Code": '29463-7' from "LOINC" display 'Body Weight'
code "Height Code": '8302-2' from "LOINC" display 'Body Height'
code "BMI Code": '39156-5' from "LOINC" display 'BMI'

context Patient

define "Blood Pressure Readings":
  [Observation: "BP Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Most Recent BP":
  Last("Blood Pressure Readings")

define "Heart Rate":
  [Observation: "HR Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Body Temperature":
  [Observation: "Temp Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Respiratory Rate":
  [Observation: "RR Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "SpO2":
  [Observation: "SpO2 Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Body Weight":
  [Observation: "Weight Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Body Height":
  [Observation: "Height Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "BMI":
  [Observation: "BMI Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc
`,
  },
  {
    name: 'TWCDI Laboratory',
    category: 'Laboratory',
    description: 'HbA1c, glucose, eGFR, lipid panel, CBC, liver function',
    cql: `library TWCDILaboratory version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Laboratory Result Queries
codesystem "LOINC": 'http://loinc.org'

code "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'
code "Glucose Code": '2345-7' from "LOINC" display 'Glucose'
code "eGFR Code": '48642-3' from "LOINC" display 'eGFR'
code "Cholesterol Code": '2093-3' from "LOINC" display 'Total Cholesterol'
code "LDL Code": '2089-1' from "LOINC" display 'LDL Cholesterol'
code "HDL Code": '2085-9' from "LOINC" display 'HDL Cholesterol'
code "Triglycerides Code": '2571-8' from "LOINC" display 'Triglycerides'
code "WBC Code": '6690-2' from "LOINC" display 'WBC'
code "RBC Code": '789-8' from "LOINC" display 'RBC'
code "Hemoglobin Code": '718-7' from "LOINC" display 'Hemoglobin'
code "Platelets Code": '777-3' from "LOINC" display 'Platelets'
code "Creatinine Code": '2160-0' from "LOINC" display 'Creatinine'
code "ALT Code": '1742-6' from "LOINC" display 'ALT (GPT)'
code "AST Code": '1920-8' from "LOINC" display 'AST (GOT)'

context Patient

define "HbA1c Results":
  [Observation: "HbA1c Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Most Recent HbA1c":
  Last("HbA1c Results")

define "Glucose Results":
  [Observation: "Glucose Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "eGFR Results":
  [Observation: "eGFR Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Total Cholesterol":
  [Observation: "Cholesterol Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "LDL Cholesterol":
  [Observation: "LDL Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "HDL Cholesterol":
  [Observation: "HDL Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Triglycerides":
  [Observation: "Triglycerides Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "CBC - WBC":
  [Observation: "WBC Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "CBC - Hemoglobin":
  [Observation: "Hemoglobin Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "CBC - Platelets":
  [Observation: "Platelets Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "Creatinine":
  [Observation: "Creatinine Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "ALT (GPT)":
  [Observation: "ALT Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc

define "AST (GOT)":
  [Observation: "AST Code"] O
    where O.status in { 'final', 'amended', 'corrected' }
    sort by issued desc
`,
  },
  {
    name: 'TWCDI Medications',
    category: 'Medications',
    description: 'Active medications, prescriptions, and medication statements',
    cql: `library TWCDIMedications version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Medication Queries
// Uses NHI Medication code system for Taiwan drug codes
codesystem "NHI Medication": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medication-nhi-tw'

context Patient

define "Active Medication Orders":
  [MedicationRequest] M
    where M.status = 'active'
      and M.intent = 'order'
    sort by authoredOn desc

define "All Medication Orders":
  [MedicationRequest] M
    sort by authoredOn desc

define "Completed Medication Orders":
  [MedicationRequest] M
    where M.status = 'completed'
    sort by authoredOn desc

define "Medication Statements":
  [MedicationStatement] M

define "Active Medication Statements":
  [MedicationStatement] M
    where M.status = 'active'

define "Medication Resources":
  [Medication]

define "Has Active Medications":
  exists("Active Medication Orders")

define "Active Medication Count":
  Count("Active Medication Orders")
`,
  },
  {
    name: 'TWCDI Conditions',
    category: 'Conditions',
    description: 'Active conditions, diagnoses, and problem list',
    cql: `library TWCDIConditions version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Condition / Diagnosis Queries
// Taiwan uses ICD-10-CM-TW for diagnoses
codesystem "ICD-10-CM-TW": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw'

context Patient

define "All Conditions":
  [Condition] C
    sort by recordedDate desc

define "Active Conditions":
  [Condition] C
    where C.clinicalStatus.coding[0].code = 'active'
    sort by recordedDate desc

define "Confirmed Conditions":
  [Condition] C
    where C.verificationStatus.coding[0].code = 'confirmed'
    sort by recordedDate desc

define "Problem List":
  [Condition] C
    where C.category.coding[0].code = 'problem-list-item'
      and C.clinicalStatus.coding[0].code = 'active'
    sort by recordedDate desc

define "Encounter Diagnoses":
  [Condition] C
    where C.category.coding[0].code = 'encounter-diagnosis'
    sort by recordedDate desc

define "Resolved Conditions":
  [Condition] C
    where C.clinicalStatus.coding[0].code = 'resolved'
    sort by recordedDate desc

define "Active Condition Count":
  Count("Active Conditions")
`,
  },
  {
    name: 'TWCDI Encounters',
    category: 'Encounters',
    description: 'Encounters, visits, and hospitalizations',
    cql: `library TWCDIEncounters version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Encounter Queries
context Patient

define "All Encounters":
  [Encounter] E
    sort by period.start desc

define "Finished Encounters":
  [Encounter] E
    where E.status = 'finished'
    sort by period.start desc

define "In Progress Encounters":
  [Encounter] E
    where E.status = 'in-progress'

define "Ambulatory Encounters":
  [Encounter] E
    where E.class.code = 'AMB'
    sort by period.start desc

define "Inpatient Encounters":
  [Encounter] E
    where E.class.code = 'IMP'
    sort by period.start desc

define "Emergency Encounters":
  [Encounter] E
    where E.class.code = 'EMER'
    sort by period.start desc

define "Most Recent Encounter":
  Last(
    [Encounter] E
      where E.status = 'finished'
      sort by period.start
  )

define "Encounter Count":
  Count("Finished Encounters")
`,
  },
  {
    name: 'TWCDI Allergies',
    category: 'Allergies',
    description: 'Allergy and intolerance records',
    cql: `library TWCDIAllergies version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Allergy / Intolerance Queries
context Patient

define "All Allergies":
  [AllergyIntolerance] A

define "Active Allergies":
  [AllergyIntolerance] A
    where A.clinicalStatus.coding[0].code = 'active'

define "Medication Allergies":
  [AllergyIntolerance] A
    where A.category contains 'medication'
      and A.clinicalStatus.coding[0].code = 'active'

define "Food Allergies":
  [AllergyIntolerance] A
    where A.category contains 'food'
      and A.clinicalStatus.coding[0].code = 'active'

define "High Criticality Allergies":
  [AllergyIntolerance] A
    where A.criticality = 'high'
      and A.clinicalStatus.coding[0].code = 'active'

define "Has Active Allergies":
  exists("Active Allergies")

define "Active Allergy Count":
  Count("Active Allergies")
`,
  },
  {
    name: 'TWCDI Immunizations',
    category: 'Immunizations',
    description: 'Immunization records and vaccination history',
    cql: `library TWCDIImmunizations version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1' called FHIRHelpers

// TW Core Immunization Queries
context Patient

define "All Immunizations":
  [Immunization] I
    sort by occurrence desc

define "Completed Immunizations":
  [Immunization] I
    where I.status = 'completed'
    sort by occurrence desc

define "Not Done Immunizations":
  [Immunization] I
    where I.status = 'not-done'

define "Most Recent Immunization":
  Last(
    [Immunization] I
      where I.status = 'completed'
      sort by occurrence
  )

define "Immunization Count":
  Count("Completed Immunizations")

define "Has Immunizations":
  exists("Completed Immunizations")
`,
  },
]

// Group templates by category for UI display
export function getTemplatesByCategory(): Map<string, TwcdiTemplate[]> {
  const map = new Map<string, TwcdiTemplate[]>()
  for (const template of TWCDI_TEMPLATES) {
    const list = map.get(template.category) || []
    list.push(template)
    map.set(template.category, list)
  }
  return map
}
