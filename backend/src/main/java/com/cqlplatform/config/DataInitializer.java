package com.cqlplatform.config;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MeasureDefinitionRepository measureDefinitionRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            UserEntity admin = UserEntity.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin"))
                    .role(UserEntity.Role.ADMIN)
                    .enabled(true)
                    .build();
            admin.setEmailWithHash("admin@cqlplatform.com");
            userRepository.save(admin);
            log.warn(
                    "=== Default admin user created (username: admin, password: admin). CHANGE THIS IN PRODUCTION! ===");

            UserEntity demo = UserEntity.builder()
                    .username("demo")
                    .password(passwordEncoder.encode("password"))
                    .role(UserEntity.Role.USER)
                    .enabled(true)
                    .build();
            demo.setEmailWithHash("demo@cqlplatform.com");
            userRepository.save(demo);
            log.debug("=== Demo user created (username: demo). ===");

            // Seed demo eQCM measure
            seedDemoMeasure();
        }
    }

    private void seedDemoMeasure() {
        String cqlContent = """
                library DiabetesHbA1cRate version '1.0.0'
                using FHIR version '4.0.1'
                include FHIRHelpers version '4.0.1' called FHIRHelpers

                // ============================================================
                // 糖尿病病人醣化血紅素(HbA1c)或糖化白蛋白(glycated albumin)執行率
                // ============================================================
                // 分母: 門診主次診斷為糖尿病且使用糖尿病用藥之病人數
                //   - 糖尿病: 任一主、次診斷之 ICD-10-CM 前三碼為 E08-E13
                //   - 糖尿病用藥: ATC 前3碼為 A10
                //   - 兩個條件須發生在同一處方案件
                //
                // 分子: 分母ID中，在統計期間有執行 HbA1c 或 glycated albumin 檢驗人數
                //   - HbA1c: 醫令代碼前五碼為 09006
                //   - Glycated albumin: 醫令代碼前五碼為 09139
                //
                // 資料範圍: 西醫基層、西醫醫院

                // --- Code Systems ---
                codesystem "ICD10CM": 'http://hl7.org/fhir/sid/icd-10-cm'
                codesystem "ATC": 'http://www.whocc.no/atc'
                codesystem "LOINC": 'http://loinc.org'
                codesystem "TW-NHI-ORDER": 'https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw'
                codesystem "ActCode": 'http://terminology.hl7.org/CodeSystem/v3-ActCode'

                // --- Codes ---
                code "HbA1c LOINC": '4548-4' from "LOINC" display 'Hemoglobin A1c/Hemoglobin.total in Blood'
                code "Glycated Albumin LOINC": '13980-8' from "LOINC" display 'Albumin glycated/Albumin.total in Serum or Plasma'
                code "AMB": 'AMB' from "ActCode" display 'ambulatory'
                code "IMP": 'IMP' from "ActCode" display 'inpatient encounter'

                // --- Measurement Period (parameter) ---
                parameter "Measurement Period" Interval<DateTime>
                  default Interval[@2024-01-01T00:00:00.0, @2024-12-31T23:59:59.999]

                context Patient

                // =====================
                // Helper Definitions
                // =====================

                // All outpatient encounters in the measurement period
                define "Outpatient Encounters":
                  [Encounter] E
                    where E.status = 'finished'
                      and (E.class ~ "AMB" or E.class ~ "IMP")
                      and E.period overlaps "Measurement Period"

                // Diabetes diagnosis: ICD-10-CM E08-E13
                define "Diabetes Conditions":
                  [Condition] C
                    where exists(C.code.coding Coding
                      where Coding.system = 'http://hl7.org/fhir/sid/icd-10-cm'
                        and (
                          StartsWith(Coding.code.value, 'E08')
                          or StartsWith(Coding.code.value, 'E09')
                          or StartsWith(Coding.code.value, 'E10')
                          or StartsWith(Coding.code.value, 'E11')
                          or StartsWith(Coding.code.value, 'E12')
                          or StartsWith(Coding.code.value, 'E13')
                        )
                    )

                // Diabetes medications: ATC code starting with A10
                define "Diabetes Medications":
                  [MedicationRequest] MR
                    where MR.status in { 'active', 'completed' }
                      and MR.authoredOn during "Measurement Period"
                      and exists(MR.medication.coding Coding
                        where Coding.system = 'http://www.whocc.no/atc'
                          and StartsWith(Coding.code.value, 'A10')
                      )

                // =====================
                // Population Definitions
                // =====================

                // Initial Population: all patients
                define "Initial Population":
                  exists "Outpatient Encounters"

                // Denominator: patients with diabetes diagnosis AND diabetes medication
                // in the same encounter context during the measurement period
                define "Denominator":
                  exists "Diabetes Conditions"
                    and exists "Diabetes Medications"

                // HbA1c lab results (NHI order code prefix 09006, or LOINC 4548-4)
                define "HbA1c Results":
                  [Observation] O
                    where O.status in { 'final', 'amended', 'corrected' }
                      and (
                        O.code ~ "HbA1c LOINC"
                        or exists(O.code.coding Coding
                          where StartsWith(Coding.code.value, '09006')
                        )
                      )
                      and O.effective during "Measurement Period"

                // Glycated Albumin lab results (NHI order code prefix 09139, or LOINC 13980-8)
                define "Glycated Albumin Results":
                  [Observation] O
                    where O.status in { 'final', 'amended', 'corrected' }
                      and (
                        O.code ~ "Glycated Albumin LOINC"
                        or exists(O.code.coding Coding
                          where StartsWith(Coding.code.value, '09139')
                        )
                      )
                      and O.effective during "Measurement Period"

                // Numerator: denominator patients who have HbA1c OR glycated albumin test
                define "Numerator":
                  exists "HbA1c Results"
                    or exists "Glycated Albumin Results"
                """;

        List<PopulationDefinition> populations = List.of(
                PopulationDefinition.builder()
                        .populationType(com.cqlplatform.model.measure.PopulationTypeConstants.INITIAL_POPULATION)
                        .criteriaExpression("Initial Population")
                        .description("門診就醫之病人")
                        .build(),
                PopulationDefinition.builder()
                        .populationType(com.cqlplatform.model.measure.PopulationTypeConstants.DENOMINATOR)
                        .criteriaExpression("Denominator")
                        .description("門診主次診斷為糖尿病且使用糖尿病用藥之病人數")
                        .build(),
                PopulationDefinition.builder()
                        .populationType(com.cqlplatform.model.measure.PopulationTypeConstants.NUMERATOR)
                        .criteriaExpression("Numerator")
                        .description("分母ID中，在統計期間有執行醣化血紅素(HbA1c)或糖化白蛋白(glycated albumin)檢驗人數")
                        .build());

        List<GroupDefinition> groups = List.of(
                GroupDefinition.builder()
                        .groupId("group-1")
                        .description("糖尿病病人 HbA1c/Glycated Albumin 執行率")
                        .populationBasis("boolean")
                        .populations(populations)
                        .scoringUnit("%")
                        .build());

        MeasureDefinitionEntity measure = MeasureDefinitionEntity.builder()
                .name("DiabetesHbA1cRate")
                .version("1.0.0")
                .title("糖尿病病人醣化血紅素(HbA1c)或糖化白蛋白(glycated albumin)執行率")
                .description("衡量門診主次診斷為糖尿病且使用糖尿病用藥之病人，在統計期間有執行醣化血紅素(HbA1c)或糖化白蛋白(glycated albumin)檢驗的比率。")
                .status("active")
                .scoringType(com.cqlplatform.model.measure.ScoringTypeConstants.PROPORTION)
                .cqlLibraryId("DiabetesHbA1cRate")
                .cqlContent(cqlContent)
                .groupDefinitionList(groups)
                .ownerUsername("demo")
                .createdBy("demo")
                .accessLevel("public")
                .setting("outpatient")
                .rationale("糖尿病為重要慢性病，定期檢測HbA1c或糖化白蛋白有助於監控血糖控制情形，及早發現異常並調整治療方案。")
                .clinicalGuidance("糖尿病 - 任一主、次診斷之ICD-10-CM前三碼為E08-E13之門診案件。\\n"
                        + "糖尿病用藥 - 指ATC前3碼為A10。\\n"
                        + "HbA1c案件係指申報醫令代碼前五碼為09006之案件。\\n"
                        + "糖化白蛋白係指報醫令代碼前五碼為09139之案件。\\n"
                        + "計算符合分母條件之ID時，主次診斷為糖尿病且使用糖尿病用藥這兩個條件限定要發生在同處方案件。\\n"
                        + "資料範圍：西醫基層、西醫醫院。")
                .steward("衛生福利部中央健康保險署")
                .build();

        measureDefinitionRepository.save(measure);
        log.info("=== Demo eQCM measure 'DiabetesHbA1cRate' created for user 'demo'. ===");
    }
}
