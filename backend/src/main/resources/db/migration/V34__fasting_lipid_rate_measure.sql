-- V34: Seed measure definition — 糖尿病病人空腹血脂執行率
-- Diabetes Fasting Lipid Profile Rate

INSERT INTO measure_definition (
    name, version, title, description, status, scoring_type,
    cql_library_id, cql_content, group_definitions,
    owner_username, created_by, access_level,
    setting, improvement_notation,
    rationale, clinical_guidance,
    moh_indicator_code, indicator_category,
    created_at, updated_at
) VALUES (
    'DM_FastingLipid_Rate',
    '1.0.0',
    '糖尿病病人空腹血脂(Fasting Lipid Profile)執行率',
    '監測糖尿病病人空腹血脂檢驗執行率。分母為門診主次診斷為糖尿病且使用糖尿病用藥之病人數；分子為分母ID中，在統計期間有執行空腹血脂(Fasting Lipid Profile)檢驗人數。',
    'draft',
    'proportion',
    'DM_FastingLipid_Rate',
    '/**
 * 糖尿病病人空腹血脂(Fasting Lipid Profile)執行率
 *
 * 分子: 分母ID中, 在統計期間有執行空腹血脂(Fasting lipid profile)檢驗人數
 * 分母: 門診主次診斷為糖尿病且使用糖尿病用藥之病人數
 *
 * 備註:
 *  - 糖尿病: 任一主、次診斷之ICD-10-CM前三碼為E08-E13之門診案件
 *  - 糖尿病用藥: ATC前3碼為A10
 *  - 空腹血脂(Fasting lipid profile)人數定義:
 *    條件1: 同處方案件有 09001(血清總膽固醇CHOL) + 09004(空腹血清中性脂肪TG) + 09043(高密度脂蛋白膽固醇HDL) 三項
 *    條件2: 同處方案件有 09001(CHOL) + 09004(TG) + 09044(低密度脂蛋白膽固醇LDL) 三項
 *    條件3: 套裝檢驗醫令 — 醫令代碼21(就醫序號IC21)、22(IC22)、25(IC23)、27(IC24)、
 *           21+L1001C(IC21)、25+L1001C(IC23)
 *  - 註1: 條件1、2的三項檢查需限定要發生在同處方案件
 *  - 註2: 條件3為101年(含)起新增之空腹血脂人數定義
 *  - 分母條件: 主次診斷為糖尿病且使用糖尿病用藥須在同處方案件
 *  - 分子條件: 從分母ID繼續觀察, 只要該ID於統計期間有執行空腹血脂檢驗即成立
 *  - 資料範圍: 西醫基層、西醫醫院
 */
library DM_FastingLipid_Rate version ''1.0.0''

using FHIR version ''4.0.1''

include FHIRHelpers version ''4.0.1'' called FHIRHelpers

// =============================================================================
// Code Systems
// =============================================================================

codesystem "ICD10CM": ''http://hl7.org/fhir/sid/icd-10-cm''
codesystem "ATC": ''http://www.whocc.no/atc''
codesystem "NHI_ORDER": ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order''
codesystem "ActCode": ''http://terminology.hl7.org/CodeSystem/v3-ActCode''

// Encounter class codes
code "AMB": ''AMB'' from "ActCode" display ''ambulatory''

parameter "Measurement Period" Interval<DateTime>

context Patient

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Check if a code starts with a given prefix
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
        where Coding.system = ''http://hl7.org/fhir/sid/icd-10-cm''
          and (
            CodeStartsWith(Coding.code.value, ''E08'')
              or CodeStartsWith(Coding.code.value, ''E09'')
              or CodeStartsWith(Coding.code.value, ''E10'')
              or CodeStartsWith(Coding.code.value, ''E11'')
              or CodeStartsWith(Coding.code.value, ''E12'')
              or CodeStartsWith(Coding.code.value, ''E13'')
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
    where M.status in { ''active'', ''completed'' }
      and exists(
        M.medication.coding Coding
          where Coding.system = ''http://www.whocc.no/atc''
            and CodeStartsWith(Coding.code.value, ''A10'')
      )

// =============================================================================
// Outpatient Encounters During Measurement Period
// =============================================================================

/**
 * 門診案件: 西醫基層、西醫醫院之門診 (ambulatory encounters)
 */
define "Outpatient Encounters During Measurement Period":
  [Encounter] E
    where E.status = ''finished''
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
              where D.condition.reference = ''Condition/'' + C.id
          )
      )
      // 且該門診案件有糖尿病用藥 (同處方案件)
      and exists(
        "Diabetes Medications" M
          where M.encounter.reference = ''Encounter/'' + E.id
      )

// =============================================================================
// Individual Lipid Tests (NHI Order Codes)
// =============================================================================

/**
 * 血清總膽固醇(CHOL): 醫令代碼前五碼為 09001
 */
define "CHOL Tests During Measurement Period":
  [Procedure] P
    where P.status = ''completed''
      and exists(
        P.code.coding Coding
          where Coding.system = ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order''
            and CodeStartsWith(Coding.code.value, ''09001'')
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

/**
 * 空腹血清中性脂肪(TG): 醫令代碼前五碼為 09004
 */
define "TG Tests During Measurement Period":
  [Procedure] P
    where P.status = ''completed''
      and exists(
        P.code.coding Coding
          where Coding.system = ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order''
            and CodeStartsWith(Coding.code.value, ''09004'')
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

/**
 * 高密度脂蛋白膽固醇(HDL): 醫令代碼前五碼為 09043
 */
define "HDL Tests During Measurement Period":
  [Procedure] P
    where P.status = ''completed''
      and exists(
        P.code.coding Coding
          where Coding.system = ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order''
            and CodeStartsWith(Coding.code.value, ''09043'')
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

/**
 * 低密度脂蛋白膽固醇(LDL): 醫令代碼前五碼為 09044
 */
define "LDL Tests During Measurement Period":
  [Procedure] P
    where P.status = ''completed''
      and exists(
        P.code.coding Coding
          where Coding.system = ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order''
            and CodeStartsWith(Coding.code.value, ''09044'')
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

// =============================================================================
// Fasting Lipid Profile — Condition 1 & 2 (同處方案件三項檢查)
// =============================================================================

/**
 * 條件1: 同一門診案件有 CHOL(09001) + TG(09004) + HDL(09043) 三項檢查
 */
define "Lipid Panel Condition 1 Encounters":
  "Outpatient Encounters During Measurement Period" E
    where exists(
      "CHOL Tests During Measurement Period" P
        where P.encounter.reference = ''Encounter/'' + E.id
    )
    and exists(
      "TG Tests During Measurement Period" P
        where P.encounter.reference = ''Encounter/'' + E.id
    )
    and exists(
      "HDL Tests During Measurement Period" P
        where P.encounter.reference = ''Encounter/'' + E.id
    )

/**
 * 條件2: 同一門診案件有 CHOL(09001) + TG(09004) + LDL(09044) 三項檢查
 */
define "Lipid Panel Condition 2 Encounters":
  "Outpatient Encounters During Measurement Period" E
    where exists(
      "CHOL Tests During Measurement Period" P
        where P.encounter.reference = ''Encounter/'' + E.id
    )
    and exists(
      "TG Tests During Measurement Period" P
        where P.encounter.reference = ''Encounter/'' + E.id
    )
    and exists(
      "LDL Tests During Measurement Period" P
        where P.encounter.reference = ''Encounter/'' + E.id
    )

// =============================================================================
// Fasting Lipid Profile — Condition 3 (套裝檢驗醫令)
// =============================================================================

/**
 * 條件3: 套裝檢驗 — 健保套裝血脂檢驗醫令
 * 醫令代碼 21(就醫序號IC21)、22(IC22)、25(IC23)、27(IC24)、
 * 21+L1001C(IC21)、25+L1001C(IC23)
 *
 * 在 FHIR 中, 套裝檢驗以單一 Procedure 表示, 醫令代碼對應 NHI_ORDER code,
 * 就醫序號對應 Encounter.identifier (NHI visit sequence)
 */
define "Lipid Panel Order Tests During Measurement Period":
  [Procedure] P
    where P.status = ''completed''
      and exists(
        P.code.coding Coding
          where Coding.system = ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-order''
            and (
              Coding.code.value = ''21''
                or Coding.code.value = ''22''
                or Coding.code.value = ''25''
                or Coding.code.value = ''27''
                or Coding.code.value = ''L1001C''
            )
      )
      and NormalizeInterval(P.performed) overlaps "Measurement Period"

// =============================================================================
// Combined Fasting Lipid Profile Check
// =============================================================================

/**
 * 空腹血脂檢驗: 條件1 或 條件2 或 條件3 任一成立
 */
define "Has Fasting Lipid Profile":
  exists("Lipid Panel Condition 1 Encounters")
    or exists("Lipid Panel Condition 2 Encounters")
    or exists("Lipid Panel Order Tests During Measurement Period")

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
 * Numerator: 分母ID中, 在統計期間有執行空腹血脂(Fasting Lipid Profile)檢驗
 */
define "Numerator":
  "Has Fasting Lipid Profile"
',
    '[{"groupId":"group-1","description":"糖尿病病人空腹血脂執行率","populations":[{"populationType":"initial-population","criteriaExpression":"Initial Population","description":"門診有糖尿病診斷且使用糖尿病用藥之病人"},{"populationType":"denominator","criteriaExpression":"Denominator","description":"門診主次診斷為糖尿病且使用糖尿病用藥之病人數"},{"populationType":"numerator","criteriaExpression":"Numerator","description":"分母ID中，在統計期間有執行空腹血脂檢驗人數"}],"stratifiers":[],"scoringUnit":"%"}]',
    'admin',
    'admin',
    'public',
    'outpatient',
    'increase',
    '空腹血脂檢驗是糖尿病照護品質的重要指標。糖尿病患者罹患心血管疾病的風險較高，定期監測血脂有助於早期發現異常並及時介入治療，降低心血管事件發生率。',
    '備註：
• 糖尿病定義：任一主、次診斷之 ICD-10-CM 前三碼為 E08-E13 之門診案件
• 糖尿病用藥：ATC 前 3 碼為 A10
• 空腹血脂人數定義（三個條件任一成立）：
  條件1：同處方有 09001(CHOL) + 09004(TG) + 09043(HDL)
  條件2：同處方有 09001(CHOL) + 09004(TG) + 09044(LDL)
  條件3：套裝檢驗醫令代碼 21/22/25/27/L1001C
• 分母條件：主次診斷糖尿病+糖尿病用藥須在同處方案件
• 分子條件：分母ID於統計期間有執行空腹血脂檢驗即成立
• 資料範圍：西醫基層、西醫醫院',
    'DM-LIPID',
    '糖尿病照護',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
