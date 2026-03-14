-- V30: Seed measure definition — 人工膝關節置換手術後九十日以內傷口感染率
-- Knee Replacement Surgical Site Infection Rate within 90 Days

INSERT INTO measure_definition (
    name, version, title, description, status, scoring_type,
    cql_library_id, cql_content, group_definitions,
    owner_username, created_by, access_level,
    setting, improvement_notation,
    rationale, clinical_guidance,
    moh_indicator_code, indicator_category,
    created_at, updated_at
) VALUES (
    'KneeReplacementSSIRate',
    '1.0.0',
    '人工膝關節置換手術後九十日以內傷口感染率',
    '監測人工膝關節及半人工膝關節置換術後 90 天內手術傷口感染發生率。分母為統計期間內全人工膝關節（醫令 64164B/97805K/97806A/97807B）及半人工膝關節（64169B）置換術之住院案件數；分子為分母案件中，術後 90 天內發生手術傷口感染（傷口感染處置或 SSI 診斷碼）且術後 7 日後有使用抗生素之案件數。',
    'draft',
    'proportion',
    'KneeReplacementSSIRate',
    'library KneeReplacementSSIRate version ''1.0.0''

using FHIR version ''4.0.1''

include FHIRHelpers version ''4.0.1''

/*
 * 人工膝關節置換手術後九十日以內傷口感染率
 * Surgical Site Infection Rate within 90 Days after Knee Replacement
 *
 * 分母：統計期間內醫院人工膝關節及半人工膝關節置換術執行案件數
 * 分子：分母案件中，置換後 90 天內發生手術傷口感染之案件數
 * 評分方式：proportion（越低越好，improvement notation = decrease）
 */

// ══════════════════════════════════════════════════
// Code Systems
// ══════════════════════════════════════════════════

codesystem "NHI_ORDER": ''https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/nhi-procedure''
codesystem "ICD10CM":   ''http://hl7.org/fhir/sid/icd-10-cm''
codesystem "ATC":       ''http://www.whocc.no/atc''
codesystem "ActCode":   ''http://terminology.hl7.org/CodeSystem/v3-ActCode''

// ── Encounter Class ──
code "Inpatient": ''IMP'' from "ActCode" display ''inpatient encounter''

// ══════════════════════════════════════════════════
// 膝關節置換手術醫令代碼 (NHI)
// 全人工膝關節：64164B(類別2), 97805K, 97806A, 97807B
// 半人工膝關節：64169B
// ══════════════════════════════════════════════════

code "NHI_64164B": ''64164B'' from "NHI_ORDER" display ''全人工膝關節置換術''
code "NHI_97805K": ''97805K'' from "NHI_ORDER" display ''全人工膝關節置換術''
code "NHI_97806A": ''97806A'' from "NHI_ORDER" display ''全人工膝關節置換術''
code "NHI_97807B": ''97807B'' from "NHI_ORDER" display ''全人工膝關節置換術''
code "NHI_64169B": ''64169B'' from "NHI_ORDER" display ''半人工膝關節置換術''

// ══════════════════════════════════════════════════
// 傷口感染處置醫令代碼 (NHI)
// ══════════════════════════════════════════════════

code "NHI_48004C": ''48004C'' from "NHI_ORDER" display ''傷口感染處置''
code "NHI_48005C": ''48005C'' from "NHI_ORDER" display ''傷口感染處置''
code "NHI_48006C": ''48006C'' from "NHI_ORDER" display ''傷口感染處置''

// ══════════════════════════════════════════════════
// SSI 診斷碼 (ICD-10-CM)
// ══════════════════════════════════════════════════

// T85.7 — 其他內部人工裝置感染及發炎反應
code "dx_T8579XA": ''T8579XA'' from "ICD10CM"
code "dx_T85730A": ''T85730A'' from "ICD10CM"
code "dx_T85731A": ''T85731A'' from "ICD10CM"
code "dx_T85732A": ''T85732A'' from "ICD10CM"
code "dx_T85733A": ''T85733A'' from "ICD10CM"
code "dx_T85734A": ''T85734A'' from "ICD10CM"
code "dx_T85735A": ''T85735A'' from "ICD10CM"
code "dx_T85738A": ''T85738A'' from "ICD10CM"

// T84.50 — 內部關節人工裝置感染（第7位碼A，初始照護）
code "dx_T84500A": ''T84500A'' from "ICD10CM"
code "dx_T84501A": ''T84501A'' from "ICD10CM"
code "dx_T84502A": ''T84502A'' from "ICD10CM"
code "dx_T84503A": ''T84503A'' from "ICD10CM"
code "dx_T84504A": ''T84504A'' from "ICD10CM"
code "dx_T84509A": ''T84509A'' from "ICD10CM"

// T84.6 — 骨內部固定裝置機械性併發症（第7位碼A）
code "dx_T8460XA": ''T8460XA'' from "ICD10CM"
code "dx_T8461XA": ''T8461XA'' from "ICD10CM"
code "dx_T8462XA": ''T8462XA'' from "ICD10CM"
code "dx_T8463XA": ''T8463XA'' from "ICD10CM"
code "dx_T8469XA": ''T8469XA'' from "ICD10CM"

// T84.7 — 骨內部固定裝置感染及發炎反應（第7位碼A）
code "dx_T8470XA": ''T8470XA'' from "ICD10CM"
code "dx_T8471XA": ''T8471XA'' from "ICD10CM"
code "dx_T8472XA": ''T8472XA'' from "ICD10CM"
code "dx_T8473XA": ''T8473XA'' from "ICD10CM"
code "dx_T8479XA": ''T8479XA'' from "ICD10CM"

// T85.72-T85.79 其餘（第7位碼A）
code "dx_T85720A": ''T85720A'' from "ICD10CM"
code "dx_T85721A": ''T85721A'' from "ICD10CM"
code "dx_T85722A": ''T85722A'' from "ICD10CM"
code "dx_T85723A": ''T85723A'' from "ICD10CM"
code "dx_T85724A": ''T85724A'' from "ICD10CM"
code "dx_T85728A": ''T85728A'' from "ICD10CM"
code "dx_T85729A": ''T85729A'' from "ICD10CM"
code "dx_T85790A": ''T85790A'' from "ICD10CM"
code "dx_T85791A": ''T85791A'' from "ICD10CM"
code "dx_T85792A": ''T85792A'' from "ICD10CM"
code "dx_T85798A": ''T85798A'' from "ICD10CM"
code "dx_T85799A": ''T85799A'' from "ICD10CM"

// T86.842 — 骨移植物併發症
code "dx_T868421": ''T868421'' from "ICD10CM"
code "dx_T868422": ''T868422'' from "ICD10CM"
code "dx_T868423": ''T868423'' from "ICD10CM"
code "dx_T868429": ''T868429'' from "ICD10CM"

// T81.4 — 手術後感染（第7位碼A）
code "dx_T8140XA": ''T8140XA'' from "ICD10CM"
code "dx_T8141XA": ''T8141XA'' from "ICD10CM"
code "dx_T8142XA": ''T8142XA'' from "ICD10CM"
code "dx_T8143XA": ''T8143XA'' from "ICD10CM"
code "dx_T8144XA": ''T8144XA'' from "ICD10CM"
code "dx_T8149XA": ''T8149XA'' from "ICD10CM"

// M86.9 — 骨髓炎（未明示）
code "dx_M869": ''M869'' from "ICD10CM"

// M46.2x — 椎骨骨髓炎
code "dx_M4620": ''M4620'' from "ICD10CM"
code "dx_M4621": ''M4621'' from "ICD10CM"
code "dx_M4622": ''M4622'' from "ICD10CM"
code "dx_M4623": ''M4623'' from "ICD10CM"
code "dx_M4624": ''M4624'' from "ICD10CM"
code "dx_M4625": ''M4625'' from "ICD10CM"
code "dx_M4626": ''M4626'' from "ICD10CM"
code "dx_M4627": ''M4627'' from "ICD10CM"
code "dx_M4628": ''M4628'' from "ICD10CM"

// M00.xx — 化膿性關節炎
code "dx_M0005": ''M0005'' from "ICD10CM"
code "dx_M0015": ''M0015'' from "ICD10CM"
code "dx_M0025": ''M0025'' from "ICD10CM"
code "dx_M0085": ''M0085'' from "ICD10CM"
code "dx_M0006": ''M0006'' from "ICD10CM"
code "dx_M0016": ''M0016'' from "ICD10CM"
code "dx_M0026": ''M0026'' from "ICD10CM"
code "dx_M0086": ''M0086'' from "ICD10CM"
code "dx_M0008": ''M0008'' from "ICD10CM"
code "dx_M0018": ''M0018'' from "ICD10CM"
code "dx_M0028": ''M0028'' from "ICD10CM"
code "dx_M0088": ''M0088'' from "ICD10CM"
code "dx_M0009": ''M0009'' from "ICD10CM"

// ══════════════════════════════════════════════════
// 抗生素 ATC7 代碼
// ══════════════════════════════════════════════════

code "atc_J01CA12": ''J01CA12'' from "ATC" display ''Piperacillin''
code "atc_J01CF01": ''J01CF01'' from "ATC" display ''Dicloxacillin''
code "atc_J01CF02": ''J01CF02'' from "ATC" display ''Cloxacillin''
code "atc_J01CA04": ''J01CA04'' from "ATC" display ''Amoxicillin''
code "atc_J04CA05": ''J04CA05'' from "ATC" display ''Ethambutol''
code "atc_J01DA11": ''J01DA11'' from "ATC" display ''Cefalexin''
code "atc_J01DA24": ''J01DA24'' from "ATC" display ''Cefuroxime''
code "atc_J01EA01": ''J01EA01'' from "ATC" display ''Trimethoprim''
code "atc_J01EC01": ''J01EC01'' from "ATC" display ''Sulfamethoxazole''
code "atc_J01XA01": ''J01XA01'' from "ATC" display ''Vancomycin''
code "atc_J01XA02": ''J01XA02'' from "ATC" display ''Teicoplanin''
code "atc_J01DD02": ''J01DD02'' from "ATC" display ''Ceftazidime''
code "atc_J01DE01": ''J01DE01'' from "ATC" display ''Cefepime''
code "atc_A07AA09": ''A07AA09'' from "ATC" display ''Vancomycin (oral)''

// ══════════════════════════════════════════════════
// Parameters
// ══════════════════════════════════════════════════

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2025-01-01T00:00:00.0, @2025-12-31T23:59:59.999]

context Patient

// ══════════════════════════════════════════════════
// Helper Definitions
// ══════════════════════════════════════════════════

define "Inpatient Encounters":
  [Encounter] E
    where E.status = ''finished''
      and E.class ~ "Inpatient"
      and E.period starts during "Measurement Period"

// ══════════════════════════════════════════════════
// 分母邏輯：膝關節置換手術
// ══════════════════════════════════════════════════

define "Knee Replacement Codes":
  { "NHI_64164B", "NHI_97805K", "NHI_97806A", "NHI_97807B", "NHI_64169B" }

define "Knee Replacement Procedures":
  [Procedure] P
    where P.status = ''completed''
      and P.code in "Knee Replacement Codes"

define "Qualifying Knee Replacements":
  "Knee Replacement Procedures" P
    with "Inpatient Encounters" E
      such that P.performed overlaps E.period
    where P.performed starts during "Measurement Period"

define "Earliest Surgery Date":
  Min("Qualifying Knee Replacements" P
    return start of (P.performed as Period))

// ══════════════════════════════════════════════════
// 分子邏輯：手術傷口感染
// 判定條件：((1) or (2)) and (3)
//   (1) 有執行傷口感染處置 (48004C/48005C/48006C)
//   (2) 任一 SSI 診斷碼
//   (3) 術後 7 日後有使用抗生素
//   觀察窗口：最早手術日起 90 天內
// ══════════════════════════════════════════════════

define "SSI Observation Window":
  if "Earliest Surgery Date" is not null
  then Interval["Earliest Surgery Date", "Earliest Surgery Date" + 90 days]
  else null as Interval<DateTime>

// ── 條件 (1)：傷口感染處置 ──

define "SSI Procedure Codes":
  { "NHI_48004C", "NHI_48005C", "NHI_48006C" }

define "Has SSI Procedure":
  exists (
    [Procedure] P
      where P.status = ''completed''
        and P.code in "SSI Procedure Codes"
        and "SSI Observation Window" is not null
        and P.performed starts during "SSI Observation Window"
  )

// ── 條件 (2)：SSI 診斷碼 ──

define "SSI Diagnosis Codes":
  {
    // T85.7 系列
    "dx_T8579XA", "dx_T85730A", "dx_T85731A", "dx_T85732A",
    "dx_T85733A", "dx_T85734A", "dx_T85735A", "dx_T85738A",
    // T84.50 系列
    "dx_T84500A", "dx_T84501A", "dx_T84502A", "dx_T84503A",
    "dx_T84504A", "dx_T84509A",
    // T84.6 系列
    "dx_T8460XA", "dx_T8461XA", "dx_T8462XA", "dx_T8463XA", "dx_T8469XA",
    // T84.7 系列
    "dx_T8470XA", "dx_T8471XA", "dx_T8472XA", "dx_T8473XA", "dx_T8479XA",
    // T85.72-T85.79 其餘
    "dx_T85720A", "dx_T85721A", "dx_T85722A", "dx_T85723A",
    "dx_T85724A", "dx_T85728A", "dx_T85729A",
    "dx_T85790A", "dx_T85791A", "dx_T85792A", "dx_T85798A", "dx_T85799A",
    // T86.842 系列
    "dx_T868421", "dx_T868422", "dx_T868423", "dx_T868429",
    // T81.4 系列
    "dx_T8140XA", "dx_T8141XA", "dx_T8142XA",
    "dx_T8143XA", "dx_T8144XA", "dx_T8149XA",
    // M86.9
    "dx_M869",
    // M46.2x 系列
    "dx_M4620", "dx_M4621", "dx_M4622", "dx_M4623",
    "dx_M4624", "dx_M4625", "dx_M4626", "dx_M4627", "dx_M4628",
    // M00.xx 系列 — 化膿性關節炎
    "dx_M0005", "dx_M0015", "dx_M0025", "dx_M0085",
    "dx_M0006", "dx_M0016", "dx_M0026", "dx_M0086",
    "dx_M0008", "dx_M0018", "dx_M0028", "dx_M0088",
    "dx_M0009"
  }

define "Has SSI Diagnosis":
  exists (
    [Condition] C
      where C.code in "SSI Diagnosis Codes"
        and "SSI Observation Window" is not null
        and (
          (C.onset is dateTime
            and C.onset as dateTime in "SSI Observation Window")
          or (C.onset is Period
            and C.onset as Period starts during "SSI Observation Window")
          or (C.recordedDate in "SSI Observation Window")
        )
  )

// ── 條件 (3)：術後 7 日後使用抗生素 ──

define "Antibiotic Codes":
  {
    "atc_J01CA12", "atc_J01CF01", "atc_J01CF02", "atc_J01CA04",
    "atc_J04CA05", "atc_J01DA11", "atc_J01DA24",
    "atc_J01EA01", "atc_J01EC01",
    "atc_J01XA01", "atc_J01XA02",
    "atc_J01DD02", "atc_J01DE01", "atc_A07AA09"
  }

define "Antibiotic Requests":
  [MedicationRequest] MR
    where MR.status in { ''active'', ''completed'' }
      and MR.medication as CodeableConcept in "Antibiotic Codes"

// 抗生素醫令迄日(取最大的迄日) - 手術醫令起日 >= 7
define "Latest Antibiotic End Date":
  Max(
    "Antibiotic Requests" MR
      return Coalesce(
        end of (MR.dispenseRequest.validityPeriod),
        MR.authoredOn
      )
  )

define "Has Post Op Antibiotic Use":
  "Earliest Surgery Date" is not null
    and "Latest Antibiotic End Date" is not null
    and days between "Earliest Surgery Date" and "Latest Antibiotic End Date" >= 7

// ══════════════════════════════════════════════════
// Population Criteria
// ══════════════════════════════════════════════════

// 初始族群 = 有膝關節置換手術的住院案件
define "Initial Population":
  exists "Qualifying Knee Replacements"

// 分母 = 初始族群
define "Denominator":
  "Initial Population"

// 分子 = (傷口感染處置 OR 感染診斷) AND 術後7日抗生素
define "Numerator":
  ("Has SSI Procedure" or "Has SSI Diagnosis")
    and "Has Post Op Antibiotic Use"
',
    '[{"groupId":"group-1","description":"人工膝關節置換手術後九十日以內傷口感染率","populations":[{"populationType":"initial-population","criteriaExpression":"Initial Population","description":"統計期間內有人工膝關節或半人工膝關節置換術的住院案件"},{"populationType":"denominator","criteriaExpression":"Denominator","description":"統計期間內醫院人工膝關節及半人工膝關節置換術執行案件數"},{"populationType":"numerator","criteriaExpression":"Numerator","description":"分母案件中，置換後3個月(90天)內發生手術傷口感染之案件數"}],"stratifiers":[],"scoringUnit":"%"}]',
    'admin',
    'admin',
    'public',
    'inpatient',
    'decrease',
    '手術傷口感染是膝關節置換術後最嚴重的併發症之一，可導致植入物失敗、二次手術及延長住院天數。監測此指標有助於早期識別感染控制問題並改善手術品質。',
    '備註：\n• 全人工膝關節置換：醫令代碼 64164B（類別2）或 97805K、97806A、97807B 之住院案件\n• 半人工膝關節置換：醫令代碼 64169B 之住院案件\n• 手術傷口感染判定：((1) or (2)) and (3)\n  (1) 執行 48004C / 48005C / 48006C\n  (2) 任一 SSI 診斷碼（T85/T84/T81/T86/M86/M46/M00 系列）\n  (3) 術後 7 日後有使用抗生素（最大迄日 − 手術起日 ≥ 7）\n• 抗生素 ATC7：J01CA12, J01CF01, J01CF02, J01CA04, J04CA05, J01DA11, J01DA24, J01EA01, J01EC01, J01XA01, J01XA02, J01DD02, J01DE01, A07AA09\n• 觀察窗口：最早手術醫令起日往後 90 天',
    'SSI-KNEE-90',
    '手術感染監測',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
