import { Box, Typography, Paper, Stepper, Step, StepLabel, StepContent, Alert, Grid } from '@mui/material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'

const ECQM_STRUCTURE_CODE = `// eCQM Population Criteria Flow (Proportion Measure)
//
//   Initial Population (IPP)
//       |
//       v
//   Denominator (= IPP or subset)
//       |
//       +---> Denominator Exclusion (removed from denominator)
//       |
//       v
//   Denominator (after exclusions)
//       |
//       +---> Numerator (meets quality goal)
//       |
//       +---> Denominator Exception (excluded with reason)
//
// Quality Rate = Numerator / (Denominator - Exclusions - Exceptions)`

const SCORING_TYPES_CODE = `// Scoring Types:
//
// 1. Proportion: Numerator / Denominator (most common)
//    Example: % of diabetic patients with HbA1c < 9%
//
// 2. Ratio: Numerator / Denominator (different populations)
//    Example: falls per 1000 patient-days
//
// 3. Continuous Variable: aggregate function on population
//    Example: median time from ED arrival to departure
//
// 4. Cohort: membership only, no numerator/denominator
//    Example: patients eligible for a care program`

const DIABETES_HEADER_CODE = `library DiabetesHbA1cControl version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

// Value sets
valueset "Diabetes":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001'
valueset "HbA1c Lab Test":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.198.12.1013'
valueset "Hospice Care":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.1003'

// Code systems
codesystem "LOINC": 'http://loinc.org'
code "HbA1c": '4548-4' from "LOINC"
  display 'Hemoglobin A1c/Hemoglobin.total in Blood'

// Measurement Period
parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01T00:00:00, @2024-12-31T23:59:59]

context Patient`

const DIABETES_IPP_CODE = `// Initial Population (IPP):
// Patients aged 18-75 with diabetes diagnosis
define "Initial Population":
  AgeInYearsAt(start of "Measurement Period") in Interval[18, 75]
    and exists(
      [Condition: "Diabetes"] D
        where D.clinicalStatus.coding[0].code = 'active'
          and Interval[D.onset, D.abatement]
            overlaps "Measurement Period"
    )`

const DIABETES_DENOM_CODE = `// Denominator: same as Initial Population (most common pattern)
define "Denominator":
  "Initial Population"`

const DIABETES_NUMER_CODE = `// Numerator: patients with most recent HbA1c < 9%
define "Most Recent HbA1c":
  First(
    [Observation: "HbA1c Lab Test"] O
      where O.effective during "Measurement Period"
        and O.status in { 'final', 'amended' }
      sort by effective desc
  )

define "Numerator":
  "Most Recent HbA1c".value as Quantity < 9 '%'`

const DIABETES_EXCL_CODE = `// Denominator Exclusion: hospice patients
define "Denominator Exclusion":
  exists(
    [Encounter: "Hospice Care"] E
      where E.period overlaps "Measurement Period"
        and E.status = 'finished'
  )`

const DIABETES_COMPLETE_CODE = `library DiabetesHbA1cControl version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

valueset "Diabetes":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001'
valueset "HbA1c Lab Test":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.198.12.1013'
valueset "Hospice Care":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.1003'

codesystem "LOINC": 'http://loinc.org'
code "HbA1c": '4548-4' from "LOINC"
  display 'Hemoglobin A1c/Hemoglobin.total in Blood'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01T00:00:00, @2024-12-31T23:59:59]

context Patient

define "Initial Population":
  AgeInYearsAt(start of "Measurement Period") in Interval[18, 75]
    and exists(
      [Condition: "Diabetes"] D
        where D.clinicalStatus.coding[0].code = 'active'
          and Interval[D.onset, D.abatement]
            overlaps "Measurement Period"
    )

define "Denominator":
  "Initial Population"

define "Most Recent HbA1c":
  First(
    [Observation: "HbA1c Lab Test"] O
      where O.effective during "Measurement Period"
        and O.status in { 'final', 'amended' }
      sort by effective desc
  )

define "Numerator":
  "Most Recent HbA1c".value as Quantity < 9 '%'

define "Denominator Exclusion":
  exists(
    [Encounter: "Hospice Care"] E
      where E.period overlaps "Measurement Period"
        and E.status = 'finished'
  )`

const TESTING_BUNDLE_CODE = `// Test Patient Bundle (JSON FHIR Bundle)
// Patient: 45-year-old with diabetes, HbA1c = 7.2%
// Expected: IPP=true, Denom=true, Numer=true
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "id": "test-patient-1",
        "birthDate": "1979-03-15",
        "gender": "male"
      }
    },
    {
      "resource": {
        "resourceType": "Condition",
        "id": "diabetes-dx",
        "subject": { "reference": "Patient/test-patient-1" },
        "code": {
          "coding": [{
            "system": "http://hl7.org/fhir/sid/icd-10-cm",
            "code": "E11.9",
            "display": "Type 2 diabetes without complications"
          }]
        },
        "clinicalStatus": {
          "coding": [{ "code": "active" }]
        },
        "onsetDateTime": "2020-01-01"
      }
    },
    {
      "resource": {
        "resourceType": "Observation",
        "id": "hba1c-result",
        "subject": { "reference": "Patient/test-patient-1" },
        "code": {
          "coding": [{
            "system": "http://loinc.org",
            "code": "4548-4",
            "display": "HbA1c"
          }]
        },
        "status": "final",
        "effectiveDateTime": "2024-06-15",
        "valueQuantity": {
          "value": 7.2,
          "unit": "%",
          "system": "http://unitsofmeasure.org"
        }
      }
    }
  ]
}`

const CMS146_CODE = `library CMS146 version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

// CMS146: Appropriate Testing for Pharyngitis
// Measures % of children/adults with pharyngitis who
// received a group A strep test before antibiotics

valueset "Pharyngitis":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.102.12.1011'
valueset "Group A Strep Test":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.198.12.1012'
valueset "Antibiotics for Pharyngitis":
  'http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.196.12.1001'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01T00:00:00, @2024-12-31T23:59:59]

context Patient

// IPP: Encounter with pharyngitis diagnosis
define "Pharyngitis Encounter":
  [Encounter] E
    with [Condition: "Pharyngitis"] C
      such that C.encounter.reference =
        'Encounter/' + E.id
    where E.period during "Measurement Period"
      and E.status = 'finished'

define "Initial Population":
  exists("Pharyngitis Encounter")
    and AgeInYearsAt(start of "Measurement Period") >= 3

// Denominator: patients who received antibiotics
define "Denominator":
  "Initial Population"
    and exists(
      [MedicationRequest: "Antibiotics for Pharyngitis"] MR
        where MR.authoredOn during "Measurement Period"
    )

// Numerator: had strep test before or within 3 days
// of antibiotic prescription
define "Numerator":
  exists(
    from
      [Observation: "Group A Strep Test"] T,
      [MedicationRequest: "Antibiotics for Pharyngitis"] MR
    where T.effective 3 days or less before MR.authoredOn
      or T.effective same day or after MR.authoredOn
  )`

const CMS146_TWCORE_CODE = `// TWCORE equivalent mappings for CMS146:
//
// Original (US)                  | TWCORE (Taiwan)
// -------------------------------|---------------------------
// ICD-10-CM pharyngitis codes    | ICD-10-CM-TW: J02.x, J03.x
// (same ICD codes, TW edition)   |
//                                |
// LOINC Strep test codes         | LOINC: same codes apply
// (international standard)       | (68954-7, 49610-9, etc.)
//                                |
// RxNorm antibiotics             | NHI Medication codes
// (US drug codes)                | (健保藥品代碼)
//                                |
// Example NHI antibiotic codes:
//   A024265100 — Amoxicillin 500mg
//   A029179100 — Azithromycin 250mg
//   A036498100 — Cephalexin 500mg

codesystem "NHI-Med":
  'http://www.nhi.gov.tw/medication'
codesystem "ICD10-TW":
  'http://hl7.org/fhir/sid/icd-10-cm'

// Taiwan-specific pharyngitis codes
code "Acute Pharyngitis": 'J02.9' from "ICD10-TW"
code "Acute Tonsillitis": 'J03.90' from "ICD10-TW"

// NHI antibiotic codes
code "Amoxicillin": 'A024265100' from "NHI-Med"
code "Azithromycin": 'A029179100' from "NHI-Med"`

export default function EcqmTutorial() {
  const { t } = useTranslation('landing')

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.ecqmTutorial.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.ecqmTutorial.subtitle')}
      </Typography>

      <Stepper orientation="vertical" sx={{ '& .MuiStepConnector-line': { minHeight: 20 } }}>
        {/* Step 1: Understanding eCQM Structure */}
        <Step active expanded>
          <StepLabel>
            <Typography variant="h6" fontWeight={700} color="primary.main">
              {t('learn.ecqmTutorial.step1.title')}
            </Typography>
          </StepLabel>
          <StepContent>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, lineHeight: 1.7 }}>
              {t('learn.ecqmTutorial.step1.description')}
            </Typography>

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step1.populationFlow')}
                  </Typography>
                  <CodeBlock code={ECQM_STRUCTURE_CODE} maxHeight={300} />
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step1.scoringTypes')}
                  </Typography>
                  <CodeBlock code={SCORING_TYPES_CODE} maxHeight={300} />
                </Paper>
              </Grid>
            </Grid>

            <Alert severity="info" sx={{ mt: 2, borderRadius: 2 }}>
              {t('learn.ecqmTutorial.step1.keyPoints')}
            </Alert>
          </StepContent>
        </Step>

        {/* Step 2: Building a Proportion Measure */}
        <Step active expanded>
          <StepLabel>
            <Typography variant="h6" fontWeight={700} color="primary.main">
              {t('learn.ecqmTutorial.step2.title')}
            </Typography>
          </StepLabel>
          <StepContent>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, lineHeight: 1.7 }}>
              {t('learn.ecqmTutorial.step2.description')}
            </Typography>

            <Grid container spacing={2}>
              <Grid size={12}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step2.libraryHeader')}
                  </Typography>
                  <CodeBlock code={DIABETES_HEADER_CODE} maxHeight={360} />
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step2.ipp')}
                  </Typography>
                  <CodeBlock code={DIABETES_IPP_CODE} maxHeight={260} />
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step2.denominator')}
                  </Typography>
                  <CodeBlock code={DIABETES_DENOM_CODE} maxHeight={260} />
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step2.numerator')}
                  </Typography>
                  <CodeBlock code={DIABETES_NUMER_CODE} maxHeight={280} />
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step2.denomExclusion')}
                  </Typography>
                  <CodeBlock code={DIABETES_EXCL_CODE} maxHeight={280} />
                </Paper>
              </Grid>
              <Grid size={12}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step2.completeCql')}
                  </Typography>
                  <CodeBlock code={DIABETES_COMPLETE_CODE} maxHeight={600} />
                </Paper>
              </Grid>
            </Grid>
          </StepContent>
        </Step>

        {/* Step 3: Testing and Validation */}
        <Step active expanded>
          <StepLabel>
            <Typography variant="h6" fontWeight={700} color="primary.main">
              {t('learn.ecqmTutorial.step3.title')}
            </Typography>
          </StepLabel>
          <StepContent>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, lineHeight: 1.7 }}>
              {t('learn.ecqmTutorial.step3.description')}
            </Typography>

            <Grid container spacing={2}>
              <Grid size={12}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step3.testBundle')}
                  </Typography>
                  <CodeBlock code={TESTING_BUNDLE_CODE} maxHeight={500} />
                </Paper>
              </Grid>
            </Grid>

            <Alert severity="warning" sx={{ mt: 2, borderRadius: 2 }}>
              {t('learn.ecqmTutorial.step3.debuggingTips')}
            </Alert>
          </StepContent>
        </Step>

        {/* Step 4: CMS Measure Example */}
        <Step active expanded>
          <StepLabel>
            <Typography variant="h6" fontWeight={700} color="primary.main">
              {t('learn.ecqmTutorial.step4.title')}
            </Typography>
          </StepLabel>
          <StepContent>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, lineHeight: 1.7 }}>
              {t('learn.ecqmTutorial.step4.description')}
            </Typography>

            <Grid container spacing={2}>
              <Grid size={12}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step4.cms146Cql')}
                  </Typography>
                  <CodeBlock code={CMS146_CODE} maxHeight={600} />
                </Paper>
              </Grid>
              <Grid size={12}>
                <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                  <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>
                    {t('learn.ecqmTutorial.step4.twcoreMapping')}
                  </Typography>
                  <CodeBlock code={CMS146_TWCORE_CODE} maxHeight={500} />
                </Paper>
              </Grid>
            </Grid>

            <Alert severity="info" sx={{ mt: 2, borderRadius: 2 }}>
              {t('learn.ecqmTutorial.step4.keyPoints')}
            </Alert>
          </StepContent>
        </Step>
      </Stepper>
    </Box>
  )
}
