import { Box, Typography, Paper, Accordion, AccordionSummary, AccordionDetails, Alert, Grid } from '@mui/material'
import { ExpandMore as ExpandMoreIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'

const CONDITIONAL_IF_CODE = `// if/then/else — basic conditional
define "Risk Level":
  if AgeInYears() >= 65 then 'High Risk'
  else if AgeInYears() >= 45 then 'Moderate Risk'
  else 'Low Risk'`

const CONDITIONAL_CASE_CODE = `// Case expression — without operand (general form)
define "BMI Category":
  case
    when "BMI Value" < 18.5 then 'Underweight'
    when "BMI Value" < 25.0 then 'Normal'
    when "BMI Value" < 30.0 then 'Overweight'
    else 'Obese'
  end

// Case expression — with operand (matching form)
define "Encounter Type Label":
  case "Encounter Class Code"
    when 'AMB' then 'Ambulatory'
    when 'IMP' then 'Inpatient'
    when 'EMER' then 'Emergency'
    else 'Other'
  end`

const CONDITIONAL_CLINICAL_CODE = `// Clinical example: cardiovascular risk stratification
define "Cardiovascular Risk Score":
  case
    when "Has Diabetes" and "Has Hypertension" and AgeInYears() >= 60
      then 'Very High'
    when "Has Diabetes" or ("Has Hypertension" and AgeInYears() >= 55)
      then 'High'
    when "Total Cholesterol" > 240 'mg/dL'
      then 'Moderate'
    else 'Low'
  end`

const NULL_LOGIC_CODE = `// Three-valued logic: true, false, null
// null represents unknown or missing data

define "Is Null Check":
  [Observation] O where O.value is null

define "Is Not Null Check":
  [Observation] O where O.value is not null`

const NULL_COALESCE_CODE = `// Coalesce returns the first non-null argument
define "Effective Date":
  Coalesce(
    [Observation] O return O.effective,
    [Observation] O return O.issued,
    @2024-01-01  // fallback default
  )

// Null propagation in arithmetic
// 5 + null = null,  true and null = null
define "Safe Calculation":
  if "Lab Value" is not null
    then "Lab Value" * 2
    else 0`

const NULL_CLINICAL_CODE = `// Clinical example: handling missing lab values
define "Latest eGFR with Fallback":
  Coalesce(
    (First(
      [Observation] O
        where O.code ~ "eGFR LOINC"
        sort by effective desc
    ).value as Quantity).value,
    -1  // sentinel value indicating missing data
  )

define "Renal Function Status":
  if "Latest eGFR with Fallback" = -1 then 'Unknown'
  else if "Latest eGFR with Fallback" < 30 then 'Severe Impairment'
  else if "Latest eGFR with Fallback" < 60 then 'Moderate Impairment'
  else 'Normal or Mild'`

const TYPE_CAST_CODE = `// Type casting with 'as'
define "Quantity Observations":
  [Observation] O
    where O.value is Quantity
    return (O.value as Quantity)

// Type testing with 'is'
define "Has Quantity Value":
  [Observation] O
    where O.value is Quantity

define "Has CodeableConcept Value":
  [Observation] O
    where O.value is CodeableConcept`

const TYPE_CONVERT_CODE = `// Conversion functions
define "String to Integer":
  ToInteger('42')        // returns 42

define "Integer to String":
  ToString(42)           // returns '42'

define "String to Decimal":
  ToDecimal('3.14')      // returns 3.14

define "String to DateTime":
  ToDateTime('2024-01-15T08:30:00')

// convert expression
define "Quantity to String":
  convert 120 'mmHg' to String`

const TYPE_CLINICAL_CODE = `// Clinical example: polymorphic Observation.value handling
// Observation.value can be Quantity, CodeableConcept, string, etc.
define "Observation Value Display":
  [Observation] O
    return
      if O.value is Quantity then
        ToString((O.value as Quantity).value) + ' ' +
          (O.value as Quantity).unit
      else if O.value is CodeableConcept then
        (O.value as CodeableConcept).coding[0].display
      else if O.value is FHIR.string then
        O.value as FHIR.string
      else
        'Unknown type'`

const DATETIME_CONSTRUCT_CODE = `// Date/DateTime construction
define "Specific Date":
  Date(2024, 1, 15)

define "Specific DateTime":
  DateTime(2024, 1, 15, 8, 30, 0)

// Component extraction
define "Birth Year":
  year from Patient.birthDate

define "Encounter Month":
  month from start of [Encounter] E return E.period`

const DATETIME_DURATION_CODE = `// Duration calculation
define "Days Since Last Visit":
  duration in days between
    (First([Encounter] E sort by period.start desc)).period.start
    and Today()

// Difference (calendar boundary crossings)
define "Patient Age":
  difference in years between
    Patient.birthDate and Today()

// Date arithmetic
define "Thirty Days Ago":
  Today() - 30 days

define "One Year From Now":
  Today() + 1 year`

const DATETIME_CLINICAL_CODE = `// Clinical example: measurement period and age calculation
parameter "Measurement Period"
  Interval<DateTime>
  default Interval[@2024-01-01T00:00:00, @2024-12-31T23:59:59]

define "Patient Age at Start":
  AgeInYearsAt(start of "Measurement Period")

define "Is Adult During Period":
  "Patient Age at Start" >= 18

define "Recent Labs (Last 90 Days)":
  [Observation] O
    where O.effective during
      Interval[Today() - 90 days, Today()]

define "Overdue for Annual Screening":
  not exists(
    [Observation: "HbA1c LOINC"] O
      where O.effective during "Measurement Period"
  )`

const STRING_BASIC_CODE = `// Concatenation
define "Full Name":
  Patient.name[0].given[0] + ' ' + Patient.name[0].family

// Null-safe concatenation with &
// (null & 'text' = 'text', unlike + which returns null)
define "Safe Full Name":
  Patient.name[0].given[0] & ' ' & Patient.name[0].family

// String functions
define "Name Length":
  Length(Patient.name[0].family)

define "Upper Case Name":
  Upper(Patient.name[0].family)

define "Lower Case Name":
  Lower(Patient.name[0].family)`

const STRING_ADVANCED_CODE = `// Substring (0-based index)
define "First Three Chars":
  Substring(Patient.id, 0, 3)

// PositionOf — find substring position
define "Dash Position":
  PositionOf('-', Patient.id)

// Split and Combine
define "Name Parts":
  Split('Smith-Jones', '-')
  // returns { 'Smith', 'Jones' }

define "Rejoin Name":
  Combine({ 'Smith', 'Jones' }, ' ')
  // returns 'Smith Jones'

// StartsWith / EndsWith
define "Is Taiwan ID":
  StartsWith(Patient.identifier[0].value, 'A')

// Matches (regex)
define "Valid ID Format":
  Matches(Patient.identifier[0].value, '[A-Z][12]\\d{8}')`

const STRING_CLINICAL_CODE = `// Clinical example: parsing structured text
define "ICD Code Category":
  if StartsWith("Primary Diagnosis Code", 'E11')
    then 'Type 2 Diabetes'
  else if StartsWith("Primary Diagnosis Code", 'E10')
    then 'Type 1 Diabetes'
  else if StartsWith("Primary Diagnosis Code", 'I10')
    then 'Essential Hypertension'
  else
    'Other: ' & Substring("Primary Diagnosis Code", 0, 3)

define "Observation Display Text":
  Combine(
    [Observation] O
      return O.code.coding[0].display & ': ' &
        ToString((O.value as Quantity).value) & ' ' &
        (O.value as Quantity).unit,
    '; '
  )`

const SECTIONS = [
  {
    key: 'conditionals',
    codes: [
      { code: CONDITIONAL_IF_CODE, label: 'ifThenElse' },
      { code: CONDITIONAL_CASE_CODE, label: 'caseWhen' },
      { code: CONDITIONAL_CLINICAL_CODE, label: 'clinicalExample' },
    ],
  },
  {
    key: 'nullHandling',
    codes: [
      { code: NULL_LOGIC_CODE, label: 'threeValuedLogic' },
      { code: NULL_COALESCE_CODE, label: 'coalesceAndPropagation' },
      { code: NULL_CLINICAL_CODE, label: 'clinicalExample' },
    ],
  },
  {
    key: 'typeSystem',
    codes: [
      { code: TYPE_CAST_CODE, label: 'castingAndTesting' },
      { code: TYPE_CONVERT_CODE, label: 'conversionFunctions' },
      { code: TYPE_CLINICAL_CODE, label: 'clinicalExample' },
    ],
  },
  {
    key: 'dateTime',
    codes: [
      { code: DATETIME_CONSTRUCT_CODE, label: 'constructionAndExtraction' },
      { code: DATETIME_DURATION_CODE, label: 'durationAndArithmetic' },
      { code: DATETIME_CLINICAL_CODE, label: 'clinicalExample' },
    ],
  },
  {
    key: 'stringOps',
    codes: [
      { code: STRING_BASIC_CODE, label: 'basicOperations' },
      { code: STRING_ADVANCED_CODE, label: 'advancedOperations' },
      { code: STRING_CLINICAL_CODE, label: 'clinicalExample' },
    ],
  },
] as const

export default function LanguageReference() {
  const { t } = useTranslation('landing')

  return (
    <Box>
      <Typography
        variant="h4"
        gutterBottom
        sx={{
          fontWeight: 700,
          color: "secondary.main"
        }}>
        {t('learn.languageRef.title')}
      </Typography>
      <Typography
        variant="body1"
        sx={{
          color: "text.secondary",
          mb: 3
        }}>
        {t('learn.languageRef.subtitle')}
      </Typography>
      {SECTIONS.map(({ key, codes }) => (
        <Accordion key={key} defaultExpanded sx={{ mb: 2, borderRadius: '12px !important', '&:before': { display: 'none' }, border: '1px solid', borderColor: 'divider' }} elevation={0}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 700,
                color: "primary.main"
              }}>
              {t(`learn.languageRef.${key}.title`)}
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Typography
              variant="body2"
              sx={{
                color: "text.secondary",
                mb: 2,
                lineHeight: 1.7
              }}>
              {t(`learn.languageRef.${key}.description`)}
            </Typography>

            <Grid container spacing={2}>
              {codes.map(({ code, label }) => (
                <Grid key={label} size={12}>
                  <Paper elevation={0} sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 2 }}>
                    <Typography
                      variant="subtitle2"
                      sx={{
                        fontWeight: 600,
                        mb: 1
                      }}>
                      {t(`learn.languageRef.${key}.${label}`)}
                    </Typography>
                    <CodeBlock code={code} maxHeight={360} />
                  </Paper>
                </Grid>
              ))}
            </Grid>

            <Alert severity="info" sx={{ mt: 2, borderRadius: 2 }}>
              {t(`learn.languageRef.${key}.keyPoints`)}
            </Alert>
          </AccordionDetails>
        </Accordion>
      ))}
    </Box>
  );
}
