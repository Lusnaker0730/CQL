import { Box, Typography, Paper, Grid, Alert } from '@mui/material'
import { useTranslation } from 'react-i18next'
import CodeBlock from './CodeBlock'

const ERROR_FIXES: Record<string, string> = {
  resolveType: `library MyLibrary version '1.0.0'
using FHIR version '4.0.1'   // <-- Add this line
include FHIRHelpers version '4.0.1'

context Patient

define "All Conditions":
  [Condition]`,

  resolveContext: `library MyLibrary version '1.0.0'
using FHIR version '4.0.1'

// Correct: use 'Patient' (singular, capitalized)
context Patient

define "Example":
  [Observation]`,

  invokeMember: `library MyLibrary version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

context Patient

// Wrong: [Observation].value  (list, not single element)
// Correct: use a query alias
define "Observation Values":
  [Observation] O
    where O.value is not null
    return O.value`,

  resolveCodeSystem: `library MyLibrary version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

// Declare code systems before using them
codesystem "LOINC": 'http://loinc.org'
codesystem "ICD10": 'http://hl7.org/fhir/sid/icd-10-cm'

code "HbA1c": '4548-4' from "LOINC"

context Patient

define "HbA1c Tests":
  [Observation: "HbA1c"]`,

  timeout: `// Before (complex nested query):
define "Complex":
  [Observation] O
    where exists (
      [Condition] C
        where exists (
          [MedicationRequest] M
            where M.subject = C.subject
        )
    )

// After (split into smaller definitions):
define "Active Medications":
  [MedicationRequest] M
    where M.status = 'active'

define "Active Conditions":
  [Condition] C
    where C.clinicalStatus.coding[0].code = 'active'

define "Patients with Both":
  exists "Active Conditions"
    and exists "Active Medications"`,

  noResults: `library MyLibrary version '1.0.0'
using FHIR version '4.0.1'
include FHIRHelpers version '4.0.1'

// Check: Is the Measurement Period correct?
parameter "Measurement Period" Interval<DateTime>
  default Interval[@2024-01-01T00:00:00, @2024-12-31T23:59:59]

context Patient

// Start simple to verify data exists
define "All Conditions":
  [Condition]

define "Condition Count":
  Count([Condition])

// Then add filters gradually
define "Active Conditions":
  [Condition] C
    where C.clinicalStatus.coding[0].code = 'active'`,
}

const ERROR_KEYS = ['resolveType', 'resolveContext', 'invokeMember', 'resolveCodeSystem', 'timeout', 'noResults'] as const

export default function TroubleshootingGuide() {
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
        {t('learn.troubleshooting.title')}
      </Typography>
      <Typography
        variant="body1"
        sx={{
          color: "text.secondary",
          mb: 3
        }}>
        {t('learn.troubleshooting.subtitle')}
      </Typography>
      <Grid container spacing={3}>
        {ERROR_KEYS.map((key) => (
          <Grid key={key} size={12}>
            <Paper elevation={0} sx={{ p: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
              <Typography
                variant="h6"
                gutterBottom
                sx={{
                  fontWeight: 700,
                  color: "primary.main"
                }}>
                {t(`learn.troubleshooting.errors.${key}.title`)}
              </Typography>

              <Alert severity="error" sx={{ mb: 2, fontFamily: 'monospace', fontSize: '0.85rem' }}>
                {t(`learn.troubleshooting.errors.${key}.error`)}
              </Alert>

              <Alert severity="success" sx={{ mb: 2 }}>
                <Typography variant="body2" gutterBottom sx={{
                  fontWeight: 600
                }}>
                  {t(`learn.troubleshooting.errors.${key}.fix`)}
                </Typography>
                <Typography variant="body2">
                  {t(`learn.troubleshooting.errors.${key}.solution`)}
                </Typography>
              </Alert>

              <CodeBlock code={ERROR_FIXES[key]} maxHeight={300} />
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
