/** TWCORE IG compliant CQL examples for educational content */

export const CQL_SHOWCASE_EXAMPLE = `library TWCoreDiabetesCare version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

// TWCORE IG 術語系統
codesystem "ICD10CMTW": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw'
codesystem "LOINC": 'http://loinc.org'

// 值集與代碼定義
code "HbA1c": '4548-4' from "LOINC" display '糖化血色素'
code "Diabetes": 'E11' from "ICD10CMTW" display '第2型糖尿病'

// 量測期間參數
parameter "Measurement Period" Interval<DateTime>

context Patient

// 母群體：活躍的糖尿病診斷
define "Diabetic Patients":
  [Condition: "Diabetes"] C
    where C.clinicalStatus ~ ToConcept(
      Global."active")

// 分母：量測期間就醫的糖尿病患者
define "Denominator":
  "Diabetic Patients" is not null

// 分子：HbA1c < 7% 的患者
define "Numerator":
  exists (
    [Observation: "HbA1c"] O
      where O.effective during "Measurement Period"
        and O.value < 7 '%'
  )`

export const CQL_EXAMPLE_DIABETES = `library TWCoreDiabetesCare version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

codesystem "LOINC": 'http://loinc.org'
codesystem "ICD10CMTW": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw'
codesystem "ConditionClinicalStatus": 'http://terminology.hl7.org/CodeSystem/condition-clinical'

code "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'
code "Diabetes Type 2": 'E11' from "ICD10CMTW" display '第2型糖尿病'
code "Active": 'active' from "ConditionClinicalStatus"

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2025-01-01, @2025-12-31]

context Patient

// 初始母群體：有活躍糖尿病診斷的病患
define "Initial Population":
  exists (
    [Condition: "Diabetes Type 2"] C
      where C.clinicalStatus ~ "Active"
  )

// 分母：量測期間有就醫紀錄
define "Denominator":
  "Initial Population"
    and exists (
      [Encounter] E
        where E.period overlaps "Measurement Period"
          and E.status = 'finished'
    )

// 分子：量測期間 HbA1c < 7%
define "Numerator":
  exists (
    [Observation: "HbA1c Code"] O
      where O.effective during "Measurement Period"
        and O.status in { 'final', 'amended' }
        and O.value < 7 '%'
  )

// 最新一次 HbA1c 結果
define "Latest HbA1c":
  Last(
    [Observation: "HbA1c Code"] O
      where O.status in { 'final', 'amended' }
      sort by effective
  )`

export const CQL_EXAMPLE_HYPERTENSION = `library TWCoreHypertensionScreening version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

codesystem "LOINC": 'http://loinc.org'
codesystem "ICD10CMTW": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw'

code "BP Panel": '85354-9' from "LOINC" display 'Blood pressure panel'
code "Systolic BP": '8480-6' from "LOINC" display 'Systolic blood pressure'
code "Diastolic BP": '8462-4' from "LOINC" display 'Diastolic blood pressure'
code "Hypertension": 'I10' from "ICD10CMTW" display '本態性高血壓'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2025-01-01, @2025-12-31]

context Patient

// 血壓量測紀錄
define "BP Observations":
  [Observation: "BP Panel"] O
    where O.effective during "Measurement Period"
      and O.status in { 'final', 'amended' }

// 收縮壓 >= 140 或舒張壓 >= 90 的量測
define "Elevated BP Readings":
  "BP Observations" O
    where exists (
      O.component C
        where C.code ~ "Systolic BP"
          and C.value >= 140 'mm[Hg]'
    )
    or exists (
      O.component C
        where C.code ~ "Diastolic BP"
          and C.value >= 90 'mm[Hg]'
    )

// 已確診高血壓的病患
define "Known Hypertension":
  exists (
    [Condition: "Hypertension"] C
      where C.clinicalStatus ~ ToConcept(
        Global."active")
  )

// 有高血壓用藥的病患
define "On Antihypertensive":
  exists (
    [MedicationRequest] MR
      where MR.status = 'active'
        and MR.authoredOn during "Measurement Period"
  )`

export const CQL_EXAMPLE_MEDICATION = `library TWCoreMedicationSafety version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

codesystem "LOINC": 'http://loinc.org'
codesystem "NHIMedication": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medication-nhi-tw'

code "eGFR": '48642-3' from "LOINC" display 'eGFR'
code "Creatinine": '2160-0' from "LOINC" display 'Creatinine'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2025-01-01, @2025-12-31]

context Patient

// 目前有效的用藥醫令
define "Active Medications":
  [MedicationRequest] MR
    where MR.status = 'active'
      and MR.intent = 'order'

// 最新腎功能 (eGFR)
define "Latest eGFR":
  Last(
    [Observation: "eGFR"] O
      where O.status in { 'final', 'amended' }
      sort by effective
  )

// 腎功能不全 (eGFR < 60)
define "Renal Impairment":
  "Latest eGFR" O
    return O.value < 60 'mL/min/1.73m2'

// 需要劑量調整的病患
define "Needs Dose Adjustment":
  "Renal Impairment" is true
    and exists ("Active Medications")

// 同時使用的藥品數量
define "Polypharmacy Count":
  Count("Active Medications")`

export const CQL_EXAMPLE_ENCOUNTER = `library TWCoreEncounterAnalysis version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

codesystem "ActCode": 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

code "Ambulatory": 'AMB' from "ActCode" display '門診'
code "Emergency": 'EMER' from "ActCode" display '急診'
code "Inpatient": 'IMP' from "ActCode" display '住院'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2025-01-01, @2025-12-31]

context Patient

// 量測期間所有就醫紀錄
define "All Encounters":
  [Encounter] E
    where E.period overlaps "Measurement Period"
      and E.status = 'finished'

// 門診就醫
define "Ambulatory Visits":
  "All Encounters" E
    where E.class ~ "Ambulatory"

// 急診就醫
define "Emergency Visits":
  "All Encounters" E
    where E.class ~ "Emergency"

// 住院紀錄
define "Inpatient Stays":
  "All Encounters" E
    where E.class ~ "Inpatient"

// 就醫次數統計
define "Visit Summary":
  Tuple {
    ambulatory: Count("Ambulatory Visits"),
    emergency: Count("Emergency Visits"),
    inpatient: Count("Inpatient Stays"),
    total: Count("All Encounters")
  }`

/** Starter template for the playground */
export const CQL_PLAYGROUND_STARTER = `library MyFirstLibrary version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

// 台灣術語系統
codesystem "LOINC": 'http://loinc.org'
codesystem "ICD10CMTW": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw'

context Patient

// 在此撰寫您的 CQL 定義
define "Patient Name":
  Patient.name

define "Active Conditions":
  [Condition] C
    where C.clinicalStatus ~ ToConcept(
      Global."active")
`

/** Concept code examples for the concepts section */
export const CQL_CONCEPT_LIBRARY = `library MyLibrary version '1.0.0'

using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'`

export const CQL_CONCEPT_VALUESET = `// 值集：引用外部術語定義
valueset "Diabetes Diagnoses":
  'https://twcore.mohw.gov.tw/ig/twcore/ValueSet/icd-10-cm-2023-tw'

// 代碼：直接定義單一代碼
code "HbA1c": '4548-4' from "LOINC"
  display 'Hemoglobin A1c'`

export const CQL_CONCEPT_CONTEXT = `context Patient

// 所有後續查詢自動限定為目前病患
define "Patient Age":
  AgeInYears()`

export const CQL_CONCEPT_RETRIEVE = `// 查詢所有診斷
define "All Conditions":
  [Condition]

// 查詢特定術語的觀察值
define "HbA1c Results":
  [Observation: "HbA1c"]`

export const CQL_CONCEPT_QUERY = `define "Recent High BP":
  [Observation: "BP Panel"] O
    where O.effective during "Measurement Period"
      and O.value > 140 'mm[Hg]'
    sort by effective desc`

export const CQL_CONCEPT_DEFINE = `define "Has Diabetes":
  exists ([Condition: "Diabetes"])

define "Needs Screening":
  "Has Diabetes" and not "Has Recent HbA1c"`
