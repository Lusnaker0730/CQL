/**
 * 糖尿病病人醣化血紅素(HbA1c)或糖化白蛋白(Glycated Albumin)執行率
 *
 * 分子: 分母ID中, 在統計期間有執行醣化血紅素(HbA1c)或糖化白蛋白(glycated albumin)檢驗人數
 * 分母: 門診主次診斷為糖尿病且使用糖尿病用藥之病人數
 *
 * 備註:
 *  - 糖尿病: 任一主、次診斷之ICD-10-CM前三碼為E08-E13之門診案件
 *  - 糖尿病用藥: ATC前3碼為A10
 *  - 醣化血紅素(HbA1c): 申報醫令代碼前五碼為09006
 *  - 糖化白蛋白(glycated albumin): 申報醫令代碼前五碼為09139
 *  - 分母條件: 主次診斷為糖尿病且使用糖尿病用藥須在同處方案件
 *  - 分子條件: 從分母ID繼續觀察, 只要該ID於統計期間有執行HbA1c或GA檢驗即成立
 *  - 資料範圍: 西醫基層、西醫醫院
 */
library DM_HbA1c_GA_Rate version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1' called FHIRHelpers

// =============================================================================
// Code Systems
// =============================================================================

codesystem "ICD10CM": 'http://hl7.org/fhir/sid/icd-10-cm'
codesystem "ATC": 'http://www.whocc.no/atc'
codesystem "NHI_ORDER": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order'
codesystem "ActCode": 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

// Encounter class codes
code "AMB": 'AMB' from "ActCode" display 'ambulatory'

parameter "Measurement Period" Interval<DateTime>

context Patient

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Check if a code starts with any of the given prefixes
 */
define function CodeStartsWith(codeValue String, prefix String):
  StartsWith(codeValue, prefix)

/**
 * Normalize effective/performed timing to Interval
 */
define function NormalizeInterval(choice Choice<FHIR.dateTime, FHIR.Period, FHIR.Timing, FHIR.instant, FHIR.string, FHIR.Age, FHIR.Range>):
  case
    when choice is FHIR.dateTime then
      Interval[FHIRHelpers.ToDateTime(choice as FHIR.dateTime), FHIRHelpers.ToDateTime(choice as FHIR.dateTime)]
    when choice is FHIR.Period then
      FHIRHelpers.ToInterval(choice as FHIR.Period)
    when choice is FHIR.instant then
      Interval[FHIRHelpers.ToDateTime(choice as FHIR.instant), FHIRHelpers.ToDateTime(choice as FHIR.instant)]
    else null as Interval<DateTime>
  end

// =============================================================================
// Diabetes Diagnosis (ICD-10-CM E08-E13)
// =============================================================================

/**
 * 糖尿病診斷: ICD-10-CM 前三碼為 E08, E09, E10, E11, E12, E13
 */
define "Diabetes Conditions":
  [Condition] C
    where exists(
      C.code.coding Coding
        where Coding.system = 'http://hl7.org/fhir/sid/icd-10-cm'
          and (
            CodeStartsWith(Coding.code.value, 'E08')
              or CodeStartsWith(Coding.code.value, 'E09')
              or CodeStartsWith(Coding.code.value, 'E10')
              or CodeStartsWith(Coding.code.value, 'E11')
              or CodeStartsWith(Coding.code.value, 'E12')
              or CodeStartsWith(Coding.code.value, 'E13')
          )
    )

// =============================================================================
// Diabetes Medications (ATC A10)
// =============================================================================

/**
 * 糖尿病用藥: ATC 前3碼為 A10
 */
define "Diabetes Medications":
  [MedicationRequest] M
    where M.status in { 'active', 'completed' }
      and exists(
        M.medication.coding Coding
          where Coding.system = 'http://www.whocc.no/atc'
            and CodeStartsWith(Coding.code.value, 'A10')
      )

// =============================================================================
// Outpatient Encounters During Measurement Period
// =============================================================================

/**
 * 門診案件: 西醫基層、西醫醫院之門診 (ambulatory encounters)
 */
define "Outpatient Encounters During Measurement Period":
  [Encounter] E
    where E.status = 'finished'
      and E.class ~ "AMB"
      and E.period overlaps "Measurement Period"

// =============================================================================
// Qualifying Encounters (Denominator Logic)
// =============================================================================

/**
 * 符合分母條件之門診案件:
 * 同一門診案件中, 主次診斷為糖尿病且有使用糖尿病用藥
 */
define "Qualifying Diabetes Encounters":
  "Outpatient Encounters During Measurement Period" E
    where
      // 該門診案件有糖尿病診斷 (主次診斷)
      exists(
        "Diabetes Conditions" C
          where exists(
            E.diagnosis D
              where D.condition.reference = 'Condition/' + C.id
          )
      )
      // 且該門診案件有糖尿病用藥 (同處方案件)
      and exists(
        "Diabetes Medications" M
          where M.encounter.reference = 'Encounter/' + E.id
      )

// =============================================================================
// HbA1c and Glycated Albumin Tests
// =============================================================================

/**
 * 醣化血紅素(HbA1c)檢驗: 醫令代碼前五碼為 09006
 */
define "HbA1c Tests During Measurement Period":
  [Procedure] P
    where P.status = 'completed'
      and exists(
        P.code.coding Coding
          where CodeStartsWith(Coding.code.value, '09006')
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

/**
 * 糖化白蛋白(Glycated Albumin)檢驗: 醫令代碼前五碼為 09139
 */
define "Glycated Albumin Tests During Measurement Period":
  [Procedure] P
    where P.status = 'completed'
      and exists(
        P.code.coding Coding
          where CodeStartsWith(Coding.code.value, '09139')
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

/**
 * HbA1c 或 Glycated Albumin 檢驗 (合併)
 */
define "HbA1c or GA Tests During Measurement Period":
  "HbA1c Tests During Measurement Period"
    union "Glycated Albumin Tests During Measurement Period"

// =============================================================================
// Population Definitions
// =============================================================================

/**
 * Initial Population: 門診有糖尿病診斷且使用糖尿病用藥之病人
 */
define "Initial Population":
  exists("Qualifying Diabetes Encounters")

/**
 * Denominator: 同 Initial Population
 * 門診主次診斷為糖尿病且使用糖尿病用藥之病人數
 */
define "Denominator":
  "Initial Population"

/**
 * Numerator: 分母ID中, 在統計期間有執行 HbA1c 或 Glycated Albumin 檢驗
 */
define "Numerator":
  exists("HbA1c or GA Tests During Measurement Period")
