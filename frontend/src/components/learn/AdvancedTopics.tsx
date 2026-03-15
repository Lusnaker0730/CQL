import { Box, Typography, Paper, Grid } from '@mui/material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'

const AGGREGATE_CODE = `library AggregateExample version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

context Patient

define "All Observations":
  [Observation]

define "Observation Count":
  Count([Observation])

define "Average Systolic BP":
  Avg(
    [Observation] O
      where O.code.coding[0].code = '8480-6'
      return (O.value as Quantity).value
  )

define "Total Lab Tests This Year":
  Count(
    [Observation] O
      where O.effective during Interval[@2024-01-01, @2024-12-31]
  )`

const MULTI_SOURCE_CODE = `library MultiSourceExample version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

context Patient

define "Diabetic Patients on Metformin":
  from [Condition] C, [MedicationRequest] M
    where C.code.coding[0].code in { 'E11.9', 'E11.65' }
      and M.medication.coding[0].display = 'Metformin'
      and C.subject = M.subject
    return { condition: C, medication: M }

define "Encounters with Lab Results":
  from [Encounter] E, [Observation] O
    where O.encounter.reference = 'Encounter/' + E.id
    return {
      encounterDate: E.period.start,
      labCode: O.code.coding[0].code,
      labValue: O.value
    }`

const INTERVAL_CODE = `library IntervalExample version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01T00:00:00, @2024-12-31T23:59:59]

context Patient

// Overlaps: Two intervals share at least one point
define "Active Conditions During Period":
  [Condition] C
    where Interval[C.onset, C.abatement] overlaps "Measurement Period"

// During: One interval is entirely within another
define "Labs During Measurement Period":
  [Observation] O
    where O.effective during "Measurement Period"

// Meets: One interval ends exactly where another begins
define "Consecutive Encounters":
  from [Encounter] E1, [Encounter] E2
    where E1.period meets E2.period

// Width: Calculate the length of an interval
define "Encounter Duration Days":
  [Encounter] E
    return duration in days of E.period`

const TUPLE_CODE = `library TupleExample version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

context Patient

// Construct a Tuple with named fields
define "Patient Summary":
  Tuple {
    patientId: Patient.id,
    birthDate: Patient.birthDate,
    age: AgeInYears(),
    isAdult: AgeInYears() >= 18
  }

// Return Tuples from a query
define "Lab Summary":
  [Observation] O
    return Tuple {
      code: O.code.coding[0].code,
      value: (O.value as Quantity).value,
      unit: (O.value as Quantity).unit,
      date: O.effective
    }

// Access Tuple fields
define "Patient Age":
  "Patient Summary".age`

const TOPICS = [
  { key: 'aggregate', code: AGGREGATE_CODE },
  { key: 'multiSource', code: MULTI_SOURCE_CODE },
  { key: 'intervals', code: INTERVAL_CODE },
  { key: 'tuples', code: TUPLE_CODE },
] as const

export default function AdvancedTopics() {
  const { t } = useTranslation('landing')

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} gutterBottom color="secondary.main">
        {t('learn.advanced.title')}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
        {t('learn.advanced.subtitle')}
      </Typography>

      <Grid container spacing={3}>
        {TOPICS.map(({ key, code }) => (
          <Grid key={key} size={12}>
            <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
              <Typography variant="h6" fontWeight={700} gutterBottom color="primary.main">
                {t(`learn.advanced.${key}.title`)}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2, lineHeight: 1.7 }}>
                {t(`learn.advanced.${key}.description`)}
              </Typography>
              <CodeBlock code={code} maxHeight={400} />
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}
